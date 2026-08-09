package com.stackd.ignition.zeropsconfig;

import com.stackd.ignition.analyzer.DetectedStack;
import com.stackd.ignition.analyzer.DetectedStack.Backend;
import com.stackd.ignition.analyzer.DetectedStack.Database;
import com.stackd.ignition.analyzer.DetectedStack.Frontend;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Renders the {@code zerops.yaml} deployment configuration for a detected STACKD stack.
 *
 * <p>One service template per framework variant, mirroring the template-per-concern pattern
 * STACKD itself uses for its generated files. The generator selects the templates for the
 * detected frontend, backend, and database (a lookup table, never an if/else chain), fills
 * the {@code {{placeholder}}} tokens, and assembles the {@code zerops:} document. A template
 * with an unknown or unresolved placeholder fails loudly instead of producing a broken file.
 *
 * <p>The rendered file declares runtime services only. Database services are provisioned in the
 * Zerops project import file; this config references them through cross-service environment
 * variables ({@code ${db_user}} etc.).
 */
@Component
public class ZeropsConfigGenerator {

    /** Classpath folder holding the {@code .tmpl} resource files. */
    private static final String TEMPLATE_RESOURCE_PREFIX = "/zeropsconfig/";

    /**
     * The single owned source of truth for the PostgreSQL connection string
     * injected by the platform. Rendered into {@code env-postgres.tmpl} via the
     * {@code {{dbConnectionString}}} placeholder and used by the deployment
     * pipeline's environment validation gate, so the gate and the generated
     * {@code zerops.yaml} can never silently diverge.
     */
    public static final String DB_CONNECTION_STRING_REFERENCE = "${db_connectionString}";

    private static final Map<Frontend, String> FRONTEND_TEMPLATES =
            Map.of(
                    Frontend.REACT_JS, "frontend-vite.tmpl",
                    Frontend.REACT_TS, "frontend-vite.tmpl",
                    Frontend.VUE, "frontend-vite.tmpl",
                    Frontend.ANGULAR, "frontend-angular.tmpl",
                    Frontend.NEXT, "frontend-next.tmpl");

    private static final Map<Backend, String> BACKEND_TEMPLATES =
            Map.of(
                    Backend.EXPRESS_JS, "backend-expressjs.tmpl",
                    Backend.EXPRESS_TS, "backend-expressts.tmpl",
                    Backend.DRF, "backend-drf.tmpl");

    private static final Map<Database, String> DATABASE_ENV_TEMPLATES =
            Map.of(
                    Database.POSTGRESQL, "env-postgres.tmpl",
                    Database.MYSQL, "env-mysql.tmpl",
                    Database.MONGODB, "env-mongo.tmpl");

    /**
     * Generates the {@code zerops.yaml} content for the given stack.
     *
     * @param stack the detected stack
     * @return the YAML document as a string
     * @throws IllegalArgumentException if the stack has no frontend and no backend, or uses a
     *         framework for which no service template exists
     */
    public String generate(DetectedStack stack) {
        List<String> services = new ArrayList<>();
        renderFrontend(stack, services);
        renderBackend(stack, services);
        if (services.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot generate a Zerops configuration for a stack without a frontend or backend");
        }
        return "zerops:\n" + String.join("\n", services) + "\n";
    }

    private void renderFrontend(DetectedStack stack, List<String> into) {
        Frontend frontend = stack.frontend();
        if (frontend == Frontend.NONE) {
            return;
        }
        String template = FRONTEND_TEMPLATES.get(frontend);
        if (template == null) {
            throw new IllegalArgumentException("No Zerops service template for frontend " + frontend);
        }
        into.add(render(template, envValues(stack)));
    }

    private void renderBackend(DetectedStack stack, List<String> into) {
        Backend backend = stack.backend();
        if (backend == Backend.NONE) {
            return;
        }
        String template = BACKEND_TEMPLATES.get(backend);
        if (template == null) {
            throw new IllegalArgumentException("No Zerops service template for backend " + backend);
        }
        into.add(render(template, envValues(stack)));
    }

    private Map<String, String> envValues(DetectedStack stack) {
        if (stack.database() == Database.NONE) {
            return Map.of("envVariables", "");
        }
        String template = DATABASE_ENV_TEMPLATES.get(stack.database());
        if (template == null) {
            throw new IllegalArgumentException("No Zerops environment template for database " + stack.database());
        }
        if (stack.database() == Database.POSTGRESQL) {
            return Map.of("envVariables", render(template,
                    Map.of("dbConnectionString", DB_CONNECTION_STRING_REFERENCE)));
        }
        return Map.of("envVariables", render(template, Map.of()));
    }

    private static String render(String templateName, Map<String, String> values) {
        String template = loadTemplate(templateName);
        String rendered = template;
        for (Map.Entry<String, String> value : values.entrySet()) {
            rendered = rendered.replace("{{" + value.getKey() + "}}", value.getValue());
        }
        rendered = rendered.replaceAll("(?m)^[ \\t]*\\n", "");
        if (rendered.contains("{{")) {
            throw new IllegalStateException("Unresolved placeholder in Zerops template " + templateName);
        }
        return rendered.stripTrailing();
    }

    private static String loadTemplate(String templateName) {
        String resource = TEMPLATE_RESOURCE_PREFIX + templateName;
        try (InputStream in = ZeropsConfigGenerator.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing Zerops template resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read Zerops template " + resource, e);
        }
    }
}
