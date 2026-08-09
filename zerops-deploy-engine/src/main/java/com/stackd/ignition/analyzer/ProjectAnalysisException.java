package com.stackd.ignition.analyzer;

/**
 * Thrown when a project's stack cannot be determined from its filesystem layout.
 *
 * <p>The analyzer fails loudly rather than guessing: unknown, ambiguous,
 * incomplete, or unsupported projects surface as this exception with a stable
 * machine-readable {@link #getErrorCode()}.
 */
public class ProjectAnalysisException extends RuntimeException {

    /** The project path is blank, missing, or not a directory. */
    public static final String PROJECT_PATH_INVALID = "PROJECT_PATH_INVALID";

    /** The directory has no recognizable STACKD frontend or backend. */
    public static final String NOT_A_STACKD_PROJECT = "NOT_A_STACKD_PROJECT";

    /** Multiple conflicting frameworks/databases were detected. */
    public static final String AMBIGUOUS_STACK = "AMBIGUOUS_STACK";

    /** A valid layout that automatic detection does not support (monorepo, unknown DB scheme). */
    public static final String UNSUPPORTED_LAYOUT = "UNSUPPORTED_LAYOUT";

    /** A required file could not be read or parsed. */
    public static final String UNREADABLE_PROJECT = "UNREADABLE_PROJECT";

    /** Stable machine-readable code describing the analysis failure. */
    private final String errorCode;

    /**
     * Creates the exception.
     *
     * @param errorCode the stable machine-readable error code
     * @param message   the human-readable failure reason
     */
    public ProjectAnalysisException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Returns the stable machine-readable error code.
     *
     * @return one of the {@code *_PROJECT} or {@code *_STACK} constants
     */
    public String getErrorCode() {
        return errorCode;
    }
}
