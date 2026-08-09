package com.stackd.ignition.deployment;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.stereotype.Component;

/**
 * Packages a project source directory into a gzipped tarball for upload to
 * Zerops, mirroring the official showcase deploy script's {@code tar czf -C
 * <dir> .} step.
 *
 * <p>Secret and irrelevant material is deliberately excluded: {@code .env}
 * files at any depth (they can carry real connection strings and passwords),
 * {@code .git}, and {@code node_modules}. Symlinks and non-regular files are
 * skipped so archive creation cannot follow links outside the project. The
 * archive is built in memory; per-file reads are not bounded because a project
 * source archive is expected to be small for the MVP demo.
 */
@Component
public class SourcePackager {

    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(".git", "node_modules");
    private static final String ENV_FILE_NAME = ".env";

    /**
     * Packages the project directory into a gzipped tar archive.
     *
     * @param projectPath the project root path
     * @return the gzipped tar bytes
     * @throws SourcePackagingException if the directory cannot be walked or read
     */
    public byte[] packageSource(String projectPath) {
        Path root = resolveRoot(projectPath);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (OutputStream gzip = new GZIPOutputStream(out);
                 TarArchiveOutputStream tar = new TarArchiveOutputStream(new BufferedOutputStream(gzip))) {
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                writeTree(tar, root, root);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new SourcePackagingException("Could not package project source: " + root, e);
        }
    }

    private static Path resolveRoot(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            throw new SourcePackagingException("Project path must not be blank");
        }
        Path real;
        try {
            real = Path.of(projectPath).toRealPath();
        } catch (IOException e) {
            throw new SourcePackagingException("Project path does not exist or is not readable: " + projectPath);
        }
        if (!Files.isDirectory(real)) {
            throw new SourcePackagingException("Project path is not a directory: " + projectPath);
        }
        return real;
    }

    private static void writeTree(TarArchiveOutputStream tar, Path root, Path current) throws IOException {
        try (Stream<Path> children = Files.list(current)) {
            for (Path child : children.toList()) {
                String name = child.getFileName().toString();
                if (ENV_FILE_NAME.equals(name)) {
                    continue;
                }
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    if (!EXCLUDED_DIRECTORIES.contains(name)) {
                        writeTree(tar, root, child);
                    }
                    continue;
                }
                if (!Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                String entryName = root.relativize(child).toString().replace('\\', '/');
                if (entryName.isEmpty()) {
                    continue;
                }
                TarArchiveEntry entry = new TarArchiveEntry(entryName);
                entry.setSize(Files.size(child));
                tar.putArchiveEntry(entry);
                Files.copy(child, tar);
                tar.closeArchiveEntry();
            }
        }
    }
}
