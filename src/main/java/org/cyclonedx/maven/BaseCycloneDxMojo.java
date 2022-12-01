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

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.CumulativeScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.CollectingDependencyNodeVisitor;
import org.cyclonedx.BomGeneratorFactory;
import org.cyclonedx.CycloneDxSchema;
import org.cyclonedx.exception.GeneratorException;
import org.cyclonedx.generators.json.BomJsonGenerator;
import org.cyclonedx.generators.xml.BomXmlGenerator;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.Metadata;
import org.cyclonedx.parsers.JsonParser;
import org.cyclonedx.parsers.Parser;
import org.cyclonedx.parsers.XmlParser;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;

public abstract class BaseCycloneDxMojo extends AbstractMojo {

    @Parameter(property = "session", readonly = true, required = true)
    private MavenSession session;

    @Parameter(property = "project", readonly = true, required = true)
    private MavenProject project;

    /**
     * The component type associated to the SBOM metadata. See
     * <a href="https://cyclonedx.org/docs/1.4/json/#metadata_component_type">CycloneDX reference</a> for supported
     * values. 
     */
    @Parameter(property = "projectType", defaultValue = "library", required = false)
    private String projectType;

    /**
     * The CycloneDX schema version the BOM will comply with.
     *
     * @since 2.1.0
     */
    @Parameter(property = "schemaVersion", defaultValue = "1.4", required = false)
    private String schemaVersion;

    /**
     * The CycloneDX output format that should be generated (<code>xml</code>, <code>json</code> or <code>all</code>).
     *
     * @since 2.1.0
     */
    @Parameter(property = "outputFormat", defaultValue = "all", required = false)
    private String outputFormat;

    /**
     * The CycloneDX output file name (without extension) that should be generated (in {@code target/} directory).
     *
     * @since 2.2.0
     */
    @Parameter(property = "outputName", defaultValue = "bom", required = false)
    private String outputName;

    /**
     * Should the resulting BOM contain a unique serial number?
     *
     * @since 2.1.0
     */
    @Parameter(property = "includeBomSerialNumber", defaultValue = "true", required = false)
    private Boolean includeBomSerialNumber;

    /**
     * Should compile scoped artifacts be included in bom?
     *
     * @since 2.1.0
     */
    @Parameter(property = "includeCompileScope", defaultValue = "true", required = false)
    private Boolean includeCompileScope;

    /**
     * Should provided scoped artifacts be included in bom?
     *
     * @since 2.1.0
     */
    @Parameter(property = "includeProvidedScope", defaultValue = "true", required = false)
    private Boolean includeProvidedScope;

    /**
     * Should runtime scoped artifacts be included in bom?
     *
     * @since 2.1.0
     */
    @Parameter(property = "includeRuntimeScope", defaultValue = "true", required = false)
    private Boolean includeRuntimeScope;

    /**
     * Should test scoped artifacts be included in bom?
     *
     * @since 2.1.0
     */
    @Parameter(property = "includeTestScope", defaultValue = "false", required = false)
    private Boolean includeTestScope;

    /**
     * Should system scoped artifacts be included in bom?
     *
     * @since 2.1.0
     */
    @Parameter(property = "includeSystemScope", defaultValue = "true", required = false)
    private Boolean includeSystemScope;

    /**
     * Should license text be included in bom?
     *
     * @since 2.1.0
     */
    @Parameter(property = "includeLicenseText", defaultValue = "false", required = false)
    private Boolean includeLicenseText;

    /**
     * Excluded types.
     *
     * @since 2.1.0
     */
    @Parameter(property = "excludeTypes", required = false)
    private String[] excludeTypes;

    /**
     * Skip CycloneDX execution.
     */
    @SuppressWarnings("CanBeFinal")
    @Parameter(property = "cyclonedx.skip", defaultValue = "false", required = false)
    private boolean skip = false;

    /**
     * Don't attach bom.
     *
     * @since 2.1.0
     */
    @SuppressWarnings("CanBeFinal")
    @Parameter(property = "cyclonedx.skipAttach", defaultValue = "false", required = false)
    private boolean skipAttach = false;

    /**
     * Verbose output.
     *
     * @since 2.6.0
     */
    @SuppressWarnings("CanBeFinal")
    @Parameter(property = "cyclonedx.verbose", defaultValue = "true", required = false)
    private boolean verbose = true;

