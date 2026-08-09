package com.stackd.ignition.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stackd.ignition.zeropsconfig.ZeropsConfigGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

/**
 * Unit tests for {@link ArchitectureAnalyzer} using temp-directory fixtures
 * that mirror the layouts the STACKD generator produces.
 */
class ArchitectureAnalyzerTest {

    private static final ObjectMapper TEST_JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    private final ArchitectureAnalyzer analyzer = new ArchitectureAnalyzer(new ObjectMapper());

    @Test
    void fbLayoutReactExpressPrismaPostgres() throws Exception {
        Path root = tempDir.resolve("fb");
        writePackageJson(write(root, "frontend/package.json"), Map.of("react", "^18.2.0", "react-dom", "^18.2.0", "vite", "^5.0.0"), Map.of());
        writePackageJson(write(root, "backend/package.json"), Map.of("express", "^4.19.0", "cors", "^2.8.5"), Map.of());
        write(root, "backend/prisma/schema.prisma", prismaSchema("postgresql"));
        write(root, "backend/.env", "DATABASE_URL=postgresql://stackd:supersecret@localhost:5432/stackd\n");

        DetectedStack stack = analyzer.analyze(root.toString());

        assertEquals(DetectedStack.Frontend.REACT_JS, stack.frontend());
        assertEquals(DetectedStack.Backend.EXPRESS_JS, stack.backend());
        assertEquals(DetectedStack.Database.POSTGRESQL, stack.database());
        assertEquals(DetectedStack.Orm.PRISMA, stack.orm());
        assertEquals(DetectedStack.Auth.NONE, stack.auth());
    }

    @Test
    void fbLayoutReactTsExpressTs() throws Exception {
        Path root = tempDir.resolve("fb-ts");
        writePackageJson(write(root, "frontend/package.json"),
                Map.of("react", "^18.2.0", "react-dom", "^18.2.0", "vite", "^5.0.0"),
                Map.of("typescript", "^5.0.0", "@types/react", "^18.0.0"));
        write(root, "frontend/src/main.tsx", "import React from 'react';\n");
        writePackageJson(write(root, "backend/package.json"),
                Map.of("express", "^4.19.0", "cors", "^2.8.5"),
                Map.of("typescript", "^5.0.0", "@types/express", "^4.17.0", "@types/node", "^20.0.0"));
        write(root, "backend/index.ts", "import express from 'express';\n");
        write(root, "backend/tsconfig.json", "{}");

        DetectedStack stack = analyzer.analyze(root.toString());

        assertEquals(DetectedStack.Frontend.REACT_TS, stack.frontend());
        assertEquals(DetectedStack.Backend.EXPRESS_TS, stack.backend());
    }

    @Test
    void fsLayoutNextWithNextAuthPrismaMongo() throws Exception {
        Path root = tempDir.resolve("fs-next");
        writePackageJson(write(root, "package.json"),
                Map.of("next", "^14.0.0", "react", "^18.2.0", "react-dom", "^18.2.0", "next-auth", "^4.24.0"),
                Map.of("typescript", "^5.0.0"));
        write(root, "prisma/schema.prisma", prismaSchema("mongodb"));
        write(root, ".env", "DATABASE_URL=mongodb://stackd:supersecret@localhost:27017/stackd\n");

        DetectedStack stack = analyzer.analyze(root.toString());

        assertEquals(DetectedStack.Frontend.NEXT, stack.frontend());
        assertEquals(DetectedStack.Backend.NONE, stack.backend());
        assertEquals(DetectedStack.Database.MONGODB, stack.database());
        assertEquals(DetectedStack.Orm.PRISMA, stack.orm());
        assertEquals(DetectedStack.Auth.NEXTAUTH, stack.auth());
    }

    @Test
    void djangoProjectDetectedAsDrf() throws Exception {
        Path root = tempDir.resolve("django");
        write(root, "manage.py", "#!/usr/bin/env python\n");
        write(root, "requirements.txt", "Django==5.0.0\ndjangorestframework==3.15.0\n");

        DetectedStack stack = analyzer.analyze(root.toString());

        assertEquals(DetectedStack.Frontend.NONE, stack.frontend());
        assertEquals(DetectedStack.Backend.DRF, stack.backend());
        assertEquals(DetectedStack.Database.NONE, stack.database());
        assertEquals(DetectedStack.Orm.NONE, stack.orm());
    }

