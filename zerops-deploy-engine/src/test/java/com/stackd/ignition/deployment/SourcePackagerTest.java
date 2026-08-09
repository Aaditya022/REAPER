package com.stackd.ignition.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link SourcePackager}: archive structure, exclusion of secrets and
 * tooling directories, and error handling.
 */
class SourcePackagerTest {

    private final SourcePackager packager = new SourcePackager();

    @TempDir
    Path tempDir;

    @Test
    void packagesRegularFilesWithRelativePaths() throws Exception {
        Files.writeString(tempDir.resolve("package.json"), "{\"name\":\"app\"}");
        Files.createDirectories(tempDir.resolve("backend"));
        Files.writeString(tempDir.resolve("backend/index.js"), "console.log('hi')");

        List<String> entries = readEntries(packager.packageSource(tempDir.toString()));

        assertTrue(entries.contains("package.json"), "entries: " + entries);
        assertTrue(entries.contains("backend/index.js"), "entries: " + entries);
        assertFalse(entries.stream().anyMatch(e -> e.startsWith("./")), "no leading ./: " + entries);
    }

    @Test
    void excludesEnvGitAndNodeModules() throws Exception {
        Files.writeString(tempDir.resolve(".env"), "DATABASE_URL=postgresql://u:p@localhost/db");
        Files.createDirectories(tempDir.resolve("backend"));
        Files.writeString(tempDir.resolve("backend/.env"), "JWT_SECRET=top-secret");
        Files.writeString(tempDir.resolve("backend/index.js"), "x");
        Files.createDirectories(tempDir.resolve("node_modules"));
        Files.writeString(tempDir.resolve("node_modules/lib.js"), "y");
        Files.createDirectories(tempDir.resolve(".git"));
        Files.writeString(tempDir.resolve(".git/config"), "z");

        List<String> entries = readEntries(packager.packageSource(tempDir.toString()));

        assertTrue(entries.contains("backend/index.js"));
        assertFalse(entries.contains(".env"));
        assertFalse(entries.contains("backend/.env"));
        assertFalse(entries.stream().anyMatch(e -> e.contains("node_modules")));
        assertFalse(entries.stream().anyMatch(e -> e.contains(".git")));
    }

    @Test
    void skipsSymlinksSoArchiveCannotEscapeTheProject() throws Exception {
        Files.writeString(tempDir.resolve("real.txt"), "real");
        Path outside = Files.createTempFile("outside", ".txt");
        Files.writeString(outside, "secret-outside");
        try {
            Files.createSymbolicLink(tempDir.resolve("linked.txt"), outside);

            List<String> entries = readEntries(packager.packageSource(tempDir.toString()));

            assertTrue(entries.contains("real.txt"));
            assertFalse(entries.contains("linked.txt"));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void blankProjectPathIsRejected() {
        assertThrows(SourcePackagingException.class, () -> packager.packageSource("   "));
    }

    @Test
    void missingProjectPathIsRejected() {
        assertThrows(SourcePackagingException.class,
                () -> packager.packageSource(tempDir.resolve("nope").toString()));
    }

    private static List<String> readEntries(byte[] archive) throws IOException {
        List<String> names = new ArrayList<>();
        try (TarArchiveInputStream in =
                     new TarArchiveInputStream(new GZIPInputStream(new ByteArrayInputStream(archive)))) {
            TarArchiveEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}
