package com.sursolver;

import java.util.Map;

/**
 * One {@code getTaskResult} answer.
 *
 * @param status {@link #PROCESSING} or {@link #READY}
 * @param solution the solution when ready, otherwise {@code null}. Keys differ per type
 *     ({@code token}, {@code gRecaptchaResponse}, {@code cf_clearance}, {@code cookies}, ...) and are
 *     passed through untouched. Tokens are single-use.
 */
public record TaskResult(String status, Map<String, Object> solution) {
    /** Status while the task runs. */
    public static final String PROCESSING = "processing";

    /** Status once the solution is available. */
    public static final String READY = "ready";

    /** @return true when {@link #status()} is {@link #READY} */
    public boolean isReady() {
        return READY.equals(status);
    }
}
