package com.sursolver;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The default transport: {@code java.net.http.HttpClient} that never follows redirects, so a request
 * body carrying the API key is not replayed to another location.
 */
public final class HttpTransport implements Transport {
    private static final class Shared {
        static final HttpTransport INSTANCE =
                new HttpTransport(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build());
    }

    private final HttpClient client;

    /**
     * Sends through {@code client} (configure proxies or TLS on it).
     *
     * @param client an HttpClient built with {@code followRedirects(HttpClient.Redirect.NEVER)}
     * @throws IllegalArgumentException when the client follows redirects
     */
    public HttpTransport(HttpClient client) {
        if (client.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("the HttpClient must use followRedirects(HttpClient.Redirect.NEVER)");
        }
        this.client = client;
    }

    static HttpTransport shared() {
        return Shared.INSTANCE;
    }

    @Override
    public Response send(Request request) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(request.url())).timeout(request.timeout());
        for (Map.Entry<String, String> header : request.headers().entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        builder.method(request.method(), request.body() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(request.body()));
        CompletableFuture<HttpResponse<byte[]>> pending =
                client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        try {
            // Waiting on the future bounds the whole exchange, body included.
            HttpResponse<byte[]> response = pending.get(request.timeout().toNanos(), TimeUnit.NANOSECONDS);
            return new Response(response.statusCode(), response.body());
        } catch (TimeoutException timeout) {
            pending.cancel(true);
            throw new HttpTimeoutException("no response within " + request.timeout().toMillis() + " ms");
        } catch (InterruptedException interrupted) {
            pending.cancel(true);
            throw interrupted;
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            throw cause instanceof IOException io ? io : new IOException(cause);
        }
    }
}
