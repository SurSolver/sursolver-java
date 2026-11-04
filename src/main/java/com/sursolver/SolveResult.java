package com.sursolver;

import java.util.Map;

/**
 * What {@code solve} returns.
 *
 * @param taskId the solved task
 * @param solution the solution, passed through untouched. Tokens and cookies are single-use.
 */
public record SolveResult(String taskId, Map<String, Object> solution) {}
