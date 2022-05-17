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
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuildingRequest;
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
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
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
import org.cyclonedx.parsers.XmlParser;
import org.cyclonedx.util.BomUtils;
import org.cyclonedx.util.LicenseResolver;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.commons.io.input.BOMInputStream;

import static org.apache.maven.artifact.Artifact.SCOPE_COMPILE;

public abstract class BaseCycloneDxMojo extends AbstractMojo implements Contextualizable {

    @Parameter(property = "session", readonly = true, required = true)
    private MavenSession session;

    @Parameter(property = "project", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "reactorProjects", readonly = true, required = true)
    private List<MavenProject> reactorProjects;

    @Parameter(property = "projectType", defaultValue = "library", required = false)
    private String projectType;

    @Parameter(property = "schemaVersion", defaultValue = "1.4", required = false)
    private String schemaVersion;

    @Parameter(property = "outputFormat", defaultValue = "all", required = false)
    private String outputFormat;

    @Parameter(property = "outputName", defaultValue = "bom", required = false)
    private String outputName;

    @Parameter(property = "outputReactorProjects", defaultValue = "true", required = false)
    private Boolean outputReactorProjects;

    @Parameter(property = "includeBomSerialNumber", defaultValue = "true", required = false)
    private Boolean includeBomSerialNumber;

    @Parameter(property = "includeCompileScope", defaultValue = "true", required = false)
    private Boolean includeCompileScope;

    @Parameter(property = "includeProvidedScope", defaultValue = "true", required = false)
    private Boolean includeProvidedScope;

    @Parameter(property = "includeRuntimeScope", defaultValue = "true", required = false)
    private Boolean includeRuntimeScope;

    @Parameter(property = "includeTestScope", defaultValue = "false", required = false)
    private Boolean includeTestScope;

    @Parameter(property = "includeSystemScope", defaultValue = "true", required = false)
    private Boolean includeSystemScope;

    @Parameter(property = "includeLicenseText", defaultValue = "false", required = false)
    private Boolean includeLicenseText;

    @Parameter(property = "excludeTypes", required = false)
    private String[] excludeTypes;

    @Parameter(property = "excludeArtifactId", required = false)
    protected String[] excludeArtifactId;

    @Parameter(property = "excludeTestProject", defaultValue = "false", required = false)
    protected Boolean excludeTestProject;

    @org.apache.maven.plugins.annotations.Component(hint = "default")
    private MavenProjectHelper mavenProjectHelper;

    @org.apache.maven.plugins.annotations.Component(hint = "default")
    private DependencyCollectorBuilder dependencyCollectorBuilder;

    @SuppressWarnings("CanBeFinal")
    @Parameter(property = "cyclonedx.skip", defaultValue = "false", required = false)
    private boolean skip = false;

    @SuppressWarnings("CanBeFinal")
    @Parameter(property = "cyclonedx.skipAttach", defaultValue = "false", required = false)
    private boolean skipAttach = false;

    @SuppressWarnings("CanBeFinal")
    @Parameter(property = "cyclonedx.verbose", defaultValue = "true", required = false)
    private boolean verbose = true;

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
     * <a href="/shared/maven-dependency-analyzer/">maven-dependency-analyzer</a> is used. To use this, you must declare
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

    public MavenSession getSession() {
        return session;
    }

    /**
     * Returns a reference to the current project. This method is used instead
     * of auto-binding the project via component annotation in concrete
     * implementations of this. If the child has a
     * <code>@Component MavenProject project;</code> defined then the abstract
     * class (i.e. this class) will not have access to the current project (just
     * the way Maven works with the binding).
     *
     * @return returns a reference to the current project
     */
    protected MavenProject getProject() {
        return project;
    }

    /**
     * Returns the list of Maven Projects in this build.
     *
     * @return the list of Maven Projects in this build
     */
    protected List<MavenProject> getReactorProjects() {
        return reactorProjects;
    }

    /**
     * Returns the CycloneDX schema version the BOM will comply with.
     *
     * @return the CycloneDX schema version
     */
    public String getSchemaVersion() {
        return schemaVersion;
    }

    /**
     * Returns the CycloneDX output format that should be generated.
     *
     * @return the CycloneDX output format
     */
    public String getOutputFormat() {
        return outputFormat;
    }

