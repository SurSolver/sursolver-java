package com.sursolver;

import java.util.List;

/**
 * The task ended without a result ({@code ERROR_FAILED}, {@code ERROR_TASK_TIMEOUT},
 * {@code ERROR_TASK_EXPIRED}, or {@code ERROR_WRONG_USERAGENT} after acceptance). Its charge is
 * returned once.
 */
public final class TaskFailedException extends ApiException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the error.
     *
     * @param operation the call that failed
     * @param code the {@code errorCode} as sent
     * @param description the {@code errorDescription} as sent
     * @param taskId the task, or {@code null}
     * @param supportedUserAgents the list sent with {@code ERROR_WRONG_USERAGENT}, or {@code null}
     */
    public TaskFailedException(Operation operation, String code, String description, String taskId,
            List<String> supportedUserAgents) {
        super(operation, code, description, taskId, supportedUserAgents);
    }
}
