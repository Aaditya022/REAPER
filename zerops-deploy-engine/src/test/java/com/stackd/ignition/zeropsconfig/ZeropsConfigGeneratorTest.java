package com.stackd.ignition.zeropsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stackd.ignition.analyzer.DetectedStack;
import com.stackd.ignition.analyzer.DetectedStack.Auth;
import com.stackd.ignition.analyzer.DetectedStack.Backend;
import com.stackd.ignition.analyzer.DetectedStack.Database;
import com.stackd.ignition.analyzer.DetectedStack.Frontend;
import com.stackd.ignition.analyzer.DetectedStack.Orm;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Unit tests for {@link ZeropsConfigGenerator}. Every generated document is re-parsed with a
 * real YAML parser, so "generates valid YAML" is asserted structurally, not by string matching.
 */
class ZeropsConfigGeneratorTest {

    private final ZeropsConfigGenerator generator = new ZeropsConfigGenerator();

    private static DetectedStack stack(Frontend frontend, Backend backend, Database database) {
        return new DetectedStack(frontend, backend, database, Orm.PRISMA, Auth.JWT);
    }

    private static Map<String, Object> parse(String yaml) {
        Object parsed = new Yaml().load(yaml);
        assertTrue(parsed instanceof Map, "output must parse as a YAML mapping");
        return cast(parsed);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> services(Map<String, Object> document) {
        Object services = document.get("zerops");
        assertTrue(services instanceof List, "top-level 'zerops' must be a list");
        return (List<Map<String, Object>>) services;
    }

    private static Map<String, Object> run(Map<String, Object> service) {
        return cast(service.get("run"));
    }

    private static Map<String, Object> build(Map<String, Object> service) {
        return cast(service.get("build"));
    }

    @Test
    void fullstackReactExpressPostgresGeneratesTwoServices() {
        String yaml = generator.generate(stack(Frontend.REACT_JS, Backend.EXPRESS_JS, Database.POSTGRESQL));

        List<Map<String, Object>> services = services(parse(yaml));
        assertEquals(2, services.size());

        Map<String, Object> frontend = services.get(0);
        assertEquals("frontend", frontend.get("setup"));
        assertEquals("static", run(frontend).get("base"));

        Map<String, Object> backend = services.get(1);
        assertEquals("backend", backend.get("setup"));
        assertEquals("nodejs@22", run(backend).get("base"));
        assertEquals("node index.js", run(backend).get("start"));
    }

    @Test
    void expressBackendListensOnPort3000WithRootHealthCheck() {
        String yaml = generator.generate(stack(Frontend.REACT_JS, Backend.EXPRESS_JS, Database.POSTGRESQL));

        Map<String, Object> backend = services(parse(yaml)).get(1);
        Map<String, Object> run = run(backend);

        List<Map<String, Object>> ports = list(run.get("ports"));
        assertEquals(1, ports.size());
        assertEquals(3000, ports.get(0).get("port"));
        assertTrue((boolean) ports.get(0).get("httpSupport"));

        Map<String, Object> healthCheck = cast(run.get("healthCheck"));
        Map<String, Object> httpGet = cast(healthCheck.get("httpGet"));
        assertEquals(3000, httpGet.get("port"));
        assertEquals("/", httpGet.get("path"));
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> list(Object value) {
        return (List<T>) value;
    }

    @Test
    void postgresDatabaseUrlUsesDbConnectionStringReference() {
        String yaml = generator.generate(stack(Frontend.REACT_JS, Backend.EXPRESS_JS, Database.POSTGRESQL));

        Map<String, Object> backend = services(parse(yaml)).get(1);
        Map<String, Object> env = cast(run(backend).get("envVariables"));
        String url = (String) env.get("DATABASE_URL");

        assertEquals("${db_connectionString}", url);
        assertEquals(ZeropsConfigGenerator.DB_CONNECTION_STRING_REFERENCE, url,
                "generated YAML value must match the single shared connection-string reference");
        assertFalse(yaml.contains("dbConnectionString"),
                "the template placeholder must be fully rendered, not leaked into zerops.yaml");
    }

    @Test
    void expressTypeScriptBuildsWithTsc() {
        String yaml = generator.generate(stack(Frontend.REACT_TS, Backend.EXPRESS_TS, Database.POSTGRESQL));

        List<Map<String, Object>> services = services(parse(yaml));
        Map<String, Object> backend = services.get(1);
        List<Object> buildCommands = list(build(backend).get("buildCommands"));
        assertTrue(buildCommands.contains("cd backend && npx tsc"));
        assertEquals("node index.js", run(backend).get("start"));
    }

    @Test
    void nextJsRunsAsNodeServiceWithHealthCheck() {
        String yaml = generator.generate(stack(Frontend.NEXT, Backend.NONE, Database.POSTGRESQL));

        List<Map<String, Object>> services = services(parse(yaml));
        assertEquals(1, services.size());
        Map<String, Object> run = run(services.get(0));
        assertEquals("nodejs@22", run.get("base"));
        assertEquals("npm start", run.get("start"));
        assertEquals(3000, ((Map<String, Object>) list(run.get("ports")).get(0)).get("port"));
        assertEquals("/", cast(cast(run.get("healthCheck")).get("httpGet")).get("path"));
        assertNotNull(run.get("envVariables"));
    }

    @Test
    void djangoBackendUsesPythonRuntimeWithoutHealthCheck() {
        String yaml = generator.generate(stack(Frontend.NONE, Backend.DRF, Database.POSTGRESQL));

        Map<String, Object> backend = services(parse(yaml)).get(0);
        assertEquals("backend", backend.get("setup"));
        Map<String, Object> run = run(backend);
        assertEquals("python@3.11", run.get("base"));
        assertEquals("python manage.py runserver 0.0.0.0:8000", run.get("start"));
        assertEquals(8000, ((Map<String, Object>) list(run.get("ports")).get(0)).get("port"));
        assertFalse(run.containsKey("healthCheck"));
    }

    @Test
    void databaseFreeBackendOmitsEnvVariablesBlock() {
        String yaml = generator.generate(stack(Frontend.NONE, Backend.EXPRESS_JS, Database.NONE));

        Map<String, Object> backend = services(parse(yaml)).get(0);
        assertFalse(run(backend).containsKey("envVariables"));
        assertFalse(yaml.contains("DATABASE_URL"));
    }

    @Test
    void mongoAndMysqlUseTheirOwnConnectionStringSchemes() {
        String mongo = generator.generate(stack(Frontend.REACT_JS, Backend.EXPRESS_JS, Database.MONGODB));
        assertTrue(mongo.contains(
                "DATABASE_URL: mongodb+srv://${db_user}:${db_password}@${db_hostname}/${db_dbName}"));

        String mysql = generator.generate(stack(Frontend.REACT_JS, Backend.EXPRESS_JS, Database.MYSQL));
        assertTrue(mysql.contains(
                "DATABASE_URL: mysql://${db_user}:${db_password}@${db_hostname}:${db_port}/${db_dbName}"));
    }

    @Test
    void frontendOnlyGeneratesSingleStaticService() {
        String yaml = generator.generate(stack(Frontend.REACT_JS, Backend.NONE, Database.NONE));

        List<Map<String, Object>> services = services(parse(yaml));
        assertEquals(1, services.size());
        assertEquals("frontend", services.get(0).get("setup"));
        assertEquals("static", run(services.get(0)).get("base"));
    }

    @Test
    void backendOnlyGeneratesSingleService() {
        String yaml = generator.generate(stack(Frontend.NONE, Backend.EXPRESS_JS, Database.POSTGRESQL));

        List<Map<String, Object>> services = services(parse(yaml));
        assertEquals(1, services.size());
        assertEquals("backend", services.get(0).get("setup"));
    }

    @Test
    void angularFrontendUsesAngularTemplate() {
        String yaml = generator.generate(stack(Frontend.ANGULAR, Backend.NONE, Database.NONE));

        assertTrue(yaml.contains("frontend/dist/angular-app/browser/~"));
        assertEquals("static", run(services(parse(yaml)).get(0)).get("base"));
    }

    @Test
    void emptyStackIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> generator.generate(stack(Frontend.NONE, Backend.NONE, Database.NONE)));
    }

