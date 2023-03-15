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
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilderException;
import org.apache.maven.shared.dependency.graph.internal.DefaultDependencyCollectorBuilder;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.Metadata;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class DefaultProjectDependenciesConverter implements ProjectDependenciesConverter {
    private final Logger logger = LoggerFactory.getLogger(DefaultModelConverter.class);

    @Inject
    private MavenSession session;

    @Inject
    private ModelConverter modelConverter;

    @Inject
    private RepositorySystem aetherRepositorySystem;

    private Set<String> excludeTypesSet;
    private Scopes include;

    @Override
    public Set<Dependency> extractBOMDependencies(MavenProject mavenProject, Scopes include, String[] excludeTypes) throws MojoExecutionException {
        this.include = include;
        excludeTypesSet = new HashSet<>(Arrays.asList(excludeTypes));

        final ProjectBuildingRequest buildingRequest = getProjectBuildingRequest(mavenProject);

        final Map<String, String> resolvedPUrls = generateResolvedPUrls(mavenProject);

        final Map<Dependency, Dependency> dependencies = new LinkedHashMap<>();
        try {
            final DelegatingRepositorySystem delegateRepositorySystem = new DelegatingRepositorySystem(aetherRepositorySystem);
            final DependencyCollectorBuilder dependencyCollectorBuilder = new DefaultDependencyCollectorBuilder(delegateRepositorySystem);
            dependencyCollectorBuilder.collectDependencyGraph(buildingRequest, null);
            final CollectResult collectResult = delegateRepositorySystem.getCollectResult();
            if (collectResult == null) {
                throw new MojoExecutionException("Failed to generate aether dependency graph");
            }
            final DependencyNode root = collectResult.getRoot();

            // Generate the tree, removing excluded and filtered nodes
            final Set<String> loggedReplacementPUrls = new HashSet<>();
            final Set<String> loggedFilteredArtifacts = new HashSet<>();

            buildDependencyGraphNode(dependencies, root, null, null, resolvedPUrls, loggedReplacementPUrls, loggedFilteredArtifacts);
        } catch (DependencyCollectorBuilderException e) {
            // When executing makeAggregateBom, some projects may not yet be built. Workaround is to warn on this
            // rather than throwing an exception https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/55
            logger.warn("An error occurred building dependency graph: " + e.getMessage());
        }
        return dependencies.keySet();
    }

    private boolean isFilteredNode(final DependencyNode node, final Set<String> loggedFilteredArtifacts) {
        final Map<?, ?> nodeData = node.getData();
        final String originalScope = (String)nodeData.get(ConflictResolver.NODE_DATA_ORIGINAL_SCOPE);
        final String scope;
        if (originalScope != null) {
            scope = originalScope;
        } else {
            scope = node.getDependency().getScope();
        }

        final Boolean scoped ;
        switch (scope) {
            case Artifact.SCOPE_COMPILE:
                scoped = include.compile;
                break;
            case Artifact.SCOPE_PROVIDED:
                scoped = include.provided;
                break;
            case Artifact.SCOPE_RUNTIME:
                scoped = include.runtime;
                break;
            case Artifact.SCOPE_SYSTEM:
                scoped = include.system;
                break;
            case Artifact.SCOPE_TEST:
                scoped = include.test;
                break;
            default:
                scoped = Boolean.FALSE;
        }
        final boolean result = Boolean.FALSE.equals(scoped);
        if (result) {
            final String purl = modelConverter.generatePackageUrl(node.getArtifact());
            final String key = purl + ":" + originalScope + ":" + node.getDependency().getScope();
            if (loggedFilteredArtifacts.add(key) && logger.isDebugEnabled()) {
                logger.debug("Filtering " + purl + " with original scope " + originalScope + " and scope " + node.getDependency().getScope());
            }
        }
        return result;
    }

    private boolean isExcludedNode(final DependencyNode node) {
        final String type = node.getArtifact().getProperties().get(ArtifactProperties.TYPE);
        return ((type == null) || excludeTypesSet.contains(type));
    }

    private void buildDependencyGraphNode(final Map<Dependency, Dependency> dependencies, DependencyNode node,
            final Dependency parent, final String parentClassifierlessPUrl, final Map<String, String> resolvedPUrls,
            final Set<String> loggedReplacementPUrls, final Set<String> loggedFilteredArtifacts) {
        String purl = modelConverter.generatePackageUrl(node.getArtifact());

        if (isExcludedNode(node) || (parent != null && isFilteredNode(node, loggedFilteredArtifacts))) {
            return;
        }

        // If the node has no children then it could be a marker node for conflict resolution
        if (node.getChildren().isEmpty()) {
            final Map<?,?> nodeData = node.getData();
            final DependencyNode winner = (DependencyNode) nodeData.get(ConflictResolver.NODE_DATA_WINNER);
            final String resolvedPurl = resolvedPUrls.get(modelConverter.generateVersionlessPackageUrl(node.getArtifact()));
            if (!purl.equals(resolvedPurl)) {
                if (!loggedReplacementPUrls.contains(purl)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Replacing reference to " + purl + " with resolved package url " + resolvedPurl);
                    }
                    loggedReplacementPUrls.add(purl);
                }
                purl = resolvedPurl;
            }
            if (winner != null) {
                node = winner;
            }
        }

        Dependency topDependency = new Dependency(purl);
        final Dependency origDependency = dependencies.putIfAbsent(topDependency, topDependency);
        if (origDependency != null) {
            topDependency = origDependency;
        }
        if (parent != null) {
            parent.addDependency(new Dependency(purl));
        }

        final String nodeClassifierlessPUrl = modelConverter.generateClassifierlessPackageUrl(node.getArtifact());
        if (!nodeClassifierlessPUrl.equals(parentClassifierlessPUrl)) {
            for (final DependencyNode childrenNode : node.getChildren()) {
                buildDependencyGraphNode(dependencies, childrenNode, topDependency, nodeClassifierlessPUrl, resolvedPUrls, loggedReplacementPUrls, loggedFilteredArtifacts);
            }
        }
    }

    /**
     * Generate a map of versionless purls to their resolved versioned purl
     * @return the map of versionless purls to resolved versioned purls
     */
    private Map<String, String> generateResolvedPUrls(final MavenProject mavenProject) {
        final Map<String, String> resolvedPUrls = new HashMap<>();
        final Artifact projectArtifact = mavenProject.getArtifact();
        resolvedPUrls.put(modelConverter.generateVersionlessPackageUrl(projectArtifact), modelConverter.generatePackageUrl(projectArtifact));
        for (Artifact artifact: mavenProject.getArtifacts()) {
            resolvedPUrls.put(modelConverter.generateVersionlessPackageUrl(artifact), modelConverter.generatePackageUrl(artifact));
        }
        return resolvedPUrls;
    }

    /**
     * Create a project building request
     * @param mavenProject The maven project associated with this build request
     * @return The project building request
     */
    private ProjectBuildingRequest getProjectBuildingRequest(final MavenProject mavenProject) {
        final ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setProject(mavenProject);
        return buildingRequest;
    }

    @Override
    public void cleanupBomDependencies(Metadata metadata, Set<Component> components, Set<Dependency> dependencies) {
        // map(component ref -> component)
        final Map<String, Component> componentRefs = new HashMap<>();
        components.forEach(c -> componentRefs.put(c.getBomRef(), c));

        // set(dependencies refs) and set(dependencies of dependencies)
        final Set<String> dependencyRefs = new HashSet<>();
        final Set<String> dependsOns = new HashSet<>();
        dependencies.forEach(d -> {
            dependencyRefs.add(d.getRef());
            if (d.getDependencies() != null) {
                d.getDependencies().forEach(on -> dependsOns.add(on.getRef()));
            }
        });

        // Check all BOM components have an associated BOM dependency
        for (Map.Entry<String, Component> entry: componentRefs.entrySet()) {
            if (!dependencyRefs.contains(entry.getKey())) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Component reference not listed in dependencies, pruning from bom components: " + entry.getKey());
                }
                components.remove(entry.getValue());
            } else if (!dependsOns.contains(entry.getKey())) {
                logger.warn("BOM dependency listed but is not depended upon: " + entry.getKey());
            }
        }

        // add BOM main component
        Component main = metadata.getComponent();
        componentRefs.put(main.getBomRef(), main);

        // Check all BOM dependencies have a BOM component
        for (String dependencyRef: dependencyRefs) {
            if (!componentRefs.containsKey(dependencyRef)) {
                logger.warn("Dependency missing component entry: " + dependencyRef);
            }
        }
    }
}
