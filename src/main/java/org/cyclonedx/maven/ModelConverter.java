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
import org.cyclonedx.Version;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.Metadata;

/**
 * Model converter from Maven concepts (dependency Artifact + MavenProject) to CycloneDX ones
 * (resp. Component with pURL + Metadata).
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
    default Component convertMavenDependency(Artifact artifact, Version schemaVersion, boolean includeLicenseText) {
        return convertMavenDependency(artifact, schemaVersion, includeLicenseText, false);
    }

    /**
     * Converts a Maven artifact (dependency or transitive dependency) into a
     * CycloneDX component. When {@code skipArtifactDownload} is {@code true},
     * component hashes are omitted (no read of the artifact file on disk); all
     * other metadata (purl, licenses, external references derived from the
     * effective POM) is unaffected.
     *
     * @param artifact the artifact to convert
     * @param schemaVersion the target CycloneDX schema version
     * @param includeLicenseText should license text be included in bom?
     * @param skipArtifactDownload when {@code true}, do not read {@code artifact.getFile()} to compute hashes
     * @return a CycloneDX component
     * @since 2.9.2
     */
    Component convertMavenDependency(Artifact artifact, Version schemaVersion, boolean includeLicenseText, boolean skipArtifactDownload);

    /**
     * Converts a MavenProject into a CycloneDX Metadata object.
     *
     * @param project the MavenProject to convert
     * @param projectType the target CycloneDX component type
     * @param schemaVersion the target CycloneDX schema version
     * @param includeLicenseText should license text be included in bom?
     * @param externalReferences the external references
     * @return a CycloneDX Metadata object
     */
    Metadata convertMavenProject(MavenProject project, String projectType, Version schemaVersion, boolean includeLicenseText, ExternalReference[] externalReferences);

}