    @Test
    void everySupportedStackComboProducesParseableYaml() {
        for (Frontend frontend : List.of(Frontend.REACT_JS, Frontend.REACT_TS, Frontend.VUE,
                Frontend.ANGULAR, Frontend.NEXT)) {
            for (Backend backend : List.of(Backend.EXPRESS_JS, Backend.EXPRESS_TS, Backend.DRF)) {
                for (Database database : Database.values()) {
                    String yaml = generator.generate(stack(frontend, backend, database));
                    assertFalse(services(parse(yaml)).isEmpty(), "no services for " + frontend + "/" + backend);
                }
            }
        }
    }

    @Test
    void everyFrontendOnlyComboProducesParseableYaml() {
        for (Frontend frontend : List.of(Frontend.REACT_JS, Frontend.REACT_TS, Frontend.VUE,
                Frontend.ANGULAR, Frontend.NEXT)) {
            for (Database database : Database.values()) {
                parse(generator.generate(stack(frontend, Backend.NONE, database)));
            }
        }
    }

    @Test
    void everyBackendOnlyComboProducesParseableYaml() {
        for (Backend backend : List.of(Backend.EXPRESS_JS, Backend.EXPRESS_TS, Backend.DRF)) {
            for (Database database : Database.values()) {
                parse(generator.generate(stack(Frontend.NONE, backend, database)));
            }
        }
    }
}