    @Test
    void djangoInBackendDirWithRestFrameworkDetected() throws Exception {
        Path root = tempDir.resolve("django-fb");
        writePackageJson(write(root, "frontend/package.json"), Map.of("vue", "^3.4.0"), Map.of());
        write(root, "backend/manage.py", "#!/usr/bin/env python\n");
        write(root, "backend/requirements.txt", "Django==5.0.0\ndjangorestframework==3.15.0\n");

        DetectedStack stack = analyzer.analyze(root.toString());

        assertEquals(DetectedStack.Backend.DRF, stack.backend());
    }

    @Test
    void djangoWithoutRestFrameworkIsUnsupported() throws Exception {
        Path root = tempDir.resolve("django-plain");
        write(root, "manage.py", "#!/usr/bin/env python\n");
        write(root, "requirements.txt", "Django==5.0.0\n");

        ProjectAnalysisException ex = assertThrows(ProjectAnalysisException.class,
                () -> analyzer.analyze(root.toString()));

        assertEquals(ProjectAnalysisException.UNSUPPORTED_LAYOUT, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Django REST Framework"));
    }

    @Test
    void djangoRestFrameworkInRequirementsSubfolderDetected() throws Exception {
        Path root = tempDir.resolve("django-req-dir");
        write(root, "manage.py", "#!/usr/bin/env python\n");
        write(root, "requirements/base.txt", "Django==5.0.0\ndjangorestframework==3.15.0\n");

        DetectedStack stack = analyzer.analyze(root.toString());

        assertEquals(DetectedStack.Backend.DRF, stack.backend());
    }

    @Test
    void emptyWorkspacesArrayIsNotTreatedAsMonorepo() throws Exception {
        Path root = tempDir.resolve("fs-empty-workspaces");
        write(root, "package.json", "{\"name\":\"app\",\"workspaces\":[],\"dependencies\":{\"next\":\"14.0.0\"}}");

        DetectedStack stack = analyzer.analyze(root.toString());

        assertEquals(DetectedStack.Frontend.NEXT, stack.frontend());
    }

    @Test
    void vueAndAngularFrontendsDetected() throws Exception {
        Path vueRoot = tempDir.resolve("vue");
        writePackageJson(write(vueRoot, "frontend/package.json"), Map.of("vue", "^3.4.0"), Map.of());
        assertEquals(DetectedStack.Frontend.VUE,
                analyzer.analyze(vueRoot.toString()).frontend());

        Path angularRoot = tempDir.resolve("angular");
        writePackageJson(write(angularRoot, "frontend/package.json"), Map.of("@angular/core", "^17.0.0"), Map.of());
        assertEquals(DetectedStack.Frontend.ANGULAR,
                analyzer.analyze(angularRoot.toString()).frontend());
    }

    @Test
    void nextBundlesReactWithoutBeingAmbiguous() throws Exception {
        Path root = tempDir.resolve("next-react");
        writePackageJson(write(root, "package.json"),
                Map.of("next", "^14.0.0", "react", "^18.2.0", "react-dom", "^18.2.0"),
                Map.of());

        assertEquals(DetectedStack.Frontend.NEXT, analyzer.analyze(root.toString()).frontend());
    }

    @Test
    void frontendOnlyFbProjectIsValid() throws Exception {
        Path root = tempDir.resolve("fe-only");
        writePackageJson(write(root, "frontend/package.json"), Map.of("react", "^18.2.0", "vite", "^5.0.0"), Map.of());

        DetectedStack stack = analyzer.analyze(root.toString());

        assertEquals(DetectedStack.Frontend.REACT_JS, stack.frontend());
        assertEquals(DetectedStack.Backend.NONE, stack.backend());
    }

    @Test
    void backendOnlyFbProjectIsValid() throws Exception {
        Path root = tempDir.resolve("be-only");
        writePackageJson(write(root, "backend/package.json"), Map.of("express", "^4.19.0"), Map.of());

        DetectedStack stack = analyzer.analyze(root.toString());

        assertEquals(DetectedStack.Frontend.NONE, stack.frontend());
        assertEquals(DetectedStack.Backend.EXPRESS_JS, stack.backend());
    }

    @Test
    void pathWithTraversalComponentsIsNormalized() throws Exception {
        Path root = tempDir.resolve("proj");
        writePackageJson(write(root, "frontend/package.json"), Map.of("react", "^18.2.0"), Map.of());
        Files.createDirectories(tempDir.resolve("other"));

        String sneaky = tempDir.resolve("other").resolve("..").resolve("proj").toString();
        DetectedStack stack = analyzer.analyze(sneaky);

        assertEquals(DetectedStack.Frontend.REACT_JS, stack.frontend());
    }

    @Test
    void emptyDirectoryFailsAsNotAStackdProject() {
        Path root = tempDir.resolve("empty");
        assertTrue(root.toFile().mkdirs());

        ProjectAnalysisException ex = assertThrows(ProjectAnalysisException.class,
                () -> analyzer.analyze(root.toString()));

        assertEquals(ProjectAnalysisException.NOT_A_STACKD_PROJECT, ex.getErrorCode());
    }

    @Test
    void randomFilesFailAsNotAStackdProject() throws Exception {
        Path root = tempDir.resolve("random");
        write(root, "README.md", "just a readme\n");
        write(root, "notes.txt", "nothing here\n");

        ProjectAnalysisException ex = assertThrows(ProjectAnalysisException.class,
                () -> analyzer.analyze(root.toString()));

        assertEquals(ProjectAnalysisException.NOT_A_STACKD_PROJECT, ex.getErrorCode());
    }

    @Test
    void nonExistentPathFailsWithInvalidPath() {
        Path missing = tempDir.resolve("missing");

        ProjectAnalysisException ex = assertThrows(ProjectAnalysisException.class,
                () -> analyzer.analyze(missing.toString()));

        assertEquals(ProjectAnalysisException.PROJECT_PATH_INVALID, ex.getErrorCode());
    }

    @Test
    void filePathInsteadOfDirectoryFailsWithInvalidPath() throws Exception {
        Path file = tempDir.resolve("file.txt");
        write(file.getParent(), "file.txt", "not a dir");

        ProjectAnalysisException ex = assertThrows(ProjectAnalysisException.class,
                () -> analyzer.analyze(file.toString()));

        assertEquals(ProjectAnalysisException.PROJECT_PATH_INVALID, ex.getErrorCode());
    }

    @Test
    void blankPathFailsWithInvalidPath() {
        ProjectAnalysisException ex = assertThrows(ProjectAnalysisException.class,
                () -> analyzer.analyze("   "));

        assertEquals(ProjectAnalysisException.PROJECT_PATH_INVALID, ex.getErrorCode());
    }

    @Test
    void reactAndVueTogetherAreAmbiguous() throws Exception {
        Path root = tempDir.resolve("amb-fe");
        writePackageJson(write(root, "frontend/package.json"),
                Map.of("react", "^18.2.0", "vue", "^3.4.0"), Map.of());

        ProjectAnalysisException ex = assertThrows(ProjectAnalysisException.class,
                () -> analyzer.analyze(root.toString()));

        assertEquals(ProjectAnalysisException.AMBIGUOUS_STACK, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("frontend"));
    }

    @Test
    void prismaAndDrizzleTogetherAreAmbiguous() throws Exception {
        Path root = tempDir.resolve("amb-orm");
        writePackageJson(write(root, "backend/package.json"), Map.of("express", "^4.19.0"), Map.of());
        write(root, "backend/prisma/schema.prisma", prismaSchema("postgresql"));
        write(root, "backend/drizzle.config.ts", "export default {};\n");

        ProjectAnalysisException ex = assertThrows(ProjectAnalysisException.class,
                () -> analyzer.analyze(root.toString()));

        assertEquals(ProjectAnalysisException.AMBIGUOUS_STACK, ex.getErrorCode());
    }

    @Test
    void databaseUrlAndPrismaProviderDisagreementIsAmbiguousAndLeaksNoSecret() throws Exception {
        Path root = tempDir.resolve("amb-db");
        writePackageJson(write(root, "backend/package.json"), Map.of("express", "^4.19.0"), Map.of());
        write(root, "backend/prisma/schema.prisma", prismaSchema("mysql"));
        write(root, "backend/.env", "DATABASE_URL=postgresql://stackd:supersecretpassword@localhost:5432/stackd\n");

        ProjectAnalysisException ex = assertThrows(ProjectAnalysisException.class,
                () -> analyzer.analyze(root.toString()));

        assertEquals(ProjectAnalysisException.AMBIGUOUS_STACK, ex.getErrorCode());
        assertFalse(ex.getMessage().contains("supersecretpassword"));
        assertFalse(ex.getMessage().contains("postgresql://"));
    }

    @Test
    void prismaSchemaProviderUsedWhenEnvMissing() throws Exception {
        Path root = tempDir.resolve("provider-only");
        writePackageJson(write(root, "backend/package.json"), Map.of("express", "^4.19.0"), Map.of());
        write(root, "backend/prisma/schema.prisma", prismaSchema("mysql"));

        assertEquals(DetectedStack.Database.MYSQL, analyzer.analyze(root.toString()).database());
    }

    @Test
    void generatorBlockProviderDoesNotConfuseDetection() throws Exception {
        Path root = tempDir.resolve("prisma-generator");
        writePackageJson(write(root, "backend/package.json"), Map.of("express", "^4.19.0"), Map.of());
        write(root, "backend/prisma/schema.prisma",
                "generator client {\n  provider = \"prisma-client-js\"\n}\n\ndatasource db {\n  provider = \"postgresql\"\n}\n");

        assertEquals(DetectedStack.Database.POSTGRESQL, analyzer.analyze(root.toString()).database());
    }

    @Test
    void mongodbSrvUrlSchemeDetectedAsMongodb() throws Exception {
        Path root = tempDir.resolve("mongo-srv");
        writePackageJson(write(root, "backend/package.json"), Map.of("express", "^4.19.0"), Map.of());
        write(root, "backend/.env",
                "DATABASE_URL=mongodb+srv://stackd:supersecret@cluster0.example.net/stackd\n");

        DetectedStack stack = analyzer.analyze(root.toString());

        assertEquals(DetectedStack.Database.MONGODB, stack.database());
    }

    @Test
    void sqlitePrismaProviderIsUnsupported() throws Exception {
        Path root = tempDir.resolve("sqlite");
        writePackageJson(write(root, "backend/package.json"), Map.of("express", "^4.19.0"), Map.of());
        write(root, "backend/prisma/schema.prisma", prismaSchema("sqlite"));

        ProjectAnalysisException ex = assertThrows(ProjectAnalysisException.class,
                () -> analyzer.analyze(root.toString()));

        assertEquals(ProjectAnalysisException.UNSUPPORTED_LAYOUT, ex.getErrorCode());
        assertTrue(ex.getMessage().toLowerCase().contains("sqlite"));
    }

    @Test
    void fullStackProjectWiresAnalyzerToConfigGenerator() throws Exception {
        Path root = tempDir.resolve("wired");
        writePackageJson(write(root, "frontend/package.json"),
                Map.of("react", "^18.2.0", "react-dom", "^18.2.0", "vite", "^5.0.0"), Map.of());
        writePackageJson(write(root, "backend/package.json"),
                Map.of("express", "^4.19.0", "cors", "^2.8.5"), Map.of());
        write(root, "backend/prisma/schema.prisma", prismaSchema("postgresql"));
        write(root, "backend/.env", "DATABASE_URL=postgresql://stackd:supersecret@localhost:5432/stackd\n");

        DetectedStack stack = analyzer.analyze(root.toString());
        String yaml = new ZeropsConfigGenerator().generate(stack);

        Object parsed = new Yaml().load(yaml);
        assertTrue(parsed instanceof Map, "generated config must parse as a YAML mapping");
        Map<String, Object> document = cast(parsed);
        List<Map<String, Object>> services = cast(document.get("zerops"));
        assertEquals(2, services.size(), "detected full-stack project must generate frontend + backend services");

        Map<String, Object> frontend = services.get(0);
        assertEquals("frontend", frontend.get("setup"));
        Map<String, Object> frontendRun = cast(frontend.get("run"));
        assertEquals("static", frontendRun.get("base"));

        Map<String, Object> backend = services.get(1);
        assertEquals("backend", backend.get("setup"));
        Map<String, Object> backendRun = cast(backend.get("run"));
        assertEquals("nodejs@22", backendRun.get("base"));
        assertEquals("node index.js", backendRun.get("start"));
        Map<String, Object> backendEnv = cast(backendRun.get("envVariables"));
        assertEquals(ZeropsConfigGenerator.DB_CONNECTION_STRING_REFERENCE, backendEnv.get("DATABASE_URL"));
    }

    @Test
    void turboMonorepoIsUnsupported() throws Exception {
        Path root = tempDir.resolve("turbo");
        write(root, "turbo.json", "{\"tasks\":{}}\n");
        writePackageJson(write(root, "package.json"), Map.of(), Map.of());

        ProjectAnalysisException ex = assertThrows(ProjectAnalysisException.class,
                () -> analyzer.analyze(root.toString()));

        assertEquals(ProjectAnalysisException.UNSUPPORTED_LAYOUT, ex.getErrorCode());
        assertTrue(ex.getMessage().toLowerCase().contains("monorepo"));
    }

    @Test
    void workspacesLayoutIsUnsupported() throws Exception {
        Path root = tempDir.resolve("workspaces");
        writePackageJsonWithWorkspaces(write(root, "package.json"), Map.of("next", "^14.0.0"), Map.of());

        ProjectAnalysisException ex = assertThrows(ProjectAnalysisException.class,
                () -> analyzer.analyze(root.toString()));

        assertEquals(ProjectAnalysisException.UNSUPPORTED_LAYOUT, ex.getErrorCode());
    }

    @Test
    void malformedPackageJsonIsUnreadable() throws Exception {
        Path root = tempDir.resolve("bad-json");
        write(root, "frontend/package.json", "{ this is not json ");

        ProjectAnalysisException ex = assertThrows(ProjectAnalysisException.class,
                () -> analyzer.analyze(root.toString()));

        assertEquals(ProjectAnalysisException.UNREADABLE_PROJECT, ex.getErrorCode());
    }

    @Test
    void incompleteFrontendFailsClearly() throws Exception {
        Path root = tempDir.resolve("partial-fe");
        write(root, "frontend/.gitignore", "node_modules\n");
        writePackageJson(write(root, "backend/package.json"), Map.of("express", "^4.19.0"), Map.of());

        ProjectAnalysisException ex = assertThrows(ProjectAnalysisException.class,
                () -> analyzer.analyze(root.toString()));

        assertEquals(ProjectAnalysisException.NOT_A_STACKD_PROJECT, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("incomplete"));
    }

    @Test
    void backendWithoutRecognizableFrameworkFailsClearly() throws Exception {
        Path root = tempDir.resolve("odd-backend");
        writePackageJson(write(root, "backend/package.json"), Map.of("lodash", "^4.17.0"), Map.of());

        ProjectAnalysisException ex = assertThrows(ProjectAnalysisException.class,
                () -> analyzer.analyze(root.toString()));

        assertEquals(ProjectAnalysisException.NOT_A_STACKD_PROJECT, ex.getErrorCode());
    }

    @Test
    void symlinkedConfigFileIsNotFollowed() throws Exception {
        Path outside = tempDir.resolve("outside.json");
        write(outside.getParent(), "outside.json", "{\"dependencies\":{\"react\":\"^18.0.0\"}}");
        Path root = tempDir.resolve("symlink");
        write(root, "frontend/placeholder.txt", "x");
        Files.createSymbolicLink(root.resolve("frontend/package.json"), outside);

        ProjectAnalysisException ex = assertThrows(ProjectAnalysisException.class,
                () -> analyzer.analyze(root.toString()));

        assertEquals(ProjectAnalysisException.NOT_A_STACKD_PROJECT, ex.getErrorCode());
    }

    private static String prismaSchema(String provider) {
        return "generator client {\n  provider = \"prisma-client-js\"\n}\n\n"
                + "datasource db {\n  provider = \"" + provider + "\"\n  url      = env(\"DATABASE_URL\")\n}\n";
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }

    private static Path write(Path base, String relative, String content) throws IOException {
        Path file = base.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return base;
    }

    private static Path write(Path base, String relative) throws IOException {
        Path file = base.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "");
        return file.getParent();
    }

