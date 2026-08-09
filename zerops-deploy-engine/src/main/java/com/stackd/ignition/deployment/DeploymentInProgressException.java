package com.stackd.ignition.deployment;

/**
 * Thrown synchronously when a deployment request targets a project directory
 * and Zerops project pair that already has a deploy running.
 *
 * <p>Maps to {@code 409 DEPLOYMENT_ALREADY_IN_PROGRESS} via
 * {@code GlobalExceptionHandler}.
 */
public class DeploymentInProgressException extends RuntimeException {

    /** Stable machine-readable error code. */
    public static final String ERROR_CODE = "DEPLOYMENT_ALREADY_IN_PROGRESS";

    /**
     * Creates the exception for the duplicate deploy target.
     *
     * @param projectPath     the source project directory
     * @param zeropsProjectId the target Zerops project
     */
    public DeploymentInProgressException(String projectPath, String zeropsProjectId) {
        super("A deployment is already in progress for project path " + projectPath
                + " and Zerops project " + zeropsProjectId);
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
