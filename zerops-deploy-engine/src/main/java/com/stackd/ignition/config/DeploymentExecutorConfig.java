package com.stackd.ignition.config;

import com.stackd.ignition.deployment.DeployProperties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the executor used to run deployment pipelines off the request
 * thread. A deploy can take minutes (build, polling, health check), so it must
 * never block the HTTP handler that accepted it.
 */
@Configuration
public class DeploymentExecutorConfig {

    /**
     * Creates the deployment executor.
     *
     * @param properties the {@code ignition.deploy.*} settings
     * @return a fixed-size thread pool with named worker threads
     */
    @Bean(name = "deploymentExecutor", destroyMethod = "shutdown")
    public ExecutorService deploymentExecutor(DeployProperties properties) {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "deployment-worker-" + counter.getAndIncrement());
                thread.setDaemon(false);
                return thread;
            }
        };
        return Executors.newFixedThreadPool((int) properties.getExecutorPoolSize(), factory);
    }
}
