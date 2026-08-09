package com.stackd.ignition.api.controller;

import com.stackd.ignition.api.dto.DeploymentCreateRequest;
import com.stackd.ignition.api.dto.DeploymentResponse;
import com.stackd.ignition.api.dto.DeploymentStatusResponse;
import com.stackd.ignition.deployment.DeploymentService;
import com.stackd.ignition.status.Deployment;
import com.stackd.ignition.status.DeploymentStatusService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for deployment lifecycle.
 *
 * <p>Thin web layer: every request is delegated to {@link DeploymentStatusService}.
 * No idempotency, orchestration, filesystem access, Zerops calls, polling, or
 * health checks live here. Exceptions raised by the service are mapped to the
 * standard error shape by {@code GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/deployments")
public class DeploymentController {

    private final DeploymentStatusService statusService;
    private final DeploymentService deploymentService;

    /**
     * Creates the controller over the deployment status and orchestration services.
     *
     * @param statusService      the deployment lifecycle facade
     * @param deploymentService  the asynchronous deployment orchestrator
     */
    public DeploymentController(DeploymentStatusService statusService, DeploymentService deploymentService) {
        this.statusService = statusService;
        this.deploymentService = deploymentService;
    }

    /**
     * Creates a new deployment in {@code PENDING} state and starts the pipeline
     * asynchronously. The pair reservation and record creation are atomic, so a
     * duplicate in-flight request is rejected with {@code 409} before any
     * orphaned record is stored. The request thread is released immediately;
     * progress is tracked through the status endpoints.
     *
     * @param request validated create request
     * @return the created deployment id and current status
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeploymentStatusResponse createDeployment(@Valid @RequestBody DeploymentCreateRequest request) {
        Deployment deployment =
                deploymentService.createAndStartAsync(request.projectPath(), request.zeropsProjectId());
        return DeploymentStatusResponse.from(deployment);
    }

    /**
     * Returns the full summary of a single deployment.
     *
     * @param deploymentId the deployment id
     * @return the deployment summary
     * @throws com.stackd.ignition.status.DeploymentNotFoundException if unknown
     */
    @GetMapping("/{deploymentId}")
    public DeploymentResponse getDeployment(@PathVariable String deploymentId) {
        return DeploymentResponse.from(statusService.getDeployment(deploymentId));
    }

    /**
     * Returns the status-only view of a single deployment.
     *
     * @param deploymentId the deployment id
     * @return the deployment status
     * @throws com.stackd.ignition.status.DeploymentNotFoundException if unknown
     */
    @GetMapping("/{deploymentId}/status")
    public DeploymentStatusResponse getDeploymentStatus(@PathVariable String deploymentId) {
        return DeploymentStatusResponse.from(statusService.getDeployment(deploymentId));
    }

    /**
     * Returns the current health view of a single deployment: its lifecycle
     * status, stable error code when failed, and the verified live URL when a
     * health check has succeeded.
     *
     * @param deploymentId the deployment id
     * @return the deployment health view
     * @throws com.stackd.ignition.status.DeploymentNotFoundException if unknown
     */
    @GetMapping("/{deploymentId}/health")
    public DeploymentStatusResponse getDeploymentHealth(@PathVariable String deploymentId) {
        return DeploymentStatusResponse.from(statusService.getDeployment(deploymentId));
    }
}
