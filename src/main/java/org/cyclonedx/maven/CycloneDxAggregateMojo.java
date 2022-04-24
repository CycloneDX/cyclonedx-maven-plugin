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
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Mojo(
        name = "makeAggregateBom",
        defaultPhase = LifecyclePhase.PACKAGE,
        aggregator = true,
        requiresOnline = true,
        requiresDependencyCollection = ResolutionScope.TEST,
        requiresDependencyResolution = ResolutionScope.TEST
)
@Execute( phase = LifecyclePhase.TEST_COMPILE )
public class CycloneDxAggregateMojo extends BaseCycloneDxMojo {

    protected boolean shouldExclude(MavenProject mavenProject) {
        boolean shouldExclude = false;
        if (excludeArtifactId != null && excludeArtifactId.length > 0) {
            shouldExclude = Arrays.stream(excludeArtifactId).anyMatch(mavenProject.getArtifactId()::equals);
        }
        if (excludeTestProject && mavenProject.getArtifactId().contains("test")) {
            shouldExclude = true;
        }
        return shouldExclude;
    }

    public void execute() throws MojoExecutionException {
        final boolean shouldSkip = Boolean.parseBoolean(System.getProperty("cyclonedx.skip", Boolean.toString(getSkip())));
        if (shouldSkip) {
            getLog().info("Skipping CycloneDX");
            return;
        }

        final Set<Component> components = new LinkedHashSet<>();
        final Set<String> componentRefs = new LinkedHashSet<>();
        final Map<String, ProjectDependencyAnalysis> dependencyAnalysisMap = new LinkedHashMap<>();

        Set<Dependency> dependencies = new LinkedHashSet<>();
        // Use default dependency analyzer
        dependencyAnalyzer = createProjectDependencyAnalyzer();
        // Perform dependency analysis for all projects upfront
        for (final MavenProject mavenProject : getReactorProjects()) {
            if (shouldExclude(mavenProject)) {
                continue;
            }
            ProjectDependencyAnalysis dependencyAnalysis = null;
            try {
                dependencyAnalysis = dependencyAnalyzer.analyze(mavenProject);
                dependencyAnalysisMap.put(mavenProject.getArtifactId(), dependencyAnalysis);
            } catch (Exception e) {
                getLog().debug(e);
            }
        }

        // Add reference to BOM metadata component.
        // Without this, direct dependencies of the Maven project cannot be determined.
        final Component bomComponent = convert(getProject().getArtifact());
        componentRefs.add(bomComponent.getBomRef());

        for (final MavenProject mavenProject : getReactorProjects()) {
            if (shouldExclude(mavenProject)) {
                getLog().info("Skipping " + mavenProject.getArtifactId());
                continue;
            }

            final Set<Component> projectComponents = new LinkedHashSet<>();
            final Set<String> projectComponentRefs = new LinkedHashSet<>();
            final Set<Dependency> projectDependencies = new LinkedHashSet<>();

            // Add reference to BOM metadata component.
            // Without this, direct dependencies of the Maven project cannot be determined.
            final Component projectBomComponent = convert(mavenProject.getArtifact());
            if (! mavenProject.isExecutionRoot()) {
                // DO NOT include root project as it's already been included as a bom metadata component
                // Also, ensure that only one project component with the same bom-ref exists in the BOM
                boolean found = false;
                for (String s : componentRefs) {
                    if (s != null && s.equals(projectBomComponent.getBomRef())) {
                        found = true;
                    }
                }
                if (!found) {
                    components.add(projectBomComponent);
                }
            }
            componentRefs.add(projectBomComponent.getBomRef());

            for (final Artifact artifact : mavenProject.getArtifacts()) {
                if (shouldInclude(artifact)) {
                    final Component component = convert(artifact);

                    // ensure that only one component with the same bom-ref exists in the BOM
                    boolean found = false;
                    for (String s : componentRefs) {
                        if (s != null && s.equals(component.getBomRef())) {
                            found = true;
                        }
                    }
                    if (!found) {
                        Component.Scope componentScope = null;
                        for (String projectId : dependencyAnalysisMap.keySet()) {
                            ProjectDependencyAnalysis dependencyAnalysis = dependencyAnalysisMap.get(projectId);
                            Component.Scope currentProjectScope = getComponentScope(component, artifact, dependencyAnalysis);
                            // Set scope to required if the component is used in any project
                            if (Component.Scope.REQUIRED.equals(currentProjectScope)) {
                                componentScope = currentProjectScope;
                                break;
                            } else if (componentScope == null && currentProjectScope != null) {
                                // Set optional or excluded scope
                                componentScope = currentProjectScope;
                            }
                        }
                        component.setScope(componentScope);
                        componentRefs.add(component.getBomRef());
                        components.add(component);

                        projectComponentRefs.add(component.getBomRef());
                        projectComponents.add(component);
                    }
                }
            }
            if (schemaVersion().getVersion() >= 1.2) {
                projectDependencies.addAll(buildDependencyGraph(componentRefs, mavenProject));
                dependencies.addAll(projectDependencies);
            }
            if (! mavenProject.isExecutionRoot()) {
                super.execute(projectComponents, projectDependencies, mavenProject);
            }
        }
        addMavenProjectsAsDependencies(getReactorProjects(), dependencies);
        super.execute(components, dependencies, getProject());
    }
}
