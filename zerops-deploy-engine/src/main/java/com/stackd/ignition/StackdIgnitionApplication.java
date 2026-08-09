package com.stackd.ignition;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * STACKD Ignition application entry point.
 *
 * <p>Boots the Spring Boot web server exposing the REST API for deploying
 * STACKD-generated projects to Zerops. Component scanning covers all
 * {@code com.stackd.ignition} sub-packages (analyzer, zeropsconfig,
 * envmanager, deployment, status, health, api, config).
 */
@SpringBootApplication
public class StackdIgnitionApplication {

    /**
     * Launches the STACKD Ignition application.
     *
     * @param args command line arguments forwarded to SpringApplication
     */
    public static void main(String[] args) {
        SpringApplication.run(StackdIgnitionApplication.class, args);
    }
}
