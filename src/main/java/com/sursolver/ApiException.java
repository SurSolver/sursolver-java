package com.sursolver;

import java.util.List;
import java.util.Set;

/**
 * The API answered {@code errorId: 1}. Catch the subclasses for the codes you handle; this class
 * itself means {@code ERROR_INTERNAL_ERROR}, {@code ERROR_FAILED} on create, or a code newer than
 * this package.
 */
public class ApiException extends SurSolverException {
    private static final long serialVersionUID = 1L;

    private static final Set<String> TASK_FAILURE_CODES = Set.of(
            ErrorCodes.ERROR_FAILED, ErrorCodes.ERROR_TASK_TIMEOUT, ErrorCodes.ERROR_TASK_EXPIRED,
            ErrorCodes.ERROR_WRONG_USERAGENT);

    private final String code;
    private final String description;
    private final String taskId;
    @SuppressWarnings("serial") // List.copyOf lists are serializable
    private final List<String> supportedUserAgents;

    /**
     * Creates the error.
     *
     * @param operation the call that failed
     * @param code the {@code errorCode} as sent
     * @param description the {@code errorDescription} as sent
     * @param taskId the task, or {@code null}
     * @param supportedUserAgents the list sent with {@code ERROR_WRONG_USERAGENT}, or {@code null}
     */
    public ApiException(Operation operation, String code, String description, String taskId,
            List<String> supportedUserAgents) {
        super(operation, code + ": " + description + (taskId == null ? "" : " (task " + taskId + ")"), null);
        this.code = code;
        this.description = description;
        this.taskId = taskId;
        this.supportedUserAgents = supportedUserAgents == null ? List.of() : List.copyOf(supportedUserAgents);
    }

    static ApiException create(Operation operation, String code, String description, String taskId,
            List<String> supported) {
        if (operation == Operation.GET_TASK_RESULT && TASK_FAILURE_CODES.contains(code)) {
            return new TaskFailedException(operation, code, description, taskId, supported);
        }
        return switch (code) {
            case ErrorCodes.ERROR_WRONG_CLIENT_KEY, ErrorCodes.ERROR_KEY_DOES_NOT_EXIST ->
                    new AuthenticationException(operation, code, description, taskId, supported);
            case ErrorCodes.ERROR_INVALID_REQUEST, ErrorCodes.ERROR_TYPE_NOT_SUPPORTED, ErrorCodes.ERROR_WRONG_USERAGENT ->
                    new InvalidTaskException(operation, code, description, taskId, supported);
            case ErrorCodes.ERROR_ZERO_BALANCE -> new InsufficientBalanceException(operation, code, description, taskId, supported);
            case ErrorCodes.ERROR_NO_SLOT_AVAILABLE -> new NoSlotAvailableException(operation, code, description, taskId, supported);
            case ErrorCodes.ERROR_TASK_NOT_FOUND -> new TaskNotFoundException(operation, code, description, taskId, supported);
            default -> new ApiException(operation, code, description, taskId, supported);
        };
    }

    /** @return the {@code errorCode} exactly as sent, including codes newer than this package */
    public String code() {
        return code;
    }

    /** @return the {@code errorDescription} as sent */
    public String description() {
        return description;
    }

    /** @return true only for {@code ERROR_NO_SLOT_AVAILABLE} */
    public boolean retryable() {
        return ErrorCodes.ERROR_NO_SLOT_AVAILABLE.equals(code);
    }

    /** @return the task for {@code getTaskResult} and for {@code solve} after acceptance, otherwise {@code null} */
    public String taskId() {
        return taskId;
    }

    /** @return the list sent with {@code ERROR_WRONG_USERAGENT}; empty otherwise */
    public List<String> supportedUserAgents() {
        return supportedUserAgents;
    }
}
