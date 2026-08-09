package com.stackd.ignition.status;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle states of a deployment.
 *
 * <p>The allowed transitions are defined here and enforced by
 * {@link DeploymentStatusService}; terminal states {@link #HEALTHY} and
 * {@link #FAILED} accept no outgoing transitions.
 */
public enum DeploymentStatus {

    /** Deployment created, awaiting pipeline start. */
    PENDING,
    /** The project stack is being analyzed. */
    ANALYZING,
    /** Environment and Zerops configuration are being prepared. */
    CONFIGURING,
    /** The deploy request has been sent to Zerops. */
    DEPLOYING,
    /** The live URL is being checked for a healthy response. */
    HEALTH_CHECKING,
    /** Deployment succeeded and the live URL responds. */
    HEALTHY,
    /** Deployment failed from any appropriate stage. */
    FAILED;

    private static final Map<DeploymentStatus, Set<DeploymentStatus>> ALLOWED = new EnumMap<>(DeploymentStatus.class);

    static {
        ALLOWED.put(PENDING, EnumSet.of(ANALYZING, FAILED));
        ALLOWED.put(ANALYZING, EnumSet.of(CONFIGURING, FAILED));
        ALLOWED.put(CONFIGURING, EnumSet.of(DEPLOYING, FAILED));
        ALLOWED.put(DEPLOYING, EnumSet.of(HEALTH_CHECKING, FAILED));
        ALLOWED.put(HEALTH_CHECKING, EnumSet.of(HEALTHY, FAILED));
        ALLOWED.put(HEALTHY, EnumSet.noneOf(DeploymentStatus.class));
        ALLOWED.put(FAILED, EnumSet.noneOf(DeploymentStatus.class));
    }

    /**
     * Returns whether a direct transition from this status to {@code target} is valid.
     *
     * @param target the candidate target status
     * @return {@code true} if the transition is allowed
     */
    public boolean canTransitionTo(DeploymentStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /**
     * Returns the default human-readable message shown when entering this status.
     *
     * @return the default status message
     */
    public String defaultMessage() {
        return switch (this) {
            case PENDING -> "Deployment created";
            case ANALYZING -> "Analyzing project stack";
            case CONFIGURING -> "Preparing environment and Zerops configuration";
            case DEPLOYING -> "Deploying to Zerops";
            case HEALTH_CHECKING -> "Checking live deployment health";
            case HEALTHY -> "Deployment healthy";
            case FAILED -> "Deployment failed";
        };
    }
}
