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

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.cyclonedx.maven.model.Component;
import org.w3c.dom.Document;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedHashSet;
import java.util.Set;

@Mojo(
        name = "writeBom",
        defaultPhase = LifecyclePhase.VERIFY,
        requiresOnline = true,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class CycloneDxMojo extends BaseCycloneDxMojo {

    public void execute() throws MojoExecutionException {
        if ("pom".equals(project.getPackaging())) {
            getLog().info(MESSAGE_SKIPPING_POM);
            return;
        }
        logParameters();

        Set<Component> components = new LinkedHashSet<>();
        getLog().info(MESSAGE_RESOLVING_DEPS);
        if (project != null && project.getDependencyArtifacts() != null) {
            for (Artifact artifact : project.getDependencyArtifacts()) {
                components.add(convert(artifact));
            }
        }

        try {
            Document doc = createBom(components);
            String bomString = toString(doc);
            File bomFile = new File(project.getBasedir(), "target/bom.xml");
            getLog().info(MESSAGE_WRITING_BOM);
            FileUtils.write(bomFile, bomString, Charset.forName("UTF-8"), false);

            boolean isValid = validateBom(bomFile);
            if (!isValid) {
                throw new MojoExecutionException(MESSAGE_VALIDATION_FAILURE);
            }

        } catch (ParserConfigurationException | IOException e) {
            throw new MojoExecutionException("An error occurred executing " + this.getClass().getName(), e);
        }
    }

}