    @org.apache.maven.plugins.annotations.Component
    private MavenProjectHelper mavenProjectHelper;

    @org.apache.maven.plugins.annotations.Component
    private DependencyCollectorBuilder dependencyCollectorBuilder;

    @org.apache.maven.plugins.annotations.Component
    private ModelConverter modelConverter;

    /**
     * Various messages sent to console.
     */
    protected static final String MESSAGE_RESOLVING_DEPS = "CycloneDX: Resolving Dependencies";
    protected static final String MESSAGE_RESOLVING_AGGREGATED_DEPS = "CycloneDX: Resolving Aggregated Dependencies";
    protected static final String MESSAGE_CREATING_BOM = "CycloneDX: Creating BOM";
    static final String MESSAGE_CALCULATING_HASHES = "CycloneDX: Calculating Hashes";
    protected static final String MESSAGE_WRITING_BOM = "CycloneDX: Writing BOM (%s): %s";
    protected static final String MESSAGE_VALIDATING_BOM = "CycloneDX: Validating BOM (%s): %s";
    protected static final String MESSAGE_VALIDATION_FAILURE = "The BOM does not conform to the CycloneDX BOM standard as defined by the XSD";

    /**
     * Returns a reference to the current project.
     *
     * @return returns a reference to the current project
     */
    protected MavenProject getProject() {
        return project;
    }

    protected String generatePackageUrl(Artifact artifact) {
        return modelConverter.generatePackageUrl(artifact);
    }

    protected Component convert(Artifact artifact) {
        return modelConverter.convert(artifact, schemaVersion(), includeLicenseText);
    }

    protected boolean shouldInclude(Artifact artifact) {
        if (artifact.getScope() == null) {
            return false;
        }
        if (excludeTypes != null) {
            final boolean shouldExclude = Arrays.asList(excludeTypes).contains(artifact.getType());
            if (shouldExclude) {
                return false;
            }
        }
        if (includeCompileScope && "compile".equals(artifact.getScope())) {
            return true;
        } else if (includeProvidedScope && "provided".equals(artifact.getScope())) {
            return true;
        } else if (includeRuntimeScope && "runtime".equals(artifact.getScope())) {
            return true;
        } else if (includeTestScope && "test".equals(artifact.getScope())) {
            return true;
        } else if (includeSystemScope && "system".equals(artifact.getScope())) {
            return true;
        }
        return false;
    }

    protected abstract boolean analyze(Set<Component> components, Set<Dependency> dependencies) throws MojoExecutionException;

    public void execute() throws MojoExecutionException {
        final boolean shouldSkip = Boolean.parseBoolean(System.getProperty("cyclonedx.skip", Boolean.toString(skip)));
        if (shouldSkip) {
            getLog().info("Skipping CycloneDX");
            return;
        }
        logParameters();

        final Set<Component> components = new LinkedHashSet<>();
        final Set<Dependency> dependencies = new LinkedHashSet<>();

        if (analyze(components, dependencies)) {
            generateBom(components, dependencies);
        }
    }

    private void generateBom(Set<Component> components, Set<Dependency> dependencies) throws MojoExecutionException {
        try {
            getLog().info(MESSAGE_CREATING_BOM);
            final Bom bom = new Bom();
            if (schemaVersion().getVersion() >= 1.1 && includeBomSerialNumber) {
                bom.setSerialNumber("urn:uuid:" + UUID.randomUUID());
            }
            if (schemaVersion().getVersion() >= 1.2) {
                final Metadata metadata = modelConverter.convert(project, projectType, schemaVersion(), includeLicenseText);
                bom.setMetadata(metadata);
            }
            bom.setComponents(new ArrayList<>(components));
            if (schemaVersion().getVersion() >= 1.2 && dependencies != null && !dependencies.isEmpty()) {
                bom.setDependencies(new ArrayList<>(dependencies));
            }
            if (schemaVersion().getVersion() >= 1.3) {
                //if (excludeArtifactId != null && excludeTypes.length > 0) { // TODO
                /*
                    final Composition composition = new Composition();
                    composition.setAggregate(Composition.Aggregate.INCOMPLETE);
                    composition.setDependencies(Collections.singletonList(new Dependency(bom.getMetadata().getComponent().getBomRef())));
                    bom.setCompositions(Collections.singletonList(composition));
                */
                //}
            }

            if (!outputFormat.trim().equalsIgnoreCase("all")
                    && !outputFormat.trim().equalsIgnoreCase("xml")
                    && !outputFormat.trim().equalsIgnoreCase("json")) {
                getLog().error("Unsupported output format. Valid options are XML and JSON");
                return;
            }

            saveBom(bom);

        } catch (GeneratorException | ParserConfigurationException | IOException e) {
            throw new MojoExecutionException("An error occurred executing " + this.getClass().getName() + ": " + e.getMessage(), e);
        }
    }

