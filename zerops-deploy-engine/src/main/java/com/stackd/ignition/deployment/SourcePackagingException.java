package com.stackd.ignition.deployment;

/**
 * Thrown when the project source could not be packaged into the upload archive.
 *
 * <p>Messages name the file or directory that failed and never include file
 * contents.
 */
public class SourcePackagingException extends RuntimeException {

    /** Stable machine-readable error code. */
    public static final String SOURCE_PACKAGING_FAILED = "SOURCE_PACKAGING_FAILED";

    /**
     * Creates the exception.
     *
     * @param message the failure reason
     */
    public SourcePackagingException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message the failure reason
     * @param cause   the underlying exception
     */
    public SourcePackagingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Returns the stable error code.
     *
     * @return {@link #SOURCE_PACKAGING_FAILED}
     */
    public String getErrorCode() {
        return SOURCE_PACKAGING_FAILED;
    }
}
