package com.stackd.ignition.envmanager;

/**
 * Thrown when the environment configuration for a deployment cannot be prepared.
 *
 * <p>Message bodies name only environment variable keys — never their values — so
 * secrets cannot leak into logs or error responses.
 */
public class EnvConfigException extends RuntimeException {

    /** The project path is blank, missing, or not a directory. */
    public static final String PROJECT_PATH_INVALID = "PROJECT_PATH_INVALID";

    /** A required environment variable is not provided by the Zerops deployment environment. */
    public static final String MISSING_REQUIRED_ENV_VARS = "MISSING_REQUIRED_ENV_VARS";

    /** The project .env file could not be read or parsed. */
    public static final String UNREADABLE_ENV_FILE = "UNREADABLE_ENV_FILE";

    /** Stable machine-readable code describing the environment failure. */
    private final String errorCode;

    /**
     * Creates the exception.
     *
     * @param errorCode the stable machine-readable error code
     * @param message   the human-readable failure reason; must not contain secret values
     */
    public EnvConfigException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Returns the stable machine-readable error code.
     *
     * @return one of the {@code *_ENV*} constants
     */
    public String getErrorCode() {
        return errorCode;
    }
}
