package com.stackd.ignition.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DeploymentStore}.
 */
class DeploymentStoreTest {

    private final DeploymentStore store = new DeploymentStore();

    private Deployment newDeployment(String id) {
        return Deployment.initial(id, "/tmp/proj-" + id, "zerops-" + id);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void createStoresAndGetReturnsTheDeployment() {
        Deployment deployment = store.create(newDeployment("d1"));

        assertEquals(deployment, store.get("d1").orElseThrow());
    }

    @Test
    void getReturnsEmptyForUnknownId() {
        Optional<Deployment> result = store.get("missing");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void duplicateCreateIsRejected() {
        store.create(newDeployment("d1"));

        assertThrows(DeploymentAlreadyExistsException.class, () -> store.create(newDeployment("d1")));
    }

    @Test
    void updateIfPresentAppliesTheUpdate() {
        store.create(newDeployment("d1"));

        Deployment updated = store.updateIfPresent("d1", current -> current.withMessage("changed"));

        assertNotNull(updated);
        assertEquals("changed", store.get("d1").orElseThrow().getMessage());
    }

    @Test
    void updateIfPresentReturnsNullForUnknownId() {
        Deployment updated = store.updateIfPresent("missing", current -> current.withMessage("x"));

        assertEquals(null, updated);
    }

    @Test
    void concurrentCreatesAndTransitionsOnDistinctIdsAreSafe() throws Exception {
        int threads = 8;
        int perThread = 25;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int threadIndex = t;
            futures.add(executor.submit(() -> {
                await(start);
                for (int i = 0; i < perThread; i++) {
                    String id = "t" + threadIndex + "-" + i;
                    store.create(newDeployment(id));
                    store.updateIfPresent(id, current -> current.withMessage("updated by " + threadIndex));
                }
            }));
        }

        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < perThread; i++) {
                String id = "t" + t + "-" + i;
                assertTrue(store.get(id).isPresent());
                assertEquals("updated by " + t, store.get(id).orElseThrow().getMessage());
            }
        }
    }

    @Test
    void concurrentMessageUpdatesOnSameKeyAreSafe() throws Exception {
        store.create(newDeployment("same"));
        int threads = 32;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int threadIndex = t;
            futures.add(executor.submit(() -> {
                await(start);
                store.updateIfPresent("same", current -> current.withMessage("m" + threadIndex));
            }));
        }

        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        Deployment finalState = store.get("same").orElseThrow();
        assertTrue(finalState.getMessage().matches("m\\d+"));
    }
}
