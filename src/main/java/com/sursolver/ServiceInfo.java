package com.sursolver;

/**
 * One entry of {@code GET /v1/services}.
 *
 * @param id service id
 * @param slug service slug
 * @param displayName human-readable name
 * @param pricePerRequest USD price per task as a decimal string, e.g. {@code "0.0006"}
 * @param status service status
 * @param avgLatencyMs recent average latency in milliseconds, or {@code null}
 */
public record ServiceInfo(long id, String slug, String displayName, String pricePerRequest, String status,
        Long avgLatencyMs) {}
