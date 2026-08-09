package com.stackd.ignition.api.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stackd.ignition.deployment.DeploymentInProgressException;
import com.stackd.ignition.deployment.DeploymentService;
import com.stackd.ignition.status.Deployment;
import com.stackd.ignition.status.DeploymentNotFoundException;
import com.stackd.ignition.status.DeploymentStatus;
import com.stackd.ignition.status.DeploymentStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice tests for {@link DeploymentController}: endpoint routing, HTTP status
 * codes, JSON content type, and the centralized error shape.
 */
@WebMvcTest(DeploymentController.class)
class DeploymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeploymentStatusService statusService;

    @MockitoBean
    private DeploymentService deploymentService;

    private static final String BASE = "/api/v1/deployments";

    @Test
    void createDeploymentReturns202WithDeploymentIdAndStatus() throws Exception {
        Deployment pending = Deployment.initial("d1", "/tmp/project", "zerops-project");
        when(deploymentService.createAndStartAsync("/tmp/project", "zerops-project")).thenReturn(pending);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectPath\":\"/tmp/project\",\"zeropsProjectId\":\"zerops-project\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.deploymentId").value("d1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value("Deployment created"));

        verify(deploymentService).createAndStartAsync("/tmp/project", "zerops-project");
        verifyNoInteractions(statusService);
    }

    @Test
    void createDeploymentRejectsInFlightDuplicateWith409() throws Exception {
        doThrow(new DeploymentInProgressException("/tmp/project", "zerops-project"))
                .when(deploymentService).createAndStartAsync("/tmp/project", "zerops-project");

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectPath\":\"/tmp/project\",\"zeropsProjectId\":\"zerops-project\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(true))
                .andExpect(jsonPath("$.code").value("DEPLOYMENT_ALREADY_IN_PROGRESS"));

        verifyNoInteractions(statusService);
    }

    @Test
    void createDeploymentRejectsBlankProjectPathWithStandardError() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectPath\":\"   \",\"zeropsProjectId\":\"zerops-project\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(true))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("projectPath: must not be blank"));

        verifyNoInteractions(statusService);
    }

    @Test
    void createDeploymentRejectsBlankZeropsProjectIdWithStandardError() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectPath\":\"/tmp/project\",\"zeropsProjectId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(true))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("zeropsProjectId: must not be blank"));

        verifyNoInteractions(statusService);
    }

    @Test
    void getDeploymentReturns200WithSummary() throws Exception {
        Deployment deployment = Deployment.initial("d1", "/tmp/project", "zerops-project");
        when(statusService.getDeployment("d1")).thenReturn(deployment);

        mockMvc.perform(get(BASE + "/d1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.deploymentId").value("d1"))
                .andExpect(jsonPath("$.projectPath").value("/tmp/project"))
                .andExpect(jsonPath("$.zeropsProjectId").value("zerops-project"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value("Deployment created"));
    }

    @Test
    void getDeploymentUnknownReturnsStandardErrorShape() throws Exception {
        when(statusService.getDeployment("nope")).thenThrow(new DeploymentNotFoundException("nope"));

        mockMvc.perform(get(BASE + "/nope"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                        "{\"error\":true,\"code\":\"DEPLOYMENT_NOT_FOUND\",\"message\":\"Deployment not found: nope\"}",
                        true));
    }

    @Test
    void getDeploymentStatusReturns200() throws Exception {
        Deployment deployment = Deployment.initial("d1", "/tmp/project", "zerops-project");
        when(statusService.getDeployment("d1")).thenReturn(deployment);

        mockMvc.perform(get(BASE + "/d1/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.deploymentId").value("d1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value("Deployment created"));
    }

    @Test
    void getDeploymentStatusUnknownReturnsStandardErrorShape() throws Exception {
        when(statusService.getDeployment("missing")).thenThrow(new DeploymentNotFoundException("missing"));

        mockMvc.perform(get(BASE + "/missing/status"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                        "{\"error\":true,\"code\":\"DEPLOYMENT_NOT_FOUND\",\"message\":\"Deployment not found: missing\"}",
                        true));
    }

    @Test
    void getDeploymentHealthReturns200WithHealthView() throws Exception {
        Deployment deployment = Deployment.initial("d1", "/tmp/project", "zerops-project")
                .withStatus(DeploymentStatus.HEALTHY, "Deployment is healthy")
                .withLiveUrl("https://backend-demo-prg1.zerops.app");
        when(statusService.getDeployment("d1")).thenReturn(deployment);

        mockMvc.perform(get(BASE + "/d1/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.deploymentId").value("d1"))
                .andExpect(jsonPath("$.status").value("HEALTHY"))
                .andExpect(jsonPath("$.liveUrl").value("https://backend-demo-prg1.zerops.app"));
    }

    @Test
    void getDeploymentHealthOmitsLiveUrlWhenNotVerified() throws Exception {
        Deployment deployment = Deployment.initial("d1", "/tmp/project", "zerops-project")
                .withStatus(DeploymentStatus.FAILED, "Health check failed")
                .withError("HEALTH_CHECK_FAILED", "Live URL returned HTTP 500");
        when(statusService.getDeployment("d1")).thenReturn(deployment);

        mockMvc.perform(get(BASE + "/d1/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.deploymentId").value("d1"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("HEALTH_CHECK_FAILED"))
                .andExpect(jsonPath("$.liveUrl").doesNotExist());
    }

    @Test
    void getDeploymentHealthUnknownReturnsStandardErrorShape() throws Exception {
        when(statusService.getDeployment("missing")).thenThrow(new DeploymentNotFoundException("missing"));

        mockMvc.perform(get(BASE + "/missing/health"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                        "{\"error\":true,\"code\":\"DEPLOYMENT_NOT_FOUND\",\"message\":\"Deployment not found: missing\"}",
                        true));
    }

    @Test
    void createDeploymentFailsOnMissingBody() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(true))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(statusService);
    }
}
