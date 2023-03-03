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
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates a CycloneDX aggregate BOM at build root (with dependencies from the whole multi-modules build), and eventually a BOM for each module.
 *
 * @since 2.1.0
 */
@Mojo(
        name = "makeAggregateBom",
        defaultPhase = LifecyclePhase.PACKAGE,
        threadSafe = true,
        aggregator = true,
        requiresOnline = true,
        requiresDependencyCollection = ResolutionScope.TEST,
        requiresDependencyResolution = ResolutionScope.TEST
)
public class CycloneDxAggregateMojo extends CycloneDxMojo {
    @Parameter(property = "reactorProjects", readonly = true, required = true)
    private List<MavenProject> reactorProjects;

    /**
     * Should non-root reactor projects create a module-only BOM?
     *
     * @since 2.6.2
     */
    @Parameter(property = "outputReactorProjects", defaultValue = "true", required = false)
    private Boolean outputReactorProjects;

    /**
     * Excluded reactor project (aka module) ArtifactIds from aggregate BOM.
     *
     * @since 2.4.0
     */
    @Parameter(property = "excludeArtifactId", required = false)
    protected String[] excludeArtifactId;

    /**
     * Excluded reactor project (aka module) GroupIds from aggregate BOM.
     *
     * @since 2.7.3
     */
    @Parameter(property = "excludeGroupId", required = false)
    protected String[] excludeGroupId;

    /**
     * Should reactor project (aka module) artifactId with the word "test" be excluded from aggregate BOM?
     *
     * @since 2.4.0
     */
    @Parameter(property = "excludeTestProject", defaultValue = "false", required = false)
    protected Boolean excludeTestProject;

    protected boolean shouldExclude(MavenProject mavenProject) {
        boolean shouldExclude = false;
        if (excludeArtifactId != null && excludeArtifactId.length > 0) {
            shouldExclude = Arrays.asList(excludeArtifactId).contains(mavenProject.getArtifactId());
        }
        if (!shouldExclude && excludeGroupId != null && excludeGroupId.length > 0) {
            shouldExclude = Arrays.asList(excludeGroupId).contains(mavenProject.getGroupId());
        }
        if (excludeTestProject && mavenProject.getArtifactId().contains("test")) {
            shouldExclude = true;
        }
        return shouldExclude;
    }

    @Override
    protected void logAdditionalParameters() {
        getLog().info("outputReactorProjects  : " + outputReactorProjects);
    }

    protected String extractComponentsAndDependencies(final Set<Component> components, final Set<Dependency> dependencies) throws MojoExecutionException {
        if (! getProject().isExecutionRoot()) {
            // non-root project: let parent class create a module-only BOM?
            if (outputReactorProjects) {
                return super.extractComponentsAndDependencies(components, dependencies);
            }
            getLog().info("Skipping CycloneDX on non-execution root");
            return null;
        }

        // root project: analyze and aggregate all the modules
        getLog().info((reactorProjects.size() <= 1) ? MESSAGE_RESOLVING_DEPS : MESSAGE_RESOLVING_AGGREGATED_DEPS);
        final Set<String> componentRefs = new LinkedHashSet<>();

        // Perform used/unused dependencies analysis for all projects upfront
        final List<ProjectDependencyAnalysis> projectsDependencyAnalysis = prepareMavenDependencyAnalysis();

        // Add reference to BOM metadata component.
        // Without this, direct dependencies of the Maven project cannot be determined.
        final Component bomComponent = convert(getProject().getArtifact());
        componentRefs.add(bomComponent.getBomRef());

        for (final MavenProject mavenProject : reactorProjects) {
            if (shouldExclude(mavenProject)) {
                getLog().info("Excluding " + mavenProject.getArtifactId());
                continue;
            }

            // Add reference to BOM metadata component.
            // Without this, direct dependencies of the Maven project cannot be determined.
            final Component projectBomComponent = convert(mavenProject.getArtifact());
            if (! mavenProject.isExecutionRoot()) {
                // DO NOT include root project as it's already been included as a bom metadata component
                // Also, ensure that only one project component with the same bom-ref exists in the BOM
                if (!componentRefs.contains(projectBomComponent.getBomRef())) {
                    components.add(projectBomComponent);
                }
            }
            componentRefs.add(projectBomComponent.getBomRef());

            for (final Artifact artifact : mavenProject.getArtifacts()) {
                final Component component = convert(artifact);

                // ensure that only one component with the same bom-ref exists in the BOM
                if (componentRefs.add(component.getBomRef())) {
                    component.setScope(inferComponentScope(artifact, projectsDependencyAnalysis));
                    components.add(component);
                }
            }

            dependencies.addAll(extractBOMDependencies(mavenProject));
        }

        addMavenProjectsAsParentDependencies(reactorProjects, dependencies);

        return "makeAggregateBom";
    }

    /**
     * When a Maven project from the reactor has his Maven parent in the reactor, register it as a dependency of his parent.
     * This completes the BOM dependency graph with references between projects in the reactor that don't have any
     * code dependency, but only the build reactor.
     *
     * @param reactorProjects the Maven projects from the reactor
     * @param dependencies all BOM dependencies found in reactor
     */
    private void addMavenProjectsAsParentDependencies(List<MavenProject> reactorProjects, Set<Dependency> dependencies) {
        Map<String, Dependency> dependenciesByRef = new HashMap<>();
        dependencies.forEach(d -> dependenciesByRef.put(d.getRef(), d));

        for (final MavenProject project: reactorProjects) {
            if (project.hasParent()) {
                final String parentRef = generatePackageUrl(project.getParentArtifact());
                Dependency parentDependency = dependenciesByRef.get(parentRef);
                if (parentDependency != null) {
                    final Dependency child = new Dependency(generatePackageUrl(project.getArtifact()));
                    parentDependency.addDependency(child);
                }
            }
        }
    }

    private List<ProjectDependencyAnalysis> prepareMavenDependencyAnalysis() throws MojoExecutionException {
        final List<ProjectDependencyAnalysis> dependencyAnalysisMap = new ArrayList<>();
        for (final MavenProject mavenProject : reactorProjects) {
            if (shouldExclude(mavenProject)) {
                continue;
            }
            ProjectDependencyAnalysis dependencyAnalysis = doProjectDependencyAnalysis(mavenProject);
            if (dependencyAnalysis != null) {
                dependencyAnalysisMap.add(dependencyAnalysis);
            }
        }
        return dependencyAnalysisMap;
    }

    private Component.Scope inferComponentScope(Artifact artifact, List<ProjectDependencyAnalysis> projectsDependencyAnalysis) {
        Component.Scope componentScope = null;
        for (ProjectDependencyAnalysis dependencyAnalysis : projectsDependencyAnalysis) {
            Component.Scope currentProjectScope = inferComponentScope(artifact, dependencyAnalysis);

            // Set scope to required if the component is used in any project
            if (Component.Scope.REQUIRED.equals(currentProjectScope)) {
                return Component.Scope.REQUIRED;
            }

            if (componentScope == null && currentProjectScope != null) {
                // Set optional or excluded scope
                componentScope = currentProjectScope;
            }
        }
        return componentScope;
    }
}
