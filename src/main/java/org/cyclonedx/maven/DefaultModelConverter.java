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

import java.util.TreeMap;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.artifact.Artifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;

@Singleton
@Named
public class DefaultModelConverter implements ModelConverter {
    private final Logger logger = LoggerFactory.getLogger(DefaultModelConverter.class);

    public DefaultModelConverter() {
    }

    public String generatePackageUrl(Artifact artifact) {
        TreeMap<String, String> qualifiers = null;
        if (artifact.getType() != null || artifact.getClassifier() != null) {
            qualifiers = new TreeMap<>();
            if (artifact.getType() != null) {
                qualifiers.put("type", artifact.getType());
            }
            if (artifact.getClassifier() != null) {
                qualifiers.put("classifier", artifact.getClassifier());
            }
        }
        return generatePackageUrl(artifact.getGroupId(), artifact.getArtifactId(), artifact.getBaseVersion(), qualifiers, null);
    }

    private String generatePackageUrl(String groupId, String artifactId, String version, TreeMap<String, String> qualifiers, String subpath) {
        try {
            return new PackageURL(PackageURL.StandardTypes.MAVEN, groupId, artifactId, version, qualifiers, subpath).canonicalize();
        } catch(MalformedPackageURLException e) {
          logger.warn("An unexpected issue occurred attempting to create a PackageURL for "
                + groupId + ":" + artifactId + ":" + version, e);
        }
        return null;
    }
}
