package com.sursolver;

import java.util.List;

/**
 * {@code ERROR_INVALID_REQUEST}, {@code ERROR_TYPE_NOT_SUPPORTED}, or {@code ERROR_WRONG_USERAGENT} on
 * create (see {@link #supportedUserAgents()}). Nothing was created or charged.
 */
public final class InvalidTaskException extends ApiException {
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
    public InvalidTaskException(Operation operation, String code, String description, String taskId,
            List<String> supportedUserAgents) {
        super(operation, code, description, taskId, supportedUserAgents);
    }
}
