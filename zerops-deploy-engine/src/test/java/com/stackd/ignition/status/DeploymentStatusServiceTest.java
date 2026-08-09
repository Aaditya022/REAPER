package com.stackd.ignition.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.stackd.ignition.analyzer.DetectedStack;

/**
 * Unit tests for {@link DeploymentStatusService} and the state machine.
 */
class DeploymentStatusServiceTest {

    private final DeploymentStore store = new DeploymentStore();
    private final DeploymentStatusService service = new DeploymentStatusService(store);
    private static final String ID = "deployment-1";

    private Deployment createDeployment() {
        return service.createDeployment(ID, "/tmp/project", "zerops-project");
    }

    @Test
    void createDeploymentStartsInPendingWithDefaults() {
        Deployment deployment = createDeployment();

        assertEquals(ID, deployment.getDeploymentId());
        assertEquals("/tmp/project", deployment.getProjectPath());
        assertEquals("zerops-project", deployment.getZeropsProjectId());
        assertEquals(DeploymentStatus.PENDING, deployment.getStatus());
        assertEquals("Deployment created", deployment.getMessage());
        assertNull(deployment.getErrorCode());
        assertNull(deployment.getLiveUrl());
        assertNull(deployment.getStack());
        assertEquals(deployment.getCreatedAt(), deployment.getUpdatedAt());
    }

    @Test
    void getDeploymentReturnsTheCreatedOne() {
        createDeployment();

        assertEquals(ID, service.getDeployment(ID).getDeploymentId());
        assertEquals(DeploymentStatus.PENDING, service.getDeployment(ID).getStatus());
    }

    @Test
    void getDeploymentThrowsNotFoundForUnknownId() {
        DeploymentNotFoundException ex = assertThrows(DeploymentNotFoundException.class,
                () -> service.getDeployment("missing"));

        assertEquals(DeploymentNotFoundException.ERROR_CODE, ex.getErrorCode());
    }

    @Test
    void idGeneratingOverloadCreatesUniquePendingDeployments() {
        Deployment first = service.createDeployment("/tmp/project-a", "zerops-a");
        Deployment second = service.createDeployment("/tmp/project-b", "zerops-b");

        assertTrue(!first.getDeploymentId().equals(second.getDeploymentId()));
        assertEquals("/tmp/project-a", first.getProjectPath());
        assertEquals("zerops-a", first.getZeropsProjectId());
        assertEquals(DeploymentStatus.PENDING, first.getStatus());
        assertEquals(DeploymentStatus.PENDING, second.getStatus());
        assertEquals(first, service.getDeployment(first.getDeploymentId()));
    }

    @Test
    void everyValidTransitionIsAccepted() {
        List<ValidTransition> valid = List.of(
                new ValidTransition(DeploymentStatus.PENDING, DeploymentStatus.ANALYZING),
                new ValidTransition(DeploymentStatus.ANALYZING, DeploymentStatus.CONFIGURING),
                new ValidTransition(DeploymentStatus.CONFIGURING, DeploymentStatus.DEPLOYING),
                new ValidTransition(DeploymentStatus.DEPLOYING, DeploymentStatus.HEALTH_CHECKING),
                new ValidTransition(DeploymentStatus.HEALTH_CHECKING, DeploymentStatus.HEALTHY));

        for (ValidTransition transition : valid) {
            String id = transition.from + "-" + transition.to;
            service.createDeployment(id, "/tmp/p", "zp");
            if (transition.from != DeploymentStatus.PENDING) {
                reach(service, id, transition.from);
            }

            Deployment updated = service.transitionTo(id, transition.to);
            assertEquals(transition.to, updated.getStatus());
            assertEquals(transition.to.defaultMessage(), updated.getMessage());
        }
    }

    @Test
    void failIsAcceptedFromEveryNonTerminalStage() {
        for (DeploymentStatus stage : List.of(
                DeploymentStatus.PENDING,
                DeploymentStatus.ANALYZING,
                DeploymentStatus.CONFIGURING,
                DeploymentStatus.DEPLOYING,
                DeploymentStatus.HEALTH_CHECKING)) {
            String id = "fail-" + stage;
            service.createDeployment(id, "/tmp/p", "zp");
            if (stage != DeploymentStatus.PENDING) {
                reach(service, id, stage);
            }

            Deployment failed = service.fail(id, "SOME_ERROR", "boom from " + stage);
            assertEquals(DeploymentStatus.FAILED, failed.getStatus());
            assertEquals("SOME_ERROR", failed.getErrorCode());
            assertEquals("boom from " + stage, failed.getMessage());
        }
    }

