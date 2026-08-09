package com.stackd.ignition.api.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.stackd.ignition.deployment.DeploymentInProgressException;
import com.stackd.ignition.envmanager.EnvConfigException;
import com.stackd.ignition.status.DeploymentAlreadyExistsException;
import com.stackd.ignition.status.DeploymentNotFoundException;
import com.stackd.ignition.status.InvalidStateTransitionException;

/**
 * Central error boundary converting exceptions into the standard REST error
 * shape {@code {"error":true,"code":"...","message":"..."}}. Stack traces
 * are logged server-side and never exposed in response bodies.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Maps a missing deployment to {@code 404 DEPLOYMENT_NOT_FOUND}.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(DeploymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(DeploymentNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage());
    }

    /**
     * Maps a duplicate deployment to {@code 409 DEPLOYMENT_ALREADY_EXISTS}.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(DeploymentAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyExists(DeploymentAlreadyExistsException ex) {
        return build(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage());
    }

    /**
     * Maps an invalid state transition to {@code 409 INVALID_STATE_TRANSITION}.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidStateTransitionException ex) {
        return build(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage());
    }

    /**
     * Maps a duplicate in-flight deploy to {@code 409 DEPLOYMENT_ALREADY_IN_PROGRESS}.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(DeploymentInProgressException.class)
    public ResponseEntity<ErrorResponse> handleInProgress(DeploymentInProgressException ex) {
        return build(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage());
    }

    /**
     * Maps an environment configuration failure to {@code 422}. The message
     * names only missing variable keys, never their values.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(EnvConfigException.class)
    public ResponseEntity<ErrorResponse> handleEnvConfig(EnvConfigException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage());
    }

    /**
     * Maps a bean-validation failure to {@code 400 VALIDATION_FAILED}.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    /**
     * Fallback for unexpected exceptions: logs the full stack server-side and
     * returns a generic message with no stack trace in the body.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(true, code, message));
    }
}
