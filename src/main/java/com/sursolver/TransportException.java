package com.sursolver;

/**
 * No usable answer: a connection failure, a timeout, a non-200 status, or a body that is not the
 * API's envelope. It says nothing about the task.
 */
public final class TransportException extends SurSolverException {
    private static final long serialVersionUID = 1L;

    private final Integer status;
    private final String serverCode;
    private final Integer retryAfterSeconds;

    /**
     * Creates the error.
     *
     * @param operation the call that failed
     * @param reason what went wrong, e.g. {@code HTTP 503}
     * @param status the HTTP status when a response arrived, otherwise {@code null}
     * @param serverCode {@code error.code} of the internal envelope, or {@code null}
     * @param retryAfterSeconds {@code error.retry_after_seconds} of the internal envelope, or {@code null}
     * @param cause the underlying I/O failure, or {@code null}
     */
    public TransportException(Operation operation, String reason, Integer status, String serverCode,
            Integer retryAfterSeconds, Throwable cause) {
        super(operation, describe(operation, reason), cause);
        this.status = status;
        this.serverCode = serverCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    private static String describe(Operation operation, String reason) {
        String note = operation == Operation.CREATE_TASK
                ? " The task may have been created and charged; check getBalance() before submitting again."
                : "";
        return operation.wireName() + " failed: " + reason + "." + note;
    }

    /** @return the HTTP status when a response arrived, otherwise {@code null} */
    public Integer status() {
        return status;
    }

    /** @return {@code error.code} of the API's internal envelope, or {@code null} */
    public String serverCode() {
        return serverCode;
    }

    /** @return {@code error.retry_after_seconds} of the internal envelope, or {@code null} */
    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }

    /**
     * True for {@code createTask}: the request may have been accepted and charged before the answer
     * was lost. Check {@code getBalance()} before submitting again.
     *
     * @return whether a task may exist
     */
    public boolean mayHaveCreatedTask() {
        return operation() == Operation.CREATE_TASK;
    }
}
