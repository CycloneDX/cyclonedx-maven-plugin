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
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package org.cyclonedx.maven;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.cyclonedx.BomGenerator;
import org.cyclonedx.BomGeneratorFactory;
import org.cyclonedx.BomParser;
import org.cyclonedx.CycloneDxSchema;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.License;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.util.BomUtils;
import org.cyclonedx.util.LicenseResolver;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public abstract class BaseCycloneDxMojo extends AbstractMojo {

    @Parameter(property = "session", readonly = true, required = true)
    private MavenSession session;

    @Parameter(property = "project", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "reactorProjects", readonly = true, required = true)
    private List<MavenProject> reactorProjects;

    @Parameter(property = "schemaVersion", defaultValue = "1.1", required = false)
    private String schemaVersion;

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

    @SuppressWarnings("CanBeFinal")
    @Parameter(property = "cyclonedx.skip", defaultValue = "false", required = false)
    private boolean skip = false;


    /**
     * Various messages sent to console.
     */
    protected static final String MESSAGE_RESOLVING_DEPS = "CycloneDX: Resolving Dependencies";
    protected static final String MESSAGE_CREATING_BOM = "CycloneDX: Creating BOM";
    protected static final String MESSAGE_CALCULATING_HASHES = "CycloneDX: Calculating Hashes";
    protected static final String MESSAGE_WRITING_BOM = "CycloneDX: Writing BOM";
    protected static final String MESSAGE_VALIDATING_BOM = "CycloneDX: Validating BOM";
    protected static final String MESSAGE_VALIDATION_FAILURE = "The BOM does not conform to the CycloneDX BOM standard as defined by the XSD";

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
     * Returns if CycloneDX execution should be skipped.
     *
     * @return true if execution should be skipped, otherwise false
     */
    protected Boolean getSkip() {
        return skip;
    }

    protected boolean shouldInclude(Artifact artifact) {
        if (artifact.getScope() == null) {
            return false;
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
     * Converts a Maven artifact (dependency or transitive dependency) into a
     * CycloneDX component./
     * @param artifact the artifact to convert
     * @return a CycloneDX component
     */
    protected Component convert(Artifact artifact) {
        final Component component = new Component();
        component.setGroup(artifact.getGroupId());
        component.setName(artifact.getArtifactId());
        component.setVersion(artifact.getVersion());
        component.setType(Component.Type.LIBRARY);
        try {
            getLog().debug(MESSAGE_CALCULATING_HASHES);
            component.setHashes(BomUtils.calculateHashes(artifact.getFile()));
        } catch (IOException e) {
            getLog().error("Error encountered calculating hashes", e);
        }
        if (CycloneDxSchema.Version.VERSION_10 == schemaVersion()) {
            component.setModified(isModified(artifact));
        }

        try {
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
            final PackageURL purl = new PackageURL(PackageURL.StandardTypes.MAVEN,
                    artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), qualifiers, null);
            component.setPurl(purl.canonicalize());
        } catch (MalformedPackageURLException e) {
            getLog().warn("An unexpected issue occurred attempting to create a PackageURL for " + component.getName(), e);
        }

        if (isDescribedArtifact(artifact)) {
            final MavenProject project = extractPom(artifact);
            if (project != null) {
                getClosestMetadata(artifact, project, component);
            }
        }
        return component;
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
    }

    private LicenseChoice resolveMavenLicenses(final List<org.apache.maven.model.License> projectLicenses) {
        final LicenseChoice licenseChoice = new LicenseChoice();
        for (org.apache.maven.model.License artifactLicense : projectLicenses) {
            boolean resolved = false;
            if (artifactLicense.getName() != null) {
                final LicenseChoice resolvedByName = LicenseResolver.resolve(artifactLicense.getName());
                if (resolvedByName != null) {
                    if (resolvedByName.getLicenses() != null && !resolvedByName.getLicenses().isEmpty()) {
                        resolved = true;
                        licenseChoice.addLicense(resolvedByName.getLicenses().get(0));
                    } else if (resolvedByName.getExpression() != null && CycloneDxSchema.Version.VERSION_10 != schemaVersion()) {
                        resolved = true;
                        licenseChoice.setExpression(resolvedByName.getExpression());
                    }
                }
            }
            if (artifactLicense.getUrl() != null && !resolved) {
                final LicenseChoice resolvedByUrl = LicenseResolver.resolve(artifactLicense.getUrl());
                if (resolvedByUrl != null) {
                    if (resolvedByUrl.getLicenses() != null && !resolvedByUrl.getLicenses().isEmpty()) {
                        resolved = true;
                        licenseChoice.addLicense(resolvedByUrl.getLicenses().get(0));
                    } else if (resolvedByUrl.getExpression() != null && CycloneDxSchema.Version.VERSION_10 != schemaVersion()) {
                        resolved = true;
                        licenseChoice.setExpression(resolvedByUrl.getExpression());
                    }
                }
            }
            if (artifactLicense.getName() != null && !resolved) {
                final License license = new License();;
                license.setName(artifactLicense.getName());
                licenseChoice.addLicense(license);
            }
        }
        return licenseChoice;
    }

    /**
     * Retrieves the parent pom for an artifact (if any). The parent pom may contain license,
     * description, and other metadata whereas the artifact itself may not.
     * @param artifact the artifact to retrieve the parent pom for
     * @param project the maven project the artifact is part of
     */
    private MavenProject retrieveParentProject(Artifact artifact, MavenProject project) {
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
        if (artifact.getFile() != null) {
            try {
                final JarFile jarFile = new JarFile(artifact.getFile());
                final JarEntry entry = jarFile.getJarEntry("META-INF/maven/"+ artifact.getGroupId() + "/" + artifact.getArtifactId() + "/pom.xml");
                if (entry != null) {
                    try (final InputStream input = jarFile.getInputStream(entry)) {
                        return readPom(input);
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
            try (final InputStreamReader reader = new InputStreamReader(in)) {
                final Model model = mavenreader.read(reader);
                return new MavenProject(model);
            }
        } catch (XmlPullParserException | IOException e) {
            getLog().error("An error occurred attempting to read POM", e);
        }
        return null;
    }

    protected void execute(Set<Component> components) throws MojoExecutionException{
        try {
            getLog().info(MESSAGE_CREATING_BOM);
            final Bom bom = new Bom();
            if (CycloneDxSchema.Version.VERSION_10 != schemaVersion() && includeBomSerialNumber) {
                bom.setSerialNumber("urn:uuid:" + UUID.randomUUID().toString());
            }
            bom.setComponents(new ArrayList<>(components));
            final BomGenerator bomGenerator = BomGeneratorFactory.create(schemaVersion(), bom);
            bomGenerator.generate();
            final String bomString = bomGenerator.toXmlString();
            final File bomFile = new File(project.getBasedir(), "target/bom.xml");
            getLog().info(MESSAGE_WRITING_BOM);
            FileUtils.write(bomFile, bomString, Charset.forName("UTF-8"), false);

            getLog().info(MESSAGE_VALIDATING_BOM);
            final BomParser bomParser = new BomParser();
            if (!bomParser.isValid(bomFile, schemaVersion())) {
                throw new MojoExecutionException(MESSAGE_VALIDATION_FAILURE);
            }

        } catch (ParserConfigurationException | TransformerException | IOException e) {
            throw new MojoExecutionException("An error occurred executing " + this.getClass().getName(), e);
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
    private CycloneDxSchema.Version schemaVersion() {
        if (schemaVersion != null && schemaVersion.equals("1.0")) {
            return CycloneDxSchema.Version.VERSION_10;
        } else {
            return CycloneDxSchema.Version.VERSION_11;
        }
    }

    protected void logParameters() {
        if (getLog().isInfoEnabled()) {
            getLog().info("CycloneDX: Parameters");
            getLog().info("------------------------------------------------------------------------");
            getLog().info("schemaVersion          : " + schemaVersion().name());
            getLog().info("includeBomSerialNumber : " + includeBomSerialNumber);
            getLog().info("includeCompileScope    : " + includeCompileScope);
            getLog().info("includeProvidedScope   : " + includeProvidedScope);
            getLog().info("includeRuntimeScope    : " + includeRuntimeScope);
            getLog().info("includeTestScope       : " + includeTestScope);
            getLog().info("includeSystemScope     : " + includeSystemScope);
            getLog().info("------------------------------------------------------------------------");
        }
    }

}
