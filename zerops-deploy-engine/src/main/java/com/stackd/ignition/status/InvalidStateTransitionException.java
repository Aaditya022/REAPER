package com.stackd.ignition.status;

/**
 * Thrown when an invalid lifecycle transition is attempted.
 */
public class InvalidStateTransitionException extends RuntimeException {

    /** Stable machine-readable error code. */
    public static final String ERROR_CODE = "INVALID_STATE_TRANSITION";

    /**
     * Creates the exception.
     *
     * @param from the current status
     * @param to   the rejected target status
     */
    public InvalidStateTransitionException(DeploymentStatus from, DeploymentStatus to) {
        super("Invalid state transition from " + from + " to " + to);
    }

    /**
     * Returns the stable error code.
     *
     * @return {@link #ERROR_CODE}
     */
    public String getErrorCode() {
        return ERROR_CODE;
    }
}
