package com.stackd.ignition.analyzer;

import java.util.Set;

/**
 * Parsed view of a {@code package.json} sufficient for stack detection.
 *
 * @param dependencies the union of names in {@code dependencies} and {@code devDependencies}
 * @param hasWorkspaces whether a {@code workspaces} field is present
 */
record PackageJson(Set<String> dependencies, boolean hasWorkspaces) {

    /**
     * Returns whether the package declares the given dependency.
     *
     * @param name the dependency name
     * @return {@code true} if declared in either dependency section
     */
    boolean hasDependency(String name) {
        return dependencies.contains(name);
    }
}
