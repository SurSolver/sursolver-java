// Generated from the SurSolver catalog (GET /v1/task-types) by the spec repository
// tools/templates/maven.ts. Catalog fetched at 2026-11-03T21:16:22.334Z. DO NOT EDIT.

package com.sursolver;

import java.util.List;

/** The complete {@code errorCode} vocabulary of the solver endpoints. */
public final class ErrorCodes {
    private ErrorCodes() {}

    /** {@code ERROR_KEY_DOES_NOT_EXIST} */
    public static final String ERROR_KEY_DOES_NOT_EXIST = "ERROR_KEY_DOES_NOT_EXIST";
    /** {@code ERROR_WRONG_CLIENT_KEY} */
    public static final String ERROR_WRONG_CLIENT_KEY = "ERROR_WRONG_CLIENT_KEY";
    /** {@code ERROR_ZERO_BALANCE} */
    public static final String ERROR_ZERO_BALANCE = "ERROR_ZERO_BALANCE";
    /** {@code ERROR_NO_SLOT_AVAILABLE} */
    public static final String ERROR_NO_SLOT_AVAILABLE = "ERROR_NO_SLOT_AVAILABLE";
    /** {@code ERROR_TYPE_NOT_SUPPORTED} */
    public static final String ERROR_TYPE_NOT_SUPPORTED = "ERROR_TYPE_NOT_SUPPORTED";
    /** {@code ERROR_TASK_NOT_FOUND} */
    public static final String ERROR_TASK_NOT_FOUND = "ERROR_TASK_NOT_FOUND";
    /** {@code ERROR_TASK_EXPIRED} */
    public static final String ERROR_TASK_EXPIRED = "ERROR_TASK_EXPIRED";
    /** {@code ERROR_TASK_TIMEOUT} */
    public static final String ERROR_TASK_TIMEOUT = "ERROR_TASK_TIMEOUT";
    /** {@code ERROR_FAILED} */
    public static final String ERROR_FAILED = "ERROR_FAILED";
    /** {@code ERROR_INVALID_REQUEST} */
    public static final String ERROR_INVALID_REQUEST = "ERROR_INVALID_REQUEST";
    /** {@code ERROR_WRONG_USERAGENT} */
    public static final String ERROR_WRONG_USERAGENT = "ERROR_WRONG_USERAGENT";
    /** {@code ERROR_INTERNAL_ERROR} */
    public static final String ERROR_INTERNAL_ERROR = "ERROR_INTERNAL_ERROR";

    /** All codes in catalog order. */
    public static final List<String> ALL = List.of(
            ERROR_KEY_DOES_NOT_EXIST,
            ERROR_WRONG_CLIENT_KEY,
            ERROR_ZERO_BALANCE,
            ERROR_NO_SLOT_AVAILABLE,
            ERROR_TYPE_NOT_SUPPORTED,
            ERROR_TASK_NOT_FOUND,
            ERROR_TASK_EXPIRED,
            ERROR_TASK_TIMEOUT,
            ERROR_FAILED,
            ERROR_INVALID_REQUEST,
            ERROR_WRONG_USERAGENT,
            ERROR_INTERNAL_ERROR);
}
