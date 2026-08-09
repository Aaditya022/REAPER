package com.stackd.ignition;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackd.ignition.deployment.DeployProperties;
import com.stackd.ignition.deployment.DeploymentService;
import com.stackd.ignition.deployment.ZeropsProperties;
import com.stackd.ignition.health.HealthCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test asserting the full application context boots and the new stage-4.4
 * beans (properties binding, HTTP clients, and the deployment orchestrator) wire
 * together.
 */
@SpringBootTest
class StackdIgnitionApplicationTest {

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private ZeropsProperties zeropsProperties;

    @Autowired
    private DeployProperties deployProperties;

    @Autowired
    private HealthCheckService healthCheckService;

    @Test
    void contextLoadsAndWiresDeploymentPipeline() {
        assertThat(deploymentService).isNotNull();
        assertThat(healthCheckService).isNotNull();
        assertThat(zeropsProperties.getApiBaseUrl())
                .startsWith("https://api.app-prg1.zerops.io/api/rest/public");
        assertThat(deployProperties.getPollTimeoutMs()).isGreaterThan(0);
        assertThat(deployProperties.getExecutorPoolSize()).isGreaterThan(0);
    }
}
