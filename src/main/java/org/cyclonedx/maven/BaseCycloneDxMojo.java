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
import org.apache.maven.shared.dependency.graph.internal.DefaultDependencyCollectorBuilder;
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
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
     * The CycloneDX output file name (without extension) that should be generated (in {@code outputDirectory} directory).
     *
     * @since 2.2.0
     */
    @Parameter(property = "outputName", defaultValue = "bom", required = false)
    private String outputName;

    /**
     * The output directory where to store generated CycloneDX output files.
     *
     * @since 2.7.5
     */
    @Parameter(defaultValue = "${project.build.directory}", required = false)
    private File outputDirectory;

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

    @org.apache.maven.plugins.annotations.Component(hint = "default")
    private RepositorySystem aetherRepositorySystem;

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
    private ModelConverter modelConverter;

    private Set<String> excludeTypesSet;

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

    protected String generateVersionlessPackageUrl(final Artifact artifact) {
        return modelConverter.generateVersionlessPackageUrl(artifact);
    }

    protected String generatePackageUrl(final org.eclipse.aether.artifact.Artifact artifact) {
        return modelConverter.generatePackageUrl(artifact);
    }

    protected String generateVersionlessPackageUrl(final org.eclipse.aether.artifact.Artifact artifact) {
        return modelConverter.generateVersionlessPackageUrl(artifact);
    }

    protected Component convert(Artifact artifact) {
        return modelConverter.convert(artifact, schemaVersion(), includeLicenseText);
    }

    /**
     * Analyze the current Maven project to extract the BOM components list and their dependencies.
     *
     * @param components the components set to fill
     * @param dependencies the dependencies set to fill
     * @return the name of the analysis done to store as a BOM, or {@code null} to not save result.
     * @throws MojoExecutionException something weird happened...
     */
    protected abstract String extractComponentsAndDependencies(Set<Component> components, Set<Dependency> dependencies) throws MojoExecutionException;

    public void execute() throws MojoExecutionException {
        final boolean shouldSkip = Boolean.parseBoolean(System.getProperty("cyclonedx.skip", Boolean.toString(skip)));
        if (shouldSkip) {
            getLog().info("Skipping CycloneDX");
            return;
        }
        logParameters();

        final Set<Component> components = new LinkedHashSet<>();
        final Set<Dependency> dependencies = new LinkedHashSet<>();

        String analysis = extractComponentsAndDependencies(components, dependencies);
        if (analysis != null) {
            final Metadata metadata = modelConverter.convert(project, analysis, projectType, schemaVersion(), includeLicenseText);
            cleanupBomDependencies(metadata, components, dependencies);

            generateBom(analysis, metadata, components, dependencies);
        }
    }

    private void generateBom(String analysis, Metadata metadata, Set<Component> components, Set<Dependency> dependencies) throws MojoExecutionException {
        try {
            getLog().info(MESSAGE_CREATING_BOM);
            final Bom bom = new Bom();
            bom.setComponents(new ArrayList<>(components));

            if (schemaVersion().getVersion() >= 1.1 && includeBomSerialNumber) {
                bom.setSerialNumber("urn:uuid:" + UUID.randomUUID());
            }

            if (schemaVersion().getVersion() >= 1.2) {
                bom.setMetadata(metadata);
                bom.setDependencies(new ArrayList<>(dependencies));
            }

            /*if (schemaVersion().getVersion() >= 1.3) {
                if (excludeArtifactId != null && excludeTypes.length > 0) { // TODO
                    final Composition composition = new Composition();
                    composition.setAggregate(Composition.Aggregate.INCOMPLETE);
                    composition.setDependencies(Collections.singletonList(new Dependency(bom.getMetadata().getComponent().getBomRef())));
                    bom.setCompositions(Collections.singletonList(composition));
                }
            }*/

            if ("all".equalsIgnoreCase(outputFormat)
                    || "xml".equalsIgnoreCase(outputFormat)
                    || "json".equalsIgnoreCase(outputFormat)) {
                saveBom(bom);
            } else {
                getLog().error("Unsupported output format. Valid options are XML and JSON");
            }
        } catch (GeneratorException | ParserConfigurationException | IOException e) {
            throw new MojoExecutionException("An error occurred executing " + this.getClass().getName() + ": " + e.getMessage(), e);
        }
    }

    private void saveBom(Bom bom) throws ParserConfigurationException, IOException, GeneratorException,
            MojoExecutionException {
        if ("all".equalsIgnoreCase(outputFormat) || "xml".equalsIgnoreCase(outputFormat)) {
            final BomXmlGenerator bomGenerator = BomGeneratorFactory.createXml(schemaVersion(), bom);
            bomGenerator.generate();

            final String bomString = bomGenerator.toXmlString();
            saveBomToFile(bomString, "xml", new XmlParser());
        }
        if ("all".equalsIgnoreCase(outputFormat) || "json".equalsIgnoreCase(outputFormat)) {
            final BomJsonGenerator bomGenerator = BomGeneratorFactory.createJson(schemaVersion(), bom);

            final String bomString = bomGenerator.toJsonString();
            saveBomToFile(bomString, "json", new JsonParser());
        }
    }

    private void saveBomToFile(String bomString, String extension, Parser bomParser) throws IOException, MojoExecutionException {
        final File bomFile = new File(outputDirectory, outputName + "." + extension);

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

    /**
     * Check consistency between BOM components and BOM dependencies, and cleanup: drop components found while walking the
     * Maven dependency resolution graph but that are finally not kept in the effective dependencies list.
     */
    private void cleanupBomDependencies(Metadata metadata, Set<Component> components, Set<Dependency> dependencies) {
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
        for (Entry<String, Component> entry: componentRefs.entrySet()) {
            if (!dependencyRefs.contains(entry.getKey())) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug("Component reference not listed in dependencies, pruning from bom components: " + entry.getKey());
                }
                components.remove(entry.getValue());
            } else if (!dependsOns.contains(entry.getKey())) {
                getLog().warn("BOM dependency listed but is not depended upon: " + entry.getKey());
            }
        }

        // add BOM main component
        Component main = metadata.getComponent();
        componentRefs.put(main.getBomRef(), main);

        // Check all BOM dependencies have a BOM component
        for (String dependencyRef: dependencyRefs) {
            if (!componentRefs.containsKey(dependencyRef)) {
                getLog().warn("Dependency missing component entry: " + dependencyRef);
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

    private Set<String> getExcludeTypesSet() {
        if (excludeTypesSet == null) {
            excludeTypesSet = new HashSet<>(Arrays.asList(excludeTypes));
        }
        return excludeTypesSet;
    }

    protected Set<Dependency> buildBOMDependencies(MavenProject mavenProject) throws MojoExecutionException {
        if (mavenProject == null) {
            mavenProject = project;
        }
        final ProjectBuildingRequest buildingRequest = getProjectBuildingRequest(mavenProject);

        final Map<String, String> resolvedPUrls = generateResolvedPUrls(mavenProject);

        final Map<Dependency, Dependency> dependencies = new LinkedHashMap<>();
        try {
            final CycloneDxRepositorySystem cycloneRepositorySystem = new CycloneDxRepositorySystem(aetherRepositorySystem);
            final DependencyCollectorBuilder dependencyCollectorBuilder = new DefaultDependencyCollectorBuilder(cycloneRepositorySystem);
            dependencyCollectorBuilder.collectDependencyGraph(buildingRequest, null);
            final CollectResult collectResult = cycloneRepositorySystem.getCollectResult();
            if (collectResult == null) {
                throw new MojoExecutionException("Failed to generate aether dependency graph");
            }
            final DependencyNode root = collectResult.getRoot();

            // Generate the tree, removing excluded and filtered nodes
            final Set<String> loggedReplacementPUrls = new HashSet<>();
            final Set<String> loggedFilteredArtifacts = new HashSet<>();
            buildDependencyGraphNode(dependencies, root, null, resolvedPUrls, loggedReplacementPUrls, loggedFilteredArtifacts);
        } catch (DependencyCollectorBuilderException e) {
            // When executing makeAggregateBom, some projects may not yet be built. Workaround is to warn on this
            // rather than throwing an exception https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/55
            getLog().warn("An error occurred building dependency graph: " + e.getMessage());
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
                scoped = includeCompileScope;
                break;
            case Artifact.SCOPE_PROVIDED:
                scoped = includeProvidedScope;
                break;
            case Artifact.SCOPE_RUNTIME:
                scoped = includeRuntimeScope;
                break;
            case Artifact.SCOPE_SYSTEM:
                scoped = includeSystemScope;
                break;
            case Artifact.SCOPE_TEST:
                scoped = includeTestScope;
                break;
            default:
                scoped = Boolean.FALSE;
        }
        final boolean result = Boolean.FALSE.equals(scoped);
        if (result) {
            final String purl = generatePackageUrl(node.getArtifact());
            final String key = purl + ":" + originalScope + ":" + node.getDependency().getScope();
            if (loggedFilteredArtifacts.add(key) && getLog().isDebugEnabled()) {
                getLog().debug("CycloneDX: Filtering " + purl + " with original scope " + originalScope + " and scope " + node.getDependency().getScope());
            }
        }
        return result;
    }

    private void buildDependencyGraphNode(final Map<Dependency, Dependency> dependencies, DependencyNode node, final Dependency parent,
            final Map<String, String> resolvedPUrls, final Set<String> loggedReplacementPUrls, final Set<String> loggedFilteredArtifacts) {
        String purl = generatePackageUrl(node.getArtifact());

        if (isExcludedNode(node) || (parent != null && isFilteredNode(node, loggedFilteredArtifacts))) {
            return;
        }

        // If the node has no children then it could be a marker node for conflict resolution
        if (node.getChildren().isEmpty()) {
            final Map<?,?> nodeData = node.getData();
            final DependencyNode winner = (DependencyNode) nodeData.get(ConflictResolver.NODE_DATA_WINNER);
            final String resolvedPurl = resolvedPUrls.get(generateVersionlessPackageUrl(node.getArtifact()));
            if (!purl.equals(resolvedPurl)) {
                if (!loggedReplacementPUrls.contains(purl)) {
                    if (getLog().isDebugEnabled()) {
                        getLog().debug("CycloneDX: replacing reference to " + purl + " with resolved package url " + resolvedPurl);
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
        for (final DependencyNode childrenNode : node.getChildren()) {
            buildDependencyGraphNode(dependencies, childrenNode, topDependency, resolvedPUrls, loggedReplacementPUrls, loggedFilteredArtifacts);
        }
    }

    /**
     * Generate a map of versionless purls to their resolved versioned purl
     * @return the map of versionless purls to resolved versioned purls
     */
    private Map<String, String> generateResolvedPUrls(final MavenProject mavenProject) {
        final Map<String, String> resolvedPUrls = new HashMap<>();
        final Artifact projectArtifact = mavenProject.getArtifact();
        resolvedPUrls.put(generateVersionlessPackageUrl(projectArtifact), generatePackageUrl(projectArtifact));
        for (Artifact artifact: mavenProject.getArtifacts()) {
            resolvedPUrls.put(generateVersionlessPackageUrl(artifact), generatePackageUrl(artifact));
        }
        return resolvedPUrls;
    }

    private boolean isExcludedNode(final DependencyNode node) {
        final String type = node.getArtifact().getProperties().get(ArtifactProperties.TYPE);
        return ((type == null) || getExcludeTypesSet().contains(type));
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
        buildingRequest.setProject(mavenProject);
        return buildingRequest;
    }
}
