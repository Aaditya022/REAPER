package com.stackd.ignition.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackd.ignition.analyzer.ArchitectureAnalyzer;
import com.stackd.ignition.analyzer.DetectedStack;
import com.stackd.ignition.analyzer.DetectedStack.Auth;
import com.stackd.ignition.analyzer.DetectedStack.Backend;
import com.stackd.ignition.analyzer.DetectedStack.Database;
import com.stackd.ignition.analyzer.DetectedStack.Frontend;
import com.stackd.ignition.analyzer.DetectedStack.Orm;
import com.stackd.ignition.envmanager.EnvConfigManager;
import com.stackd.ignition.envmanager.MergedEnv;
import com.stackd.ignition.health.HealthCheckException;
import com.stackd.ignition.health.HealthCheckService;
import com.stackd.ignition.status.Deployment;
import com.stackd.ignition.status.DeploymentStatus;
import com.stackd.ignition.status.DeploymentStatusService;
import com.stackd.ignition.status.DeploymentStore;
import com.stackd.ignition.zeropsconfig.ZeropsConfigGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.MDC;

/**
 * Tests for {@link DeploymentService}: the full pipeline, failure mapping,
 * polling behavior, idempotency, MDC lifecycle, and secret-safe environment
 * handling. The status service and store are real so state transitions are
 * genuinely enforced.
 */
class DeploymentServiceTest {

    private static final String PROJECT_PATH = "/tmp/stackd-project";
    private static final String PROJECT_ID = "project-123";
    private static final String YAML = "zerops:\n  - setup: backend\n";
    private static final String LIVE_URL = "https://backend-demo-prg1.zerops.app";

    private final DeploymentStore store = new DeploymentStore();
    private final DeploymentStatusService statusService = new DeploymentStatusService(store);
    private final ArchitectureAnalyzer analyzer = mock(ArchitectureAnalyzer.class);
    private final EnvConfigManager envConfigManager = mock(EnvConfigManager.class);
    private final ZeropsConfigGenerator configGenerator = mock(ZeropsConfigGenerator.class);
    private final SourcePackager sourcePackager = mock(SourcePackager.class);
    private final ZeropsClient zeropsClient = mock(ZeropsClient.class);
    private final HealthCheckService healthCheckService = mock(HealthCheckService.class);

    private final DeployProperties properties = new DeployProperties();

    {
        properties.setPollIntervalMs(1);
        properties.setMaxPollIntervalMs(1_000);
        properties.setPollTimeoutMs(500);
        properties.setHealthCheckTimeoutMs(1_000);
        properties.setExecutorPoolSize(2);
    }

    private DeploymentService service() {
        return new DeploymentService(statusService, analyzer, envConfigManager, configGenerator,
                sourcePackager, zeropsClient, healthCheckService, properties, Runnable::run);
    }

    private String createDeployment() {
        return statusService.createDeployment(PROJECT_PATH, PROJECT_ID).getDeploymentId();
    }

    private DetectedStack stack() {
        return new DetectedStack(Frontend.REACT_JS, Backend.EXPRESS_JS,
                Database.POSTGRESQL, Orm.PRISMA, Auth.JWT);
    }

    private void stubHappyPath() {
        when(analyzer.analyze(PROJECT_PATH)).thenReturn(stack());
        when(configGenerator.generate(any())).thenReturn(YAML);
        when(sourcePackager.packageSource(PROJECT_PATH)).thenReturn(new byte[0]);
        when(zeropsClient.findServiceStackId(PROJECT_ID, "backend")).thenReturn("service-1");
        when(zeropsClient.createAppVersion("service-1")).thenReturn("app-version-1");
        when(zeropsClient.buildAndDeploy("app-version-1", YAML, "backend")).thenReturn("process-1");
        when(zeropsClient.getProcessStatus("process-1")).thenReturn("FINISHED");
        when(zeropsClient.findServiceStackId(PROJECT_ID, "frontend")).thenReturn("service-2");
        when(zeropsClient.createAppVersion("service-2")).thenReturn("app-version-2");
        when(zeropsClient.buildAndDeploy("app-version-2", YAML, "frontend")).thenReturn("process-2");
        when(zeropsClient.getProcessStatus("process-2")).thenReturn("FINISHED");
        when(zeropsClient.resolveLiveUrl(PROJECT_ID, "frontend")).thenReturn(LIVE_URL);
    }

