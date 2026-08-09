package com.stackd.ignition.deployment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * HTTP boundary to the Zerops public REST API.
 *
 * <p>Every call is authenticated with the configured personal access token in
 * the {@code Authorization: Bearer} header. Endpoints, request bodies, and
 * response fields match the official OpenAPI spec and the official showcase
 * deploy script (see the stage report): create app-version, binary upload,
 * build-and-deploy, process polling, service lookup, and live-URL derivation.
 *
 * <p>The client never logs the token, request bodies, or raw response bodies;
 * error messages describe the failing operation and HTTP status only. All
 * failures surface as {@link ZeropsApiException} carrying a stable error code
 * and a transient/terminal flag.
 */
@Component
public class ZeropsClient {

    private static final DateTimeFormatter VERSION_NAME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiToken;
    private final Duration requestTimeout;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates the client from the configured Zerops properties.
     *
     * @param properties the {@code ignition.zerops.*} settings
     */
    public ZeropsClient(ZeropsProperties properties) {
        this.baseUrl = stripTrailingSlash(properties.getApiBaseUrl());
        this.apiToken = properties.getApiToken() == null ? "" : properties.getApiToken();
        this.requestTimeout = Duration.ofMillis(properties.getApiTimeoutMs());
        this.httpClient = HttpClient.newBuilder().connectTimeout(requestTimeout).build();
    }

    /**
     * Resolves the id of the service stack named {@code serviceName} in the project.
     *
     * @param projectId   the Zerops project id
     * @param serviceName the service setup name (e.g. {@code backend})
     * @return the service stack id
     * @throws ZeropsApiException if the lookup fails or the service is not found
     */
    public String findServiceStackId(String projectId, String serviceName) {
        String path = "/service-stack-by-name/" + projectId + "/" + serviceName;
        try {
            HttpResponse<String> response = send("GET", path, null, null);
            return requiredText(parseObject(response, path), "id", path);
        } catch (ZeropsApiException e) {
            if (e.getHttpStatus() == 404) {
                throw new ZeropsApiException(ZeropsApiException.ZEROPS_SERVICE_NOT_FOUND,
                        "Service stack " + serviceName + " not found in project " + projectId, false, e);
            }
            throw e;
        }
    }

    /**
     * Creates a new application version under the given service stack.
     *
     * @param serviceStackId the service stack id
     * @return the created app-version id
     * @throws ZeropsApiException if the request fails
     */
    public String createAppVersion(String serviceStackId) {
        String path = "/service-stack/" + serviceStackId + "/app-version";
        String name = "stackd-" + LocalDateTime.now().format(VERSION_NAME);
        HttpResponse<String> response = send("POST", path, "application/json",
                json(Map.of("name", name)));
        return requiredText(parseObject(response, path), "id", path);
    }

    /**
     * Uploads the source archive for an app version as a gzipped tarball.
     *
     * @param appVersionId the app-version id
     * @param tarBytes     the gzipped tar source archive
     * @throws ZeropsApiException if the upload fails
     */
    public void uploadArtifact(String appVersionId, byte[] tarBytes) {
        String path = "/app-version/" + appVersionId + "/upload";
        sendBinary("PUT", path, tarBytes);
    }

    /**
     * Triggers build and deploy for an app version.
     *
     * @param appVersionId the app-version id
     * @param zeropsYaml   the generated {@code zerops.yaml} document
     * @param setupName    the setup name of the deployed service (e.g. {@code backend})
     * @return the process id to poll
     * @throws ZeropsApiException if the request fails
     */
    public String buildAndDeploy(String appVersionId, String zeropsYaml, String setupName) {
        String path = "/app-version/" + appVersionId + "/build-and-deploy";
        String body = json(Map.of("zeropsYaml", zeropsYaml, "zeropsYamlSetup", setupName));
        HttpResponse<String> response = send("PUT", path, "application/json", body);
        return requiredText(parseObject(response, path), "id", path);
    }

    /**
     * Returns the current status of a deploy process.
     *
     * @param processId the process id
     * @return the process status string (e.g. {@code PENDING}, {@code RUNNING}, {@code FINISHED})
     * @throws ZeropsApiException if the poll request fails
     */
    public String getProcessStatus(String processId) {
        String path = "/process/" + processId;
        HttpResponse<String> response = send("GET", path, null, null);
        String status = parseObject(response, path).path("status").asText();
        if (status.isEmpty()) {
            throw new ZeropsApiException(ZeropsApiException.ZEROPS_RESPONSE_MALFORMED,
                    "Zerops process response is missing the status field", false);
        }
        return status;
    }

    /**
     * Returns the public zone of the project (used to derive the live URL).
     *
     * @param projectId the Zerops project id
     * @return the public zone, or an empty string when absent
     * @throws ZeropsApiException if the request fails
     */
    public String getProjectPublicZone(String projectId) {
        String path = "/project/" + projectId;
        HttpResponse<String> response = send("GET", path, null, null);
        return parseObject(response, path).path("publicZone").asText();
    }

