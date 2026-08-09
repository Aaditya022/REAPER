package com.stackd.ignition.envmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stackd.ignition.analyzer.DetectedStack;
import com.stackd.ignition.analyzer.DetectedStack.Auth;
import com.stackd.ignition.analyzer.DetectedStack.Backend;
import com.stackd.ignition.analyzer.DetectedStack.Database;
import com.stackd.ignition.analyzer.DetectedStack.Frontend;
import com.stackd.ignition.analyzer.DetectedStack.Orm;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link EnvConfigManager} and {@link MergedEnv} using
 * temp-directory fixtures that mirror STACKD's generated project layouts.
 */
class EnvConfigManagerTest {

    private static final String PROJECT_SECRET_URL = "postgresql://stackd:supersecret@localhost:5432/stackd";
    private static final String ZEROPS_REF = "${db_connectionString}";

    @TempDir
    Path tempDir;

    private final EnvConfigManager manager = new EnvConfigManager();

    private static DetectedStack stack(Database database) {
        return new DetectedStack(Frontend.NONE, Backend.EXPRESS_JS, database, Orm.PRISMA, Auth.JWT);
    }

    @Test
    void mergesProjectEnvWithZeropsInjectedVarsAndZeropsWins() throws Exception {
        write(tempDir, "backend/.env",
                "DATABASE_URL=" + PROJECT_SECRET_URL + "\nJWT_SECRET=super-secret-key-123\n");
        Map<String, String> zerops = Map.of("DATABASE_URL", ZEROPS_REF);

        MergedEnv env = manager.merge(tempDir.toString(), stack(Database.POSTGRESQL), zerops);

        assertEquals(ZEROPS_REF, env.get("DATABASE_URL"));
        assertEquals("super-secret-key-123", env.get("JWT_SECRET"));
        assertEquals(2, env.projectValues().size());
        assertEquals(1, env.zeropsValues().size());
        assertTrue(env.isComplete());
        env.validate();
    }

    @Test
    void readsBackendEnvBeforeRootEnv() throws Exception {
        write(tempDir, "backend/.env", "JWT_SECRET=backend-secret\n");
        write(tempDir, ".env", "JWT_SECRET=root-secret\n");

        MergedEnv env = manager.merge(tempDir.toString(), stack(Database.POSTGRESQL), Map.of());

        assertEquals("backend-secret", env.get("JWT_SECRET"));
    }

    @Test
    void missingRequiredVarThrowsWithKeyNamesOnly() throws Exception {
        write(tempDir, ".env",
                "DATABASE_URL=" + PROJECT_SECRET_URL + "\nJWT_SECRET=super-secret-key-123\n");
        MergedEnv env = manager.merge(tempDir.toString(), stack(Database.POSTGRESQL), Map.of());

        assertEquals(1, env.missingRequiredVars().size());
        assertTrue(env.missingRequiredVars().contains("DATABASE_URL"));
        assertFalse(env.isComplete());

        EnvConfigException ex = assertThrows(EnvConfigException.class, env::validate);
        assertEquals(EnvConfigException.MISSING_REQUIRED_ENV_VARS, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("DATABASE_URL"));
        assertFalse(ex.getMessage().contains("supersecret"));
        assertFalse(ex.getMessage().contains(PROJECT_SECRET_URL));
    }

    @Test
    void requiredVarProvidedByZeropsValidates() throws Exception {
        write(tempDir, ".env", "DATABASE_URL=" + PROJECT_SECRET_URL + "\n");
        MergedEnv env =
                manager.mergeValidated(tempDir.toString(), stack(Database.POSTGRESQL), Map.of("DATABASE_URL", ZEROPS_REF));

        assertTrue(env.isComplete());
        assertEquals(ZEROPS_REF, env.get("DATABASE_URL"));
    }

    @Test
    void blankZeropsValueCountsAsMissing() throws Exception {
        write(tempDir, ".env", "DATABASE_URL=" + PROJECT_SECRET_URL + "\n");
        MergedEnv env = manager.merge(tempDir.toString(), stack(Database.POSTGRESQL),
                Map.of("DATABASE_URL", "   "));

        assertTrue(env.missingRequiredVars().contains("DATABASE_URL"));
        assertThrows(EnvConfigException.class, env::validate);
    }

    @Test
    void noDatabaseStackHasNoRequiredVars() throws Exception {
        write(tempDir, "backend/.env", "JWT_SECRET=super-secret-key-123\n");

        MergedEnv env = manager.mergeValidated(tempDir.toString(), stack(Database.NONE), Map.of());

        assertTrue(env.isComplete());
        assertTrue(env.missingRequiredVars().isEmpty());
        assertEquals("super-secret-key-123", env.get("JWT_SECRET"));
    }

    @Test
    void mysqlAndMongoAlsoRequireDatabaseUrl() throws Exception {
        write(tempDir, ".env", "DATABASE_URL=" + PROJECT_SECRET_URL + "\n");

        for (Database database : new Database[]{Database.MYSQL, Database.MONGODB}) {
            MergedEnv env = manager.merge(tempDir.toString(), stack(database), Map.of());
            assertTrue(env.missingRequiredVars().contains("DATABASE_URL"),
                    "expected DATABASE_URL required for " + database);
        }
    }

