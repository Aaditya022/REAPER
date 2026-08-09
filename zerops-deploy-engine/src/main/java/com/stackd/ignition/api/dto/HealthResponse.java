package com.stackd.ignition.api.dto;

/**
 * Liveness probe response for the health endpoint.
 *
 * @param status the service status, always {@code "ok"} while the application is up
 */
public record HealthResponse(String status) {
}
