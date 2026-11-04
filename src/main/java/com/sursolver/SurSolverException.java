package com.sursolver;

/** Base of every error the client throws. Messages never contain the API key. */
public abstract class SurSolverException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final Operation operation;

    SurSolverException(Operation operation, String message, Throwable cause) {
        super(message, cause);
        this.operation = operation;
    }

    /**
     * The call that failed; {@code null} for {@link ConfigurationException} and
     * {@link SolveTimeoutException}.
     *
     * @return the operation, or {@code null}
     */
    public Operation operation() {
        return operation;
    }
}
