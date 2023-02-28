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

import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.CumulativeScopeArtifactFilter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.Metadata;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Converts a Maven Project with its Maven dependencies resolution graph transformed into a SBOM dependencies list
 * with their dependsOn.
 */
public interface ProjectDependenciesConverter {

    Set<Dependency> extractBOMDependencies(MavenProject mavenProject, Scopes include, String[] excludes) throws MojoExecutionException;

    /**
     * Check consistency between BOM components and BOM dependencies, and cleanup: drop components found while walking the
     * Maven dependency resolution graph but that are finally not kept in the effective dependencies list.
     */
    void cleanupBomDependencies(Metadata metadata, Set<Component> components, Set<Dependency> dependencies);

    public static class Scopes {
        public final boolean compile;
        public final boolean provided;
        public final boolean runtime;
        public final boolean test;
        public final boolean system;

        public Scopes(boolean compile, boolean provided, boolean runtime, boolean test, boolean system) {
            this.compile = compile;
            this.provided = provided;
            this.runtime = runtime;
            this.test = test;
            this.system = system;
        }

        public ArtifactFilter getArtifactFilter() {
            final Collection<String> scope = new HashSet<>();
            if (compile) scope.add("compile");
            if (provided) scope.add("provided");
            if (runtime) scope.add("runtime");
            if (system) scope.add("system");
            if (test) scope.add("test");
            return new CumulativeScopeArtifactFilter(scope);
        }
    }
}
