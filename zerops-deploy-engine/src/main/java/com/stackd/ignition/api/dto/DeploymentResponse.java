package com.stackd.ignition.api.dto;

import java.time.Instant;

import com.stackd.ignition.analyzer.DetectedStack;
import com.stackd.ignition.status.Deployment;
import com.stackd.ignition.status.DeploymentStatus;

/**
 * Full deployment representation returned by {@code GET /api/v1/deployments/{deploymentId}}.
 *
 * @param deploymentId    unique deployment identifier
 * @param projectPath     source project directory path
 * @param zeropsProjectId target Zerops project identifier
 * @param status          current lifecycle status
 * @param message         human-readable status message
 * @param errorCode       stable error code when failed, {@code null} otherwise
 * @param liveUrl         live deployment URL when available, {@code null} otherwise
 * @param stack           detected stack when available, {@code null} otherwise
 * @param createdAt       creation timestamp
 * @param updatedAt       last update timestamp
 */
public record DeploymentResponse(
        String deploymentId,
        String projectPath,
        String zeropsProjectId,
        DeploymentStatus status,
        String message,
        String errorCode,
        String liveUrl,
        DetectedStack stack,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Maps a deployment entity to its REST representation.
     *
     * @param deployment the entity
     * @return the response DTO
     */
    public static DeploymentResponse from(Deployment deployment) {
        return new DeploymentResponse(
                deployment.getDeploymentId(),
                deployment.getProjectPath(),
                deployment.getZeropsProjectId(),
                deployment.getStatus(),
                deployment.getMessage(),
                deployment.getErrorCode(),
                deployment.getLiveUrl(),
                deployment.getStack(),
                deployment.getCreatedAt(),
                deployment.getUpdatedAt());
    }
}
