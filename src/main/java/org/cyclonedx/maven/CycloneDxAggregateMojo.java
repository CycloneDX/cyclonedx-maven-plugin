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
import org.cyclonedx.maven.ProjectDependenciesConverter.BomDependencies;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;

import java.util.Arrays;
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
        requiresOnline = true
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

    @Override
    protected String extractComponentsAndDependencies(final Set<String> topLevelComponents, final Map<String, Component> components, final Map<String, Dependency> dependencies) throws MojoExecutionException {
        if (! getProject().isExecutionRoot()) {
            // non-root project: let parent class create a module-only BOM?
            if (outputReactorProjects) {
                return super.extractComponentsAndDependencies(topLevelComponents, components, dependencies);
            }
            getLog().info("Skipping CycloneDX on non-execution root");
            return null;
        }

        // root project: analyze and aggregate all the modules
        getLog().info((reactorProjects.size() <= 1) ? MESSAGE_RESOLVING_DEPS : MESSAGE_RESOLVING_AGGREGATED_DEPS);

        for (final MavenProject mavenProject : reactorProjects) {
            if (shouldExclude(mavenProject)) {
                getLog().info("Excluding " + mavenProject.getArtifactId());
                continue;
            }

            final BomDependencies bomDependencies = extractBOMDependencies(mavenProject);
            final Map<String, Dependency> projectDependencies = bomDependencies.getDependencies();

            final Component projectBomComponent = convert(mavenProject.getArtifact());
            components.put(projectBomComponent.getPurl(), projectBomComponent);
            topLevelComponents.add(projectBomComponent.getPurl());

            populateComponents(topLevelComponents, components, bomDependencies.getArtifacts(), doProjectDependencyAnalysis(mavenProject, bomDependencies));

            projectDependencies.forEach(dependencies::putIfAbsent);
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
    private void addMavenProjectsAsParentDependencies(List<MavenProject> reactorProjects, Map<String, Dependency> dependencies) {
        for (final MavenProject project: reactorProjects) {
            if (project.hasParent()) {
                final String parentRef = generatePackageUrl(project.getParent().getArtifact());
                Dependency parentDependency = dependencies.get(parentRef);
                if (parentDependency != null) {
                    final String projectRef = generatePackageUrl(project.getArtifact());
                    parentDependency.addDependency(new Dependency(projectRef));
                }
            }
        }
    }
}