    /**
     * Returns the CycloneDX output name that should be generated.
     *
     * @return the CycloneDX output name
     */
    public String getOutputName() {
        return outputName;
    }

    /**
     * Returns if the resulting BOM should contain a unique serial number.
     *
     * @return true if serial number should be included, otherwise false
     */
    public Boolean getIncludeBomSerialNumber() {
        return includeBomSerialNumber;
    }

    /**
     * Returns if compile scoped artifacts should be included in bom.
     *
     * @return true if artifact should be included, otherwise false
     */
    protected Boolean getIncludeCompileScope() {
        return includeCompileScope;
    }

    /**
     * Returns if provided scoped artifacts should be included in bom.
     *
     * @return true if artifact should be included, otherwise false
     */
    protected Boolean getIncludeProvidedScope() {
        return includeProvidedScope;
    }

    /**
     * Returns if runtime scoped artifacts should be included in bom.
     *
     * @return true if artifact should be included, otherwise false
     */
    protected Boolean getIncludeRuntimeScope() {
        return includeRuntimeScope;
    }

    /**
     * Returns if test scoped artifacts should be included in bom.
     *
     * @return true if artifact should be included, otherwise false
     */
    protected Boolean getIncludeTestScope() {
        return includeTestScope;
    }

    /**
     * Returns if system scoped artifacts should be included in bom.
     *
     * @return true if artifact should be included, otherwise false
     */
    protected Boolean getIncludeSystemScope() {
        return includeSystemScope;
    }

    /**
     * Returns if license text should be included in bom.
     *
     * @return true if license text should be included, otherwise false
     */
    public Boolean getIncludeLicenseText() {
        return includeLicenseText;
    }


    /**
     * Returns if excluded types are defined.
     *
     * @return an array of excluded types
     */
    public String[] getExcludeTypes() {
        return excludeTypes;
    }

    /**
     * Returns if excluded ArtifactId are defined.
     *
     * @return an array of excluded Artifact Id
     */
    public String[] getExcludeArtifactId() {
        return excludeArtifactId;
    }

    /**
     * Returns if project artifactId with the word test should be excluded in bom.
     *
     * @return true if artifactId should be excluded, otherwise false
     */
    protected Boolean getExcludeTestProject() {
        return excludeTestProject;
    }

    /**
     * Returns if CycloneDX execution should be skipped.
     *
     * @return true if execution should be skipped, otherwise false
     */
    protected Boolean getSkip() {
        return skip;
    }

