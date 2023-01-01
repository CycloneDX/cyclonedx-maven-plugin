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

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.CumulativeScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.CollectingDependencyNodeVisitor;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.cyclonedx.BomGeneratorFactory;
import org.cyclonedx.CycloneDxSchema;
import org.cyclonedx.exception.GeneratorException;
import org.cyclonedx.generators.json.BomJsonGenerator;
import org.cyclonedx.generators.xml.BomXmlGenerator;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.License;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.model.Metadata;
import org.cyclonedx.model.Tool;
import org.cyclonedx.parsers.JsonParser;
import org.cyclonedx.parsers.Parser;
import org.cyclonedx.parsers.XmlParser;
import org.cyclonedx.util.BomUtils;
import org.cyclonedx.util.LicenseResolver;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.apache.maven.artifact.Artifact.SCOPE_COMPILE;

public abstract class BaseCycloneDxMojo extends AbstractMojo implements Contextualizable {

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
     * Excluded ArtifactIds.
     *
     * @since 2.4.0
     */
    @Parameter(property = "excludeArtifactId", required = false)
    protected String[] excludeArtifactId;

    /**
     * Excluded GroupIds.
     *
     * @since 2.7.3
     */
    @Parameter(property = "excludeGroupId", required = false)
    protected String[] excludeGroupId;

    /**
     * Should project artifactId with the word "test" be excluded in bom?
     *
     * @since 2.4.0
     */
    @Parameter(property = "excludeTestProject", defaultValue = "false", required = false)
    protected Boolean excludeTestProject;

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

    /**
     * The RepositorySystem to inject. Used by this plugin for building effective poms.
     */
    @org.apache.maven.plugins.annotations.Component
    private RepositorySystem repositorySystem;

    /**
     * The ProjectBuilder to inject. Used by this plugin for building effective poms.
     */
    @org.apache.maven.plugins.annotations.Component
    private ProjectBuilder mavenProjectBuilder;

    /**
     * Various messages sent to console.
     */
    protected static final String MESSAGE_RESOLVING_DEPS = "CycloneDX: Resolving Dependencies";
    protected static final String MESSAGE_CREATING_BOM = "CycloneDX: Creating BOM";
    protected static final String MESSAGE_CALCULATING_HASHES = "CycloneDX: Calculating Hashes";
    protected static final String MESSAGE_WRITING_BOM = "CycloneDX: Writing BOM (%s): %s";
    protected static final String MESSAGE_VALIDATING_BOM = "CycloneDX: Validating BOM (%s): %s";
    protected static final String MESSAGE_VALIDATION_FAILURE = "The BOM does not conform to the CycloneDX BOM standard as defined by the XSD";

    /**
     * The plexus context to look-up the right {@link ProjectDependencyAnalyzer} implementation depending on the mojo
     * configuration.
     */
    private Context context;

    /**
     * Specify the project dependency analyzer to use (plexus component role-hint). By default,
     * <a href="https://maven.apache.org/shared/maven-dependency-analyzer/">maven-dependency-analyzer</a> is used. To use this, you must declare
     * a dependency for this plugin that contains the code for the analyzer. The analyzer must have a declared Plexus
     * role name, and you specify the role name here.
     *
     * @since 2.2
     */
    @Parameter( property = "analyzer", defaultValue = "default" )
    private String analyzer;

    /**
     * DependencyAnalyzer
     */
    protected ProjectDependencyAnalyzer dependencyAnalyzer;

