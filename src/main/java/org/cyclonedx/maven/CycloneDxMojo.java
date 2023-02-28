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
import java.util.LinkedHashSet;
import java.util.Set;

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

    protected String extractComponentsAndDependencies(final Set<Component> components, final Set<Dependency> dependencies) throws MojoExecutionException {
        final Set<String> componentRefs = new LinkedHashSet<>();

        getLog().info(MESSAGE_RESOLVING_DEPS);
        if (getProject() != null && getProject().getArtifacts() != null) {
            ProjectDependencyAnalysis dependencyAnalysis = doProjectDependencyAnalysis(getProject());

            // Add reference to BOM metadata component.
            // Without this, direct dependencies of the Maven project cannot be determined.
            final Component bomComponent = convert(getProject().getArtifact());
            componentRefs.add(bomComponent.getBomRef());

            for (final Artifact artifact : getProject().getArtifacts()) {
                final Component component = convert(artifact);
                // ensure that only one component with the same bom-ref exists in the BOM
                if (componentRefs.add(component.getBomRef())) {
                    component.setScope(inferComponentScope(artifact, dependencyAnalysis));
                    components.add(component);
                }
            }
        }

        dependencies.addAll(extractBOMDependencies(getProject()));

        return "makeBom";
    }

    /**
     * Infer BOM component scope based on Maven project dependency analysis.
     *
     * @param artifact Artifact from maven project
     * @param projectDependencyAnalysis Maven Project Dependency Analysis data
     *
     * @return Component.Scope - Required: If the component is used (as detected by project dependency analysis). Optional: If it is unused
     */
    protected Component.Scope inferComponentScope(Artifact artifact, ProjectDependencyAnalysis projectDependencyAnalysis) {
        if (projectDependencyAnalysis == null) {
            return null;
        }

        Set<Artifact> usedDeclaredArtifacts = projectDependencyAnalysis.getUsedDeclaredArtifacts();
        Set<Artifact> usedUndeclaredArtifacts = projectDependencyAnalysis.getUsedUndeclaredArtifacts();
        Set<Artifact> unusedDeclaredArtifacts = projectDependencyAnalysis.getUnusedDeclaredArtifacts();
        Set<Artifact> testArtifactsWithNonTestScope = projectDependencyAnalysis.getTestArtifactsWithNonTestScope();

        // Is the artifact used?
        if (usedDeclaredArtifacts.contains(artifact) || usedUndeclaredArtifacts.contains(artifact)) {
            return Component.Scope.REQUIRED;
        }

        // Is the artifact unused or test?
        if (unusedDeclaredArtifacts.contains(artifact) || testArtifactsWithNonTestScope.contains(artifact)) {
            return Component.Scope.OPTIONAL;
        }

        return null;
    }
}
