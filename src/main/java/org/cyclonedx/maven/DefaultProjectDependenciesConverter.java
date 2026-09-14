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

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.internal.PluginDependenciesResolver;
import org.apache.maven.plugin.version.DefaultPluginVersionRequest;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.plugin.version.PluginVersionResolver;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilderException;
import org.apache.maven.shared.dependency.graph.internal.ConflictData;
import org.apache.maven.shared.dependency.graph.internal.DefaultDependencyCollectorBuilder;
import org.cyclonedx.Version;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.Metadata;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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

    @Inject
    private PluginDependenciesResolver pluginDependenciesResolver;

    @Inject
    private PluginVersionResolver pluginVersionResolver;

    private Set<String> excludeTypesSet;
    private MavenDependencyScopes include;

    @Override
    public BomDependencies extractBOMDependencies(MavenProject mavenProject, MavenDependencyScopes include, String[] excludeTypes) throws MojoExecutionException {
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
        } catch (DependencyCollectorBuilderException e) {
            // When executing makeAggregateBom, some projects may not yet be built. Workaround is to warn on this
            // rather than throwing an exception https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/55
            logger.warn("An error occurred building dependency graph: " + e.getMessage());
        }
        return new BomDependencies(dependencies, mavenArtifacts, mavenDependencyArtifacts);
    }

    @Override
    public void extractMavenPluginDependencies(
            final MavenProject mavenProject,
            final Version schemaVersion,
            final boolean includeLicenseText,
            final Map<String, Component> components,
            final Map<String, Dependency> dependencies) throws MojoExecutionException {

        final Map<String, Plugin> plugins = new HashMap<>();
        // include pluginManagement/plugins
        if (mavenProject.getPluginManagement() != null) {
            for (final Plugin plugin : mavenProject.getPluginManagement().getPlugins()) {
                normalizePlugin(plugin);
                plugins.put(plugin.getKey(), plugin);
            }
        }
        // include build/plugins
        for (final Plugin plugin : mavenProject.getBuildPlugins()) {
            addPlugin(plugin, plugins);
        }
        // include reporting/plugins
        for (final ReportPlugin reportPlugin : mavenProject.getReportPlugins()) {
            final Plugin plugin = new Plugin();
            plugin.setGroupId(reportPlugin.getGroupId());
            plugin.setArtifactId(reportPlugin.getArtifactId());
            plugin.setVersion(reportPlugin.getVersion());
            addPlugin(plugin, plugins);
        }
        // resolve plugins without a version
        for (final Plugin plugin : plugins.values()) {
            if (!hasVersion(plugin)) {
                resolvePluginVersion(plugin, mavenProject);
            }
        }
        final String projectRef =
                modelConverter.generatePackageUrl(mavenProject.getArtifact());
        final Dependency projectDependency =
                dependencies.computeIfAbsent(projectRef, Dependency::new);
        final List<RemoteRepository> repositories =
                mavenProject.getRemotePluginRepositories();
        for (final Plugin plugin : plugins.values()) {
            try {
                final DependencyNode root =
                        pluginDependenciesResolver.resolve(
                                plugin,
                                null,
                                null,
                                repositories,
                                session.getRepositorySession());
                final String pluginRef = buildPluginDependencyGraph(
                        root,
                        null,
                        components,
                        dependencies,
                        schemaVersion,
                        includeLicenseText,
                        new HashSet<String>());
                if (pluginRef != null && !containsDependency(projectDependency, pluginRef)) {
                    projectDependency.addDependency(new Dependency(pluginRef));
                }
            } catch (PluginResolutionException e) {
                throw new MojoExecutionException(
                        "failed to resolve Maven plugin "
                                + plugin.getKey() + ":" + plugin.getVersion()
                                + ": " + e.getMessage());
            }
        }
    }

    private static void addPlugin(Plugin plugin, Map<String, Plugin> plugins) {
        normalizePlugin(plugin);
        final String key = plugin.getKey();
        final Plugin existingPlugin = plugins.get(key);
        if (!hasVersion(plugin)) {
            if (null == existingPlugin) {
                plugins.put(key, plugin);
            }
        } else {
            if (null != existingPlugin) {
                existingPlugin.setVersion(plugin.getVersion());
            } else {
                plugins.put(key, plugin);
            }
        }
    }

    private void resolvePluginVersion(
            final Plugin plugin,
            final MavenProject mavenProject) throws MojoExecutionException {
        try {
            final DefaultPluginVersionRequest request =
                    new DefaultPluginVersionRequest(
                            plugin,
                            session.getRepositorySession(),
                            mavenProject.getRemotePluginRepositories());

            plugin.setVersion(pluginVersionResolver.resolve(request).getVersion());
        } catch (PluginVersionResolutionException e) {
            throw new MojoExecutionException(
                    "failed to resolve Maven plugin " + plugin.getKey()
                            + ": " + e.getMessage(),
                    e);
        }
    }

    private static boolean hasVersion(Plugin plugin) {
        return plugin.getVersion() != null && !plugin.getVersion().trim().isEmpty();
    }

    private static void normalizePlugin(final Plugin plugin) {
        if (plugin.getGroupId() == null || plugin.getGroupId().trim().isEmpty()) {
            plugin.setGroupId("org.apache.maven.plugins");
        }
    }

    private String buildPluginDependencyGraph(
            final DependencyNode node,
            final Dependency parent,
            final Map<String, Component> components,
            final Map<String, Dependency> dependencies,
            final Version schemaVersion,
            final boolean includeLicenseText,
            final Set<String> visiting) {
        if (node == null || node.getArtifact() == null) {
            return null;
        }
        final String purl = modelConverter.generatePackageUrl(node.getArtifact());
        if (purl == null) {
            return null;
        }
        if (!components.containsKey(purl)) {
            final Artifact artifact = RepositoryUtils.toArtifact(node.getArtifact());
            components.put(
                    purl,
                    modelConverter.convertMavenDependency(
                            artifact,
                            schemaVersion,
                            includeLicenseText));
        }
        final Dependency current =
                dependencies.computeIfAbsent(purl, Dependency::new);
        if (parent != null && !containsDependency(parent, purl)) {
            parent.addDependency(new Dependency(purl));
        }
        if (!visiting.add(purl)) {
            return purl;
        }
        for (final DependencyNode child : node.getChildren()) {
            buildPluginDependencyGraph(
                    child,
                    current,
                    components,
                    dependencies,
                    schemaVersion,
                    includeLicenseText,
                    visiting);
        }
        visiting.remove(purl);
        return purl;
    }

    private static boolean containsDependency(
            final Dependency dependency,
            final String ref) {
        if (dependency.getDependencies() == null) {
            return false;
        }
        for (final Dependency child : dependency.getDependencies()) {
            if (ref.equals(child.getRef())) {
                return true;
            }
        }
        return false;
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
}
