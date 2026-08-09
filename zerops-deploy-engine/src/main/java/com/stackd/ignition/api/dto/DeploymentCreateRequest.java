package com.stackd.ignition.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/deployments}.
 *
 * <p>Only blank/required validation is performed here; filesystem existence
 * checks belong to the analyzer/environment validation stage.
 *
 * @param projectPath     source STACKD-generated project directory path
 * @param zeropsProjectId target Zerops project identifier
 */
public record DeploymentCreateRequest(
        @NotBlank(message = "must not be blank") String projectPath,
        @NotBlank(message = "must not be blank") String zeropsProjectId) {
}