    private void saveBom(Bom bom) throws ParserConfigurationException, IOException, GeneratorException,
            MojoExecutionException {
        validateBom(bom);
        if (outputFormat.trim().equalsIgnoreCase("all") || outputFormat.trim().equalsIgnoreCase("xml")) {
            final BomXmlGenerator bomGenerator = BomGeneratorFactory.createXml(schemaVersion(), bom);
            bomGenerator.generate();
            final String bomString = bomGenerator.toXmlString();

            saveBomToFile(bomString, "xml", new XmlParser());
        }
        if (outputFormat.trim().equalsIgnoreCase("all") || outputFormat.trim().equalsIgnoreCase("json")) {
            final BomJsonGenerator bomGenerator = BomGeneratorFactory.createJson(schemaVersion(), bom);
            final String bomString = bomGenerator.toJsonString();

            saveBomToFile(bomString, "json", new JsonParser());
        }
    }

    private void saveBomToFile(String bomString, String extension, Parser bomParser) throws IOException, MojoExecutionException {
        final File bomFile = new File(project.getBasedir(), "target/" + outputName + "." + extension);

        getLog().info(String.format(MESSAGE_WRITING_BOM, extension.toUpperCase(), bomFile.getAbsolutePath()));
        FileUtils.write(bomFile, bomString, StandardCharsets.UTF_8, false);

        getLog().info(String.format(MESSAGE_VALIDATING_BOM, extension.toUpperCase(), bomFile.getAbsolutePath()));
        if (!bomParser.isValid(bomFile, schemaVersion())) {
            throw new MojoExecutionException(MESSAGE_VALIDATION_FAILURE);
        }
        if (!skipAttach) {
            mavenProjectHelper.attachArtifact(project, extension, "cyclonedx", bomFile);
        }
    }

    private void validateBom(final Bom bom) {
        final Map<String, Component> components = new HashMap<>();
        components.put(bom.getMetadata().getComponent().getBomRef(), bom.getMetadata().getComponent());
        for (Component component: bom.getComponents()) {
            components.put(component.getBomRef(), component);
        }
        final Set<String> dependencyRefs = new HashSet<>();
        for (Dependency dependency: bom.getDependencies()) {
            dependencyRefs.add(dependency.getRef());
            List<Dependency> childDependencies = dependency.getDependencies();
            if (childDependencies != null) {
                childDependencies.forEach(d -> dependencyRefs.add(d.getRef()));
            }
        }
        // Check all components have a top level dependency
        for (Entry<String, Component> entry: components.entrySet()) {
            final String componentRef = entry.getKey();
            if (!dependencyRefs.contains(componentRef)) {
                getLog().info("CycloneDX: Component missing top level dependency entry, pruning component from bom: " + componentRef);
                final Component component = entry.getValue();
                if (component != null) {
                    bom.getComponents().remove(component);
                }
            }
        }
        // Check all transitive dependencies have a component
        for (String dependencyRef: dependencyRefs) {
            if (!components.containsKey(dependencyRef)) {
                getLog().warn("CycloneDX: Dependency missing component entry: " + dependencyRef);
            }
        }
    }

    /**
     * Resolves the CycloneDX schema the mojo has been requested to use.
     * @return the CycloneDX schema to use
     */
    protected CycloneDxSchema.Version schemaVersion() {
        if ("1.0".equals(schemaVersion)) {
            return CycloneDxSchema.Version.VERSION_10;
        } else if ("1.1".equals(schemaVersion)) {
            return CycloneDxSchema.Version.VERSION_11;
        } else if ("1.2".equals(schemaVersion)) {
            return CycloneDxSchema.Version.VERSION_12;
        } else if ("1.3".equals(schemaVersion)) {
            return CycloneDxSchema.Version.VERSION_13;
        } else {
            return CycloneDxSchema.Version.VERSION_14;
        }
    }

