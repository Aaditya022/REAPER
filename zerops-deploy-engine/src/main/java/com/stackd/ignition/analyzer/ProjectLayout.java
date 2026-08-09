package com.stackd.ignition.analyzer;

import java.nio.file.Path;

/**
 * Resolved file-system layout of a STACKD project.
 *
 * @param root         the canonical project root directory
 * @param frontendDir  the directory containing the frontend package, or {@code null}
 * @param backendDir   the directory containing the backend package, or {@code null}
 */
record ProjectLayout(Path root, Path frontendDir, Path backendDir) {
}
