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

import org.apache.maven.plugin.MojoExecution;
import org.cyclonedx.model.Component;

import java.util.Map;

/**
 * Update Bom Components to match a plugin that has an impact on SBOM.
 */
public interface BomTransformer {
    /**
     * Based on goal execution configuration, adapt the component that represents the project being build and/or
     * the components that are extracted from Maven dependencies.
     *
     * @param execution goal execution from build lifecycle
     * @param metadataComponent component of the project that and will be kept as metadata component in the final Bom
     * @param components components of the Bom
     */
    void transform(MojoExecution execution, Component metadataComponent, Map<String, Component> components);
}
