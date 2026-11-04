// Runs the vendored language-neutral conformance scenarios (conformance/README.md) with a
// scripted fake transport, a virtual clock and a fixed random source.
package com.sursolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class ConformanceTest {
    static final String TEST_API_KEY = "sur_00000000000000000000000000000000";
    private static final Path ROOT = Path.of("conformance");

    @TestFactory
    Stream<DynamicTest> scenarios() throws IOException {
        List<Path> files;
        try (Stream<Path> listing = Files.list(ROOT.resolve("scenarios"))) {
            files = listing.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertFalse(files.isEmpty(), "no vendored scenarios");
        return files.stream().map(file -> DynamicTest.dynamicTest(
                file.getFileName().toString().replace(".json", ""), () -> run(file)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value == null ? List.of() : (List<Object>) value;
    }

    private static Duration ms(Object value) {
        return Duration.ofMillis(((Number) value).longValue());
    }

    private static void run(Path file) throws Exception {
        Map<String, Object> sc = obj(Json.parse(Files.readString(file)));
        List<Object> script = list(sc.get("script"));
        Map<String, Object> call = obj(sc.get("call"));
        Map<String, Object> expect = obj(sc.get("expect"));
        Map<String, Object> args = obj(call.get("args"));

        int cancelAt = call.get("cancelAfterSleeps") instanceof Number n ? n.intValue() : 0;
        VirtualClock clock = new VirtualClock(cancelAt);
        ScriptedTransport transport = new ScriptedTransport(script);
        double random = sc.get("random") instanceof Number n ? n.doubleValue() : 0;
        String apiKey = TEST_API_KEY;
        Map<String, Object> clientOptions = obj(sc.get("client"));
        if (clientOptions.containsKey("apiKey")) apiKey = (String) clientOptions.get("apiKey");
        SurSolverClient.Builder builder = SurSolverClient.builder(apiKey).transport(transport).clock(clock).random(() -> random);
        for (Map.Entry<String, Object> setting : clientOptions.entrySet()) {
            Object value = setting.getValue();
            switch (setting.getKey()) {
                case "apiKey" -> { }
                case "baseUrl" -> builder.baseUrl((String) value);
                case "requestTimeoutMs" -> builder.requestTimeout(ms(value));
                case "pollIntervalMs" -> builder.pollInterval(ms(value));
                case "solveTimeoutMs" -> builder.solveTimeout(ms(value));
                case "maxSubmitRetries" -> builder.maxSubmitRetries(((Number) value).intValue());
                case "appId" -> builder.appId((String) value);
                case "userAgentSuffix" -> builder.userAgentSuffix((String) value);
                default -> fail("unknown client option " + setting.getKey());
            }
        }

        List<String> created = new ArrayList<>();
        Object result = null;
        Throwable error = null;
        try {
            SurSolverClient client = builder.build();
            Map<String, Object> task = obj(args.get("task"));
            switch ((String) call.get("method")) {
                case "construct" -> { }
                case "createTask" -> result = client.createTask(task);
                case "getTaskResult" -> result = client.getTaskResult((String) args.get("taskId"));
                case "getBalance" -> result = client.getBalance();
                case "listTaskTypes" -> result = client.listTaskTypes();
                case "listServices" -> result = client.listServices();
                case "solve" -> {
                    SolveOptions options = SolveOptions.defaults().onTaskCreated(created::add);
                    if (args.containsKey("pollIntervalMs")) options = options.pollInterval(ms(args.get("pollIntervalMs")));
                    if (args.containsKey("solveTimeoutMs")) options = options.solveTimeout(ms(args.get("solveTimeoutMs")));
                    result = client.solve(task, options);
                }
                default -> fail("unknown call " + call.get("method"));
            }
        } catch (Exception caught) {
            error = caught;
        }

        assertTrue(transport.failures.isEmpty(), () -> "harness: " + transport.failures);
        assertEquals(script.size(), transport.requests.size(), "scripted requests consumed");
        if (expect.get("sleeps") instanceof List<?> sleeps) {
            assertEquals(sleeps.stream().map(s -> ((Number) s).longValue()).toList(), clock.sleeps, "sleeps");
        }
        if (expect.get("lastTaskId") instanceof String lastTaskId) {
            assertEquals(lastTaskId, created.isEmpty() ? null : created.get(created.size() - 1), "onTaskCreated");
        }

        Map<String, Object> outcome = obj(expect.get("outcome"));
        if (outcome.get("error") instanceof Map<?, ?> expectedError) {
            assertNotNull(error, () -> "expected " + expectedError.get("type") + ", got a result");
            Throwable failure = error;
            ErrorView view = errorView(failure);
            assertEquals(expectedError.get("type"), view.type, () -> "error " + failure);
            for (Map.Entry<?, ?> field : expectedError.entrySet()) {
                String key = (String) field.getKey();
                if (key.equals("type") || key.equals("messageIncludes") || key.equals("messageExcludes")) continue;
                assertTrue(view.fields.containsKey(key) && jsonEquals(field.getValue(), view.fields.get(key)),
                        () -> key + " = " + view.fields.get(key) + ", want " + field.getValue());
            }
            String rendered = failure.getMessage() + "\n" + failure;
            for (Object text : list(expectedError.get("messageIncludes"))) {
                assertTrue(rendered.contains((String) text), () -> "message " + rendered + " lacks " + text);
            }
            for (Object text : list(expectedError.get("messageExcludes"))) {
                assertFalse(rendered.contains((String) text), () -> "message " + rendered + " contains " + text);
            }
        } else {
            if (error != null) throw new AssertionError("unexpected error: " + error, error);
            Object wire = toWire(result);
            if (outcome.containsKey("ok")) {
                Object want = outcome.get("ok");
                assertTrue(jsonEquals(normalize(want, false), normalize(wire, false)),
                        () -> "result " + Json.write(wire) + ", want " + Json.write(want));
            }
            for (Object raw : list(expect.get("outcomeSubset"))) {
                Map<String, Object> check = obj(raw);
                Object value = pointerGet(wire, (String) check.get("pointer"));
                if (check.get("length") instanceof Number length) {
                    assertEquals(length.intValue(), ((List<?>) value).size(), "length of " + check.get("pointer"));
                }
                if (check.containsKey("equals")) {
                    assertTrue(jsonEquals(normalize(check.get("equals"), false), normalize(value, false)),
                            () -> check.get("pointer") + " = " + Json.write(value) + ", want " + Json.write(check.get("equals")));
                }
            }
        }

        if (Boolean.TRUE.equals(expect.get("redaction"))) {
            for (Transport.Request request : transport.requests) {
                assertFalse(request.url().contains(apiKey), "key in URL");
                assertFalse(request.headers().toString().contains(apiKey), "key in a header");
                String body = new String(request.body(), StandardCharsets.UTF_8);
                assertEquals(1, body.split(Pattern.quote(apiKey), -1).length - 1, "key exactly once in the body");
                assertEquals(apiKey, obj(Json.parse(body)).get("clientKey"));
            }
            if (error != null) {
                java.io.StringWriter trace = new java.io.StringWriter();
                error.printStackTrace(new java.io.PrintWriter(trace));
                assertFalse(trace.toString().contains(apiKey), "key in error output");
            }
        }
    }

    record ErrorView(String type, Map<String, Object> fields) {}

    static ErrorView errorView(Throwable error) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (error instanceof InterruptedException) return new ErrorView("Cancelled", fields);
        if (!(error instanceof SurSolverException sur)) return new ErrorView("unexpected " + error, fields);
        String name = error.getClass().getSimpleName();
        String type = name.substring(0, name.length() - "Exception".length()) + "Error";
        if (sur.operation() != null) fields.put("operation", sur.operation().wireName());
        if (error instanceof SolveTimeoutException timeout) {
            fields.put("taskId", timeout.taskId());
            fields.put("lastErrorType", timeout.lastError() == null ? null : errorView(timeout.lastError()).type);
        } else if (error instanceof TransportException transport) {
            fields.put("mayHaveCreatedTask", transport.mayHaveCreatedTask());
            if (transport.status() != null) fields.put("status", (long) transport.status());
            if (transport.serverCode() != null) fields.put("serverCode", transport.serverCode());
            if (transport.retryAfterSeconds() != null) fields.put("retryAfterSeconds", (long) transport.retryAfterSeconds());
        } else if (error instanceof ApiException api) {
            fields.put("code", api.code());
            fields.put("description", api.description());
            fields.put("retryable", api.retryable());
            fields.put("taskId", api.taskId());
            fields.put("supportedUserAgents", api.supportedUserAgents());
        }
        return new ErrorView(type, fields);
    }

    /** The result in wire spelling: record components in snake_case; solutions untouched. */
    private static Object toWire(Object value) {
        if (value instanceof Record record) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (var component : record.getClass().getRecordComponents()) {
                Object field;
                try {
                    field = component.getAccessor().invoke(record);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
                if (field == null && component.getName().equals("solution")) continue;
                out.put(component.getName().replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT), toWire(field));
            }
            return out;
        }
        if (value instanceof List<?> items) return items.stream().map(ConformanceTest::toWire).toList();
        return value;
    }

    /** The README key rule: lowercase without underscores, except inside a solution. */
    private static Object normalize(Object value, boolean insideSolution) {
        if (value instanceof List<?> items) {
            List<Object> out = new ArrayList<>();
            for (Object item : items) out.add(normalize(item, insideSolution));
            return out;
        }
        if (value instanceof Map<?, ?> map && !insideSolution) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = (String) entry.getKey();
                out.put(key.replace("_", "").toLowerCase(Locale.ROOT), normalize(entry.getValue(), key.equals("solution")));
            }
            return out;
        }
        return value;
    }

    static boolean jsonEquals(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) return x.doubleValue() == y.doubleValue();
        if (a instanceof Map<?, ?> x && b instanceof Map<?, ?> y) {
            if (x.size() != y.size()) return false;
            for (Map.Entry<?, ?> entry : x.entrySet()) {
                if (!y.containsKey(entry.getKey()) || !jsonEquals(entry.getValue(), y.get(entry.getKey()))) return false;
            }
            return true;
        }
        if (a instanceof List<?> x && b instanceof List<?> y) {
            if (x.size() != y.size()) return false;
            for (int i = 0; i < x.size(); i++) {
                if (!jsonEquals(x.get(i), y.get(i))) return false;
            }
            return true;
        }
        return a == null ? b == null : a.equals(b);
    }

    private static Object pointerGet(Object root, String pointer) {
        Object current = root;
        if (pointer.isEmpty()) return current;
        for (String raw : pointer.substring(1).split("/", -1)) {
            String segment = raw.replace("~1", "/").replace("~0", "~");
            current = current instanceof List<?> items ? items.get(Integer.parseInt(segment)) : ((Map<?, ?>) current).get(segment);
        }
        return current;
    }

    private static final class VirtualClock implements SurSolverClock {
        private final int cancelAt;
        private Instant now = Instant.EPOCH;
        final List<Long> sleeps = new ArrayList<>();

        VirtualClock(int cancelAt) {
            this.cancelAt = cancelAt;
        }

        @Override
        public Instant now() {
            return now;
        }

        @Override
        public void sleep(Duration duration) throws InterruptedException {
            sleeps.add(duration.toMillis());
            now = now.plus(duration);
            if (sleeps.size() == cancelAt) throw new InterruptedException("cancelled by the scenario");
        }
    }

    private static final class ScriptedTransport implements Transport {
        private final List<Object> script;
        final List<Request> requests = new ArrayList<>();
        final List<String> failures = new ArrayList<>();

        ScriptedTransport(List<Object> script) {
            this.script = script;
        }

        private IOException mismatch(String message) {
            failures.add("request " + requests.size() + ": " + message);
            return new IOException("harness mismatch");
        }

        @Override
        public Response send(Request request) throws IOException {
            requests.add(request);
            int step = requests.size();
            if (step > script.size()) throw mismatch("unexpected " + request.method() + " " + request.url());
            Map<String, Object> entry = obj(script.get(step - 1));
            Map<String, Object> expected = obj(entry.get("expect"));
            URI url = URI.create(request.url());
            if (!expected.get("method").equals(request.method()) || !expected.get("path").equals(url.getPath())) {
                throw mismatch("expected " + expected.get("method") + " " + expected.get("path") + ", got "
                        + request.method() + " " + url.getPath());
            }
            if (Boolean.TRUE.equals(expected.get("noQuery")) && url.getRawQuery() != null) throw mismatch("unexpected query");
            if (expected.containsKey("json")) {
                Object body;
                try {
                    body = Json.parse(new String(request.body(), StandardCharsets.UTF_8));
                } catch (Json.ParseException e) {
                    throw mismatch("body is not JSON");
                }
                if (!jsonEquals(expected.get("json"), body)) {
                    throw mismatch("body " + Json.write(body) + " != " + Json.write(expected.get("json")));
                }
            }
            Map<String, String> headers = new LinkedHashMap<>();
            request.headers().forEach((name, value) -> headers.put(name.toLowerCase(Locale.ROOT), value));
            for (Map.Entry<String, Object> header : obj(expected.get("headers")).entrySet()) {
                String actual = headers.get(header.getKey());
                boolean matches = actual != null && (header.getValue() instanceof Map<?, ?> pattern
                        ? Pattern.compile((String) pattern.get("matches")).matcher(actual).find()
                        : actual.equals(header.getValue()));
                if (!matches) throw mismatch("header " + header.getKey() + " mismatch: " + actual);
            }

            if (entry.get("fail") instanceof String kind) {
                switch (kind) {
                    case "request_timeout" -> throw new HttpTimeoutException("request timed out");
                    case "connection_reset" -> throw new SocketException("Connection reset");
                    default -> throw new EOFException("EOF reached while reading");
                }
            }
            return respond(obj(entry.get("respond")));
        }

        private static Response respond(Map<String, Object> respond) throws IOException {
            int status = respond.get("status") instanceof Number n ? n.intValue() : 200;
            if (respond.get("respondFrom") instanceof String fixtureName) {
                Map<String, Object> fixture;
                try {
                    fixture = obj(obj(Json.parse(Files.readString(ROOT.resolve("recorded").resolve(fixtureName)))).get("response"));
                } catch (Json.ParseException e) {
                    throw new AssertionError(e);
                }
                if (!respond.containsKey("status")) status = ((Number) fixture.get("status")).intValue();
                String body = Boolean.TRUE.equals(fixture.get("body_is_json")) ? Json.write(fixture.get("body")) : (String) fixture.get("body");
                return new Response(status, body.getBytes(StandardCharsets.UTF_8));
            }
            if (respond.containsKey("json")) {
                return new Response(status, Json.write(respond.get("json")).getBytes(StandardCharsets.UTF_8));
            }
            String text = respond.get("text") instanceof String t ? t : "";
            return new Response(status, text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
