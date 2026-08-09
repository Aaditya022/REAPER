package com.stackd.ignition.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ZeropsClient} against a scripted mock of the Zerops REST
 * API over the JDK HTTP server. Covers authentication, endpoint payloads,
 * response parsing, error mapping, and timeout behavior.
 */
class ZeropsClientTest {

    private static final String TOKEN = "super-secret-token-value";

    private MockZeropsServer server;
    private ZeropsClient client;

    @BeforeEach
    void setUp() {
        server = new MockZeropsServer();
        server.start();
        client = new ZeropsClient(properties(2_000));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void sendsBearerTokenOnEveryRequest() {
        client.findServiceStackId("proj-1", "backend");
        client.createAppVersion("service-1");
        client.getProcessStatus("process-1");
        client.getProjectPublicZone("proj-1");

        assertFalse(server.requests().isEmpty());
        server.requests().forEach(request -> assertEquals("Bearer " + TOKEN, request.authorization()));
    }

    @Test
    void findsServiceStackId() {
        assertEquals("service-1", client.findServiceStackId("proj-1", "backend"));
        assertEquals("GET", server.requests().get(0).method());
        assertTrue(server.requests().get(0).path().endsWith("/service-stack-by-name/proj-1/backend"));
    }

    @Test
    void serviceLookup404MapsToServiceNotFound() {
        server.failPath("/service-stack-by-name/proj-1/missing", 404, "{\"message\":\"not found\"}");

        ZeropsApiException ex = assertThrows(ZeropsApiException.class,
                () -> client.findServiceStackId("proj-1", "missing"));
        assertEquals(ZeropsApiException.ZEROPS_SERVICE_NOT_FOUND, ex.getErrorCode());
        assertFalse(ex.isRetryable());
    }

    @Test
    void createsAppVersionWithNameBody() {
        assertEquals("app-version-1", client.createAppVersion("service-1"));
        MockZeropsServer.RequestRecord record = server.requests().get(0);
        assertEquals("POST", record.method());
        assertTrue(record.path().endsWith("/service-stack/service-1/app-version"));
        assertTrue(record.bodyText().contains("\"name\":\"stackd-"));
    }

    @Test
    void uploadsArtifactAsTarContentType() {
        byte[] tar = {1, 2, 3, 4};
        client.uploadArtifact("app-version-1", tar);

        MockZeropsServer.RequestRecord record = server.requests().get(0);
        assertEquals("PUT", record.method());
        assertTrue(record.path().endsWith("/app-version/app-version-1/upload"));
        assertEquals("application/x-tar", record.contentType());
        assertTrue(java.util.Arrays.equals(tar, record.body()));
    }

    @Test
    void triggersBuildAndDeployWithYamlAndSetupName() {
        String yaml = "zerops:\n  - setup: backend\n";
        assertEquals("process-1", client.buildAndDeploy("app-version-1", yaml, "backend"));

        MockZeropsServer.RequestRecord record = server.requests().get(0);
        assertEquals("PUT", record.method());
        assertTrue(record.path().endsWith("/app-version/app-version-1/build-and-deploy"));
        assertTrue(record.bodyText().contains("\"zeropsYaml\":\"zerops:\\n  - setup: backend\\n\""));
        assertTrue(record.bodyText().contains("\"zeropsYamlSetup\":\"backend\""));
    }

    @Test
    void pollsProcessStatus() {
        server.setDefaultProcessStatus("RUNNING");
        assertEquals("RUNNING", client.getProcessStatus("process-1"));
        assertTrue(server.requests().get(0).path().endsWith("/process/process-1"));
    }

    @Test
    void processStatusMissingFieldIsMalformed() {
        server.failPath("/process/process-1", 200, "{\"id\":\"process-1\"}");
        ZeropsApiException ex = assertThrows(ZeropsApiException.class,
                () -> client.getProcessStatus("process-1"));
        assertEquals(ZeropsApiException.ZEROPS_RESPONSE_MALFORMED, ex.getErrorCode());
    }

    @Test
    void resolvesLiveUrlFromPublicZone() {
        server.setPublicZone("demo-prg1-zerops.zone");
        assertEquals("https://backend-demo-prg1.zerops.app", client.resolveLiveUrl("proj-1", "backend"));
    }

    @Test
    void resolvesLiveUrlFromCustomSubdomainFallback() {
        server.setPublicZone("");
        server.setSubdomain("backend-demo-prg1.zerops.app");
        assertEquals("https://backend-demo-prg1.zerops.app", client.resolveLiveUrl("proj-1", "backend"));
    }

    @Test
    void resolvesLiveUrlWhenSubdomainAccessEnabledWithoutPublicZoneIsNotFabricated() {
        server.setPublicZone("");
        server.setSubdomainAccess(true);

        ZeropsApiException ex = assertThrows(ZeropsApiException.class,
                () -> client.resolveLiveUrl("proj-1", "backend"));
        assertEquals(ZeropsApiException.ZEROPS_RESPONSE_MALFORMED, ex.getErrorCode());
        assertFalse(ex.getMessage().contains("https://true"));
        assertFalse(ex.getMessage().contains("https://false"));
    }

    @Test
    void resolvesLiveUrlWhenSubdomainAccessDisabledAndNoCustomSubdomainFails() {
        server.setPublicZone("");
        server.setSubdomainAccess(false);

        ZeropsApiException ex = assertThrows(ZeropsApiException.class,
                () -> client.resolveLiveUrl("proj-1", "backend"));
        assertEquals(ZeropsApiException.ZEROPS_RESPONSE_MALFORMED, ex.getErrorCode());
        assertFalse(ex.getMessage().contains("https://true"));
        assertFalse(ex.getMessage().contains("https://false"));
    }

    @Test
    void resolvesLiveUrlWhenSubdomainAccessMissingAndNoCustomSubdomainFails() {
        server.setPublicZone("");
        server.failPath("/service-stack-by-name/proj-1/backend", 200, "{\"id\":\"service-1\"}");

        ZeropsApiException ex = assertThrows(ZeropsApiException.class,
                () -> client.resolveLiveUrl("proj-1", "backend"));
        assertEquals(ZeropsApiException.ZEROPS_RESPONSE_MALFORMED, ex.getErrorCode());
    }

    @Test
    void resolvesLiveUrlFromCustomSubdomain() {
        server.setPublicZone("");
        server.setSubdomain("api.example.com");

        assertEquals("https://api.example.com", client.resolveLiveUrl("proj-1", "backend"));
    }

    @Test
    void clientErrorIsNonRetryable() {
        server.failPath("/service-stack/service-1/app-version", 401, "{\"message\":\"unauthorized\"}");

        ZeropsApiException ex = assertThrows(ZeropsApiException.class, () -> client.createAppVersion("service-1"));
        assertEquals(ZeropsApiException.ZEROPS_API_ERROR, ex.getErrorCode());
        assertEquals(401, ex.getHttpStatus());
        assertFalse(ex.isRetryable());
        assertTrue(ex.getMessage().contains("401"));
        assertTrue(ex.getMessage().contains("unauthorized"));
    }

    @Test
    void serverErrorIsRetryable() {
        server.failPath("/process/process-1", 500, "{\"message\":\"internal error\"}");

        ZeropsApiException ex = assertThrows(ZeropsApiException.class, () -> client.getProcessStatus("process-1"));
        assertEquals(ZeropsApiException.ZEROPS_API_ERROR, ex.getErrorCode());
        assertEquals(500, ex.getHttpStatus());
        assertTrue(ex.isRetryable());
    }

    @Test
    void requestTimeoutMapsToTimeoutError() {
        client = new ZeropsClient(properties(150));
        server.delayPath("/process/process-1", 2_000);

        ZeropsApiException ex = assertThrows(ZeropsApiException.class, () -> client.getProcessStatus("process-1"));
        assertEquals(ZeropsApiException.ZEROPS_API_TIMEOUT, ex.getErrorCode());
        assertTrue(ex.isRetryable());
    }

    @Test
    void errorMessageRedactsTheToken() {
        server.failPath("/service-stack/service-1/app-version", 401,
                "{\"message\":\"invalid token " + TOKEN + " provided\"}");

        ZeropsApiException ex = assertThrows(ZeropsApiException.class, () -> client.createAppVersion("service-1"));
        assertFalse(ex.getMessage().contains(TOKEN));
        assertTrue(ex.getMessage().contains("***"));
    }

    private ZeropsProperties properties(long timeoutMs) {
        ZeropsProperties properties = new ZeropsProperties();
        properties.setApiBaseUrl(server.baseUrl());
        properties.setApiToken(TOKEN);
        properties.setApiTimeoutMs(timeoutMs);
        return properties;
    }
}
