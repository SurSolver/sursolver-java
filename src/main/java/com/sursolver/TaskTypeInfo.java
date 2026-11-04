package com.sursolver;

import java.util.List;

/**
 * One entry of {@code GET /v1/task-types}.
 *
 * @param pageSlug the solver page slug at {@code https://sursolver.com/solvers/<slug>}, or {@code null}
 * @param displayName human-readable name
 * @param taskType canonical {@code task.type}
 * @param taskTypeProxyless the ProxyLess twin, or {@code null}
 * @param aliases other accepted spellings
 * @param proxy {@code none}, {@code optional} or {@code required}
 * @param required groups of accepted spellings; supply one name per group
 * @param anyOf alternatives; supply every field of at least one
 * @param optional optional fields
 * @param solutionKeys keys of the solution object
 */
public record TaskTypeInfo(
        String pageSlug,
        String displayName,
        String taskType,
        String taskTypeProxyless,
        List<String> aliases,
        String proxy,
        List<List<String>> required,
        List<List<String>> anyOf,
        List<String> optional,
        List<String> solutionKeys) {}
