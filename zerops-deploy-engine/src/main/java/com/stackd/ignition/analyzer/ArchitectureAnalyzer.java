package com.stackd.ignition.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackd.ignition.analyzer.DetectedStack.Auth;
import com.stackd.ignition.analyzer.DetectedStack.Backend;
import com.stackd.ignition.analyzer.DetectedStack.Database;
import com.stackd.ignition.analyzer.DetectedStack.Frontend;
import com.stackd.ignition.analyzer.DetectedStack.Orm;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects the stack of a STACKD-generated project from its filesystem layout.
 *
 * <p>Recognition is based on the layouts the STACKD generator actually emits
 * (see the Go source in this repository): an {@code fb} project has
 * {@code frontend/} and/or {@code backend/} directories, a full-stack project
 * is a Next.js app at the root with {@code prisma/} and {@code .env}, and a
 * monorepo is a Turborepo workspace. Detection refuses to guess: unknown,
 * incomplete, conflicting, or unsupported layouts raise
 * {@link ProjectAnalysisException}.
 *
 * <p>Only well-known relative files are read (package.json, .env,
 * prisma/schema.prisma, manage.py); nothing is ever derived from caller
 * input, symlinked config files are not followed, file sizes are bounded, and
 * secret material from {@code .env} is reduced to a URL scheme before it can
 * appear in any result or message.
 */
@Component
public class ArchitectureAnalyzer {

    /** Upper bound for a single config file read, to keep parsing bounded. */
    private static final long MAX_CONFIG_BYTES = 256L * 1024;

    private static final Set<String> PRISMA_DB_PROVIDERS =
            Set.of("postgresql", "postgres", "mysql", "mongodb", "sqlite", "sqlserver");

    private static final Pattern PROVIDER_PATTERN =
            Pattern.compile("(?m)^\\s*provider\\s*=\\s*[\"']([^\"']+)[\"']");

    private final ObjectMapper objectMapper;

    /**
     * Creates the analyzer.
     *
     * @param objectMapper Jackson mapper used to parse package.json files
     */
    public ArchitectureAnalyzer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Detects the stack of the project rooted at the given path.
     *
     * @param projectPath the STACKD-generated project directory
     * @return the detected stack
     * @throws ProjectAnalysisException if the path is invalid, the project is
     *         not STACKD-generated, incomplete, ambiguous, unreadable, or uses an
     *         unsupported layout
     */
    public DetectedStack analyze(String projectPath) {
        Path root = resolveRoot(projectPath);
        ProjectLayout layout = inspectLayout(root);
        Frontend frontend = detectFrontend(layout);
        Backend backend = detectBackend(layout);
        Orm orm = detectOrm(layout);
        Database database = detectDatabase(layout);
        Auth auth = detectAuth(primaryPackage(layout));

        if (frontend == DetectedStack.Frontend.NONE && backend == DetectedStack.Backend.NONE) {
            throw new ProjectAnalysisException(ProjectAnalysisException.NOT_A_STACKD_PROJECT,
                    "No supported frontend or backend framework found; this is not a STACKD project");
        }
        return new DetectedStack(frontend, backend, database, orm, auth);
    }

