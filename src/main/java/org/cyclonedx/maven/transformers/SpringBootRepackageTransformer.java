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
package org.cyclonedx.maven.transformers;

import org.apache.maven.plugin.MojoExecution;
import org.cyclonedx.maven.BomTransformer;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Pedigree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;

@Singleton
@Named("org.springframework.boot:spring-boot-maven-plugin:repackage")
public class SpringBootRepackageTransformer implements BomTransformer {
    private static final Logger LOG = LoggerFactory.getLogger(SpringBootRepackageTransformer.class);

    @Override
    public void transform(MojoExecution execution, Component metadataComponent, Map<String, Component> components) {
        metadataComponent.setType(Component.Type.APPLICATION);

        components.values().forEach(c -> {
            c.setIsExternal(false);
            Pedigree p = new Pedigree();
            c.setPedigree(p);
            p.setNotes("stored in BOOT-INF/lib/");
        });

        // TODO should it also contain the fact that the boot loader has been shaded?

        // TODO manage advanced configurations https://docs.spring.io/spring-boot/maven-plugin/packaging.html#packaging.repackage-goal
    }
}
