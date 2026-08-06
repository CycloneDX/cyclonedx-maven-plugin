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
import org.apache.maven.shared.dependency.graph.internal.ConflictData;
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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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
    private MavenDependencyScopes include;

    @Override
    public BomDependencies extractBOMDependencies(MavenProject mavenProject, MavenDependencyScopes include, String[] excludeTypes) throws MojoExecutionException {
        return extractBOMDependencies(mavenProject, include, excludeTypes, false);
    }

    @Override
    public BomDependencies extractBOMDependencies(MavenProject mavenProject, MavenDependencyScopes include, String[] excludeTypes, boolean preserveParentReferences) throws MojoExecutionException {
        this.include = include;
        excludeTypesSet = new HashSet<>(Arrays.asList(excludeTypes));

        final ProjectBuildingRequest buildingRequest = getProjectBuildingRequest(mavenProject);

        final Map<String, Dependency> dependencies = new LinkedHashMap<>();
        final Map<String, Artifact> mavenArtifacts = new LinkedHashMap<>();
        final Map<String, Artifact> mavenDependencyArtifacts = new LinkedHashMap<>();
        try {
            final DelegatingRepositorySystem delegateRepositorySystem = new DelegatingRepositorySystem(aetherRepositorySystem);
            final DependencyCollectorBuilder dependencyCollectorBuilder = new DefaultDependencyCollectorBuilder(delegateRepositorySystem);

            final org.apache.maven.shared.dependency.graph.DependencyNode mavenRoot = dependencyCollectorBuilder.collectDependencyGraph(buildingRequest, null);
            populateArtifactMap(mavenArtifacts, mavenDependencyArtifacts, mavenRoot, 0);

            final CollectResult collectResult = delegateRepositorySystem.getCollectResult();
            if (collectResult == null) {
                throw new MojoExecutionException("Failed to generate aether dependency graph");
            }
            final DependencyNode root = collectResult.getRoot();

            // Generate the tree, removing excluded and filtered nodes
            final Set<String> loggedFilteredArtifacts = new HashSet<>();

            buildDependencyGraphNode(dependencies, root, null, null, loggedFilteredArtifacts);

            // Add parent POM as a dependency if preserveParentReferences is enabled
            if (preserveParentReferences) {
                addParentDependency(mavenProject, dependencies, mavenArtifacts);
            }
        } catch (DependencyCollectorBuilderException e) {
            // When executing makeAggregateBom, some projects may not yet be built. Workaround is to warn on this
            // rather than throwing an exception https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/55
            logger.warn("An error occurred building dependency graph: " + e.getMessage());
        }
        return new BomDependencies(dependencies, mavenArtifacts, mavenDependencyArtifacts);
    }

    private void populateArtifactMap(final Map<String, Artifact> artifactMap, final Map<String, Artifact> dependencyArtifactMap, final org.apache.maven.shared.dependency.graph.DependencyNode node, final int level) {
        final ConflictData conflictData = getConflictData(node);
        if ((conflictData != null) && (conflictData.getWinnerVersion() != null)) {
            return;
        }

        final Artifact artifact = node.getArtifact();
        final String purl = modelConverter.generatePackageUrl(artifact);
        if (level > 0) {
            artifactMap.putIfAbsent(purl, artifact);
        }
        if (level == 1) {
            dependencyArtifactMap.putIfAbsent(purl, artifact);
        }

        final int childLevel = level + 1;
        for (org.apache.maven.shared.dependency.graph.DependencyNode child: node.getChildren()) {
            populateArtifactMap(artifactMap, dependencyArtifactMap, child, childLevel);
        }
    }

    private ConflictData getConflictData(final org.apache.maven.shared.dependency.graph.DependencyNode node) {
        if (!node.getChildren().isEmpty()) {
            return null;
        }
        final Field field ;
        try {
            field = node.getClass().getDeclaredField("data");
        } catch (final NoSuchFieldException nsfe) {
            return null;
        }
        field.setAccessible(true);
        try {
            return (ConflictData)field.get(node);
        } catch (final IllegalAccessException iae) {
            return null;
        }
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
        if (result && logger.isDebugEnabled()) {
            final String purl = modelConverter.generatePackageUrl(node.getArtifact());
            final String key = purl + ":" + originalScope + ":" + node.getDependency().getScope();
            if (loggedFilteredArtifacts.add(key)) {
                logger.debug("Filtering " + purl + " with original scope " + originalScope + " and scope " + node.getDependency().getScope());
            }
        }
        return result;
    }

    private boolean isExcludedNode(final DependencyNode node) {
        final String type = node.getArtifact().getProperties().get(ArtifactProperties.TYPE);
        return ((type == null) || excludeTypesSet.contains(type));
    }

    private void buildDependencyGraphNode(final Map<String, Dependency> dependencies, DependencyNode node,
            final Dependency parent, final String parentClassifierlessPUrl, final Set<String> loggedFilteredArtifacts) {

        if (isExcludedNode(node) || (parent != null && isFilteredNode(node, loggedFilteredArtifacts))) {
            return;
        }

        // If the node has no children then it could be a marker node for conflict resolution
        if (node.getChildren().isEmpty()) {
            final Map<?,?> nodeData = node.getData();
            final DependencyNode winner = (DependencyNode) nodeData.get(ConflictResolver.NODE_DATA_WINNER);
            if (winner != null) {
                node = winner;
            }
        }

        String purl = modelConverter.generatePackageUrl(node.getArtifact());
        if (!dependencies.containsKey(purl)) {
            Dependency topDependency = new Dependency(purl);
            dependencies.put(purl, topDependency);
            final String nodeClassifierlessPUrl = modelConverter.generateClassifierlessPackageUrl(node.getArtifact());
            if (!nodeClassifierlessPUrl.equals(parentClassifierlessPUrl)) {
                for (final DependencyNode childrenNode : node.getChildren()) {
                    buildDependencyGraphNode(dependencies, childrenNode, topDependency, nodeClassifierlessPUrl, loggedFilteredArtifacts);
                }
            }
        }

        if (parent != null) {
            parent.addDependency(new Dependency(purl));
        }
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
    public void cleanupBomDependencies(Metadata metadata, Map<String, Component> components, Map<String, Dependency> dependencies) {
        // set(dependencies refs) and set(dependencies of dependencies)
        final Set<String> dependsOns = new HashSet<>();
        dependencies.values().forEach(d -> {
            if (d.getDependencies() != null) {
                d.getDependencies().forEach(on -> dependsOns.add(on.getRef()));
            }
        });

        // Check all BOM components have an associated BOM dependency

        final List<String> notDepended = new ArrayList<>();
        for (Iterator<Map.Entry<String, Component>> it = components.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Component> entry = it.next();
            if (!dependencies.containsKey(entry.getKey())) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Component reference not listed in dependencies, pruning from bom components: " + entry.getKey());
                }
                it.remove();
            } else if (!dependsOns.contains(entry.getKey())) {
                notDepended.add(entry.getKey());
            }
        }

        notDepended.stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(dependency -> logger.warn("BOM dependency listed but is not depended upon: " + dependency));

        // include BOM main component
        Component main = metadata.getComponent();
        final String mainBomRef = main.getBomRef();

        // Check all BOM dependencies have a BOM component
        for (String dependencyRef: dependencies.keySet()) {
            if (!mainBomRef.equals(dependencyRef) && !components.containsKey(dependencyRef)) {
                logger.warn("Dependency missing component entry: " + dependencyRef);
            }
        }
    }

    /**
     * Adds the parent POM as a direct dependency if it exists, and reorganizes dependencies
     * to show which dependencies come from the parent vs. directly from the project.
     * This method recursively processes the entire parent chain.
     *
     * @param mavenProject the current Maven project
     * @param dependencies the dependencies map to update
     * @param mavenArtifacts the artifacts map to update
     */
    private void addParentDependency(MavenProject mavenProject, Map<String, Dependency> dependencies, Map<String, Artifact> mavenArtifacts) {
        final Artifact projectArtifact = mavenProject.getArtifact();
        
        if (projectArtifact == null) {
            logger.debug("Project artifact is null, skipping parent dependency processing");
            return;
        }
        
        // Process parent chain recursively
        addParentDependencyRecursive(projectArtifact, mavenProject, dependencies, mavenArtifacts, true);
    }
    
    /**
     * Recursively adds parent dependencies for an artifact and its parent chain.
     *
     * @param childArtifact the artifact whose parent to add
     * @param mavenProject the Maven project (for getting direct dependencies of the main project)
     * @param dependencies the dependencies map to update
     * @param mavenArtifacts the artifacts map to update
     * @param isMainProject whether this is the main project artifact (vs a parent in the chain)
     */
    private void addParentDependencyRecursive(Artifact childArtifact, MavenProject mavenProject, 
                                              Map<String, Dependency> dependencies, Map<String, Artifact> mavenArtifacts,
                                              boolean isMainProject) {
        if (!modelConverter.hasParentPom(childArtifact)) {
            return;
        }
        
        final Artifact parentArtifact = modelConverter.getParentArtifact(childArtifact);
        if (parentArtifact == null) {
            return;
        }
        
        final String parentPurl = modelConverter.generatePackageUrl(parentArtifact);
        final String childPurl = modelConverter.generatePackageUrl(childArtifact);
        
        if (parentPurl == null || childPurl == null) {
            logger.debug("Could not generate pURL for parent or child, skipping");
            return;
        }
        
        // Add parent to artifacts map
        mavenArtifacts.putIfAbsent(parentPurl, parentArtifact);
        
        // Create dependency entry for parent if it doesn't exist
        Dependency parentDependency = dependencies.get(parentPurl);
        if (parentDependency == null) {
            parentDependency = new Dependency(parentPurl);
            dependencies.put(parentPurl, parentDependency);
        }
        
        // Get or create the child's dependency entry
        Dependency childDependency = dependencies.get(childPurl);
        if (childDependency == null) {
            childDependency = new Dependency(childPurl);
            dependencies.put(childPurl, childDependency);
        }
        
        // Only reorganize dependencies for the main project artifact
        if (isMainProject) {
            // Get the directly declared dependencies from the original (non-effective) POM
            final Set<String> directDependencies = getDirectDependencies(mavenProject);
            
            // Reorganize dependencies: move inherited dependencies under parent
            if (childDependency.getDependencies() != null) {
                final List<Dependency> childDeps = new ArrayList<>(childDependency.getDependencies());
                childDependency.getDependencies().clear();
                
                for (Dependency dep : childDeps) {
                    final String depRef = dep.getRef();
                    if (directDependencies.contains(depRef)) {
                        // This is a direct dependency - keep it under the child
                        childDependency.addDependency(dep);
                    } else {
                        // This is an inherited dependency - move it under the parent
                        parentDependency.addDependency(dep);
                    }
                }
            }
        } else {
            // For parent POMs (not the main project), populate with their declared dependencies
            final Set<String> parentDirectDeps = getDirectDependenciesFromArtifact(parentArtifact);
            for (String depPurl : parentDirectDeps) {
                parentDependency.addDependency(new Dependency(depPurl));
            }
        }
        
        // Add parent as a direct dependency of the child
        childDependency.addDependency(new Dependency(parentPurl));
        
        logger.debug("Added parent POM dependency: " + parentPurl + " for artifact: " + childPurl);
        
        // Recursively process the parent's parent (grandparent, etc.)
        addParentDependencyRecursive(parentArtifact, mavenProject, dependencies, mavenArtifacts, false);
    }

    /**
     * Gets the set of direct dependencies declared in the original (non-effective) POM.
     *
     * @param mavenProject the Maven project
     * @return set of package URLs for direct dependencies
     */
    private Set<String> getDirectDependencies(MavenProject mavenProject) {
        final Set<String> directDeps = new HashSet<>();
        
        // Get dependencies from the original model (not the effective model)
        if (mavenProject.getOriginalModel() != null && mavenProject.getOriginalModel().getDependencies() != null) {
            for (org.apache.maven.model.Dependency dep : mavenProject.getOriginalModel().getDependencies()) {
                try {
                    // Skip if essential coordinates are missing
                    if (dep.getGroupId() == null || dep.getArtifactId() == null) {
                        continue;
                    }
                    
                    // Version might be null if inherited from dependencyManagement
                    // We need to resolve it from the effective model
                    String version = dep.getVersion();
                    if (version == null) {
                        // Try to find the version from the effective dependencies
                        for (org.apache.maven.model.Dependency effectiveDep : mavenProject.getDependencies()) {
                            if (dep.getGroupId().equals(effectiveDep.getGroupId()) && 
                                dep.getArtifactId().equals(effectiveDep.getArtifactId())) {
                                version = effectiveDep.getVersion();
                                break;
                            }
                        }
                    }
                    
                    // If we still don't have a version, skip this dependency
                    if (version == null) {
                        logger.debug("Skipping dependency without version: " + dep.getGroupId() + ":" + dep.getArtifactId());
                        continue;
                    }
                    
                    // Create an artifact to generate the pURL
                    final org.apache.maven.artifact.DefaultArtifact artifact = new org.apache.maven.artifact.DefaultArtifact(
                        dep.getGroupId(),
                        dep.getArtifactId(),
                        version,
                        dep.getScope(),
                        dep.getType() != null ? dep.getType() : "jar",
                        dep.getClassifier(),
                        new org.apache.maven.artifact.handler.DefaultArtifactHandler(dep.getType() != null ? dep.getType() : "jar")
                    );
                    final String purl = modelConverter.generatePackageUrl(artifact);
                    if (purl != null) {
                        directDeps.add(purl);
                    }
                } catch (Exception e) {
                    logger.warn("Error processing dependency " + dep.getGroupId() + ":" + dep.getArtifactId(), e);
                }
            }
        }
        
        return directDeps;
    }

    /**
     * Gets the set of direct dependencies declared in a parent artifact's POM.
     *
     * @param artifact the artifact whose dependencies to extract
     * @return set of package URLs for direct dependencies
     */
    private Set<String> getDirectDependenciesFromArtifact(org.apache.maven.artifact.Artifact artifact) {
        return modelConverter.getDirectDependencyPurls(artifact);
    }
}
