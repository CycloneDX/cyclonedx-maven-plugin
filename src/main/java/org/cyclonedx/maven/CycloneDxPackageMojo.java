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
 * Creates a CycloneDX BOM for each Maven module with {@code war} or {@code ear} packaging.
 *
 * @since 2.4.0
 */
@Mojo(
        name = "makePackageBom",
        defaultPhase = LifecyclePhase.PACKAGE,
        threadSafe = true,
        aggregator = true,
        requiresOnline = true,
        configurator = "cyclonedx-mojo-component-configurator"
)
public class CycloneDxPackageMojo extends BaseCycloneDxMojo {
    @Parameter(property = "reactorProjects", readonly = true, required = true)
    private List<MavenProject> reactorProjects;


    protected boolean shouldInclude(MavenProject mavenProject) {
        return Arrays.asList(new String[]{"war", "ear"}).contains(mavenProject.getPackaging());
    }

    protected String extractComponentsAndDependencies(Set<String> topLevelComponents, Map<String, Component> components, Map<String, Dependency> dependencies) throws MojoExecutionException {
        if (verbose) {
            getLog().info(MESSAGE_RESOLVING_DEPS);
        } else {
            getLog().debug(MESSAGE_RESOLVING_DEPS);
        }

        for (final MavenProject mavenProject : reactorProjects) {
            if (!shouldInclude(mavenProject)) {
                continue;
            }
            if (verbose) {
                getLog().info("Analyzing " + mavenProject.getArtifactId());
            } else {
                getLog().debug("Analyzing " + mavenProject.getArtifactId());
            }

            final BomDependencies bomDependencies = extractBOMDependencies(mavenProject);
            final Map<String, Dependency> projectDependencies = bomDependencies.getDependencies();

            final Component projectBomComponent = convertMavenDependency(mavenProject.getArtifact());
            components.put(projectBomComponent.getPurl(), projectBomComponent);
            topLevelComponents.add(projectBomComponent.getPurl());

            populateComponents(topLevelComponents, components, bomDependencies.getArtifacts(), null);

            projectDependencies.forEach(dependencies::putIfAbsent);
        }

        return "makePackageBom";
    }
}