    @Test
    void invalidTransitionIsRejected() {
        createDeployment();

        InvalidStateTransitionException ex = assertThrows(InvalidStateTransitionException.class,
                () -> service.transitionTo(ID, DeploymentStatus.DEPLOYING));

        assertEquals(InvalidStateTransitionException.ERROR_CODE, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("PENDING"));
        assertTrue(ex.getMessage().contains("DEPLOYING"));
    }

    @Test
    void terminalStatesAcceptNoOutgoingTransitions() {
        String healthyId = "healthy";
        service.createDeployment(healthyId, "/tmp/p", "zp");
        reach(service, healthyId, DeploymentStatus.HEALTHY);
        assertThrows(InvalidStateTransitionException.class,
                () -> service.transitionTo(healthyId, DeploymentStatus.FAILED));
        assertThrows(InvalidStateTransitionException.class,
                () -> service.fail(healthyId, "X", "boom"));

        String failedId = "failed";
        service.createDeployment(failedId, "/tmp/p", "zp");
        service.fail(failedId, "X", "boom");
        assertThrows(InvalidStateTransitionException.class,
                () -> service.transitionTo(failedId, DeploymentStatus.ANALYZING));
        assertThrows(InvalidStateTransitionException.class,
                () -> service.fail(failedId, "Y", "again"));
    }

    @Test
    void updateMessageChangesMessageWithoutChangingStatus() {
        createDeployment();

        Deployment updated = service.updateMessage(ID, "working");

        assertEquals("working", updated.getMessage());
        assertEquals(DeploymentStatus.PENDING, updated.getStatus());
    }

    @Test
    void attachStackSetsTheDetectedStack() {
        createDeployment();
        DetectedStack stack = new DetectedStack(
                DetectedStack.Frontend.REACT_JS,
                DetectedStack.Backend.EXPRESS_JS,
                DetectedStack.Database.POSTGRESQL,
                DetectedStack.Orm.PRISMA,
                DetectedStack.Auth.JWT);

        Deployment updated = service.attachStack(ID, stack);

        assertEquals(stack, updated.getStack());
        assertEquals(stack, service.getDeployment(ID).getStack());
    }

    @Test
    void attachLiveUrlSetsTheUrl() {
        createDeployment();

        Deployment updated = service.attachLiveUrl(ID, "https://app.example.com");

        assertEquals("https://app.example.com", updated.getLiveUrl());
    }

    @Test
    void duplicateCreateIsRejected() {
        createDeployment();

        assertThrows(DeploymentAlreadyExistsException.class,
                () -> service.createDeployment(ID, "/tmp/other", "zp"));
    }

    @Test
    void operationsOnUnknownIdThrowNotFound() {
        assertThrows(DeploymentNotFoundException.class,
                () -> service.transitionTo("missing", DeploymentStatus.ANALYZING));
        assertThrows(DeploymentNotFoundException.class,
                () -> service.fail("missing", "X", "boom"));
        assertThrows(DeploymentNotFoundException.class,
                () -> service.updateMessage("missing", "m"));
        assertThrows(DeploymentNotFoundException.class,
                () -> service.attachLiveUrl("missing", "https://x"));
    }

    private static void reach(DeploymentStatusService svc, String id, DeploymentStatus target) {
        for (DeploymentStatus step : new DeploymentStatus[]{
                DeploymentStatus.ANALYZING,
                DeploymentStatus.CONFIGURING,
                DeploymentStatus.DEPLOYING,
                DeploymentStatus.HEALTH_CHECKING,
                DeploymentStatus.HEALTHY}) {
            if (svc.getDeployment(id).getStatus() == target) {
                return;
            }
            svc.transitionTo(id, step);
        }
    }

    private record ValidTransition(DeploymentStatus from, DeploymentStatus to) {
    }
}