    private static Path writePackageJson(Path base, Map<String, String> deps, Map<String, String> devDeps)
            throws IOException {
        ObjectNode node = TEST_JSON.createObjectNode();
        node.put("name", "stackd-test");
        node.put("version", "0.0.0");
        if (!deps.isEmpty()) {
            node.set("dependencies", toObjectNode(deps));
        }
        if (!devDeps.isEmpty()) {
            node.set("devDependencies", toObjectNode(devDeps));
        }
        Files.writeString(base.resolve("package.json"), TEST_JSON.writeValueAsString(node));
        return base;
    }

    private static Path writePackageJsonWithWorkspaces(Path base, Map<String, String> deps,
                                                       Map<String, String> devDeps) throws IOException {
        ObjectNode node = TEST_JSON.createObjectNode();
        node.put("name", "stackd-test");
        node.put("version", "0.0.0");
        node.putArray("workspaces").add("apps/*");
        if (!deps.isEmpty()) {
            node.set("dependencies", toObjectNode(deps));
        }
        if (!devDeps.isEmpty()) {
            node.set("devDependencies", toObjectNode(devDeps));
        }
        Files.writeString(base.resolve("package.json"), TEST_JSON.writeValueAsString(node));
        return base;
    }

    private static ObjectNode toObjectNode(Map<String, String> entries) {
        ObjectNode node = TEST_JSON.createObjectNode();
        entries.forEach(node::put);
        return node;
    }
}
