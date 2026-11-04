package com.sursolver;

/** HTTP 200 JSON whose shape the API contract does not allow. */
public final class ProtocolException extends SurSolverException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the error.
     *
     * @param operation the call that failed
     * @param detail what is wrong with the response
     */
    public ProtocolException(Operation operation, String detail) {
        super(operation, operation.wireName() + " returned an unexpected response: " + detail, null);
    }
}
