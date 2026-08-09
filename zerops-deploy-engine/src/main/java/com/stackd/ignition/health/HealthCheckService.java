package com.stackd.ignition.health;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Verifies a live deployment URL responds with a 2xx status.
 *
 * <p>The URL is validated before any network call: it must be a syntactically
 * valid absolute {@code https} URL with a host. The check then issues an
 * unauthenticated {@code GET} against the URL root with a bounded timeout and
 * treats any 2xx response as healthy. A non-2xx status, an unreachable host, a
 * timeout, or an invalid URL raises {@link HealthCheckException}. The check is
 * a single attempt; it never retries.
 */
@Component
public class HealthCheckService {

    private final HttpClient httpClient;

    /**
     * Creates the health check service with a client that has a bounded
     * connection timeout.
     */
    public HealthCheckService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    /**
     * Creates the health check service over a specific HTTP client.
     *
     * @param httpClient the client used for health check requests
     */
    public HealthCheckService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Checks that the URL responds with a 2xx status within the timeout.
     *
     * @param liveUrl the live deployment URL (must be an https URL with a host)
     * @param timeout the maximum time to wait for a response
     * @throws HealthCheckException if the URL is not a valid https URL or is not healthy
     */
    public void verify(String liveUrl, Duration timeout) {
        URI uri = validHttpsUri(liveUrl);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(timeout)
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return;
            }
            throw new HealthCheckException("Live URL returned HTTP " + response.statusCode());
        } catch (HttpTimeoutException e) {
            throw new HealthCheckException("Live URL check timed out after " + timeout.toMillis() + " ms", e);
        } catch (IOException e) {
            throw new HealthCheckException("Live URL is unreachable: " + hostOnly(uri), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HealthCheckException("Live URL check was interrupted", e);
        }
    }

    private static URI validHttpsUri(String liveUrl) {
        if (liveUrl == null || liveUrl.isBlank()) {
            throw new HealthCheckException("Live URL must be a non-empty https URL");
        }
        URI uri;
        try {
            uri = URI.create(liveUrl);
        } catch (IllegalArgumentException e) {
            throw new HealthCheckException("Live URL is not a valid URL");
        }
        if (uri.getScheme() == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new HealthCheckException("Live URL must use https");
        }
        if (uri.getHost() == null) {
            throw new HealthCheckException("Live URL must include a host");
        }
        return uri;
    }

    private static String hostOnly(URI uri) {
        return uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
    }
}
