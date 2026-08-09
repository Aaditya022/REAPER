package com.stackd.ignition.deployment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Deployment pipeline timing settings bound to {@code ignition.deploy.*}.
 *
 * <p>Governs the async pipeline: how often the Zerops process is polled, how the
 * poll interval grows, how long polling may run in total, and how long the final
 * live-URL health check may take.
 */
@Component
@ConfigurationProperties(prefix = "ignition.deploy")
public class DeployProperties {

    private long pollIntervalMs;
    private long maxPollIntervalMs;
    private long pollTimeoutMs;
    private long healthCheckTimeoutMs;
    private int executorPoolSize;

    /**
     * Returns the initial interval between process polls.
     *
     * @return the poll interval in ms
     */
    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    /**
     * Sets the initial interval between process polls.
     *
     * @param pollIntervalMs the poll interval in ms
     */
    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    /**
     * Returns the ceiling for the exponentially growing poll interval.
     *
     * @return the maximum poll interval in ms
     */
    public long getMaxPollIntervalMs() {
        return maxPollIntervalMs;
    }

    /**
     * Sets the ceiling for the exponentially growing poll interval.
     *
     * @param maxPollIntervalMs the maximum poll interval in ms
     */
    public void setMaxPollIntervalMs(long maxPollIntervalMs) {
        this.maxPollIntervalMs = maxPollIntervalMs;
    }

    /**
     * Returns the total time polling is allowed to run before the deploy is
     * considered timed out.
     *
     * @return the poll timeout in ms
     */
    public long getPollTimeoutMs() {
        return pollTimeoutMs;
    }

    /**
     * Sets the total time polling may run.
     *
     * @param pollTimeoutMs the poll timeout in ms
     */
    public void setPollTimeoutMs(long pollTimeoutMs) {
        this.pollTimeoutMs = pollTimeoutMs;
    }

    /**
     * Returns the timeout for the final live-URL health check.
     *
     * @return the health check timeout in ms
     */
    public long getHealthCheckTimeoutMs() {
        return healthCheckTimeoutMs;
    }

    /**
     * Sets the timeout for the final live-URL health check.
     *
     * @param healthCheckTimeoutMs the health check timeout in ms
     */
    public void setHealthCheckTimeoutMs(long healthCheckTimeoutMs) {
        this.healthCheckTimeoutMs = healthCheckTimeoutMs;
    }

    /**
     * Returns the number of worker threads for concurrent deployment pipelines.
     *
     * @return the executor pool size
     */
    public int getExecutorPoolSize() {
        return executorPoolSize;
    }

    /**
     * Sets the number of worker threads for concurrent deployment pipelines.
     *
     * @param executorPoolSize the executor pool size
     */
    public void setExecutorPoolSize(int executorPoolSize) {
        this.executorPoolSize = executorPoolSize;
    }
}
