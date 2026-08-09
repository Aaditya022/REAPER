package com.stackd.ignition.health;

/**
 * Thrown when the live deployment URL does not respond successfully to HTTP
 * requests within the health check timeout.
 */
public class HealthCheckException extends RuntimeException {

    /** Stable machine-readable error code. */
    public static final String HEALTH_CHECK_FAILED = "HEALTH_CHECK_FAILED";

    /**
     * Creates the exception.
     *
     * @param message the failure reason
     */
    public HealthCheckException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message the failure reason
     * @param cause   the underlying exception
     */
    public HealthCheckException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Returns the stable error code.
     *
     * @return {@link #HEALTH_CHECK_FAILED}
     */
    public String getErrorCode() {
        return HEALTH_CHECK_FAILED;
    }
}
