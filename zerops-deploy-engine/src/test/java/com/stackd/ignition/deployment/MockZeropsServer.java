package com.stackd.ignition.deployment;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Test helper exposing a scripted Zerops REST API over the JDK {@link HttpServer}
 * (no third-party test dependencies). Records every request for assertions and
 * lets tests script the returned process statuses, error responses, and delays.
 */
class MockZeropsServer implements AutoCloseable {

    private final HttpServer server;
    private final List<RequestRecord> requests =
            Collections.synchronizedList(new ArrayList<>());

    private String serviceStackId = "service-1";
    private String appVersionId = "app-version-1";
    private String processId = "process-1";
    private String publicZone = "demo-prg1-zerops.zone";
    private String subdomain = "";
    private boolean subdomainAccess = false;
    private String defaultProcessStatus = "PENDING";
    private final Queue<String> processStatusSequence = new ConcurrentLinkedQueue<>();

    private int errorStatus;
    private String errorPath;
    private String errorBody = "{\"message\":\"boom\"}";
    private long delayMs;
    private String delayedPath;

    MockZeropsServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Could not start mock server", e);
        }
        server.createContext("/", this::dispatch);
    }

    void start() {
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    List<RequestRecord> requests() {
        return List.copyOf(requests);
    }

    void setServiceStackId(String id) {
        this.serviceStackId = id;
    }

    void setAppVersionId(String id) {
        this.appVersionId = id;
    }

    void setProcessId(String id) {
        this.processId = id;
    }

    void setPublicZone(String zone) {
        this.publicZone = zone;
    }

    void setSubdomain(String value) {
        this.subdomain = value;
    }

    void setSubdomainAccess(boolean enabled) {
        this.subdomainAccess = enabled;
    }

    void setDefaultProcessStatus(String status) {
        this.defaultProcessStatus = status;
    }

    void enqueueProcessStatus(String status) {
        processStatusSequence.add(status);
    }

    void failPath(String path, int status, String body) {
        this.errorPath = path;
        this.errorStatus = status;
        this.errorBody = body;
    }

    void delayPath(String path, long ms) {
        this.delayedPath = path;
        this.delayMs = ms;
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        byte[] body = exchange.getRequestBody().readAllBytes();
        requests.add(new RequestRecord(method, path, auth, contentType, body));

        if (path.equals(delayedPath)) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (path.equals(errorPath)) {
            respond(exchange, errorStatus, errorBody);
            return;
        }

        String response;
        int status = 200;
        if (path.startsWith("/service-stack-by-name/") && method.equals("GET")) {
            response = "{\"id\":\"" + serviceStackId + "\",\"subdomainAccess\":"
                    + subdomainAccess + ",\"customSubdomain\":\"" + subdomain + "\"}";
        } else if (path.startsWith("/service-stack/") && path.endsWith("/app-version") && method.equals("POST")) {
            response = "{\"id\":\"" + appVersionId + "\"}";
        } else if (path.startsWith("/app-version/") && path.endsWith("/upload") && method.equals("PUT")) {
            response = "{\"success\":true}";
        } else if (path.startsWith("/app-version/") && path.endsWith("/build-and-deploy") && method.equals("PUT")) {
            response = "{\"id\":\"" + processId + "\",\"status\":\"PENDING\"}";
        } else if (path.startsWith("/process/") && method.equals("GET")) {
            String next = processStatusSequence.poll();
            response = "{\"id\":\"" + processId + "\",\"status\":\""
                    + (next == null ? defaultProcessStatus : next) + "\"}";
        } else if (path.startsWith("/project/") && method.equals("GET")) {
            response = "{\"id\":\"project-1\",\"publicZone\":\"" + publicZone + "\"}";
        } else {
            status = 404;
            response = "{\"error\":true,\"message\":\"unknown endpoint " + path + "\"}";
        }
        respond(exchange, status, response);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** A single captured request for assertions. */
    record RequestRecord(String method, String path, String authorization, String contentType, byte[] body) {

        String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
