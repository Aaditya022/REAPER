package com.stackd.ignition.api.dto;

import com.stackd.ignition.status.Deployment;
import com.stackd.ignition.status.DeploymentStatus;

/**
 * Status-only deployment representation returned by
 * {@code GET /api/v1/deployments/{deploymentId}/status} and
 * {@code GET /api/v1/deployments/{deploymentId}/health}.
 *
 * @param deploymentId unique deployment identifier
 * @param status       current lifecycle status
 * @param message      human-readable status message
 * @param errorCode    stable error code when failed, {@code null} otherwise
 * @param liveUrl      live deployment URL when a health check has succeeded, {@code null} otherwise
 */
public record DeploymentStatusResponse(
        String deploymentId,
        DeploymentStatus status,
        String message,
        String errorCode,
        String liveUrl) {

    /**
     * Maps a deployment entity to its status-only representation.
     *
     * @param deployment the entity
     * @return the status response DTO
     */
    public static DeploymentStatusResponse from(Deployment deployment) {
        return new DeploymentStatusResponse(
                deployment.getDeploymentId(),
                deployment.getStatus(),
                deployment.getMessage(),
                deployment.getErrorCode(),
                deployment.getLiveUrl());
    }
}
