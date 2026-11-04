package com.sursolver;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Sends one request. Return a {@link Response} for any HTTP status; throw only when no response
 * arrived ({@link java.net.http.HttpTimeoutException} when {@link Request#timeout()} passes). It must
 * not follow redirects and must stop with {@link InterruptedException} when the thread is interrupted.
 */
@FunctionalInterface
public interface Transport {
    /**
     * Sends {@code request}.
     *
     * @param request the request
     * @return the response, whatever its status
     * @throws IOException when no response arrived
     * @throws InterruptedException when the thread was interrupted
     */
    Response send(Request request) throws IOException, InterruptedException;

    /**
     * One HTTP request.
     *
     * @param method {@code GET} or {@code POST}
     * @param url absolute URL
     * @param headers {@code Accept}, {@code User-Agent}, and {@code Content-Type} with a body
     * @param body UTF-8 JSON body, or {@code null}
     * @param timeout how long the request may take in total
     */
    record Request(String method, String url, Map<String, String> headers, byte[] body, Duration timeout) {}

    /**
     * What a transport returns for any HTTP status.
     *
     * @param status HTTP status
     * @param body raw body
     */
    record Response(int status, byte[] body) {}
}
