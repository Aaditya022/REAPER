package com.stackd.ignition.status;

import java.util.UUID;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Component;

import com.stackd.ignition.analyzer.DetectedStack;

/**
 * Stateful facade over the in-memory deployment store.
 *
 * <p>Owns lifecycle state: creation, validated status transitions, message and
 * error attachments, and retrieval. Contains no HTTP or filesystem logic. All
 * changes are applied atomically via {@link DeploymentStore#updateIfPresent}.
 */
@Component
public class DeploymentStatusService {

    private final DeploymentStore store;

    /**
     * Creates the service over the given store.
     *
     * @param store the backing deployment store
     */
    public DeploymentStatusService(DeploymentStore store) {
        this.store = store;
    }

    /**
     * Creates a deployment in {@link DeploymentStatus#PENDING} state, generating
     * a fresh deployment id.
     *
     * <p>Keeps id generation out of the web layer so controllers stay thin.
     *
     * @param projectPath     source project directory path
     * @param zeropsProjectId target Zerops project identifier
     * @return the new pending deployment
     * @throws DeploymentAlreadyExistsException if the generated id collides (extremely unlikely)
     */
    public Deployment createDeployment(String projectPath, String zeropsProjectId) {
        return createDeployment(UUID.randomUUID().toString(), projectPath, zeropsProjectId);
    }

    /**
     * Creates a deployment with an explicit id in {@link DeploymentStatus#PENDING} state.
     *
     * @param deploymentId    unique deployment identifier
     * @param projectPath     source project directory path
     * @param zeropsProjectId target Zerops project identifier
     * @return the new pending deployment
     * @throws DeploymentAlreadyExistsException if the id is already present
     */
    public Deployment createDeployment(String deploymentId, String projectPath, String zeropsProjectId) {
        return store.create(Deployment.initial(deploymentId, projectPath, zeropsProjectId));
    }

    /**
     * Returns the deployment with the given id.
     *
     * @param deploymentId the deployment id
     * @return the deployment
     * @throws DeploymentNotFoundException if no such deployment exists
     */
    public Deployment getDeployment(String deploymentId) {
        return store.get(deploymentId)
                .orElseThrow(() -> new DeploymentNotFoundException(deploymentId));
    }

    /**
     * Performs a validated status transition.
     *
     * @param deploymentId the deployment id
     * @param target       the target status
     * @return the updated deployment
     * @throws DeploymentNotFoundException      if the deployment does not exist
     * @throws InvalidStateTransitionException if the transition is not allowed
     */
    public Deployment transitionTo(String deploymentId, DeploymentStatus target) {
        return mutate(deploymentId, current -> {
            validateTransition(current, target);
            return current.withStatus(target, target.defaultMessage());
        });
    }

    /**
     * Marks a deployment as {@link DeploymentStatus#FAILED} with an error code and message.
     *
     * @param deploymentId the deployment id
     * @param errorCode    stable machine-readable error code
     * @param message      failure message
     * @return the failed deployment
     * @throws DeploymentNotFoundException      if the deployment does not exist
     * @throws InvalidStateTransitionException if the current status cannot fail
     */
    public Deployment fail(String deploymentId, String errorCode, String message) {
        return mutate(deploymentId, current -> {
            validateTransition(current, DeploymentStatus.FAILED);
            return current.withError(errorCode, message);
        });
    }

    /**
     * Replaces the status message without changing the status.
     *
     * @param deploymentId the deployment id
     * @param message      the new message
     * @return the updated deployment
     * @throws DeploymentNotFoundException if the deployment does not exist
     */
    public Deployment updateMessage(String deploymentId, String message) {
        return mutate(deploymentId, current -> current.withMessage(message));
    }

    /**
     * Attaches the detected stack to the deployment.
     *
     * @param deploymentId the deployment id
     * @param stack        the detected stack
     * @return the updated deployment
     * @throws DeploymentNotFoundException if the deployment does not exist
     */
    public Deployment attachStack(String deploymentId, DetectedStack stack) {
        return mutate(deploymentId, current -> current.withStack(stack));
    }

    /**
     * Attaches the live URL to the deployment.
     *
     * @param deploymentId the deployment id
     * @param liveUrl      the live deployment URL
     * @return the updated deployment
     * @throws DeploymentNotFoundException if the deployment does not exist
     */
    public Deployment attachLiveUrl(String deploymentId, String liveUrl) {
        return mutate(deploymentId, current -> current.withLiveUrl(liveUrl));
    }

    private Deployment mutate(String deploymentId, UnaryOperator<Deployment> updater) {
        Deployment updated = store.updateIfPresent(deploymentId, updater);
        if (updated == null) {
            throw new DeploymentNotFoundException(deploymentId);
        }
        return updated;
    }

    private static void validateTransition(Deployment current, DeploymentStatus target) {
        if (!current.getStatus().canTransitionTo(target)) {
            throw new InvalidStateTransitionException(current.getStatus(), target);
        }
    }
}
