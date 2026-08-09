package com.stackd.ignition.api.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackd.ignition.envmanager.EnvConfigException;
import com.stackd.ignition.status.DeploymentNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for the standard REST error shape and the error handler boundary.
 */
class ErrorResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void serializesToTheStandardErrorShape() throws Exception {
        ErrorResponse response = new ErrorResponse(true, "SOME_CODE", "Some message");

        String json = objectMapper.writeValueAsString(response);

        assertEquals("{\"error\":true,\"code\":\"SOME_CODE\",\"message\":\"Some message\"}", json);
    }

    @Test
    void notFoundMappingReturns404WithShape() {
        ResponseEntity<ErrorResponse> result = handler.handleNotFound(new DeploymentNotFoundException("d1"));

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        assertTrue(result.getBody().error());
        assertEquals("DEPLOYMENT_NOT_FOUND", result.getBody().code());
        assertFalse(result.getBody().message().contains("Exception"));
    }

    @Test
    void unexpectedExceptionReturnsGenericInternalErrorWithNoStackTrace() {
        ResponseEntity<ErrorResponse> result = handler.handleUnexpected(new IllegalStateException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertTrue(result.getBody().error());
        assertEquals("INTERNAL_ERROR", result.getBody().code());
        assertEquals("An unexpected error occurred", result.getBody().message());
        assertFalse(result.getBody().message().contains("boom"));
    }

    @Test
    void envConfigMappingReturns422WithCodeAndNoValues() {
        EnvConfigException ex = new EnvConfigException(EnvConfigException.MISSING_REQUIRED_ENV_VARS,
                "Required environment variables missing from the Zerops deployment environment: [DATABASE_URL]");

        ResponseEntity<ErrorResponse> result = handler.handleEnvConfig(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, result.getStatusCode());
        assertTrue(result.getBody().error());
        assertEquals("MISSING_REQUIRED_ENV_VARS", result.getBody().code());
        assertTrue(result.getBody().message().contains("DATABASE_URL"));
        assertFalse(result.getBody().message().contains("postgresql://"));
    }
}
