package com.stackd.ignition.status;

/**
 * Thrown when a deployment with the given id already exists.
 */
public class DeploymentAlreadyExistsException extends RuntimeException {

    /** Stable machine-readable error code. */
    public static final String ERROR_CODE = "DEPLOYMENT_ALREADY_EXISTS";

    /**
     * Creates the exception for the duplicate deployment.
     *
     * @param deploymentId the duplicate deployment id
     */
    public DeploymentAlreadyExistsException(String deploymentId) {
        super("A deployment with id already exists: " + deploymentId);
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
