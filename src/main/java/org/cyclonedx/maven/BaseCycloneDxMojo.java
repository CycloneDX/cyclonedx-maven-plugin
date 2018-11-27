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
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.cyclonedx.BomGenerator;
import org.cyclonedx.BomParser;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.License;
import org.cyclonedx.util.BomUtils;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public abstract class BaseCycloneDxMojo extends AbstractMojo {

    @Parameter(property = "session", readonly = true, required = true)
    private MavenSession session;

    @Parameter(property = "project", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "reactorProjects", readonly = true, required = true)
    private List<MavenProject> reactorProjects;

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

    protected Component convert(Artifact artifact) {
        Component component = new Component();
        component.setGroup(artifact.getGroupId());
        component.setName(artifact.getArtifactId());
        component.setVersion(artifact.getVersion());
        component.setType("library");
        try {
            getLog().debug(MESSAGE_CALCULATING_HASHES);
            component.setHashes(BomUtils.calculateHashes(artifact.getFile()));
        } catch (IOException e) {
            getLog().error("Error encountered calculating hashes", e);
        }
        component.setModified(isModified(artifact));

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
            PackageURL purl = new PackageURL(PackageURL.StandardTypes.MAVEN, artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), qualifiers, null);
            component.setPurl(purl.canonicalize());
        } catch (MalformedPackageURLException e) {
            // throw it away
        }

        MavenProject project = extractPom(artifact);
        if (project != null) {
            if (project.getOrganization() != null) {
                component.setPublisher(project.getOrganization().getName());
            }
            component.setDescription(project.getDescription());
            if (project.getLicenses() != null) {
                List<License> licenses = new ArrayList<>();
                for (org.apache.maven.model.License artifactLicense : project.getLicenses()) {
                    License license = new License();
                    // todo: possible resolution to SPDX license ID. Without resolution, we are forced to use only the license
                    // name since Maven doesn't enforce (or officially recommend) use of SPDX license IDs (stupid Maven)....
                    if (artifactLicense.getName() != null) {
                        license.setName(artifactLicense.getName());
                        licenses.add(license);
                    } else if (artifactLicense.getUrl() != null) {
                        license.setName(artifactLicense.getUrl());
                        licenses.add(license);
                    }
                }
                if (licenses.size() > 0) {
                    component.setLicenses(licenses);
                }
            }
        }
        return component;
    }

    private MavenProject extractPom(Artifact artifact) {
        if (artifact.getFile() != null) {
            try {
                JarFile jarFile = new JarFile(artifact.getFile());
                JarEntry entry = jarFile.getJarEntry("META-INF/maven/"+ artifact.getGroupId() + "/" + artifact.getArtifactId() + "/pom.xml");
                if (entry != null) {
                    MavenXpp3Reader mavenreader = new MavenXpp3Reader();
                    try (InputStream input = jarFile.getInputStream(entry);
                         InputStreamReader reader = new InputStreamReader(input)) {
                        Model model = mavenreader.read(reader);
                        return new MavenProject(model);
                    }
                }
            } catch (XmlPullParserException | IOException e) {
                // throw it away
            }
        }
        return null;
    }

    protected void execute(Set<Component> components) throws MojoExecutionException{
        try {
            getLog().info(MESSAGE_CREATING_BOM);
            BomGenerator bomGenerator = new BomGenerator(components);
            bomGenerator.generate();
            String bomString = bomGenerator.toXmlString();
            File bomFile = new File(project.getBasedir(), "target/bom.xml");
            getLog().info(MESSAGE_WRITING_BOM);
            FileUtils.write(bomFile, bomString, Charset.forName("UTF-8"), false);

            getLog().info(MESSAGE_VALIDATING_BOM);
            BomParser bomParser = new BomParser();
            if (!bomParser.isValid(bomFile)) {
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

    protected void logParameters() {
        if (getLog().isInfoEnabled()) {
            getLog().info("CycloneDX: Parameters");
            getLog().info("------------------------------------------------------------------------");
            getLog().info("includeCompileScope   : " + includeCompileScope);
            getLog().info("includeProvidedScope  : " + includeProvidedScope);
            getLog().info("includeRuntimeScope   : " + includeRuntimeScope);
            getLog().info("includeTestScope      : " + includeTestScope);
            getLog().info("includeSystemScope    : " + includeSystemScope);
            getLog().info("------------------------------------------------------------------------");
        }
    }

}
