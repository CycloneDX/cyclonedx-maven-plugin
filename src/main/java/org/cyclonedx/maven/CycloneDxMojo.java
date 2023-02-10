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
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.codehaus.plexus.PlexusContainer;
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
     * Specify the project dependency analyzer to use (plexus component role-hint). By default,
     * <a href="https://maven.apache.org/shared/maven-dependency-analyzer/">maven-dependency-analyzer</a> is used. To use this, you must declare
     * a dependency for this plugin that contains the code for the analyzer. The analyzer must have a declared Plexus
     * role name, and you specify the role name here.
     *
     * @since 2.2
     */
    @Parameter(property = "analyzer", defaultValue = "default")
    private String analyzer;

    @org.apache.maven.plugins.annotations.Component
    private PlexusContainer plexusContainer;

    /**
     * DependencyAnalyzer
     */
    protected ProjectDependencyAnalyzer dependencyAnalyzer;

    /**
     * @return {@link ProjectDependencyAnalyzer}
     * @throws MojoExecutionException in case of an error.
     */
    protected ProjectDependencyAnalyzer createProjectDependencyAnalyzer() throws MojoExecutionException {
        final String role = ProjectDependencyAnalyzer.class.getName();
        final String roleHint = analyzer;
        try {
            return (ProjectDependencyAnalyzer) plexusContainer.lookup(role, roleHint);
        }
        catch (Exception exception) {
            throw new MojoExecutionException("Failed to instantiate ProjectDependencyAnalyser with role " + role
                    + " / role-hint " + roleHint, exception);
        }
    }

    protected boolean analyze(final Set<Component> components, final Set<Dependency> dependencies) throws MojoExecutionException {
        final Set<String> componentRefs = new LinkedHashSet<>();
        // Use default dependency analyzer
        dependencyAnalyzer = createProjectDependencyAnalyzer();
        getLog().info(MESSAGE_RESOLVING_DEPS);
        if (getProject() != null && getProject().getArtifacts() != null) {
            ProjectDependencyAnalysis dependencyAnalysis = null;
            try {
                dependencyAnalysis = dependencyAnalyzer.analyze(getProject());
            } catch (Exception e) {
                getLog().debug(e);
            }

            // Add reference to BOM metadata component.
            // Without this, direct dependencies of the Maven project cannot be determined.
            final Component bomComponent = convert(getProject().getArtifact());
            componentRefs.add(bomComponent.getBomRef());

            for (final Artifact artifact : getProject().getArtifacts()) {
                final Component component = convert(artifact);
                // ensure that only one component with the same bom-ref exists in the BOM
                if (!componentRefs.contains(component.getBomRef())) {
                    component.setScope(getComponentScope(component, artifact, dependencyAnalysis));
                    componentRefs.add(component.getBomRef());
                    components.add(component);
                }
            }
        }
        if (schemaVersion().getVersion() >= 1.2) {
            dependencies.addAll(buildDependencyGraph(null));
        }
        return true;
    }

    /**
     * Method to identify component scope based on dependency analysis
     *
     * @param component Component
     * @param artifact Artifact from maven project
     * @param dependencyAnalysis Dependency analysis data
     *
     * @return Component.Scope - Required: If the component is used. Optional: If it is unused
     */
    protected Component.Scope getComponentScope(Component component, Artifact artifact, ProjectDependencyAnalysis dependencyAnalysis) {
        if (dependencyAnalysis == null) {
            return null;
        }
        Set<Artifact> usedDeclaredArtifacts = dependencyAnalysis.getUsedDeclaredArtifacts();
        Set<Artifact> usedUndeclaredArtifacts = dependencyAnalysis.getUsedUndeclaredArtifacts();
        Set<Artifact> unusedDeclaredArtifacts = dependencyAnalysis.getUnusedDeclaredArtifacts();
        Set<Artifact> testArtifactsWithNonTestScope = dependencyAnalysis.getTestArtifactsWithNonTestScope();
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