    @Test
    void successfulDeployReachesHealthyAndAttachesLiveUrl() {
        stubHappyPath();
        String id = createDeployment();

        service().startAsync(id);

        assertEquals(DeploymentStatus.HEALTHY, statusService.getDeployment(id).getStatus());
        assertEquals(LIVE_URL, statusService.getDeployment(id).getLiveUrl());
        assertNull(statusService.getDeployment(id).getErrorCode());
        verify(zeropsClient).uploadArtifact(eq("app-version-1"), any());
        verify(zeropsClient).uploadArtifact(eq("app-version-2"), any());
        verify(zeropsClient).buildAndDeploy(eq("app-version-1"), eq(YAML), eq("backend"));
        verify(zeropsClient).buildAndDeploy(eq("app-version-2"), eq(YAML), eq("frontend"));
    }

    @Test
    void fullStackDeploysBackendBeforeFrontend() {
        stubHappyPath();
        String id = createDeployment();

        service().startAsync(id);

        assertEquals(DeploymentStatus.HEALTHY, statusService.getDeployment(id).getStatus());
        InOrder order = inOrder(zeropsClient);
        order.verify(zeropsClient).findServiceStackId(PROJECT_ID, "backend");
        order.verify(zeropsClient).buildAndDeploy(eq("app-version-1"), eq(YAML), eq("backend"));
        order.verify(zeropsClient).findServiceStackId(PROJECT_ID, "frontend");
        order.verify(zeropsClient).buildAndDeploy(eq("app-version-2"), eq(YAML), eq("frontend"));
        order.verify(zeropsClient).resolveLiveUrl(PROJECT_ID, "frontend");
    }

    @Test
    void backendOnlyStackSurfacesBackendAsPrimary() {
        when(analyzer.analyze(PROJECT_PATH))
                .thenReturn(new DetectedStack(Frontend.NONE, Backend.EXPRESS_JS,
                        Database.POSTGRESQL, Orm.PRISMA, Auth.JWT));
        when(configGenerator.generate(any())).thenReturn(YAML);
        when(sourcePackager.packageSource(PROJECT_PATH)).thenReturn(new byte[0]);
        when(zeropsClient.findServiceStackId(PROJECT_ID, "backend")).thenReturn("service-1");
        when(zeropsClient.createAppVersion("service-1")).thenReturn("app-version-1");
        when(zeropsClient.buildAndDeploy("app-version-1", YAML, "backend")).thenReturn("process-1");
        when(zeropsClient.getProcessStatus("process-1")).thenReturn("FINISHED");
        when(zeropsClient.resolveLiveUrl(PROJECT_ID, "backend")).thenReturn(LIVE_URL);

        String id = createDeployment();

        service().startAsync(id);

        assertEquals(DeploymentStatus.HEALTHY, statusService.getDeployment(id).getStatus());
        assertEquals(LIVE_URL, statusService.getDeployment(id).getLiveUrl());
        verify(zeropsClient, never()).findServiceStackId(eq(PROJECT_ID), eq("frontend"));
    }

    @Test
    void zeropsEnvUsesConnectionStringReferenceNeverLocalSecret() {
        stubHappyPath();
        String id = createDeployment();

        service().startAsync(id);

        ArgumentCaptor<Map<String, String>> envCaptor = ArgumentCaptor.forClass(Map.class);
        verify(envConfigManager).mergeValidated(eq(PROJECT_PATH), any(), envCaptor.capture());
        Map<String, String> zeropsEnv = envCaptor.getValue();
        assertEquals(ZeropsConfigGenerator.DB_CONNECTION_STRING_REFERENCE, zeropsEnv.get("DATABASE_URL"),
                "gate value must equal the single shared connection-string reference");
        assertEquals("${db_connectionString}", zeropsEnv.get("DATABASE_URL"));
        assertFalse(zeropsEnv.containsValue("postgresql://stackd:supersecret@localhost:5432/stackd"));
        assertFalse(zeropsEnv.containsValue("postgresql://stackd:password@localhost/db"));
    }

    @Test
    void apiErrorFailsDeploymentWithErrorCode() {
        stubHappyPath();
        when(zeropsClient.createAppVersion("service-1"))
                .thenThrow(new ZeropsApiException(ZeropsApiException.ZEROPS_API_ERROR,
                        "Zerops API POST /service-stack/service-1/app-version returned HTTP 401", false));
        String id = createDeployment();

        service().startAsync(id);

        assertEquals(DeploymentStatus.FAILED, statusService.getDeployment(id).getStatus());
        assertEquals(ZeropsApiException.ZEROPS_API_ERROR, statusService.getDeployment(id).getErrorCode());
    }

