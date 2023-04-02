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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.License;
import org.cyclonedx.model.LicenseChoice;
import org.jetbrains.annotations.NotNull;

@Mojo(
        name = "enforceAggregateBom",
        defaultPhase = LifecyclePhase.PACKAGE,
        aggregator = true,
        requiresOnline = true,
        requiresDependencyCollection = ResolutionScope.TEST,
        requiresDependencyResolution = ResolutionScope.TEST
)
public class CycloneDxAggregateEnforceMojo extends CycloneDxAggregateMojo {

    @Override
    protected boolean shouldExclude(@NotNull MavenProject mavenProject) {
        if (super.shouldExclude(mavenProject)) {
            return true;
        }
        if (enforceExcludeArtifactId != null && enforceExcludeArtifactId.length > 0) {
            if (Arrays.asList(enforceExcludeArtifactId).contains(mavenProject.getArtifactId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    @NotNull
    protected Bom postProcessingBom(@NotNull Bom bom) throws MojoExecutionException {
        bom = super.postProcessingBom(bom);
        doEnforceComponentsSameVersion(bom);
        doEnforceLicensesBlackListAndWhiteList(bom);
        return bom;
    }

    private void doEnforceComponentsSameVersion(@NotNull Bom bom) throws MojoExecutionException {
        if (this.enforceComponentsSameVersion) {
            List<Component> components = bom.getComponents();
            if (components != null) {
                Map<Pair<String, String>, Set<String>> componentMap =
                        new HashMap<>((int) Math.ceil(components.size() / 0.75));
                for (Component component : components) {
                    if (component == null) {
                        continue;
                    }
                    String group = component.getGroup();
                    String name = component.getName();
                    String version = component.getVersion();
                    Pair<String, String> key = Pair.of(group, name);
                    Set<String> versions = componentMap.computeIfAbsent(
                            key,
                            stringStringPair -> new HashSet<>()
                    );
                    versions.add(version);
                }
                StringBuilder stringBuilder = new StringBuilder();
                for (Map.Entry<Pair<String, String>, Set<String>> entry : componentMap.entrySet()) {
                    Pair<String, String> key = entry.getKey();
                    Set<String> versions = entry.getValue();
                    if (versions.size() > 1) {
                        stringBuilder
                                .append("[ERROR]Duplicated versions for ")
                                .append(key.getLeft())
                                .append(":")
                                .append(key.getRight())
                                .append(" , versions : ")
                                .append(StringUtils.join(versions.iterator(), ","))
                                .append("\n");
                    }
                }
                if (stringBuilder.length() > 0) {
                    throw new MojoExecutionException(stringBuilder.toString());
                }
            }
        }
    }

    private void doEnforceLicensesBlackListAndWhiteList(@NotNull Bom bom) throws MojoExecutionException {
        List<Component> components = bom.getComponents();
        if (components != null) {
            StringBuilder stringBuilder = new StringBuilder();
            for (Component component : components) {
                if (component == null) {
                    continue;
                }
                String group = component.getGroup();
                String name = component.getName();
                LicenseChoice licenseChoice = component.getLicenseChoice();
                if (licenseChoice == null) {
                    continue;
                }
                if (StringUtils.isNotBlank(licenseChoice.getExpression())) {
                    getLog().error("[ERROR]Cannot handle spdx license expression for " + group + ":" + name + " , use license id instead");
                }
                if (licenseChoice.getLicenses() != null) {
                    for (License license : licenseChoice.getLicenses()) {
                        if (!ArrayUtils.isEmpty(this.enforceLicensesBlackList)) {
                            if (
                                    ArrayUtils.contains(this.enforceLicensesBlackList, license.getId())
                                            || ArrayUtils.contains(this.enforceLicensesBlackList, license.getName())
                            ) {
                                stringBuilder
                                        .append("[ERROR]License in blackList for ")
                                        .append(group)
                                        .append(":")
                                        .append(name)
                                        .append(" , license : ")
                                        .append(license.getId())
                                        .append("\n");
                            }
                        }
                        if (!ArrayUtils.isEmpty(this.enforceLicensesWhiteList)) {
                            if (
                                    !(
                                            ArrayUtils.contains(this.enforceLicensesBlackList, license.getId())
                                                    || ArrayUtils.contains(this.enforceLicensesBlackList, license.getName())
                                    )
                            ) {
                                stringBuilder
                                        .append("[ERROR]License not in whiteList for ")
                                        .append(group)
                                        .append(":")
                                        .append(name)
                                        .append(" , license : ")
                                        .append(license.getId())
                                        .append("\n");
                            }
                        }
                    }
                }
            }
            if (stringBuilder.length() > 0) {
                throw new MojoExecutionException(stringBuilder.toString());
            }
        }
    }

}
