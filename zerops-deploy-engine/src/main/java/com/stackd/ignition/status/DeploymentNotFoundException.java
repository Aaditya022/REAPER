package com.stackd.ignition.status;

/**
 * Thrown when a deployment with the given id does not exist.
 */
public class DeploymentNotFoundException extends RuntimeException {

    /** Stable machine-readable error code. */
    public static final String ERROR_CODE = "DEPLOYMENT_NOT_FOUND";

    /**
     * Creates the exception for the missing deployment.
     *
     * @param deploymentId the missing deployment id
     */
    public DeploymentNotFoundException(String deploymentId) {
        super("Deployment not found: " + deploymentId);
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
