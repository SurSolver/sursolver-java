package com.sursolver;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * {@code solve} reached its deadline. The task may still finish and stays charged; poll it with
 * {@code getTaskResult(taskId())} if you still need the result.
 */
public final class SolveTimeoutException extends SurSolverException {
    private static final long serialVersionUID = 1L;

    private final String taskId;
    private final Duration timeout;

    /**
     * Creates the error.
     *
     * @param taskId the task that was not ready in time
     * @param timeout the deadline that passed
     * @param lastError the final poll's transient failure, or {@code null}
     */
    public SolveTimeoutException(String taskId, Duration timeout, SurSolverException lastError) {
        super(null, "task " + taskId + " was not ready within "
                + BigDecimal.valueOf(timeout.toMillis(), 3).stripTrailingZeros().toPlainString()
                + " s; it may still finish and stays charged", lastError);
        this.taskId = taskId;
        this.timeout = timeout;
    }

    /** @return the task that was not ready in time */
    public String taskId() {
        return taskId;
    }

    /** @return the deadline that passed */
    public Duration timeout() {
        return timeout;
    }

    /** @return the final poll's transient failure, or {@code null} */
    public SurSolverException lastError() {
        return (SurSolverException) getCause();
    }
}