    @Test
    void threeConsecutiveTransientFailuresFailWithUnreachable() {
        stubHappyPath();
        when(zeropsClient.getProcessStatus("process-1"))
                .thenThrow(new ZeropsApiException(ZeropsApiException.ZEROPS_API_TIMEOUT,
                        "Zerops API request timed out", true));
        String id = createDeployment();

        service().startAsync(id);

        assertEquals(DeploymentStatus.FAILED, statusService.getDeployment(id).getStatus());
        assertEquals(ZeropsApiException.ZEROPS_API_UNREACHABLE, statusService.getDeployment(id).getErrorCode());
        verify(zeropsClient, atLeast(3)).getProcessStatus("process-1");
    }

    @Test
    void pollsThroughIntermediateStatesToSuccess() {
        stubHappyPath();
        when(zeropsClient.getProcessStatus("process-1")).thenReturn("PENDING", "RUNNING", "FINISHED");
        String id = createDeployment();

        service().startAsync(id);

        assertEquals(DeploymentStatus.HEALTHY, statusService.getDeployment(id).getStatus());
        verify(zeropsClient, times(3)).getProcessStatus("process-1");
    }

    @Test
    void terminalProcessFailureFailsDeployment() {
        stubHappyPath();
        when(zeropsClient.getProcessStatus("process-1")).thenReturn("FAILED");
        String id = createDeployment();

        service().startAsync(id);

        assertEquals(DeploymentStatus.FAILED, statusService.getDeployment(id).getStatus());
        assertEquals(ZeropsApiException.ZEROPS_DEPLOY_FAILED, statusService.getDeployment(id).getErrorCode());
    }

    @Test
    void pollingBudgetExceededFailsWithTimeout() {
        stubHappyPath();
        properties.setPollTimeoutMs(50);
        when(zeropsClient.getProcessStatus("process-1")).thenReturn("PENDING");
        String id = createDeployment();

        service().startAsync(id);

        assertEquals(DeploymentStatus.FAILED, statusService.getDeployment(id).getStatus());
        assertEquals(ZeropsApiException.ZEROPS_DEPLOY_TIMEOUT, statusService.getDeployment(id).getErrorCode());
    }

    @Test
    void healthCheckFailureFailsDeployment() {
        stubHappyPath();
        doThrow(new HealthCheckException("Live URL returned HTTP 500"))
                .when(healthCheckService).verify(LIVE_URL, Duration.ofMillis(1_000));
        String id = createDeployment();

        service().startAsync(id);

        assertEquals(DeploymentStatus.FAILED, statusService.getDeployment(id).getStatus());
        assertEquals("HEALTH_CHECK_FAILED", statusService.getDeployment(id).getErrorCode());
        assertNull(statusService.getDeployment(id).getLiveUrl(),
                "the live URL must not be exposed when the health check fails");
    }