    @Test
    void secretValuesNeverAppearInToStringOrMaskedValues() throws Exception {
        write(tempDir, "backend/.env",
                "DATABASE_URL=" + PROJECT_SECRET_URL + "\nJWT_SECRET=super-secret-key-123\n");
        MergedEnv env =
                manager.merge(tempDir.toString(), stack(Database.POSTGRESQL), Map.of("DATABASE_URL", ZEROPS_REF));

        String description = env.toString();
        assertFalse(description.contains("supersecret"));
        assertFalse(description.contains("super-secret-key-123"));
        assertFalse(description.contains("postgresql://"));

        Map<String, String> masked = env.maskedValues();
        assertTrue(masked.containsValue(MergedEnv.MASK));
        assertFalse(masked.containsValue(PROJECT_SECRET_URL));
        assertFalse(masked.containsValue(ZEROPS_REF));
        for (String value : masked.values()) {
            assertEquals(MergedEnv.MASK, value);
        }
    }

    @Test
    void exceptionMessageNeverContainsValues() throws Exception {
        write(tempDir, "backend/.env",
                "DATABASE_URL=" + PROJECT_SECRET_URL + "\nJWT_SECRET=super-secret-key-123\n");

        EnvConfigException ex = assertThrows(EnvConfigException.class,
                () -> manager.mergeValidated(tempDir.toString(), stack(Database.POSTGRESQL), Map.of()));

        assertFalse(ex.getMessage().contains("supersecret"));
        assertFalse(ex.getMessage().contains("super-secret-key-123"));
        assertFalse(ex.getMessage().contains("postgresql://"));
    }

    @Test
    void missingEnvFileYieldsEmptyProjectValues() {
        MergedEnv env = manager.merge(tempDir.toString(), stack(Database.POSTGRESQL),
                Map.of("DATABASE_URL", ZEROPS_REF));

        assertTrue(env.projectValues().isEmpty());
        assertEquals(ZEROPS_REF, env.get("DATABASE_URL"));
        assertTrue(env.isComplete());
    }

    @Test
    void envFileParsesCommentsQuotesAndExportPrefix() throws Exception {
        write(tempDir, ".env",
                "# leading comment\n"
                        + "\n"
                        + "export JWT_SECRET=exported-secret\n"
                        + "PORT=\"8080\"\n"
                        + "MODE='production'\n"
                        + "WITH_SPACES = value with spaces\n");

        MergedEnv env = manager.merge(tempDir.toString(), stack(Database.NONE), Map.of());

        assertEquals("exported-secret", env.get("JWT_SECRET"));
        assertEquals("8080", env.get("PORT"));
        assertEquals("production", env.get("MODE"));
        assertEquals("value with spaces", env.get("WITH_SPACES"));
    }

    @Test
    void malformedLineWithoutEqualsIsIgnored() throws Exception {
        write(tempDir, ".env", "JWT_SECRET=kept\nSTRAY_LINE\nOTHER=1\n");

        MergedEnv env = manager.merge(tempDir.toString(), stack(Database.NONE), Map.of());

        assertEquals("kept", env.get("JWT_SECRET"));
        assertEquals("1", env.get("OTHER"));
        assertNull(env.get("STRAY_LINE"));
    }

    @Test
    void symlinkedEnvFileIsNotFollowed() throws Exception {
        Path outside = tempDir.resolve("outside.env");
        write(tempDir, "outside.env", "JWT_SECRET=outside-secret\n");
        Files.createSymbolicLink(tempDir.resolve(".env"), outside);

        MergedEnv env = manager.merge(tempDir.toString(), stack(Database.NONE), Map.of());

        assertTrue(env.projectValues().isEmpty());
        assertNull(env.get("JWT_SECRET"));
    }

    @Test
    void oversizedEnvFileIsRejected() throws Exception {
        StringBuilder big = new StringBuilder();
        while (big.length() <= 256L * 1024) {
            big.append("PADDING_KEY_012345678901234567890123456789012345678901234567890=value\n");
        }
        write(tempDir, ".env", big.toString());

        EnvConfigException ex = assertThrows(EnvConfigException.class,
                () -> manager.merge(tempDir.toString(), stack(Database.NONE), Map.of()));

        assertEquals(EnvConfigException.UNREADABLE_ENV_FILE, ex.getErrorCode());
    }

    @Test
    void mergedEnvMapsAreUnmodifiable() throws Exception {
        write(tempDir, ".env", "JWT_SECRET=secret\n");
        MergedEnv env = manager.merge(tempDir.toString(), stack(Database.NONE), Map.of("EXTRA", "x"));

        assertThrows(UnsupportedOperationException.class, () -> env.values().put("K", "v"));
        assertThrows(UnsupportedOperationException.class, () -> env.projectValues().put("K", "v"));
        assertThrows(UnsupportedOperationException.class, () -> env.zeropsValues().put("K", "v"));
    }

    @Test
    void nullZeropsEnvTreatedAsEmpty() throws Exception {
        write(tempDir, ".env", "JWT_SECRET=secret\n");

        MergedEnv env = manager.merge(tempDir.toString(), stack(Database.NONE), null);

        assertTrue(env.zeropsValues().isEmpty());
        assertEquals("secret", env.get("JWT_SECRET"));
        assertTrue(env.isComplete());
    }

    @Test
    void invalidProjectPathThrowsEnvConfigException() {
        EnvConfigException ex = assertThrows(EnvConfigException.class,
                () -> manager.merge(tempDir.resolve("missing").toString(), stack(Database.NONE), Map.of()));

        assertEquals(EnvConfigException.PROJECT_PATH_INVALID, ex.getErrorCode());
    }

    private static Path write(Path base, String relative, String content) throws IOException {
        Path file = base.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return base;
    }
}