    /**
     * Returns if reactor projects should be included or not.
     *
     * @return true if reactor projects should be included, otherwise false
     */
    protected Boolean getOutputReactorProjects() {
        return outputReactorProjects;
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
        final Properties properties = readPluginProperties();
        final Metadata metadata = new Metadata();
        final Tool tool = new Tool();
        tool.setVendor(properties.getProperty("vendor"));
        tool.setName(properties.getProperty("name"));
        tool.setVersion(properties.getProperty("version"));
        // Attempt to add hash values from the current mojo
        final Artifact self = new DefaultArtifact(properties.getProperty("groupId"), properties.getProperty("artifactId"),
                properties.getProperty("version"), SCOPE_COMPILE, "jar", null, new DefaultArtifactHandler());
        final Artifact resolved = this.getSession().getLocalRepository().find(self);
        if (resolved != null) {
            try {
                resolved.setFile(new File(resolved.getFile() + ".jar"));
                tool.setHashes(BomUtils.calculateHashes(resolved.getFile(), schemaVersion()));
            } catch (IOException e) {
                getLog().warn("Unable to calculate hashes of self", e);
            }
        }
        metadata.addTool(tool);
        final Component component = new Component();
        component.setGroup(project.getGroupId());
        component.setName(project.getArtifactId());
        component.setVersion(project.getVersion());
        component.setType(resolveProjectType());
        component.setPurl(generatePackageUrl(project.getArtifact()));
        component.setBomRef(component.getPurl());
        extractMetadata(project, component);
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
     * CycloneDX component./
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
            final MavenProject project = extractPom(artifact);
            if (project != null) {
                getClosestMetadata(artifact, project, component);
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
     * Resolves meta for an artifact. This method essentially does what an 'effective pom' would do,
     * but for an artifact instead of a project. This method will attempt to resolve metadata at
     * the lowest level of the inheritance tree and work its way up.
     * @param artifact the artifact to resolve metadata for
     * @param project the associated project for the artifact
     * @param component the component to populate data for
     */
    private void getClosestMetadata(Artifact artifact, MavenProject project, Component component) {
        extractMetadata(project, component);
        if (project.getParent() != null) {
            getClosestMetadata(artifact, project.getParent(), component);
        } else if (project.getModel().getParent() != null) {
            final MavenProject parentProject = retrieveParentProject(artifact, project);
            if (parentProject != null) {
                getClosestMetadata(artifact, parentProject, component);
            }
        }
    }

    /**
     * Extracts data from a project and adds the data to the component.
     * @param project the project to extract data from
     * @param component the component to add data to
     */
    private void extractMetadata(MavenProject project, Component component) {
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
            if (project.getOrganization() != null && project.getOrganization().getUrl() != null) {
                if (!doesComponentHaveExternalReference(component, ExternalReference.Type.WEBSITE)) {
                    addExternalReference(ExternalReference.Type.WEBSITE, project.getOrganization().getUrl(), component);
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

    /**
     * Retrieves the parent pom for an artifact (if any). The parent pom may contain license,
     * description, and other metadata whereas the artifact itself may not.
     * @param artifact the artifact to retrieve the parent pom for
     * @param project the maven project the artifact is part of
     */
    private MavenProject retrieveParentProject(final Artifact artifact, final MavenProject project) {
        if (artifact.getFile() == null || artifact.getFile().getParentFile() == null || !isDescribedArtifact(artifact)) {
            return null;
        }
        final Model model = project.getModel();
        if (model.getParent() != null) {
            final Parent parent = model.getParent();
            // Navigate out of version, artifactId, and first (possibly only) level of groupId
            final StringBuilder getout = new StringBuilder("../../../");
            final int periods = artifact.getGroupId().length() - artifact.getGroupId().replace(".", "").length();
            for (int i= 0; i< periods; i++) {
                getout.append("../");
            }
            final File parentFile = new File(artifact.getFile().getParentFile(), getout + parent.getGroupId().replace(".", "/") + "/" + parent.getArtifactId() + "/" + parent.getVersion() + "/" + parent.getArtifactId() + "-" + parent.getVersion() + ".pom");
            if (parentFile.exists() && parentFile.isFile()) {
                try {
                    return readPom(parentFile.getCanonicalFile());
                } catch (Exception e) {
                    getLog().error("An error occurred retrieving an artifacts parent pom", e);
                }
            }
        }
        return null;
    }

    /**
     * Extracts a pom from an artifacts jar file and creates a MavenProject from it.
     * @param artifact the artifact to extract the pom from
     * @return a Maven project
     */
    private MavenProject extractPom(Artifact artifact) {
        if (!isDescribedArtifact(artifact)) {
            return null;
        }
        if (artifact.getFile() != null && artifact.getFile().isFile()) {
            try {
                final JarFile jarFile = new JarFile(artifact.getFile());
                final JarEntry entry = jarFile.getJarEntry("META-INF/maven/"+ artifact.getGroupId() + "/" + artifact.getArtifactId() + "/pom.xml");
                if (entry != null) {
                    try (final InputStream input = jarFile.getInputStream(entry)) {
                        return readPom(input);
                    }
                } else {
                    // Read the pom.xml directly from the filesystem as a fallback
                    Path artifactPath = Paths.get(artifact.getFile().getPath());
                    String pomFilename = artifactPath.getFileName().toString().replace("jar", "pom");
                    Path pomPath = artifactPath.resolveSibling(pomFilename);
                    if (Files.exists(pomPath)) {
                       try (final InputStream input = Files.newInputStream(pomPath)) {
                          return readPom(input);
                       }
                    }
                }
            } catch (IOException e) {
                getLog().error("An error occurred attempting to extract POM from artifact", e);
            }
        }
        return null;
    }

    /**
     * Reads a POM and creates a MavenProject from it.
     * @param file the file object of the POM to read
     * @return a MavenProject
     * @throws IOException oops
     */
    private MavenProject readPom(File file) throws IOException {
        try (final FileInputStream in = new FileInputStream(file)) {
            return readPom(in);
        }
    }

    /**
     * Reads a POM and creates a MavenProject from it.
     * @param in the inputstream to read from
     * @return a MavenProject
     */
    private MavenProject readPom(InputStream in) {
        try {
            final MavenXpp3Reader mavenreader = new MavenXpp3Reader();
            try (final InputStreamReader reader = new InputStreamReader(new BOMInputStream(in))) {
                final Model model = mavenreader.read(reader);
                return new MavenProject(model);
            }
            //if you don't like BOMInputStream you can also escape the error this way:
//            catch (XmlPullParserException xppe){
//               if (! xppe.getMessage().startsWith("only whitespace content allowed before start tag")){
//                   throw xppe;
//               } else {
//                   getLog().debug("The pom.xml starts with a Byte Order Marker and MavenXpp3Reader doesn't like it");
//               }
//            }
        } catch (XmlPullParserException | IOException e) {
            getLog().error("An error occurred attempting to read POM", e);
        }
        return null;
    }

    protected void execute(Set<Component> components, Set<Dependency> dependencies, MavenProject mavenProject) throws MojoExecutionException {
        try {
            getLog().info(MESSAGE_CREATING_BOM);
            final Bom bom = new Bom();
            if (schemaVersion().getVersion() >= 1.1 && includeBomSerialNumber) {
                bom.setSerialNumber("urn:uuid:" + UUID.randomUUID());
            }
            if (schemaVersion().getVersion() >= 1.2) {
                final Metadata metadata = convert(mavenProject);
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

            createBom(bom, mavenProject);

        } catch (GeneratorException | ParserConfigurationException | IOException e) {
            throw new MojoExecutionException("An error occurred executing " + this.getClass().getName() + ": " + e.getMessage(), e);
        }
    }

    private void createBom(Bom bom, MavenProject mavenProject) throws ParserConfigurationException, IOException, GeneratorException,
            MojoExecutionException {
        if (outputFormat.trim().equalsIgnoreCase("all") || outputFormat.trim().equalsIgnoreCase("xml")) {
            final BomXmlGenerator bomGenerator = BomGeneratorFactory.createXml(schemaVersion(), bom);
            bomGenerator.generate();
            final String bomString = bomGenerator.toXmlString();
            final File bomFile = new File(mavenProject.getBasedir(), "target/" + outputName + ".xml");
            getLog().info(String.format(MESSAGE_WRITING_BOM, "XML", bomFile.getAbsolutePath()));
            FileUtils.write(bomFile, bomString, StandardCharsets.UTF_8, false);

            getLog().info(String.format(MESSAGE_VALIDATING_BOM, "XML", bomFile.getAbsolutePath()));
            final XmlParser bomParser = new XmlParser();
            if (!bomParser.isValid(bomFile, schemaVersion())) {
                throw new MojoExecutionException(MESSAGE_VALIDATION_FAILURE);
            }
            if (!skipAttach) {
                mavenProjectHelper.attachArtifact(mavenProject, "xml", "cyclonedx", bomFile);
            }
        }
        if (outputFormat.trim().equalsIgnoreCase("all") || outputFormat.trim().equalsIgnoreCase("json")) {
            final BomJsonGenerator bomGenerator = BomGeneratorFactory.createJson(schemaVersion(), bom);
            final String bomString = bomGenerator.toJsonString();
            final File bomFile = new File(mavenProject.getBasedir(), "target/" + outputName + ".json");
            getLog().info(String.format(MESSAGE_WRITING_BOM, "JSON", bomFile.getAbsolutePath()));
            FileUtils.write(bomFile, bomString, StandardCharsets.UTF_8, false);

            getLog().info(String.format(MESSAGE_VALIDATING_BOM, "JSON", bomFile.getAbsolutePath()));
            final JsonParser bomParser = new JsonParser();
            if (!bomParser.isValid(bomFile, schemaVersion())) {
                throw new MojoExecutionException(MESSAGE_VALIDATION_FAILURE);
            }
            if (!skipAttach) {
                mavenProjectHelper.attachArtifact(mavenProject, "json", "cyclonedx", bomFile);
            }
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
        if (schemaVersion != null && schemaVersion.equals("1.0")) {
            return CycloneDxSchema.Version.VERSION_10;
        } else if (schemaVersion != null && schemaVersion.equals("1.1")) {
            return CycloneDxSchema.Version.VERSION_11;
        } else if (schemaVersion != null && schemaVersion.equals("1.2")) {
            return CycloneDxSchema.Version.VERSION_12;
        } else if (schemaVersion != null && schemaVersion.equals("1.3")) {
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
