// Behaviour the language-neutral scenarios cannot cover: the real java.net.http transport against a
// local server, thread interruption and toString redaction.
package com.sursolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RuntimeTest {
    private static final String TASK_ID = "tsk_4f7a9c1e2b8d3a6f5c0e9d21";
    private static final Map<String, Object> TASK = Map.of(
            "type", TaskTypes.TurnstileTokenProxyLess,
            "websiteURL", "https://example.com/",
            "websiteKey", "k",
            "userAgent", "ua");

    private interface Handler {
        void handle(HttpExchange exchange, String body) throws IOException;
    }

    private HttpServer server;
    private final List<HttpExchange> exchanges = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private final CountDownLatch release = new CountDownLatch(1);

    private String serve(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            synchronized (exchanges) {
                exchanges.add(exchange);
                bodies.add(body);
            }
            handler.handle(exchange, body);
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        release.countDown();
        Thread.interrupted(); // clear a flag a cancellation test left behind
        if (server != null) server.stop(0);
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void keyTravelsOnlyInTheBodyWithTheSdkUserAgent() throws Exception {
        String base = serve((exchange, body) -> json(exchange, "{\"errorId\":0,\"taskId\":\"" + TASK_ID + "\"}"));
        SurSolverClient client = SurSolverClient.builder(ConformanceTest.TEST_API_KEY).baseUrl(base).build();

        assertEquals(TASK_ID, client.createTask(TASK));

        HttpExchange exchange = exchanges.get(0);
        assertEquals("POST /createTask", exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
        assertTrue(exchange.getRequestHeaders().getFirst("User-Agent").startsWith("sursolver-java/" + SurSolverClient.VERSION));
        assertFalse(SurSolverClient.VERSION.contains("$"), "version.properties was not filtered");
        assertFalse(exchange.getRequestHeaders().toString().contains(ConformanceTest.TEST_API_KEY));
        assertEquals(ConformanceTest.TEST_API_KEY, ((Map<?, ?>) Json.parse(bodies.get(0))).get("clientKey"));
    }

    @Test
    void requestTimeoutIsATransportException() throws Exception {
        String base = serve((exchange, body) -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        // A real 100 ms wait: the behaviour under test is HttpTransport's own deadline handling.
        SurSolverClient client = SurSolverClient.builder(ConformanceTest.TEST_API_KEY)
                .baseUrl(base).requestTimeout(Duration.ofMillis(100)).build();

        TransportException error = assertThrows(TransportException.class, client::getBalance);

        assertTrue(error.getMessage().contains("timed out after 100 ms"), error.getMessage());
        assertFalse(error.mayHaveCreatedTask());
        assertNull(error.status());
    }

    @Test
    void redirectIsNotFollowed() throws Exception {
        String base = serve((exchange, body) -> {
            exchange.getResponseHeaders().set("Location", "/elsewhere");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        SurSolverClient client = SurSolverClient.builder(ConformanceTest.TEST_API_KEY).baseUrl(base).build();

        TransportException error = assertThrows(TransportException.class, () -> client.createTask(TASK));

        assertEquals(307, error.status());
        assertTrue(error.mayHaveCreatedTask());
        assertTrue(error.getMessage().contains("charged"));
        assertEquals(1, exchanges.size());
    }

    @Test
    void httpClientThatFollowsRedirectsIsRejected() {
        HttpClient following = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        assertThrows(IllegalArgumentException.class, () -> new HttpTransport(following));
    }

    @Test
    void interruptDuringPollingKeepsTheTaskId() throws Exception {
        Thread caller = Thread.currentThread();
        String base = serve((exchange, body) -> {
            if (exchange.getRequestURI().getPath().equals("/createTask")) {
                json(exchange, "{\"errorId\":0,\"taskId\":\"" + TASK_ID + "\"}");
            } else {
                caller.interrupt(); // interrupt while the first poll is answered; solve then waits 10 s or stops
                json(exchange, "{\"errorId\":0,\"status\":\"processing\"}");
            }
        });
        SurSolverClient client = SurSolverClient.builder(ConformanceTest.TEST_API_KEY)
                .baseUrl(base).pollInterval(Duration.ofSeconds(10)).build();
        List<String> created = new ArrayList<>();

        assertThrows(InterruptedException.class,
                () -> client.solve(TASK, SolveOptions.defaults().onTaskCreated(created::add)));

        assertEquals(List.of(TASK_ID), created);
    }

    @Test
    void apiErrorsAndToStringNeverShowTheKey() throws Exception {
        String base = serve((exchange, body) -> json(exchange,
                "{\"errorId\":1,\"errorCode\":\"ERROR_KEY_DOES_NOT_EXIST\",\"errorDescription\":\"The clientKey does not match any active API key.\"}"));
        SurSolverClient client = SurSolverClient.builder(ConformanceTest.TEST_API_KEY).baseUrl(base).build();

        AuthenticationException error = assertThrows(AuthenticationException.class, client::getBalance);

        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        assertFalse(trace.toString().contains(ConformanceTest.TEST_API_KEY));
        assertFalse(client.toString().contains(ConformanceTest.TEST_API_KEY));
        assertTrue(client.toString().contains(base));
    }
}