    /**
     * Derives the public live URL for a service in a project, mirroring the
     * official showcase script: the project {@code publicZone} is the internal
     * {@code *.prg1-zerops.zone} domain, which maps to the public
     * {@code *.prg1.zerops.app} host. When the public zone is unavailable the
     * service stack's {@code customSubdomain} is used. The stack's
     * {@code subdomainAccess} flag is a boolean: it only marks whether a
     * Zerops subdomain exists and never carries the subdomain itself, so it is
     * never concatenated into the URL.
     *
     * @param projectId   the Zerops project id
     * @param serviceName the service setup name
     * @return the live URL
     * @throws ZeropsApiException if no live URL can be derived
     */
    public String resolveLiveUrl(String projectId, String serviceName) {
        String publicZone = getProjectPublicZone(projectId);
        if (!publicZone.isEmpty()) {
            String publicHost = publicZone.replace("-zerops.zone", ".zerops.app");
            return "https://" + serviceName + "-" + publicHost;
        }
        String path = "/service-stack-by-name/" + projectId + "/" + serviceName;
        HttpResponse<String> response = send("GET", path, null, null);
        JsonNode node = parseObject(response, path);
        boolean subdomainAccess = node.path("subdomainAccess").asBoolean(false);
        String customSubdomain = node.path("customSubdomain").asText();
        if (!customSubdomain.isEmpty()) {
            return "https://" + customSubdomain;
        }
        if (subdomainAccess) {
            throw new ZeropsApiException(ZeropsApiException.ZEROPS_RESPONSE_MALFORMED,
                    "Zerops subdomain access is enabled for service " + serviceName
                            + " but the project public zone is unavailable", false);
        }
        throw new ZeropsApiException(ZeropsApiException.ZEROPS_RESPONSE_MALFORMED,
                "Could not derive a live URL for service " + serviceName, false);
    }

    private HttpResponse<String> send(String method, String path, String contentType, String jsonBody) {
        HttpRequest.Builder builder = newRequest(method, path);
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        BodyPublisher publisher = jsonBody == null ? BodyPublishers.noBody() : BodyPublishers.ofString(jsonBody);
        return execute(builder.method(method, publisher).build(), method, path);
    }

    private HttpResponse<String> sendBinary(String method, String path, byte[] tarBytes) {
        HttpRequest.Builder builder = newRequest(method, path)
                .header("Content-Type", "application/x-tar");
        return execute(builder.method(method, BodyPublishers.ofByteArray(tarBytes)).build(), method, path);
    }

    private HttpRequest.Builder newRequest(String method, String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + apiToken)
                .header("Accept", "application/json");
    }

    private HttpResponse<String> execute(HttpRequest request, String method, String path) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return response;
            }
            throw errorException(method, path, status, response.body());
        } catch (HttpTimeoutException e) {
            throw new ZeropsApiException(ZeropsApiException.ZEROPS_API_TIMEOUT,
                    "Zerops API request timed out: " + method + " " + path, true, e);
        } catch (IOException e) {
            throw new ZeropsApiException(ZeropsApiException.ZEROPS_API_UNREACHABLE,
                    "Zerops API unreachable: " + method + " " + path, true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ZeropsApiException(ZeropsApiException.ZEROPS_API_UNREACHABLE,
                    "Zerops API request interrupted: " + method + " " + path, true, e);
        }
    }

    private ZeropsApiException errorException(String method, String path, int status, String body) {
        String detail = extractMessage(body);
        String message = "Zerops API " + method + " " + path + " returned HTTP " + status
                + (detail.isEmpty() ? "" : ": " + detail);
        return new ZeropsApiException(ZeropsApiException.ZEROPS_API_ERROR, message, status >= 500, status, null);
    }

    private String extractMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            String message = node.path("message").asText();
            if (message.length() > 200) {
                message = message.substring(0, 200);
            }
            if (!apiToken.isEmpty()) {
                message = message.replace(apiToken, "***");
            }
            return message;
        } catch (IOException e) {
            return "";
        }
    }

    private JsonNode parseObject(HttpResponse<String> response, String path) {
        try {
            JsonNode node = objectMapper.readTree(response.body());
            if (node == null || !node.isObject()) {
                throw new ZeropsApiException(ZeropsApiException.ZEROPS_RESPONSE_MALFORMED,
                        "Zerops response for " + path + " is not a JSON object", false);
            }
            return node;
        } catch (IOException e) {
            throw new ZeropsApiException(ZeropsApiException.ZEROPS_RESPONSE_MALFORMED,
                    "Zerops response for " + path + " is not valid JSON", false, e);
        }
    }

    private static String requiredText(JsonNode node, String field, String path) {
        String value = node.path(field).asText();
        if (value.isEmpty()) {
            throw new ZeropsApiException(ZeropsApiException.ZEROPS_RESPONSE_MALFORMED,
                    "Zerops response for " + path + " is missing the " + field + " field", false);
        }
        return value;
    }

    private static String json(Map<String, String> values) {
        try {
            return new ObjectMapper().writeValueAsString(values);
        } catch (IOException e) {
            throw new IllegalStateException("Could not serialize Zerops request body", e);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url == null ? "" : url.replaceAll("/+$", "");
    }
}
