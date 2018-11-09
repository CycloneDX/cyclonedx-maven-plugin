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
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package org.cyclonedx.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.cyclonedx.model.Component;
import java.util.LinkedHashSet;
import java.util.Set;

@Mojo(
        name = "makeBom",
        defaultPhase = LifecyclePhase.VERIFY,
        requiresOnline = true,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class CycloneDxMojo extends BaseCycloneDxMojo {

    public void execute() throws MojoExecutionException {
        final boolean shouldSkip = Boolean.parseBoolean(System.getProperty("cyclonedx.skip", Boolean.toString(getSkip())));
        if (shouldSkip) {
            getLog().info("Skipping CycloneDX");
            return;
        }
        logParameters();
        Set<Component> components = new LinkedHashSet<>();
        getLog().info(MESSAGE_RESOLVING_DEPS);
        if (getProject() != null && getProject().getArtifacts() != null) {
            for (Artifact artifact : getProject().getArtifacts()) {
                if (shouldInclude(artifact)) {
                    components.add(convert(artifact));
                }
            }
        }

        super.execute(components);
    }

}
