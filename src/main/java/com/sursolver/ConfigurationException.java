package com.sursolver;

/** The client was built with an unusable option. Nothing was sent. */
public final class ConfigurationException extends SurSolverException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the error.
     *
     * @param message what is wrong
     */
    public ConfigurationException(String message) {
        super(null, message, null);
    }
}