    @Test
    void duplicateDeployTargetIsRejectedWhileInFlight() throws Exception {
        CountDownLatch blocker = new CountDownLatch(1);
        when(analyzer.analyze(PROJECT_PATH)).thenReturn(stack());
        when(configGenerator.generate(any())).thenReturn(YAML);
        when(sourcePackager.packageSource(PROJECT_PATH)).thenReturn(new byte[0]);
        when(zeropsClient.findServiceStackId(PROJECT_ID, "backend")).thenReturn("service-1");
        when(zeropsClient.createAppVersion("service-1")).thenReturn("app-version-1");
        when(zeropsClient.buildAndDeploy("app-version-1", YAML, "backend")).thenReturn("process-1");
        when(zeropsClient.getProcessStatus("process-1")).thenAnswer(invocation -> {
            blocker.await(10, TimeUnit.SECONDS);
            return "FINISHED";
        });
        when(zeropsClient.resolveLiveUrl(PROJECT_ID, "backend")).thenReturn(LIVE_URL);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        DeploymentService service = new DeploymentService(statusService, analyzer, envConfigManager,
                configGenerator, sourcePackager, zeropsClient, healthCheckService, properties, executor);
        try {
            String first = createDeployment();
            service.startAsync(first);

            String second = statusService.createDeployment(PROJECT_PATH, PROJECT_ID).getDeploymentId();
            DeploymentInProgressException ex = assertThrows(DeploymentInProgressException.class,
                    () -> service.startAsync(second));
            assertEquals(DeploymentInProgressException.ERROR_CODE, ex.getErrorCode());
        } finally {
            blocker.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentIdenticalDeploysAllowExactlyOne() throws Exception {
        CountDownLatch blocker = new CountDownLatch(1);
        CountDownLatch start = new CountDownLatch(1);
        when(analyzer.analyze(PROJECT_PATH)).thenReturn(stack());
        when(configGenerator.generate(any())).thenReturn(YAML);
        when(sourcePackager.packageSource(PROJECT_PATH)).thenReturn(new byte[0]);
        when(zeropsClient.findServiceStackId(PROJECT_ID, "backend")).thenReturn("service-1");
        when(zeropsClient.createAppVersion("service-1")).thenReturn("app-version-1");
        when(zeropsClient.buildAndDeploy("app-version-1", YAML, "backend")).thenReturn("process-1");
        when(zeropsClient.getProcessStatus("process-1")).thenAnswer(invocation -> {
            blocker.await(10, TimeUnit.SECONDS);
            return "FINISHED";
        });
        when(zeropsClient.resolveLiveUrl(PROJECT_ID, "backend")).thenReturn(LIVE_URL);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        DeploymentService service = new DeploymentService(statusService, analyzer, envConfigManager,
                configGenerator, sourcePackager, zeropsClient, healthCheckService, properties, executor);
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        List<String> ids = List.of(createDeployment(), createDeployment());
        try {
            List<Thread> threads = new ArrayList<>();
            for (String id : ids) {
                Thread thread = new Thread(() -> {
                    try {
                        start.await(10, TimeUnit.SECONDS);
                        service.startAsync(id);
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                });
                threads.add(thread);
                thread.start();
            }
            start.countDown();
            for (Thread thread : threads) {
                thread.join(10_000);
            }

            long inProgress = failures.stream()
                    .filter(f -> f instanceof DeploymentInProgressException)
                    .count();
            assertEquals(1, inProgress, "exactly one deploy must be rejected: " + failures);
        } finally {
            blocker.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void createAndStartAsyncRejectsDuplicateWhileInFlightWithoutOrphanedRecord() throws Exception {
        CountDownLatch blocker = new CountDownLatch(1);
        when(analyzer.analyze(PROJECT_PATH)).thenReturn(stack());
        when(configGenerator.generate(any())).thenReturn(YAML);
        when(sourcePackager.packageSource(PROJECT_PATH)).thenReturn(new byte[0]);
        when(zeropsClient.findServiceStackId(PROJECT_ID, "backend")).thenReturn("service-1");
        when(zeropsClient.createAppVersion("service-1")).thenReturn("app-version-1");
        when(zeropsClient.buildAndDeploy("app-version-1", YAML, "backend")).thenReturn("process-1");
        when(zeropsClient.getProcessStatus("process-1")).thenAnswer(invocation -> {
            blocker.await(10, TimeUnit.SECONDS);
            return "FINISHED";
        });
        when(zeropsClient.findServiceStackId(PROJECT_ID, "frontend")).thenReturn("service-2");
        when(zeropsClient.createAppVersion("service-2")).thenReturn("app-version-2");
        when(zeropsClient.buildAndDeploy("app-version-2", YAML, "frontend")).thenReturn("process-2");
        when(zeropsClient.getProcessStatus("process-2")).thenReturn("FINISHED");
        when(zeropsClient.resolveLiveUrl(PROJECT_ID, "frontend")).thenReturn(LIVE_URL);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        DeploymentService service = new DeploymentService(statusService, analyzer, envConfigManager,
                configGenerator, sourcePackager, zeropsClient, healthCheckService, properties, executor);
        try {
            Deployment first = service.createAndStartAsync(PROJECT_PATH, PROJECT_ID);

            DeploymentInProgressException ex = assertThrows(DeploymentInProgressException.class,
                    () -> service.createAndStartAsync(PROJECT_PATH, PROJECT_ID));
            assertEquals(DeploymentInProgressException.ERROR_CODE, ex.getErrorCode());

            List<Deployment> stored = List.of(first.getDeploymentId()).stream()
                    .map(statusService::getDeployment)
                    .toList();
            assertEquals(1, stored.size(), "duplicate request must not leave an orphaned record");
        } finally {
            blocker.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void createAndStartAsyncReleasesReservationWhenRecordCreationFails() {
        DeploymentStatusService failing = mock(DeploymentStatusService.class);
        when(failing.createDeployment(PROJECT_PATH, PROJECT_ID))
                .thenThrow(new IllegalStateException("storage down"));
        DeploymentService service = new DeploymentService(failing, analyzer, envConfigManager,
                configGenerator, sourcePackager, zeropsClient, healthCheckService, properties, Runnable::run);

        assertThrows(IllegalStateException.class,
                () -> service.createAndStartAsync(PROJECT_PATH, PROJECT_ID));

        doReturn(Deployment.initial("d-2", PROJECT_PATH, PROJECT_ID))
                .when(failing).createDeployment(PROJECT_PATH, PROJECT_ID);
        when(failing.getDeployment("d-2")).thenReturn(Deployment.initial("d-2", PROJECT_PATH, PROJECT_ID));
        Deployment retried = service.createAndStartAsync(PROJECT_PATH, PROJECT_ID);
        assertEquals("d-2", retried.getDeploymentId(),
                "reservation must be released when creation fails, so a retry is not rejected");
    }

    @Test
    void mdcIsSetDuringPipelineAndClearedAfterwards() {
        stubHappyPath();
        List<String> seenInPipeline = new ArrayList<>();
        when(zeropsClient.getProcessStatus("process-1")).thenAnswer(invocation -> {
            seenInPipeline.add(MDC.get("deploymentId"));
            return "FINISHED";
        });
        String id = createDeployment();

        service().startAsync(id);

        assertEquals(List.of(id), seenInPipeline);
        assertNull(MDC.get("deploymentId"), "MDC must be cleared after the pipeline completes");
    }

    @Test
    void failureMessageNeverContainsTheDatabaseUrlValue() {
        stubHappyPath();
        when(zeropsClient.createAppVersion("service-1"))
                .thenThrow(new ZeropsApiException(ZeropsApiException.ZEROPS_API_ERROR,
                        "Zerops API returned HTTP 500", true));
        String id = createDeployment();

        service().startAsync(id);

        String message = statusService.getDeployment(id).getMessage();
        assertTrue(message.contains("HTTP 500"));
        assertFalse(message.contains("postgresql://stackd:supersecret@localhost:5432/stackd"));
    }

    @Test
    void transitionOrderIsValidEnforcedByRealStatusService() {
        stubHappyPath();
        String id = createDeployment();

        service().startAsync(id);

        assertEquals(DeploymentStatus.HEALTHY, statusService.getDeployment(id).getStatus());
    }

    @Test
    void analysisFailureFailsDeployment() {
        when(analyzer.analyze(PROJECT_PATH))
                .thenThrow(new com.stackd.ignition.analyzer.ProjectAnalysisException(
                        com.stackd.ignition.analyzer.ProjectAnalysisException.NOT_A_STACKD_PROJECT,
                        "Not a STACKD project"));
        String id = createDeployment();

        service().startAsync(id);

        assertEquals(DeploymentStatus.FAILED, statusService.getDeployment(id).getStatus());
        assertEquals(com.stackd.ignition.analyzer.ProjectAnalysisException.NOT_A_STACKD_PROJECT,
                statusService.getDeployment(id).getErrorCode());
    }

    @Test
    void redeployIsAllowedAfterTerminalFailureReleasesReservation() {
        AtomicBoolean failFirst = new AtomicBoolean(true);
        when(analyzer.analyze(PROJECT_PATH)).thenAnswer(invocation -> {
            if (failFirst.getAndSet(false)) {
                throw new com.stackd.ignition.analyzer.ProjectAnalysisException(
                        com.stackd.ignition.analyzer.ProjectAnalysisException.NOT_A_STACKD_PROJECT,
                        "Not a STACKD project");
            }
            return stack();
        });
        String first = createDeployment();

        service().startAsync(first);

        assertEquals(DeploymentStatus.FAILED, statusService.getDeployment(first).getStatus());

        stubHappyPath();
        Deployment retry = service().createAndStartAsync(PROJECT_PATH, PROJECT_ID);

        assertEquals(DeploymentStatus.HEALTHY, statusService.getDeployment(retry.getDeploymentId()).getStatus(),
                "a new deploy of the same target must be allowed after the failed run completes");
    }

    @Test
    void sourcePackagingFailureFailsDeployment() {
        stubHappyPath();
        when(sourcePackager.packageSource(PROJECT_PATH))
                .thenThrow(new SourcePackagingException("Could not package /tmp/stackd-project"));
        String id = createDeployment();

        service().startAsync(id);

        assertEquals(DeploymentStatus.FAILED, statusService.getDeployment(id).getStatus());
        assertEquals(SourcePackagingException.SOURCE_PACKAGING_FAILED,
                statusService.getDeployment(id).getErrorCode());
    }
}
