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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzerException;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates a CycloneDX BOM for each Maven module with its dependencies.
 */
@Mojo(
        name = "makeBom",
        defaultPhase = LifecyclePhase.PACKAGE,
        threadSafe = true,
        requiresOnline = true,
        requiresDependencyCollection = ResolutionScope.TEST,
        requiresDependencyResolution = ResolutionScope.TEST
)
public class CycloneDxMojo extends BaseCycloneDxMojo {

    /**
     * Specify the Maven project dependency analyzer to use (plexus component role-hint). By default,
     * <a href="https://maven.apache.org/shared/maven-dependency-analyzer/">maven-dependency-analyzer</a>'s one
     * is used.
     *
     * To use another implementation, you must declare a dependency for this plugin that contains the code for the analyzer
     * and you specify its Plexus role name here.
     *
     * @since 2.1.0
     */
    @Parameter(property = "analyzer", defaultValue = "default")
    private String analyzer; // https://github.com/CycloneDX/cyclonedx-maven-plugin/pull/65

    @org.apache.maven.plugins.annotations.Component
    private PlexusContainer plexusContainer;

    /**
     * Maven ProjectDependencyAnalyzer analyzes a Maven project's declared dependencies and effective classes used to find which artifacts are
     * used and declared, used but not declared, not used but declared.
     */
    protected ProjectDependencyAnalyzer dependencyAnalyzer;

    private ProjectDependencyAnalyzer getProjectDependencyAnalyzer() throws MojoExecutionException {
        if (dependencyAnalyzer == null) {
            try {
                dependencyAnalyzer = (ProjectDependencyAnalyzer) plexusContainer.lookup(ProjectDependencyAnalyzer.class, analyzer);
            } catch (ComponentLookupException cle) {
                throw new MojoExecutionException("Failed to instantiate ProjectDependencyAnalyser with role-hint " + analyzer, cle);
            }
        }
        return dependencyAnalyzer;
    }

    protected ProjectDependencyAnalysis doProjectDependencyAnalysis(MavenProject mavenProject) throws MojoExecutionException {
        try {
            return getProjectDependencyAnalyzer().analyze(mavenProject);
        } catch (ProjectDependencyAnalyzerException pdae) {
            getLog().debug("Could not analyze " + mavenProject.getId(), pdae); // TODO should warn...
        }
        return null;
    }

    protected String extractComponentsAndDependencies(final Map<String, Component> components, final Map<String, Dependency> dependencies, final Map<String, String> projectIdentities) throws MojoExecutionException {
        getLog().info(MESSAGE_RESOLVING_DEPS);

        final Map<String, Dependency> projectDependencies = extractBOMDependencies(getProject());

        final Map<String, String> projectPUrlToIdentity = new HashMap<>();
        projectDependenciesConverter.normalizeDependencies(schemaVersion(), projectDependencies, projectPUrlToIdentity);

        final Component projectBomComponent = convert(getProject().getArtifact());
        final String identity = projectPUrlToIdentity.get(projectBomComponent.getPurl());
        projectBomComponent.setBomRef(identity);
        components.put(identity, projectBomComponent);

        projectIdentities.put(projectBomComponent.getPurl(), projectBomComponent.getBomRef());

        populateComponents(components, getProject().getArtifacts(), projectPUrlToIdentity, doProjectDependencyAnalysis(getProject()));
        dependencies.putAll(projectDependencies);

        return "makeBom";
    }
}
