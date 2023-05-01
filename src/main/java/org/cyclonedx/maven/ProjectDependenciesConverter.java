/*
 * This file is part of CycloneDX Maven Plugin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) OWASP Foundation. All Rights Reserved.
 */
package org.cyclonedx.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.Metadata;

import java.util.Map;

/**
 * Converts a Maven Project with its Maven dependencies resolution graph transformed into a SBOM dependencies list
 * with their dependsOn.
 */
public interface ProjectDependenciesConverter {

    BomDependencies extractBOMDependencies(MavenProject mavenProject, MavenDependencyScopes include, String[] excludes) throws MojoExecutionException;

    /**
     * Check consistency between BOM components and BOM dependencies, and cleanup: drop components found while walking the
     * Maven dependency resolution graph but that are finally not kept in the effective dependencies list.
     */
    void cleanupBomDependencies(Metadata metadata, Map<String, Component> components, Map<String, Dependency> dependencies);

    public static class MavenDependencyScopes {
        public final boolean compile;
        public final boolean provided;
        public final boolean runtime;
        public final boolean test;
        public final boolean system;

        public MavenDependencyScopes(boolean compile, boolean provided, boolean runtime, boolean test, boolean system) {
            this.compile = compile;
            this.provided = provided;
            this.runtime = runtime;
            this.test = test;
            this.system = system;
        }
    }

    public static class BomDependencies {
        private final Map<String, Dependency> dependencies;
        private final Map<String, Artifact> artifacts;
        private final Map<String, Artifact> dependencyArtifacts;

        public BomDependencies(final Map<String, Dependency> dependencies, final Map<String, Artifact> artifacts, final Map<String, Artifact> dependencyArtifacts) {
            this.dependencies = dependencies;
            this.artifacts = artifacts;
            this.dependencyArtifacts = dependencyArtifacts;
        }

        public final Map<String, Dependency> getDependencies() {
            return dependencies;
        }

        public final Map<String, Artifact> getDependencyArtifacts() {
            return dependencyArtifacts;
        }

        public final Map<String, Artifact> getArtifacts() {
            return artifacts;
        }
    }
}