    /**
     * Returns a reference to the current project.
     *
     * @return returns a reference to the current project
     */
    protected MavenProject getProject() {
        return project;
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

    /**
     * Converts a MavenProject into a Metadata object.
     *
     * @param project the MavenProject to convert
     * @return a CycloneDX Metadata object
     */
    protected Metadata convert(final MavenProject project) {
        final Tool tool = new Tool();
        final Properties properties = readPluginProperties();
        tool.setVendor(properties.getProperty("vendor"));
        tool.setName(properties.getProperty("name"));
        tool.setVersion(properties.getProperty("version"));
        // Attempt to add hash values from the current mojo
        final Artifact self = new DefaultArtifact(properties.getProperty("groupId"), properties.getProperty("artifactId"),
                properties.getProperty("version"), SCOPE_COMPILE, "jar", null, new DefaultArtifactHandler());
        final Artifact resolved = session.getLocalRepository().find(self);
        if (resolved != null) {
            try {
                resolved.setFile(new File(resolved.getFile() + ".jar"));
                tool.setHashes(BomUtils.calculateHashes(resolved.getFile(), schemaVersion()));
            } catch (IOException e) {
                getLog().warn("Unable to calculate hashes of self", e);
            }
        }

        final Component component = new Component();
        component.setGroup(project.getGroupId());
        component.setName(project.getArtifactId());
        component.setVersion(project.getVersion());
        component.setType(resolveProjectType());
        component.setPurl(generatePackageUrl(project.getArtifact()));
        component.setBomRef(component.getPurl());
        extractComponentMetadata(project, component);

        final Metadata metadata = new Metadata();
        metadata.addTool(tool);
        metadata.setComponent(component);
        return metadata;
    }

    private Properties readPluginProperties() {
        final Properties props = new Properties();
        try {
            props.load(this.getClass().getClassLoader().getResourceAsStream("plugin.properties"));
        } catch (NullPointerException | IOException e) {
            getLog().warn("Unable to load plugin.properties", e);
        }
        return props;
    }

    /**
     * Converts a Maven artifact (dependency or transitive dependency) into a
     * CycloneDX component.
     *
     * @param artifact the artifact to convert
     * @return a CycloneDX component
     */
    protected Component convert(Artifact artifact) {
        final Component component = new Component();
        component.setGroup(artifact.getGroupId());
        component.setName(artifact.getArtifactId());
        component.setVersion(artifact.getBaseVersion());
        component.setType(Component.Type.LIBRARY);
        try {
            getLog().debug(MESSAGE_CALCULATING_HASHES);
            component.setHashes(BomUtils.calculateHashes(artifact.getFile(), schemaVersion()));
        } catch (IOException e) {
            getLog().error("Error encountered calculating hashes", e);
        }
        if (CycloneDxSchema.Version.VERSION_10 == schemaVersion()) {
            component.setModified(isModified(artifact));
        }
        component.setPurl(generatePackageUrl(artifact));
        if (CycloneDxSchema.Version.VERSION_10 != schemaVersion()) {
            component.setBomRef(component.getPurl());
        }
        if (isDescribedArtifact(artifact)) {
            try {
                final MavenProject project = getEffectiveMavenProject(artifact);
                if (project != null) {
                    extractComponentMetadata(project, component);
                }
            } catch (ProjectBuildingException e) {
                getLog().warn("An unexpected issue occurred attempting to resolve the effective pom for  "
                        + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion(), e);
            }
        }
        return component;
    }

    private String generatePackageUrl(final Artifact artifact) {
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
            getLog().warn("An unexpected issue occurred attempting to create a PackageURL for "
                    + groupId + ":" + artifactId + ":" + version, e);
        }
        return null;
    }