    private Path resolveRoot(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            throw new ProjectAnalysisException(ProjectAnalysisException.PROJECT_PATH_INVALID,
                    "Project path must not be blank");
        }
        Path real;
        try {
            real = Path.of(projectPath).toRealPath();
        } catch (IOException e) {
            throw new ProjectAnalysisException(ProjectAnalysisException.PROJECT_PATH_INVALID,
                    "Project path does not exist or is not readable: " + projectPath);
        }
        if (!Files.isDirectory(real)) {
            throw new ProjectAnalysisException(ProjectAnalysisException.PROJECT_PATH_INVALID,
                    "Project path is not a directory: " + projectPath);
        }
        return real;
    }

    private ProjectLayout inspectLayout(Path root) {
        if (isRegularFileNoFollow(root.resolve("turbo.json"))
                || isDirectoryNoFollow(root.resolve("apps"))) {
            throw new ProjectAnalysisException(ProjectAnalysisException.UNSUPPORTED_LAYOUT,
                    "Turborepo monorepo layout detected; automatic stack detection for monorepos is not supported yet");
        }
        PackageJson rootPackage = readPackageJson(root);
        if (rootPackage != null && rootPackage.hasWorkspaces()) {
            throw new ProjectAnalysisException(ProjectAnalysisException.UNSUPPORTED_LAYOUT,
                    "Workspace layout detected; automatic stack detection for monorepos is not supported yet");
        }

        boolean hasFrontendDir = isDirectoryNoFollow(root.resolve("frontend"));
        boolean hasBackendDir = isDirectoryNoFollow(root.resolve("backend"));
        boolean hasRootPackage = rootPackage != null;

        if (hasFrontendDir && !isRegularFileNoFollow(root.resolve("frontend").resolve("package.json"))) {
            throw new ProjectAnalysisException(ProjectAnalysisException.NOT_A_STACKD_PROJECT,
                    "frontend/ directory exists but is missing package.json; STACKD output appears incomplete");
        }
        if (hasBackendDir
                && !isRegularFileNoFollow(root.resolve("backend").resolve("package.json"))
                && !isRegularFileNoFollow(root.resolve("backend").resolve("manage.py"))) {
            throw new ProjectAnalysisException(ProjectAnalysisException.NOT_A_STACKD_PROJECT,
                    "backend/ directory exists but is missing package.json or manage.py; STACKD output appears incomplete");
        }

        Path frontendDir = hasFrontendDir ? root.resolve("frontend") : hasRootPackage ? root : null;
        Path backendDir = hasBackendDir ? root.resolve("backend") : null;

        if (frontendDir == null && backendDir == null) {
            if (isRegularFileNoFollow(root.resolve("manage.py"))) {
                backendDir = root;
            } else {
                throw new ProjectAnalysisException(ProjectAnalysisException.NOT_A_STACKD_PROJECT,
                        "No package.json, frontend/, backend/, or manage.py found; this is not a STACKD project");
            }
        }
        return new ProjectLayout(root, frontendDir, backendDir);
    }

    private DetectedStack.Frontend detectFrontend(ProjectLayout layout) {
        if (layout.frontendDir() == null) {
            return DetectedStack.Frontend.NONE;
        }
        PackageJson pkg = readPackageJson(layout.frontendDir());
        if (pkg == null) {
            return DetectedStack.Frontend.NONE;
        }

        boolean next = pkg.hasDependency("next");
        boolean angular = pkg.hasDependency("@angular/core");
        boolean vue = pkg.hasDependency("vue");
        boolean react = pkg.hasDependency("react") || pkg.hasDependency("react-dom");

        if (next) {
            if (angular || vue) {
                throw ambiguous("frontend", "Next.js", "Angular/Vue");
            }
            return DetectedStack.Frontend.NEXT;
        }
        if (angular && vue) {
            throw ambiguous("frontend", "Angular", "Vue");
        }
        if (angular) {
            if (react) {
                throw ambiguous("frontend", "Angular", "React");
            }
            return DetectedStack.Frontend.ANGULAR;
        }
        if (vue) {
            if (react) {
                throw ambiguous("frontend", "Vue", "React");
            }
            return DetectedStack.Frontend.VUE;
        }
        if (react) {
            boolean typescript = pkg.hasDependency("typescript")
                    && hasFile(layout.frontendDir(), "src", "main.tsx");
            return typescript ? DetectedStack.Frontend.REACT_TS : DetectedStack.Frontend.REACT_JS;
        }
        return DetectedStack.Frontend.NONE;
    }

    private DetectedStack.Backend detectBackend(ProjectLayout layout) {
        if (layout.backendDir() != null) {
            Path backend = layout.backendDir();
            boolean django = hasFile(backend, "manage.py");
            PackageJson pkg = readPackageJson(backend);
            boolean express = pkg != null && pkg.hasDependency("express");

            if (express && django) {
                throw ambiguous("backend", "Express", "Django");
            }
            if (express) {
                boolean typescript = (pkg.hasDependency("@types/express") || pkg.hasDependency("typescript"))
                        && hasFile(backend, "index.ts");
                return typescript ? DetectedStack.Backend.EXPRESS_TS : DetectedStack.Backend.EXPRESS_JS;
            }
            if (django) {
                requireDjangoRestFramework(backend);
                return DetectedStack.Backend.DRF;
            }
            throw new ProjectAnalysisException(ProjectAnalysisException.NOT_A_STACKD_PROJECT,
                    "backend/ directory exists but no supported backend framework was found");
        }
        if (hasFile(layout.root(), "manage.py")) {
            requireDjangoRestFramework(layout.root());
            return DetectedStack.Backend.DRF;
        }
        return DetectedStack.Backend.NONE;
    }

    private DetectedStack.Orm detectOrm(ProjectLayout layout) {
        boolean prisma = false;
        boolean drizzle = false;
        for (Path candidate : candidateDirs(layout)) {
            if (hasFile(candidate, "prisma", "schema.prisma")) {
                prisma = true;
            }
            if (hasFile(candidate, "drizzle.config.ts") || hasFile(candidate, "drizzle.config.js")) {
                drizzle = true;
            }
        }
        PackageJson pkg = primaryPackage(layout);
        if (pkg != null) {
            prisma = prisma || pkg.hasDependency("prisma");
            drizzle = drizzle || pkg.hasDependency("drizzle-orm");
        }
        if (prisma && drizzle) {
            throw ambiguous("ORM", "Prisma", "Drizzle");
        }
        return prisma ? DetectedStack.Orm.PRISMA
                : drizzle ? DetectedStack.Orm.DRIZZLE
                : DetectedStack.Orm.NONE;
    }

    private DetectedStack.Database detectDatabase(ProjectLayout layout) {
        String urlScheme = databaseUrlScheme(layout);
        String provider = prismaProvider(layout);
        DetectedStack.Database fromUrl = urlScheme != null ? mapDatabase(urlScheme, "DATABASE_URL") : null;
        DetectedStack.Database fromProvider = provider != null ? mapDatabase(provider, "Prisma schema") : null;

        if (fromUrl != null && fromProvider != null && fromUrl != fromProvider) {
            throw new ProjectAnalysisException(ProjectAnalysisException.AMBIGUOUS_STACK,
                    "DATABASE_URL in .env and the Prisma schema provider disagree; refusing to guess");
        }
        if (fromUrl != null) {
            return fromUrl;
        }
        if (fromProvider != null) {
            return fromProvider;
        }
        return DetectedStack.Database.NONE;
    }

    private DetectedStack.Auth detectAuth(PackageJson pkg) {
        if (pkg == null) {
            return DetectedStack.Auth.NONE;
        }
        if (pkg.hasDependency("next-auth")) {
            return DetectedStack.Auth.NEXTAUTH;
        }
        if (pkg.hasDependency("passport")) {
            return DetectedStack.Auth.PASSPORT;
        }
        if (pkg.hasDependency("jsonwebtoken")) {
            return DetectedStack.Auth.JWT;
        }
        return DetectedStack.Auth.NONE;
    }

    private String databaseUrlScheme(ProjectLayout layout) {
        for (Path candidate : candidateDirs(layout)) {
            String content = readOptionalText(candidate.resolve(".env"));
            if (content == null) {
                continue;
            }
            for (String line : content.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0 || !trimmed.substring(0, eq).trim().equals("DATABASE_URL")) {
                    continue;
                }
                String value = stripQuotes(trimmed.substring(eq + 1).trim());
                int colon = value.indexOf(':');
                if (colon <= 0) {
                    throw new ProjectAnalysisException(ProjectAnalysisException.UNREADABLE_PROJECT,
                            "DATABASE_URL in .env is malformed");
                }
                return value.substring(0, colon);
            }
        }
        return null;
    }

    private String prismaProvider(ProjectLayout layout) {
        for (Path candidate : candidateDirs(layout)) {
            String content = readOptionalText(candidate.resolve("prisma").resolve("schema.prisma"));
            if (content == null) {
                continue;
            }
            Matcher matcher = PROVIDER_PATTERN.matcher(content);
            String found = null;
            while (matcher.find()) {
                String value = matcher.group(1);
                if (!PRISMA_DB_PROVIDERS.contains(value)) {
                    continue;
                }
                if (found != null && !found.equals(value)) {
                    throw ambiguous("database", found, value);
                }
                found = value;
            }
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private PackageJson primaryPackage(ProjectLayout layout) {
        if (layout.backendDir() != null) {
            PackageJson pkg = readPackageJson(layout.backendDir());
            if (pkg != null) {
                return pkg;
            }
        }
        if (layout.frontendDir() != null) {
            return readPackageJson(layout.frontendDir());
        }
        return null;
    }

    private Path[] candidateDirs(ProjectLayout layout) {
        if (layout.backendDir() != null) {
            return new Path[]{layout.backendDir(), layout.root()};
        }
        return new Path[]{layout.root()};
    }

    private PackageJson readPackageJson(Path dir) {
        Path file = dir.resolve("package.json");
        if (!isRegularFileNoFollow(file)) {
            return null;
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(readBounded(file));
        } catch (IOException e) {
            throw new ProjectAnalysisException(ProjectAnalysisException.UNREADABLE_PROJECT,
                    "Malformed package.json in " + dir.getFileName());
        }
        if (node == null || !node.isObject()) {
            throw new ProjectAnalysisException(ProjectAnalysisException.UNREADABLE_PROJECT,
                    "package.json in " + dir.getFileName() + " is not a JSON object");
        }
        Set<String> dependencies = new HashSet<>();
        collectDependencyNames(node.get("dependencies"), dependencies);
        collectDependencyNames(node.get("devDependencies"), dependencies);
        return new PackageJson(dependencies, hasNonEmptyWorkspaces(node.get("workspaces")));
    }

    private static boolean hasNonEmptyWorkspaces(JsonNode workspaces) {
        if (workspaces == null || workspaces.isNull()) {
            return false;
        }
        if (workspaces.isArray()) {
            return workspaces.size() > 0;
        }
        return workspaces.isObject() && workspaces.size() > 0;
    }

    private static void collectDependencyNames(JsonNode section, Set<String> into) {
        if (section != null && section.isObject()) {
            section.fieldNames().forEachRemaining(into::add);
        }
    }

    private String readOptionalText(Path file) {
        if (!isRegularFileNoFollow(file)) {
            return null;
        }
        return new String(readBounded(file), StandardCharsets.UTF_8);
    }

    private byte[] readBounded(Path file) {
        try {
            if (Files.size(file) > MAX_CONFIG_BYTES) {
                throw new ProjectAnalysisException(ProjectAnalysisException.UNREADABLE_PROJECT,
                        "Config file exceeds the size limit: " + file.getFileName());
            }
            return Files.readAllBytes(file);
        } catch (ProjectAnalysisException e) {
            throw e;
        } catch (IOException e) {
            throw new ProjectAnalysisException(ProjectAnalysisException.UNREADABLE_PROJECT,
                    "Could not read config file: " + file.getFileName());
        }
    }

    private static boolean hasFile(Path dir, String... segments) {
        Path path = dir;
        for (String segment : segments) {
            path = path.resolve(segment);
        }
        return isRegularFileNoFollow(path);
    }

    private static boolean isRegularFileNoFollow(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean isDirectoryNoFollow(Path path) {
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static DetectedStack.Database mapDatabase(String keyword, String source) {
        return switch (keyword.toLowerCase(Locale.ROOT)) {
            case "postgres", "postgresql" -> DetectedStack.Database.POSTGRESQL;
            case "mysql" -> DetectedStack.Database.MYSQL;
            case "mongodb", "mongodb+srv" -> DetectedStack.Database.MONGODB;
            default -> throw new ProjectAnalysisException(ProjectAnalysisException.UNSUPPORTED_LAYOUT,
                    source + " uses an unsupported database: " + keyword);
        };
    }

    private void requireDjangoRestFramework(Path projectDir) {
        List<Path> requirementFiles = new ArrayList<>();
        Path requirements = projectDir.resolve("requirements.txt");
        if (isRegularFileNoFollow(requirements)) {
            requirementFiles.add(requirements);
        }
        Path requirementsDir = projectDir.resolve("requirements");
        if (isDirectoryNoFollow(requirementsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(requirementsDir)) {
                for (Path file : stream) {
                    if (isRegularFileNoFollow(file) && file.getFileName().toString().endsWith(".txt")) {
                        requirementFiles.add(file);
                    }
                }
            } catch (IOException e) {
                // Unreadable requirements directory: fall back to the signal below.
            }
        }
        if (requirementFiles.isEmpty()) {
            return;
        }
        for (Path file : requirementFiles) {
            String content = readOptionalText(file);
            if (content != null && content.toLowerCase(Locale.ROOT).contains("djangorestframework")) {
                return;
            }
        }
        throw new ProjectAnalysisException(ProjectAnalysisException.UNSUPPORTED_LAYOUT,
                "Django project without Django REST Framework; only DRF is supported");
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

    private static ProjectAnalysisException ambiguous(String aspect, String first, String second) {
        return new ProjectAnalysisException(ProjectAnalysisException.AMBIGUOUS_STACK,
                "Ambiguous " + aspect + " detection: found both " + first + " and " + second
                        + "; refusing to guess");
    }
}
