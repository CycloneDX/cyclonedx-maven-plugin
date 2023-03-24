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
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzerException;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.cyclonedx.maven.ProjectDependenciesConverter.BomDependencies;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Creates a CycloneDX BOM for each Maven module with its dependencies.
 */
@Mojo(
        name = "makeBom",
        defaultPhase = LifecyclePhase.PACKAGE,
        threadSafe = true,
        requiresOnline = true,
        configurator = "cyclonedx-mojo-component-configurator"
)
public class CycloneDxMojo extends BaseCycloneDxMojo {

    /**
     * Only runs this goal or adds to aggregate SBOM if the module does not skip deploy.
     *
     * @since 2.7.11
     */
    @Parameter(property = "cyclonedx.skipNotDeployed", defaultValue = "true", required = false)
    protected boolean skipNotDeployed = true;

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

    protected ProjectDependencyAnalysis doProjectDependencyAnalysis(final MavenProject mavenProject, final BomDependencies bomDependencies) throws MojoExecutionException {
        if (detectUnusedForOptionalScope) {
            final MavenProject localMavenProject = new MavenProject(mavenProject);
            localMavenProject.setArtifacts(new LinkedHashSet<>(bomDependencies.getArtifacts().values()));
            localMavenProject.setDependencyArtifacts(new LinkedHashSet<>(bomDependencies.getDependencyArtifacts().values()));
            try {
                return getProjectDependencyAnalyzer().analyze(localMavenProject);
            } catch (ProjectDependencyAnalyzerException pdae) {
                getLog().debug("Could not analyze " + mavenProject.getId(), pdae); // TODO should warn...
            }
        }
        return null;
    }

    @Override
    protected boolean shouldSkip() {
        // The list of artifacts would be empty
        return super.shouldSkip() || skipNotDeployed && !isDeployable(getProject());
    }

    @Override
    protected String getSkipReason() {
        if (super.shouldSkip()) {
            return super.getSkipReason();
        }
        return "module skips deploy";
    }

    protected String extractComponentsAndDependencies(final Set<String> topLevelComponents, final Map<String, Component> components, final Map<String, Dependency> dependencies) throws MojoExecutionException {
        if (verbose) {
            getLog().info(MESSAGE_RESOLVING_DEPS);
        } else {
            getLog().debug(MESSAGE_RESOLVING_DEPS);
        }

        final BomDependencies bomDependencies = extractBOMDependencies(getProject());
        final Map<String, Dependency> projectDependencies = bomDependencies.getDependencies();

        final Component projectBomComponent = convertMavenDependency(getProject().getArtifact());
        components.put(projectBomComponent.getPurl(), projectBomComponent);
        topLevelComponents.add(projectBomComponent.getPurl());

        populateComponents(topLevelComponents, components, bomDependencies.getArtifacts(), doProjectDependencyAnalysis(getProject(), bomDependencies));

        projectDependencies.forEach(dependencies::putIfAbsent);

        return "makeBom";
    }
}
