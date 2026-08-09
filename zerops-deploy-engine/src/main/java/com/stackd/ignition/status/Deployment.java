package com.stackd.ignition.status;

import java.time.Instant;

import com.stackd.ignition.analyzer.DetectedStack;

/**
 * Immutable snapshot of a single deployment's state.
 *
 * <p>Every state change produces a new {@code Deployment} instance, which keeps
 * the in-memory store safe under concurrent updates when used with
 * {@link DeploymentStore#updateIfPresent}.
 */
public final class Deployment {

    private final String deploymentId;
    private final String projectPath;
    private final String zeropsProjectId;
    private final DetectedStack stack;
    private final DeploymentStatus status;
    private final String message;
    private final String errorCode;
    private final String liveUrl;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Deployment(String deploymentId, String projectPath, String zeropsProjectId,
                       DetectedStack stack, DeploymentStatus status, String message,
                       String errorCode, String liveUrl, Instant createdAt, Instant updatedAt) {
        this.deploymentId = deploymentId;
        this.projectPath = projectPath;
        this.zeropsProjectId = zeropsProjectId;
        this.stack = stack;
        this.status = status;
        this.message = message;
        this.errorCode = errorCode;
        this.liveUrl = liveUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Creates a new deployment in {@link DeploymentStatus#PENDING} state.
     *
     * @param deploymentId     unique deployment identifier
     * @param projectPath      source project directory path
     * @param zeropsProjectId  target Zerops project identifier
     * @return the new pending deployment
     */
    public static Deployment initial(String deploymentId, String projectPath, String zeropsProjectId) {
        Instant now = Instant.now();
        return new Deployment(deploymentId, projectPath, zeropsProjectId, null,
                DeploymentStatus.PENDING, DeploymentStatus.PENDING.defaultMessage(),
                null, null, now, now);
    }

    /**
     * Returns a copy of this deployment with the given status and message.
     *
     * <p>Callers must validate the transition through {@link DeploymentStatusService}
     * before invoking this.
     *
     * @param newStatus the target status
     * @param newMessage the status message
     * @return the updated deployment
     */
    public Deployment withStatus(DeploymentStatus newStatus, String newMessage) {
        return new Deployment(deploymentId, projectPath, zeropsProjectId, stack, newStatus,
                newMessage, errorCode, liveUrl, createdAt, Instant.now());
    }

    /**
     * Returns a copy of this deployment marked as {@link DeploymentStatus#FAILED}
     * with the given error code and message.
     *
     * @param newErrorCode stable machine-readable error code
     * @param newMessage   failure message
     * @return the failed deployment
     */
    public Deployment withError(String newErrorCode, String newMessage) {
        return new Deployment(deploymentId, projectPath, zeropsProjectId, stack,
                DeploymentStatus.FAILED, newMessage, newErrorCode, liveUrl, createdAt, Instant.now());
    }

    /**
     * Returns a copy of this deployment with the detected stack attached.
     *
     * @param newStack the detected stack, may be {@code null}
     * @return the updated deployment
     */
    public Deployment withStack(DetectedStack newStack) {
        return new Deployment(deploymentId, projectPath, zeropsProjectId, newStack, status,
                message, errorCode, liveUrl, createdAt, Instant.now());
    }

    /**
     * Returns a copy of this deployment with the status message replaced.
     *
     * @param newMessage the new status message
     * @return the updated deployment
     */
    public Deployment withMessage(String newMessage) {
        return new Deployment(deploymentId, projectPath, zeropsProjectId, stack, status,
                newMessage, errorCode, liveUrl, createdAt, Instant.now());
    }

    /**
     * Returns a copy of this deployment with the live URL attached.
     *
     * @param newLiveUrl the live deployment URL
     * @return the updated deployment
     */
    public Deployment withLiveUrl(String newLiveUrl) {
        return new Deployment(deploymentId, projectPath, zeropsProjectId, stack, status,
                message, errorCode, newLiveUrl, createdAt, Instant.now());
    }

    /**
     * Returns the unique deployment identifier.
     *
     * @return the deployment id
     */
    public String getDeploymentId() {
        return deploymentId;
    }

    /**
     * Returns the source project directory path.
     *
     * @return the project path
     */
    public String getProjectPath() {
        return projectPath;
    }

    /**
     * Returns the target Zerops project identifier.
     *
     * @return the Zerops project id
     */
    public String getZeropsProjectId() {
        return zeropsProjectId;
    }

    /**
     * Returns the detected stack, if analysis has completed.
     *
     * @return the detected stack or {@code null}
     */
    public DetectedStack getStack() {
        return stack;
    }

    /**
     * Returns the current lifecycle status.
     *
     * @return the current status
     */
    public DeploymentStatus getStatus() {
        return status;
    }

    /**
     * Returns the current human-readable status message.
     *
     * @return the status message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the stable error code when the deployment failed.
     *
     * @return the error code or {@code null}
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the live deployment URL when available.
     *
     * @return the live URL or {@code null}
     */
    public String getLiveUrl() {
        return liveUrl;
    }

    /**
     * Returns the creation timestamp.
     *
     * @return the created-at instant
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the last-updated timestamp.
     *
     * @return the updated-at instant
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