    protected Set<Dependency> buildDependencyGraph(final Set<String> componentRefs, final MavenProject mavenProject) throws MojoExecutionException {
        final Map<Dependency, Dependency> dependencies = new LinkedHashMap<>();
        final Collection<String> scope = new HashSet<>();
        if (includeCompileScope) scope.add("compile");
        if (includeProvidedScope) scope.add("provided");
        if (includeRuntimeScope) scope.add("runtime");
        if (includeSystemScope) scope.add("system");
        if (includeTestScope) scope.add("test");
        final ArtifactFilter artifactFilter = new CumulativeScopeArtifactFilter(scope);

        final ProjectBuildingRequest buildingRequest = getProjectBuildingRequest(mavenProject);

        try {
            final DependencyNode rootNode = dependencyCollectorBuilder.collectDependencyGraph(buildingRequest, artifactFilter);
            buildDependencyGraphNode(componentRefs, dependencies, rootNode, null);
            final CollectingDependencyNodeVisitor visitor = new CollectingDependencyNodeVisitor();
            rootNode.accept(visitor);
            for (final DependencyNode dependencyNode : visitor.getNodes()) {
                buildDependencyGraphNode(componentRefs, dependencies, dependencyNode, null);
            }

            /*
             Test and Runtime scope artifacts may conceal transitive compile dependencies.
             Test artifacts will conceal other artifacts if includeTestScope is false, whereas Runtime artifacs
             will conceal other artifacts if both incldueTestScope and includeRuntimeScope are false.
             */
            if (includeCompileScope && !includeTestScope) {
                final ProjectBuildingRequest testBuildingRequest = getProjectBuildingRequest(mavenProject);
                final DependencyNode testRootNode = dependencyCollectorBuilder.collectDependencyGraph(testBuildingRequest, null);
                final Map<String, DependencyNode> concealedNodes = new HashMap<>();
                final Map<String, DependencyNode> concealedEmptyNodes = new HashMap<>();
                for (DependencyNode child: testRootNode.getChildren()) {
                    if (Artifact.SCOPE_TEST.equals(child.getArtifact().getScope())) {
                        collectNodes(concealedNodes, concealedEmptyNodes, child);
                    }
                }
                if (!includeRuntimeScope) {
                    collectRuntimeNodes(concealedNodes, concealedEmptyNodes, testRootNode);
                }

                final Deque<Dependency> toProcess = new LinkedList<>(dependencies.values());
                while (!toProcess.isEmpty()) {
                    final Dependency dependency = toProcess.remove();
                    if ((dependency.getDependencies() == null) || dependency.getDependencies().isEmpty()) {
                        final String purl = dependency.getRef();
                        DependencyNode concealedNode = concealedNodes.get(purl);
                        if (concealedNode == null) {
                            concealedNode = concealedEmptyNodes.get(purl);
                        }
                        if (concealedNode != null) {
                            getLog().info("CycloneDX: Populating concealed node: " + purl);
                            for (DependencyNode child: concealedNode.getChildren()) {
                                buildDependencyGraphNode(componentRefs, dependencies, child, dependency);
                                buildDependencyGraphNode(componentRefs, dependencies, child, null);
                            }
                            final Dependency topLevelDependency = dependencies.get(dependency);
                            if (topLevelDependency.getDependencies() != null) {
                                toProcess.addAll(topLevelDependency.getDependencies());
                            }
                        }
                    }
                }
            }
        } catch (DependencyCollectorBuilderException e) {
            if (mavenProject != null) {
                // When executing makeAggregateBom, some projects may not yet be built. Workaround is to warn on this
                // rather than throwing an exception https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/55
                getLog().warn("An error occurred building dependency graph: " + e.getMessage());
            } else {
                throw new MojoExecutionException("An error occurred building dependency graph", e);
            }
        }

        return dependencies.keySet();
    }

