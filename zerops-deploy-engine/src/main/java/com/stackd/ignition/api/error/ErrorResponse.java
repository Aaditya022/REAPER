package com.stackd.ignition.api.error;

/**
 * Standard REST error response body used for every API failure.
 *
 * @param error   always {@code true}
 * @param code    stable machine-readable error code
 * @param message human-readable error message
 */
public record ErrorResponse(boolean error, String code, String message) {
}
