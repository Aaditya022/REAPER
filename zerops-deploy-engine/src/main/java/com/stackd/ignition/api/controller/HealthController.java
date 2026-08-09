package com.stackd.ignition.api.controller;

import com.stackd.ignition.api.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness endpoint used to confirm the service is booted and responding.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    /**
     * Returns a static ok response while the application is running.
     *
     * @return {@link HealthResponse} with status {@code "ok"}
     */
    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("ok");
    }
}
