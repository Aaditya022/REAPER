package com.stackd.ignition.deployment;

import com.stackd.ignition.analyzer.ArchitectureAnalyzer;
import com.stackd.ignition.analyzer.DetectedStack;
import com.stackd.ignition.analyzer.DetectedStack.Backend;
import com.stackd.ignition.analyzer.DetectedStack.Database;
import com.stackd.ignition.analyzer.DetectedStack.Frontend;
import com.stackd.ignition.analyzer.ProjectAnalysisException;
import com.stackd.ignition.envmanager.EnvConfigException;
import com.stackd.ignition.envmanager.EnvConfigManager;
import com.stackd.ignition.health.HealthCheckException;
import com.stackd.ignition.health.HealthCheckService;
import com.stackd.ignition.status.Deployment;
import com.stackd.ignition.status.DeploymentNotFoundException;
import com.stackd.ignition.status.DeploymentStatus;
import com.stackd.ignition.status.DeploymentStatusService;
import com.stackd.ignition.status.InvalidStateTransitionException;
import com.stackd.ignition.zeropsconfig.ZeropsConfigGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Orchestrates the asynchronous deployment pipeline.
 *
 * <p>Accepts a {@code PENDING} deployment, then runs it on a shared executor:
 * analyze the project stack, prepare the environment and {@code zerops.yaml},
 * package and upload the source, trigger build-and-deploy on Zerops for every
 * service in the detected stack (backend, then frontend when both are present),
 * poll each process to a terminal state, derive the live URL of the primary
 * (user-facing) service, verify it responds, and only then attach the URL and
 * mark the deployment {@link DeploymentStatus#HEALTHY}. A failed health check
 * fails the deployment without exposing a live URL. Any failure marks the
 * deployment {@link DeploymentStatus#FAILED} with a stable error code.
 *
 * <p>Idempotency: at most one deploy may run for a given (project path, Zerops
 * project) pair. {@link #createAndStartAsync} reserves the pair before creating
 * the deployment record, so a duplicate request is rejected with
 * {@link DeploymentInProgressException} without leaving an orphaned
 * {@code PENDING} record; the key is released in a {@code finally} so a later
 * redeploy of the same project is allowed.
 *
 * <p>Concurrency hygiene: the deployment id is set in the SLF4J MDC for the
 * duration of the pipeline and cleared in {@code finally}; the pipeline never
 * blocks the accepting request thread; polling is bounded by a timeout with
 * exponential backoff and fails fast after three consecutive transient Zerops
 * API failures. Nothing secret (tokens, {@code DATABASE_URL} values, raw
 * Zerops bodies) is ever logged or stored in messages.
 */
@Component
public class DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);

    private static final String PROCESS_FINISHED = "FINISHED";

    /** Terminal failure statuses from the official showcase script and the zerops-go process enum. */
    private static final Set<String> PROCESS_TERMINAL_FAILURE =
            Set.of("FAILED", "CANCELED", "CANCELLED", "BUILD_FAILED");

    /** Number of consecutive transient API failures tolerated while polling. */
    private static final int MAX_CONSECUTIVE_UNREACHABLE = 3;

    private final DeploymentStatusService statusService;
    private final ArchitectureAnalyzer analyzer;
    private final EnvConfigManager envConfigManager;
    private final ZeropsConfigGenerator configGenerator;
    private final SourcePackager sourcePackager;
    private final ZeropsClient zeropsClient;
    private final HealthCheckService healthCheckService;
    private final DeployProperties deployProperties;
    private final Executor executor;

    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * Creates the deployment orchestrator.
     *
     * @param statusService     deployment lifecycle facade
     * @param analyzer          project stack analyzer
     * @param envConfigManager  environment merging and validation
     * @param configGenerator   {@code zerops.yaml} generator
     * @param sourcePackager    source archive packager
     * @param zeropsClient      Zerops REST API client
     * @param healthCheckService live-URL verifier
     * @param deployProperties  pipeline timing settings
     * @param executor          executor running pipelines off the request thread
     */
    public DeploymentService(DeploymentStatusService statusService, ArchitectureAnalyzer analyzer,
                             EnvConfigManager envConfigManager, ZeropsConfigGenerator configGenerator,
                             SourcePackager sourcePackager, ZeropsClient zeropsClient,
                             HealthCheckService healthCheckService, DeployProperties deployProperties,
                             Executor executor) {
        this.statusService = statusService;
        this.analyzer = analyzer;
        this.envConfigManager = envConfigManager;
        this.configGenerator = configGenerator;
        this.sourcePackager = sourcePackager;
        this.zeropsClient = zeropsClient;
        this.healthCheckService = healthCheckService;
        this.deployProperties = deployProperties;
        this.executor = executor;
    }

    /**
     * Creates a deployment in {@code PENDING} state and starts the pipeline
     * asynchronously, atomically with the in-flight reservation for its
     * (project path, Zerops project) pair.
     *
     * <p>The pair is reserved before the deployment record is created, so a
     * concurrent or sequential duplicate request is rejected with
     * {@link DeploymentInProgressException} before any orphaned {@code PENDING}
     * record is stored. The winning request returns the deployment id that is
     * associated with the only stored record for the pair.
     *
     * @param projectPath     source project directory path
     * @param zeropsProjectId target Zerops project identifier
     * @return the created {@code PENDING} deployment
     * @throws DeploymentInProgressException if the same deploy target is already in flight
     */
    public Deployment createAndStartAsync(String projectPath, String zeropsProjectId) {
        String key = keyFor(projectPath, zeropsProjectId);
        if (!inFlight.add(key)) {
            throw new DeploymentInProgressException(projectPath, zeropsProjectId);
        }
        try {
            Deployment deployment = statusService.createDeployment(projectPath, zeropsProjectId);
            submit(deployment, key);
            return deployment;
        } catch (RuntimeException e) {
            inFlight.remove(key);
            throw e;
        }
    }

    /**
     * Starts the deployment pipeline asynchronously for an existing {@code PENDING}
     * deployment, rejecting duplicates for the same (project path, Zerops project)
     * pair that are still in flight.
     *
     * @param deploymentId the deployment id
     * @throws DeploymentNotFoundException if the deployment does not exist
     * @throws DeploymentInProgressException if the same deploy target is already in flight
     */
    public void startAsync(String deploymentId) {
        Deployment deployment = statusService.getDeployment(deploymentId);
        String key = keyFor(deployment.getProjectPath(), deployment.getZeropsProjectId());
        if (!inFlight.add(key)) {
            throw new DeploymentInProgressException(deployment.getProjectPath(), deployment.getZeropsProjectId());
        }
        submit(deployment, key);
    }

    private void submit(Deployment deployment, String key) {
        String deploymentId = deployment.getDeploymentId();
        Runnable task = () -> {
            MDC.put("deploymentId", deploymentId);
            try {
                runPipeline(deploymentId);
            } finally {
                inFlight.remove(key);
                MDC.remove("deploymentId");
            }
        };
        try {
            executor.execute(task);
        } catch (RuntimeException e) {
            inFlight.remove(key);
            throw e;
        }
    }

    private static String keyFor(String projectPath, String zeropsProjectId) {
        return projectPath + "\u0000" + zeropsProjectId;
    }

    private void runPipeline(String deploymentId) {
        Deployment deployment = statusService.getDeployment(deploymentId);
        String projectPath = deployment.getProjectPath();
        String zeropsProjectId = deployment.getZeropsProjectId();
        try {
            DetectedStack stack = analyze(deploymentId, projectPath);

            statusService.transitionTo(deploymentId, DeploymentStatus.CONFIGURING);
            envConfigManager.mergeValidated(projectPath, stack, zeropsEnvFor(stack));
            String yaml = configGenerator.generate(stack);
            byte[] sourceTar = sourcePackager.packageSource(projectPath);

            statusService.transitionTo(deploymentId, DeploymentStatus.DEPLOYING);
            for (String setupName : targetSetups(stack)) {
                String serviceId = zeropsClient.findServiceStackId(zeropsProjectId, setupName);
                String appVersionId = zeropsClient.createAppVersion(serviceId);
                zeropsClient.uploadArtifact(appVersionId, sourceTar);
                statusService.updateMessage(deploymentId, "Uploaded source archive for " + setupName
                        + "; waiting for the Zerops build");
                String processId = zeropsClient.buildAndDeploy(appVersionId, yaml, setupName);
                awaitProcess(deploymentId, processId);
            }

            statusService.transitionTo(deploymentId, DeploymentStatus.HEALTH_CHECKING);
            String liveUrl = zeropsClient.resolveLiveUrl(zeropsProjectId, primarySetup(stack));
            healthCheckService.verify(liveUrl, Duration.ofMillis(deployProperties.getHealthCheckTimeoutMs()));
            statusService.attachLiveUrl(deploymentId, liveUrl);

            statusService.transitionTo(deploymentId, DeploymentStatus.HEALTHY);
        } catch (HealthCheckException e) {
            failWith(deploymentId, e.getErrorCode(), e.getMessage());
        } catch (ZeropsApiException e) {
            failWith(deploymentId, e.getErrorCode(), e.getMessage());
        } catch (EnvConfigException e) {
            failWith(deploymentId, e.getErrorCode(), e.getMessage());
        } catch (ProjectAnalysisException e) {
            failWith(deploymentId, e.getErrorCode(), e.getMessage());
        } catch (SourcePackagingException e) {
            failWith(deploymentId, e.getErrorCode(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Deployment {} failed unexpectedly", deploymentId, e);
            failWith(deploymentId, "INTERNAL_ERROR", "Deployment failed unexpectedly");
        }
    }

    private DetectedStack analyze(String deploymentId, String projectPath) {
        statusService.transitionTo(deploymentId, DeploymentStatus.ANALYZING);
        DetectedStack stack = analyzer.analyze(projectPath);
        statusService.attachStack(deploymentId, stack);
        statusService.updateMessage(deploymentId, "Analyzed project stack");
        return stack;
    }

    private static Map<String, String> zeropsEnvFor(DetectedStack stack) {
        if (stack.database() == Database.NONE) {
            return Map.of();
        }
        return Map.of("DATABASE_URL", ZeropsConfigGenerator.DB_CONNECTION_STRING_REFERENCE);
    }

    /**
     * Returns the ordered list of Zerops setups to deploy for the stack:
     * the backend first when present, then the frontend. Both setups live in
     * the same generated {@code zerops.yaml}; each is deployed through its own
     * app-version and build-and-deploy operation, selected via the
     * {@code zeropsYamlSetup} field.
     */
    private static List<String> targetSetups(DetectedStack stack) {
        List<String> setups = new ArrayList<>();
        if (stack.backend() != Backend.NONE) {
            setups.add("backend");
        }
        if (stack.frontend() != Frontend.NONE) {
            setups.add("frontend");
        }
        return setups;
    }

    /**
     * Returns the user-facing setup whose live URL is surfaced and health-checked:
     * the frontend when present, otherwise the backend.
     */
    private static String primarySetup(DetectedStack stack) {
        return stack.frontend() != Frontend.NONE ? "frontend" : "backend";
    }

    private void awaitProcess(String deploymentId, String processId) {
        long deadline = System.currentTimeMillis() + deployProperties.getPollTimeoutMs();
        long delay = Math.max(1, deployProperties.getPollIntervalMs());
        int unreachableStrikes = 0;
        while (true) {
            if (System.currentTimeMillis() >= deadline) {
                throw new ZeropsApiException(ZeropsApiException.ZEROPS_DEPLOY_TIMEOUT,
                        "Zerops deploy did not finish within " + deployProperties.getPollTimeoutMs() + " ms", false);
            }
            try {
                String status = zeropsClient.getProcessStatus(processId);
                unreachableStrikes = 0;
                if (PROCESS_FINISHED.equals(status)) {
                    return;
                }
                if (PROCESS_TERMINAL_FAILURE.contains(status)) {
                    throw new ZeropsApiException(ZeropsApiException.ZEROPS_DEPLOY_FAILED,
                            "Zerops deploy process ended with status " + status, false);
                }
            } catch (ZeropsApiException e) {
                if (!e.isRetryable()) {
                    throw e;
                }
                if (++unreachableStrikes >= MAX_CONSECUTIVE_UNREACHABLE) {
                    throw new ZeropsApiException(ZeropsApiException.ZEROPS_API_UNREACHABLE,
                            "Zerops API unreachable after " + MAX_CONSECUTIVE_UNREACHABLE
                                    + " consecutive failures while polling deploy", false, e);
                }
            }
            long remaining = Math.max(1, deadline - System.currentTimeMillis());
            sleepQuietly(Math.min(delay, remaining));
            delay = Math.min(delay * 2, Math.max(1, deployProperties.getMaxPollIntervalMs()));
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ZeropsApiException(ZeropsApiException.ZEROPS_API_UNREACHABLE,
                    "Deploy polling was interrupted", true, e);
        }
    }

    private void failWith(String deploymentId, String errorCode, String message) {
        try {
            statusService.fail(deploymentId, errorCode, message);
        } catch (InvalidStateTransitionException e) {
            log.warn("Deployment {} already in a terminal state; not applying failure {}", deploymentId, errorCode);
        }
    }
}
