package com.sursolver;

import java.util.List;

/**
 * {@code ERROR_WRONG_CLIENT_KEY} or {@code ERROR_KEY_DOES_NOT_EXIST}: fix the API key.
 */
public final class AuthenticationException extends ApiException {
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
    public AuthenticationException(Operation operation, String code, String description, String taskId,
            List<String> supportedUserAgents) {
        super(operation, code, description, taskId, supportedUserAgents);
    }
}
