package com.stackd.ignition.deployment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Zerops REST API connection settings bound to {@code ignition.zerops.*}.
 *
 * <p>Defaults live in {@code application.yml}. The base URL is the verified
 * public REST endpoint; the token is a Zerops personal access token and is
 * never logged or echoed anywhere.
 */
@Component
@ConfigurationProperties(prefix = "ignition.zerops")
public class ZeropsProperties {

    private String apiBaseUrl;
    private String apiToken;
    private long apiTimeoutMs;

    /**
     * Returns the Zerops REST API base URL.
     *
     * @return the base URL, e.g. {@code https://api.app-prg1.zerops.io/api/rest/public}
     */
    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    /**
     * Sets the Zerops REST API base URL.
     *
     * @param apiBaseUrl the base URL
     */
    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    /**
     * Returns the Zerops personal access token used for Bearer authentication.
     *
     * @return the token
     */
    public String getApiToken() {
        return apiToken;
    }

    /**
     * Sets the Zerops personal access token.
     *
     * @param apiToken the token
     */
    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    /**
     * Returns the per-request timeout in milliseconds.
     *
     * @return the timeout in ms
     */
    public long getApiTimeoutMs() {
        return apiTimeoutMs;
    }

    /**
     * Sets the per-request timeout in milliseconds.
     *
     * @param apiTimeoutMs the timeout in ms
     */
    public void setApiTimeoutMs(long apiTimeoutMs) {
        this.apiTimeoutMs = apiTimeoutMs;
    }
}
