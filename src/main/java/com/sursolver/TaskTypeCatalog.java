package com.sursolver;

import java.util.List;

/**
 * The public task-type catalog.
 *
 * @param taskTypes every task type
 * @param errorCodes every {@code errorCode} the solver endpoints send
 */
public record TaskTypeCatalog(List<TaskTypeInfo> taskTypes, List<String> errorCodes) {}