    /**
     * Add all runtime nodes with children into the map.  Key is purl, value is the node.
     * @param concealedNodes The map of references to concealed nodes with children
     * @param concealedEmptyNodes The map of references to concealed nodes without children
     * @param node The node to add
     */
    private void collectRuntimeNodes(Map<String, DependencyNode> concealedNodes, Map<String, DependencyNode> concealedEmptyNodes, DependencyNode node) {
        if (!node.getChildren().isEmpty()) {
            if (Artifact.SCOPE_RUNTIME.equals(node.getArtifact().getScope())) {
                final String purl = generatePackageUrl(node.getArtifact());
                concealedNodes.put(purl, node) ;
                for (DependencyNode child: node.getChildren()) {
                    collectNodes(concealedNodes, concealedEmptyNodes, child);
                }
            } else {
                for (DependencyNode child: node.getChildren()) {
                    collectRuntimeNodes(concealedNodes, concealedEmptyNodes, child);
                }
            }
        }
    }

    /**
     * Add all nodes with children into the map.  Key is purl, value is the node.
     * @param concealedNodes The map of references to concealed nodes with children
     * @param concealedEmptyNodes The map of references to concealed nodes without children
     * @param node The node to add
     */
    private void collectNodes(Map<String, DependencyNode> concealedNodes, Map<String, DependencyNode> concealedEmptyNodes, DependencyNode node) {
        final String purl = generatePackageUrl(node.getArtifact());
        if (!node.getChildren().isEmpty()) {
            concealedNodes.put(purl, node) ;
            for (DependencyNode child: node.getChildren()) {
                collectNodes(concealedNodes, concealedEmptyNodes, child);
            }
        } else {
            concealedEmptyNodes.put(purl, node);
        }
    }

    private void buildDependencyGraphNode(final Set<String> componentRefs, final Map<Dependency, Dependency> dependencies, final DependencyNode artifactNode, final Dependency parent) {
        final String purl = modelConverter.generatePackageUrl(artifactNode.getArtifact());
        final Dependency dependency = new Dependency(purl);
        if (componentRefs.contains(purl)) {
            addDependencyToGraph(dependencies, parent, dependency);
        } else {
            getLog().warn("CycloneDX: Could not locate component " + purl + " in componentRefs");
        }
        for (final DependencyNode childrenNode : artifactNode.getChildren()) {
            buildDependencyGraphNode(componentRefs, dependencies, childrenNode, dependency);
        }
    }

    private void addDependencyToGraph(final Map<Dependency, Dependency> dependencies, final Dependency parent, final Dependency dependency) {
        if (parent == null) {
            dependencies.putIfAbsent(dependency, dependency);
        } else if (!parent.getRef().equals(dependency.getRef())) {
            final Dependency topLevelParent = dependencies.get(parent);
            if (topLevelParent != null) {
                topLevelParent.addDependency(dependency);
            }
        }
    }

    protected void logAdditionalParameters() {
        // no additional parameters
    }

    protected void logParameters() {
        if (verbose && getLog().isInfoEnabled()) {
            getLog().info("CycloneDX: Parameters");
            getLog().info("------------------------------------------------------------------------");
            getLog().info("schemaVersion          : " + schemaVersion().getVersionString());
            getLog().info("includeBomSerialNumber : " + includeBomSerialNumber);
            getLog().info("includeCompileScope    : " + includeCompileScope);
            getLog().info("includeProvidedScope   : " + includeProvidedScope);
            getLog().info("includeRuntimeScope    : " + includeRuntimeScope);
            getLog().info("includeTestScope       : " + includeTestScope);
            getLog().info("includeSystemScope     : " + includeSystemScope);
            getLog().info("includeLicenseText     : " + includeLicenseText);
            getLog().info("outputFormat           : " + outputFormat);
            getLog().info("outputName             : " + outputName);
            logAdditionalParameters();
            getLog().info("------------------------------------------------------------------------");
        }
    }

    /**
     * Create a project building request
     * @param mavenProject The maven project associated with this build request
     * @return The project building request
     */
    private ProjectBuildingRequest getProjectBuildingRequest(final MavenProject mavenProject) {
        final ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        if (mavenProject != null) {
            buildingRequest.setProject(mavenProject);
        } else {
            buildingRequest.setProject(this.project);
        }
        return buildingRequest;
    }
}