    /**
     * This method generates an 'effective pom' for an artifact.
     * @param artifact the artifact to generate an effective pom of
     * @throws ProjectBuildingException if an error is encountered
     */
    private MavenProject getEffectiveMavenProject(final Artifact artifact) throws ProjectBuildingException {
        final Artifact pomArtifact = repositorySystem.createProjectArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
        final ProjectBuildingResult build = mavenProjectBuilder.build(pomArtifact,
                session.getProjectBuildingRequest().setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL)
        );
        return build.getProject();
    }

    /**
     * Extracts data from a project and adds the data to the component.
     * @param project the project to extract data from
     * @param component the component to add data to
     */
    private void extractComponentMetadata(MavenProject project, Component component) {
        if (component.getPublisher() == null) {
            // If we don't already have publisher information, retrieve it.
            if (project.getOrganization() != null) {
                component.setPublisher(project.getOrganization().getName());
            }
        }
        if (component.getDescription() == null) {
            // If we don't already have description information, retrieve it.
            component.setDescription(project.getDescription());
        }
        if (component.getLicenseChoice() == null || component.getLicenseChoice().getLicenses() == null || component.getLicenseChoice().getLicenses().isEmpty()) {
            // If we don't already have license information, retrieve it.
            if (project.getLicenses() != null) {
                component.setLicenseChoice(resolveMavenLicenses(project.getLicenses()));
            }
        }
        if (CycloneDxSchema.Version.VERSION_10 != schemaVersion()) {
            if (project.getUrl() != null) {
                if (!doesComponentHaveExternalReference(component, ExternalReference.Type.WEBSITE)) {
                    addExternalReference(ExternalReference.Type.WEBSITE, project.getUrl(), component);
                }
            }
            if (project.getCiManagement() != null && project.getCiManagement().getUrl() != null) {
                if (!doesComponentHaveExternalReference(component, ExternalReference.Type.BUILD_SYSTEM)) {
                    addExternalReference(ExternalReference.Type.BUILD_SYSTEM, project.getCiManagement().getUrl(), component);
                }
            }
            if (project.getDistributionManagement() != null && project.getDistributionManagement().getDownloadUrl() != null) {
                if (!doesComponentHaveExternalReference(component, ExternalReference.Type.DISTRIBUTION)) {
                    addExternalReference(ExternalReference.Type.DISTRIBUTION, project.getDistributionManagement().getDownloadUrl(), component);
                }
            }
            if (project.getDistributionManagement() != null && project.getDistributionManagement().getRepository() != null) {
                if (!doesComponentHaveExternalReference(component, ExternalReference.Type.DISTRIBUTION)) {
                    addExternalReference(ExternalReference.Type.DISTRIBUTION, project.getDistributionManagement().getRepository().getUrl(), component);
                }
            }
            if (project.getIssueManagement() != null && project.getIssueManagement().getUrl() != null) {
                if (!doesComponentHaveExternalReference(component, ExternalReference.Type.ISSUE_TRACKER)) {
                    addExternalReference(ExternalReference.Type.ISSUE_TRACKER, project.getIssueManagement().getUrl(), component);
                }
            }
            if (project.getMailingLists() != null && project.getMailingLists().size() > 0) {
                for (MailingList list : project.getMailingLists()) {
                    if (list.getArchive() != null) {
                        if (!doesComponentHaveExternalReference(component, ExternalReference.Type.MAILING_LIST)) {
                            addExternalReference(ExternalReference.Type.MAILING_LIST, list.getArchive(), component);
                        }
                    } else if (list.getSubscribe() != null) {
                        if (!doesComponentHaveExternalReference(component, ExternalReference.Type.MAILING_LIST)) {
                            addExternalReference(ExternalReference.Type.MAILING_LIST, list.getSubscribe(), component);
                        }
                    }
                }
            }
            if (project.getScm() != null && project.getScm().getUrl() != null) {
                if (!doesComponentHaveExternalReference(component, ExternalReference.Type.VCS)) {
                    addExternalReference(ExternalReference.Type.VCS, project.getScm().getUrl(), component);
                }
            }
        }
    }

    private void addExternalReference(final ExternalReference.Type referenceType, final String url, final Component component) {
        try {
            final URI uri = new URI(url.trim());
            final ExternalReference ref = new ExternalReference();
            ref.setType(referenceType);
            ref.setUrl(uri.toString());
            component.addExternalReference(ref);
        } catch (URISyntaxException e) {
            // throw it away
        }
    }

    private boolean doesComponentHaveExternalReference(final Component component, final ExternalReference.Type type) {
        if (component.getExternalReferences() != null && !component.getExternalReferences().isEmpty()) {
            for (final ExternalReference ref : component.getExternalReferences()) {
                if (type == ref.getType()) {
                    return true;
                }
            }
        }
        return false;
    }

    private LicenseChoice resolveMavenLicenses(final List<org.apache.maven.model.License> projectLicenses) {
        final LicenseChoice licenseChoice = new LicenseChoice();
        for (org.apache.maven.model.License artifactLicense : projectLicenses) {
            boolean resolved = false;
            if (artifactLicense.getName() != null) {
                final LicenseChoice resolvedByName =
                    LicenseResolver.resolve(artifactLicense.getName(), includeLicenseText);
                resolved = resolveLicenseInfo(licenseChoice, resolvedByName);
            }
            if (artifactLicense.getUrl() != null && !resolved) {
                final LicenseChoice resolvedByUrl =
                    LicenseResolver.resolve(artifactLicense.getUrl(), includeLicenseText);
                resolved = resolveLicenseInfo(licenseChoice, resolvedByUrl);
            }
            if (artifactLicense.getName() != null && !resolved) {
                final License license = new License();
                license.setName(artifactLicense.getName().trim());
                if (StringUtils.isNotBlank(artifactLicense.getUrl())) {
                    try {
                        final URI uri = new URI(artifactLicense.getUrl().trim());
                        license.setUrl(uri.toString());
                    } catch (URISyntaxException  e) {
                        // throw it away
                    }
                }
                licenseChoice.addLicense(license);
            }
        }
        return licenseChoice;
    }

    private boolean resolveLicenseInfo(
        final LicenseChoice licenseChoice,
        final LicenseChoice licenseChoiceToResolve)
    {
        if (licenseChoiceToResolve != null) {
            if (licenseChoiceToResolve.getLicenses() != null && !licenseChoiceToResolve.getLicenses().isEmpty()) {
                licenseChoice.addLicense(licenseChoiceToResolve.getLicenses().get(0));
                return true;
            }
            else if (licenseChoiceToResolve.getExpression() != null &&
                CycloneDxSchema.Version.VERSION_10 != schemaVersion()) {
                licenseChoice.setExpression(licenseChoiceToResolve.getExpression());
                return true;
            }
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
                final Metadata metadata = convert(project);
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

    private boolean isModified(Artifact artifact) {
        //todo: compare hashes + GAV with what the artifact says against Maven Central to determine if component has been modified.
        return false;
    }

    /**
     * Returns true for any artifact type which will positively have a POM that
     * describes the artifact.
     * @param artifact the artifact
     * @return true if artifact will have a POM, false if not
     */
    private boolean isDescribedArtifact(Artifact artifact) {
        return artifact.getType().equalsIgnoreCase("jar");
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

    private Component.Type resolveProjectType() {
        for (Component.Type type: Component.Type.values()) {
            if (type.getTypeName().equalsIgnoreCase(this.projectType)) {
                return type;
            }
        }
        getLog().warn("Invalid project type. Defaulting to 'library'");
        getLog().warn("Valid types are:");
        for (Component.Type type: Component.Type.values()) {
            getLog().warn("  " + type.getTypeName());
        }
        return Component.Type.LIBRARY;
    }

    protected Set<Dependency> buildDependencyGraph(final Set<String> componentRefs, final MavenProject mavenProject) throws MojoExecutionException {
        final Set<Dependency> dependencies = new LinkedHashSet<>();
        final Collection<String> scope = new HashSet<>();
        if (includeCompileScope) scope.add("compile");
        if (includeProvidedScope) scope.add("provided");
        if (includeRuntimeScope) scope.add("runtime");
        if (includeSystemScope) scope.add("system");
        if (includeTestScope) scope.add("test");
        final ArtifactFilter artifactFilter = new CumulativeScopeArtifactFilter(scope);
        final ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        if (mavenProject != null) {
            buildingRequest.setProject(mavenProject);
        } else {
            buildingRequest.setProject(this.project);
        }
        try {
            final DependencyNode rootNode = dependencyCollectorBuilder.collectDependencyGraph(buildingRequest, artifactFilter);
            buildDependencyGraphNode(componentRefs, dependencies, rootNode, null);
            final CollectingDependencyNodeVisitor visitor = new CollectingDependencyNodeVisitor();
            rootNode.accept(visitor);
            for (final DependencyNode dependencyNode : visitor.getNodes()) {
                buildDependencyGraphNode(componentRefs, dependencies, dependencyNode, null);
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
        return dependencies;
    }

    private void buildDependencyGraphNode(final Set<String> componentRefs, final Set<Dependency> dependencies, final DependencyNode artifactNode, final Dependency parent) {
        final String purl = generatePackageUrl(artifactNode.getArtifact());
        final Dependency dependency = new Dependency(purl);
        final String parentRef = (parent != null) ? parent.getRef() : null;
        componentRefs.stream().filter(s -> s != null && s.equals(purl))
                .forEach(s -> addDependencyToGraph(dependencies, parentRef, dependency));
        for (final DependencyNode childrenNode : artifactNode.getChildren()) {
            buildDependencyGraphNode(componentRefs, dependencies, childrenNode, dependency);
        }
    }

    private void addDependencyToGraph(final Set<Dependency> dependencies, final String parentRef, final Dependency dependency) {
        if (parentRef == null) {
            dependencies.add(dependency);
        } else {
            for (final Dependency d : dependencies) {
                if (d.getRef().equals(parentRef) && !parentRef.equals(dependency.getRef())) {
                    d.addDependency(dependency);
                }
            }
        }
    }

    protected void addMavenProjectsAsDependencies(List<MavenProject> reactorProjects, Set<Dependency> dependencies) {
        for (final Dependency dependency: dependencies) {
            for (final MavenProject project: reactorProjects) {
                if (project.hasParent()) {
                    final String parentRef = generatePackageUrl(project.getParentArtifact());
                    if (dependency.getRef() != null && dependency.getRef().equals(parentRef)) {
                        final Dependency child = new Dependency(generatePackageUrl(project.getArtifact()));
                        dependency.addDependency(child);
                    }
                }
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

    @Override
    public void contextualize( Context theContext )
    {
        this.context = theContext;
    }

    /**
     * @return {@link ProjectDependencyAnalyzer}
     * @throws MojoExecutionException in case of an error.
     */
    protected ProjectDependencyAnalyzer createProjectDependencyAnalyzer()
            throws MojoExecutionException
    {
        final String role = ProjectDependencyAnalyzer.class.getName();
        final String roleHint = analyzer;
        try
        {
            final PlexusContainer container = (PlexusContainer) context.get( PlexusConstants.PLEXUS_KEY );
            return (ProjectDependencyAnalyzer) container.lookup( role, roleHint );
        }
        catch ( Exception exception )
        {
            throw new MojoExecutionException( "Failed to instantiate ProjectDependencyAnalyser with role " + role
                    + " / role-hint " + roleHint, exception );
        }
    }

    /**
     * Method to identify component scope based on dependency analysis
     *
     * @param component Component
     * @param artifact Artifact from maven project
     * @param dependencyAnalysis Dependency analysis data
     *
     * @return Component.Scope - Required: If the component is used. Optional: If it is unused
     */
    protected Component.Scope getComponentScope(Component component, Artifact artifact, ProjectDependencyAnalysis dependencyAnalysis) {
        if (dependencyAnalysis == null) {
            return null;
        }
        Set<Artifact> usedDeclaredArtifacts = dependencyAnalysis.getUsedDeclaredArtifacts();
        Set<Artifact> usedUndeclaredArtifacts = dependencyAnalysis.getUsedUndeclaredArtifacts();
        Set<Artifact> unusedDeclaredArtifacts = dependencyAnalysis.getUnusedDeclaredArtifacts();
        Set<Artifact> testArtifactsWithNonTestScope = dependencyAnalysis.getTestArtifactsWithNonTestScope();
        // Is the artifact used?
        if (usedDeclaredArtifacts.contains(artifact) || usedUndeclaredArtifacts.contains(artifact)) {
            return Component.Scope.REQUIRED;
        }
        // Is the artifact unused or test?
        if (unusedDeclaredArtifacts.contains(artifact) || testArtifactsWithNonTestScope.contains(artifact)) {
            return Component.Scope.OPTIONAL;
        }
        return null;
    }
}
