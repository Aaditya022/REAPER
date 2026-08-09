package com.stackd.ignition.envmanager;

import com.stackd.ignition.analyzer.DetectedStack;
import com.stackd.ignition.analyzer.DetectedStack.Database;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Merges the environment variables a STACKD project already generated with the
 * environment variables the Zerops deployment requires, and validates that no
 * required variable is missing before a deployment is started.
 *
 * <p>The project {@code .env} is read from {@code backend/.env} when present
 * (the {@code fb} layout STACKD produces), otherwise from the project root
 * {@code .env} (the {@code fs} layout). Reads are bounded, never follow
 * symlinks, and never log anything.
 *
 * <p>Required variables are derived from the detected stack: every database stack
 * requires {@code DATABASE_URL}, provided by the Zerops side (for example the
 * {@code ${db_connectionString}} reference of a provisioned Zerops Postgres
 * service). The merged result is {@link MergedEnv}, whose secret-safe views
 * ({@link MergedEnv#maskedValues()}, {@link MergedEnv#toString()}) and
 * exception messages never expose actual values.
 */
@Component
public class EnvConfigManager {

    /** Upper bound for a single .env read, mirroring the analyzer's config bound. */
    private static final long MAX_ENV_BYTES = 256L * 1024;

    private static final Set<String> DATABASE_URL = Set.of("DATABASE_URL");

    /**
     * Merges the project {@code .env} with the Zerops-provided variables.
     *
     * @param projectPath source STACKD project directory path
     * @param stack       the detected stack, used to derive required variables
     * @param zeropsEnv   variables the Zerops deployment will inject (injected
     *                    values win over project values); may be {@code null}
     * @return the merged, secret-safe environment
     * @throws EnvConfigException if the project path is invalid or the .env cannot be read
     */
    public MergedEnv merge(String projectPath, DetectedStack stack, Map<String, String> zeropsEnv) {
        Map<String, String> injected = zeropsEnv == null ? Map.of() : zeropsEnv;
        Map<String, String> project = readProjectEnv(resolveRoot(projectPath));
        Map<String, String> merged = new LinkedHashMap<>(project);
        merged.putAll(injected);
        return new MergedEnv(unmodifiable(merged), unmodifiable(project),
                unmodifiable(injected), requiredFor(stack));
    }

    /**
     * Merges the environments and validates completeness in one step.
     *
     * @param projectPath source STACKD project directory path
     * @param stack       the detected stack
     * @param zeropsEnv   variables the Zerops deployment will inject
     * @return the merged, validated environment
     * @throws EnvConfigException if the path or .env is invalid, or a required
     *         variable is missing
     */
    public MergedEnv mergeValidated(String projectPath, DetectedStack stack, Map<String, String> zeropsEnv) {
        MergedEnv env = merge(projectPath, stack, zeropsEnv);
        env.validate();
        return env;
    }

    private static Set<String> requiredFor(DetectedStack stack) {
        return switch (stack.database()) {
            case POSTGRESQL, MYSQL, MONGODB -> DATABASE_URL;
            case NONE -> Set.of();
        };
    }

    private static Path resolveRoot(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            throw new EnvConfigException(EnvConfigException.PROJECT_PATH_INVALID,
                    "Project path must not be blank");
        }
        Path real;
        try {
            real = Path.of(projectPath).toRealPath();
        } catch (IOException e) {
            throw new EnvConfigException(EnvConfigException.PROJECT_PATH_INVALID,
                    "Project path does not exist or is not readable: " + projectPath);
        }
        if (!Files.isDirectory(real)) {
            throw new EnvConfigException(EnvConfigException.PROJECT_PATH_INVALID,
                    "Project path is not a directory: " + projectPath);
        }
        return real;
    }

    private static Map<String, String> readProjectEnv(Path root) {
        Path backend = root.resolve("backend").resolve(".env");
        if (isRegularFileNoFollow(backend)) {
            return parseEnv(backend);
        }
        Path rootEnv = root.resolve(".env");
        if (isRegularFileNoFollow(rootEnv)) {
            return parseEnv(rootEnv);
        }
        return Map.of();
    }

    private static Map<String, String> parseEnv(Path file) {
        String content = readBounded(file);
        Map<String, String> result = new LinkedHashMap<>();
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = stripQuotes(line.substring(eq + 1).trim());
            if (!key.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private static String readBounded(Path file) {
        try {
            if (Files.size(file) > MAX_ENV_BYTES) {
                throw new EnvConfigException(EnvConfigException.UNREADABLE_ENV_FILE,
                        "Environment file exceeds the size limit: " + file.getFileName());
            }
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (EnvConfigException e) {
            throw e;
        } catch (IOException e) {
            throw new EnvConfigException(EnvConfigException.UNREADABLE_ENV_FILE,
                    "Could not read environment file: " + file.getFileName());
        }
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static boolean isRegularFileNoFollow(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static Map<String, String> unmodifiable(Map<String, String> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }
}
