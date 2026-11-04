package com.sursolver;

/** The client call an error belongs to; {@code solve} reports the inner call that failed. */
public enum Operation {
    /** {@code createTask} */
    CREATE_TASK("createTask"),
    /** {@code getTaskResult} */
    GET_TASK_RESULT("getTaskResult"),
    /** {@code getBalance} */
    GET_BALANCE("getBalance"),
    /** {@code GET /v1/task-types} */
    LIST_TASK_TYPES("listTaskTypes"),
    /** {@code GET /v1/services} */
    LIST_SERVICES("listServices");

    private final String wireName;

    Operation(String wireName) {
        this.wireName = wireName;
    }

    /** The operation's name in the API documentation, e.g. {@code createTask}. */
    public String wireName() {
        return wireName;
    }
}
