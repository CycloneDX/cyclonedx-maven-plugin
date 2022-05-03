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
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import java.util.LinkedHashSet;
import java.util.Set;

@Mojo(
        name = "makeBom",
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresOnline = true,
        requiresDependencyCollection = ResolutionScope.TEST,
        requiresDependencyResolution = ResolutionScope.TEST
)
public class CycloneDxMojo extends BaseCycloneDxMojo {

    public void execute() throws MojoExecutionException {
        final boolean shouldSkip = Boolean.parseBoolean(System.getProperty("cyclonedx.skip", Boolean.toString(getSkip())));
        if (shouldSkip) {
            getLog().info("Skipping CycloneDX");
            return;
        }
        logParameters();
        final Set<Component> components = new LinkedHashSet<>();
        final Set<String> componentRefs = new LinkedHashSet<>();
        Set<Dependency> dependencies = new LinkedHashSet<>();
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
                        component.setScope(getComponentScope(component, artifact, dependencyAnalysis));
                        componentRefs.add(component.getBomRef());
                        components.add(component);
                    }
                }
            }
        }
        if (schemaVersion().getVersion() >= 1.2) {
            dependencies = buildDependencyGraph(componentRefs, null);
        }
        super.execute(components, dependencies, getProject());
    }

}
