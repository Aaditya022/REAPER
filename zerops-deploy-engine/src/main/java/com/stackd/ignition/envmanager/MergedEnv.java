package com.stackd.ignition.envmanager;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable merged result of a project {@code .env} and the Zerops-provided environment.
 *
 * <p>The merged {@link #values()} map is the project's own variables with every
 * Zerops-injected variable layered on top (injected values win on key collision).
 * Required variables must come from the Zerops side: a local development value in
 * the project {@code .env} (for example a {@code localhost} {@code DATABASE_URL})
 * is never a valid value to deploy, so {@link #missingRequiredVars()} reports any
 * required variable the Zerops environment does not provide.
 *
 * <p>Values are secrets. Never log or return {@link #values()} directly; use
 * {@link #maskedValues()} or {@link #toString()} instead, and validate with
 * {@link #validate()} before any deployment is started.
 */
public final class MergedEnv {

    /** Mask substituted for every value in secret-safe representations. */
    public static final String MASK = "********";

    private final Map<String, String> values;
    private final Map<String, String> projectValues;
    private final Map<String, String> zeropsValues;
    private final Set<String> required;

    MergedEnv(Map<String, String> values, Map<String, String> projectValues,
              Map<String, String> zeropsValues, Set<String> required) {
        this.values = values;
        this.projectValues = projectValues;
        this.zeropsValues = zeropsValues;
        this.required = required;
    }

    /**
     * Returns the merged value of a variable, or {@code null} if not present.
     *
     * @param name the variable name
     * @return the merged value or {@code null}
     */
    public String get(String name) {
        return values.get(name);
    }

    /**
     * Returns whether the merged environment defines the given variable.
     *
     * @param name the variable name
     * @return {@code true} if present
     */
    public boolean containsKey(String name) {
        return values.containsKey(name);
    }

    /**
     * Returns the full merged environment (project values overlaid with the
     * Zerops-injected values). Values are real secrets; do not log or expose.
     *
     * @return an unmodifiable merged map
     */
    public Map<String, String> values() {
        return values;
    }

    /**
     * Returns the variables read from the project {@code .env}, without any
     * Zerops injection. Values are real secrets; do not log or expose.
     *
     * @return an unmodifiable map of project variables
     */
    public Map<String, String> projectValues() {
        return projectValues;
    }

    /**
     * Returns the variables provided by the Zerops deployment environment
     * (the injected set). Values may be Zerops references such as
     * {@code ${db_connectionString}}; do not log or expose.
     *
     * @return an unmodifiable map of Zerops-provided variables
     */
    public Map<String, String> zeropsValues() {
        return zeropsValues;
    }

    /**
     * Returns the set of variables the deployment requires for this stack.
     *
     * @return an unmodifiable set of required variable names
     */
    public Set<String> requiredVars() {
        return required;
    }

    /**
     * Returns the required variables that the Zerops environment does not
     * provide (missing or blank). Every name must be satisfied by the Zerops
     * side; project {@code .env} values do not count.
     *
     * @return an unmodifiable set of missing required variable names
     */
    public Set<String> missingRequiredVars() {
        Set<String> missing = new LinkedHashSet<>();
        for (String name : required) {
            String value = zeropsValues.get(name);
            if (value == null || value.isBlank()) {
                missing.add(name);
            }
        }
        return Collections.unmodifiableSet(missing);
    }

    /**
     * Returns whether all required variables are provided.
     *
     * @return {@code true} if the deployment environment is complete
     */
    public boolean isComplete() {
        return missingRequiredVars().isEmpty();
    }

    /**
     * Throws {@link EnvConfigException} if any required variable is missing.
     * Call this before starting a deployment.
     *
     * @throws EnvConfigException if the environment is incomplete; the message
     *         names only the missing variable keys
     */
    public void validate() {
        Set<String> missing = missingRequiredVars();
        if (!missing.isEmpty()) {
            throw new EnvConfigException(EnvConfigException.MISSING_REQUIRED_ENV_VARS,
                    "Required environment variables missing from the Zerops deployment environment: " + missing);
        }
    }

    /**
     * Returns the merged environment with every value replaced by {@link #MASK}.
     * Safe for logs and responses.
     *
     * @return an unmodifiable masked map keyed by the same variable names
     */
    public Map<String, String> maskedValues() {
        Map<String, String> masked = new LinkedHashMap<>();
        for (String name : values.keySet()) {
            masked.put(name, MASK);
        }
        return Collections.unmodifiableMap(masked);
    }

    /**
     * Returns a secret-safe description: variable names and required/missing
     * sets only, never any values.
     *
     * @return the masked summary
     */
    @Override
    public String toString() {
        return "MergedEnv{required=" + required
                + ", variables=" + values.keySet()
                + ", missingRequired=" + missingRequiredVars() + "}";
    }
}
