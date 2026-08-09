package com.stackd.ignition.deployment;

/**
 * Thrown for any failure while talking to the Zerops REST API or waiting for a
 * Zerops deploy process to finish.
 *
 * <p>Messages describe the failing HTTP operation and status code only; request
 * or response bodies that could carry secrets are never included. A {@code
 * retryable} failure is a transient condition (network failure, request
 * timeout, or a Zerops 5xx) that polling may recover from; a non-retryable
 * failure (a 4xx, a broken response, or a rejected deploy) fails the deployment
 * immediately.
 */
public class ZeropsApiException extends RuntimeException {

    /** Any non-2xx response from the Zerops API. */
    public static final String ZEROPS_API_ERROR = "ZEROPS_API_ERROR";

    /** A single Zerops API request timed out. */
    public static final String ZEROPS_API_TIMEOUT = "ZEROPS_API_TIMEOUT";

    /** Zerops API unreachable: three consecutive transient failures. */
    public static final String ZEROPS_API_UNREACHABLE = "ZEROPS_API_UNREACHABLE";

    /** The target service stack was not found in the Zerops project. */
    public static final String ZEROPS_SERVICE_NOT_FOUND = "ZEROPS_SERVICE_NOT_FOUND";

    /** The Zerops deploy process ended in a terminal failure state. */
    public static final String ZEROPS_DEPLOY_FAILED = "ZEROPS_DEPLOY_FAILED";

    /** The Zerops deploy process did not finish within the polling budget. */
    public static final String ZEROPS_DEPLOY_TIMEOUT = "ZEROPS_DEPLOY_TIMEOUT";

    /** The Zerops response did not contain the expected field. */
    public static final String ZEROPS_RESPONSE_MALFORMED = "ZEROPS_RESPONSE_MALFORMED";

    /** Stable machine-readable code describing the API failure. */
    private final String errorCode;

    /** Whether the failure is transient and polling may retry. */
    private final boolean retryable;

    /** HTTP status that caused the failure, or {@code 0} when not HTTP-based. */
    private final int httpStatus;

    /**
     * Creates the exception.
     *
     * @param errorCode stable machine-readable error code
     * @param message   human-readable failure reason; must not contain secrets
     * @param retryable whether the failure is transient
     */
    public ZeropsApiException(String errorCode, String message, boolean retryable) {
        this(errorCode, message, retryable, 0, null);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param errorCode stable machine-readable error code
     * @param message   human-readable failure reason; must not contain secrets
     * @param retryable whether the failure is transient
     * @param cause     the underlying exception
     */
    public ZeropsApiException(String errorCode, String message, boolean retryable, Throwable cause) {
        this(errorCode, message, retryable, 0, cause);
    }

    /**
     * Creates the exception for an HTTP failure.
     *
     * @param errorCode  stable machine-readable error code
     * @param message    human-readable failure reason; must not contain secrets
     * @param retryable  whether the failure is transient
     * @param httpStatus the HTTP status code that caused the failure, or {@code 0}
     * @param cause      the underlying exception, may be {@code null}
     */
    public ZeropsApiException(String errorCode, String message, boolean retryable, int httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }

    /**
     * Returns the stable machine-readable error code.
     *
     * @return one of the {@code ZEROPS_*} constants
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Returns whether the failure is transient and polling may retry.
     *
     * @return {@code true} for network/timeout/5xx failures
     */
    public boolean isRetryable() {
        return retryable;
    }

    /**
     * Returns the HTTP status that caused the failure, or {@code 0} when the
     * failure was not HTTP-based.
     *
     * @return the HTTP status code
     */
    public int getHttpStatus() {
        return httpStatus;
    }
}
