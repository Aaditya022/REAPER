package com.stackd.ignition.status;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Component;

/**
 * In-memory, thread-safe store of deployments keyed by deployment id.
 *
 * <p>Backed by a {@link ConcurrentHashMap}. State is intentionally not persisted:
 * a restart loses all deployments. This is accepted MVP technical debt, justified
 * because no database dependency is allowed for the MVP and the demo lifecycle is
 * a single application boot.
 */
@Component
public class DeploymentStore {

    private final Map<String, Deployment> deployments = new ConcurrentHashMap<>();

    /**
     * Stores a new deployment, rejecting a duplicate id.
     *
     * @param deployment the deployment to store
     * @return the stored deployment
     * @throws DeploymentAlreadyExistsException if the id is already present
     */
    public Deployment create(Deployment deployment) {
        Deployment previous = deployments.putIfAbsent(deployment.getDeploymentId(), deployment);
        if (previous != null) {
            throw new DeploymentAlreadyExistsException(deployment.getDeploymentId());
        }
        return deployment;
    }

    /**
     * Returns the deployment with the given id, if present.
     *
     * @param deploymentId the deployment id
     * @return an {@link Optional} containing the deployment if present
     */
    public Optional<Deployment> get(String deploymentId) {
        return Optional.ofNullable(deployments.get(deploymentId));
    }

    /**
     * Atomically applies an update to an existing deployment.
     *
     * <p>The update function is applied under the per-key lock of the backing map,
     * which makes concurrent state transitions safe.
     *
     * @param deploymentId the deployment id
     * @param updater      maps the current deployment to the next one; must not return {@code null}
     * @return the updated deployment, or {@code null} if the id was not present
     */
    public Deployment updateIfPresent(String deploymentId, UnaryOperator<Deployment> updater) {
        AtomicReference<Deployment> applied = new AtomicReference<>();
        deployments.computeIfPresent(deploymentId, (id, current) -> {
            Deployment next = updater.apply(current);
            applied.set(next);
            return next;
        });
        return applied.get();
    }
}
