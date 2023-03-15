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
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        requiresDependencyCollection = ResolutionScope.TEST,
        requiresDependencyResolution = ResolutionScope.TEST
)
public class CycloneDxPackageMojo extends BaseCycloneDxMojo {
    @Parameter(property = "reactorProjects", readonly = true, required = true)
    private List<MavenProject> reactorProjects;


    protected boolean shouldInclude(MavenProject mavenProject) {
        return Arrays.asList(new String[]{"war", "ear"}).contains(mavenProject.getPackaging());
    }

    protected String extractComponentsAndDependencies(Map<String, Component> components, Map<String, Dependency> dependencies, final Map<String, String> projectIdentities) throws MojoExecutionException {
        getLog().info(MESSAGE_RESOLVING_DEPS);

        for (final MavenProject mavenProject : reactorProjects) {
            if (!shouldInclude(mavenProject)) {
                continue;
            }
            getLog().info("Analyzing " + mavenProject.getArtifactId());

            final Map<String, Dependency> projectDependencies = extractBOMDependencies(mavenProject);

            final Map<String, String> projectPUrlToIdentity = new HashMap<>();
            projectDependenciesConverter.normalizeDependencies(schemaVersion(), projectDependencies, projectPUrlToIdentity);

            final Component projectBomComponent = convert(mavenProject.getArtifact());
            final String identity = projectPUrlToIdentity.get(projectBomComponent.getPurl());
            projectBomComponent.setBomRef(identity);
            components.put(identity, projectBomComponent);

            projectIdentities.put(projectBomComponent.getPurl(), projectBomComponent.getBomRef());

            populateComponents(components, mavenProject.getArtifacts(), projectPUrlToIdentity, null);

            dependencies.putAll(projectDependencies);
        }

        return "makePackageBom";
    }
}
