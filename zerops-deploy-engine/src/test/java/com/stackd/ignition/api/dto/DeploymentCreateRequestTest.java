package com.stackd.ignition.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for request DTO validation.
 */
class DeploymentCreateRequestTest {

    private final Validator validator;

    DeploymentCreateRequestTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            this.validator = factory.getValidator();
        }
    }

    @Test
    void validRequestPassesValidation() {
        DeploymentCreateRequest request = new DeploymentCreateRequest("/tmp/project", "zerops-project");

        Set<ConstraintViolation<DeploymentCreateRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void blankProjectPathFailsValidation() {
        DeploymentCreateRequest request = new DeploymentCreateRequest("   ", "zerops-project");

        Set<ConstraintViolation<DeploymentCreateRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("must not be blank", violations.iterator().next().getMessage());
    }

    @Test
    void nullAndBlankZeropsProjectIdFailValidation() {
        DeploymentCreateRequest request = new DeploymentCreateRequest("/tmp/project", null);

        Set<ConstraintViolation<DeploymentCreateRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("zeropsProjectId", violations.iterator().next().getPropertyPath().toString());
    }
}
