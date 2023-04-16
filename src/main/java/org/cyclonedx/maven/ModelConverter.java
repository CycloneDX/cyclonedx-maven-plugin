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
import org.apache.maven.project.MavenProject;
import org.cyclonedx.CycloneDxSchema;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Metadata;

/**
 * Model converter from Maven concepts (Artifact + MavenProject) to CycloneDX ones
 * (resp. pURL and Component + Metadata).
 */
public interface ModelConverter {
    String generatePackageUrl(Artifact artifact);

    String generateVersionlessPackageUrl(final Artifact artifact);

    String generatePackageUrl(final org.eclipse.aether.artifact.Artifact artifact);

    String generateVersionlessPackageUrl(final org.eclipse.aether.artifact.Artifact artifact);

    String generateClassifierlessPackageUrl(final org.eclipse.aether.artifact.Artifact artifact);

    /**
     * Converts a Maven artifact (dependency or transitive dependency) into a
     * CycloneDX component.
     *
     * @param artifact the artifact to convert
     * @param schemaVersion the target CycloneDX schema version
     * @param includeLicenseText should license text be included in bom?
     * @return a CycloneDX component
     */
    Component convert(Artifact artifact, CycloneDxSchema.Version schemaVersion, boolean includeLicenseText);

    /**
     * Converts a MavenProject into a Metadata object.
     *
     * @param project the MavenProject to convert
     * @param projectType the target CycloneDX component type
     * @param schemaVersion the target CycloneDX schema version
     * @param includeLicenseText should license text be included in bom?
     * @return a CycloneDX Metadata object
     */
    Metadata convert(MavenProject project, String projectType, CycloneDxSchema.Version schemaVersion, boolean includeLicenseText);
}
