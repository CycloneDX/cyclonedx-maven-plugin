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
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.repository.RepositorySystem;
import org.cyclonedx.Version;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.Hash;
import org.cyclonedx.model.License;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.model.Metadata;
import org.cyclonedx.model.Tool;
import org.cyclonedx.model.metadata.ToolInformation;
import org.cyclonedx.util.BomUtils;
import org.cyclonedx.util.LicenseResolver;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;

@Singleton
@Named
public class DefaultModelConverter implements ModelConverter {
    private final Logger logger = LoggerFactory.getLogger(DefaultModelConverter.class);

    @Inject
    private MavenSession session;

    /**
     * The RepositorySystem to inject. Used by this component for building effective poms.
     */
    @Inject
    private RepositorySystem repositorySystem;

    /**
     * The ProjectBuilder to inject. Used by this component for building effective poms.
     */
    @Inject
    private ProjectBuilder mavenProjectBuilder;

    public DefaultModelConverter() {
    }

    @Override
    public String generatePackageUrl(final Artifact artifact) {
        return generatePackageUrl(artifact, true);
    }

    @Override
    public String generateVersionlessPackageUrl(final Artifact artifact) {
        return generatePackageUrl(artifact, false);
    }

    private String generatePackageUrl(final Artifact artifact, final boolean includeVersion) {
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
        final String version = includeVersion ? artifact.getBaseVersion() : null;
        return generatePackageUrl(artifact.getGroupId(), artifact.getArtifactId(), version, qualifiers, null);
    }

    @Override
    public String generatePackageUrl(final org.eclipse.aether.artifact.Artifact artifact) {
        return generatePackageUrl(artifact, true, true);
    }

    @Override
    public String generateVersionlessPackageUrl(final org.eclipse.aether.artifact.Artifact artifact) {
        return generatePackageUrl(artifact, false, true);
    }

    @Override
    public String generateClassifierlessPackageUrl(final org.eclipse.aether.artifact.Artifact artifact) {
        return generatePackageUrl(artifact, true, false);
    }

    private boolean isEmpty(final String value) {
        return (value == null) || (value.length() == 0);
    }

    private String generatePackageUrl(final org.eclipse.aether.artifact.Artifact artifact, final boolean includeVersion, final boolean includeClassifier) {
        TreeMap<String, String> qualifiers = null;
        final String type = artifact.getProperties().get(ArtifactProperties.TYPE);
        final String classifier = artifact.getClassifier();
        if (!isEmpty(type) || (includeClassifier && !isEmpty(classifier))) {
            qualifiers = new TreeMap<>();
            if (!isEmpty(type)) {
                qualifiers.put("type", type);
            }
            if (includeClassifier && !isEmpty(classifier)) {
                qualifiers.put("classifier", classifier);
            }
        }
        final String version = includeVersion ? artifact.getBaseVersion() : null;
        return generatePackageUrl(artifact.getGroupId(), artifact.getArtifactId(), version, qualifiers, null);
    }

    private String generatePackageUrl(String groupId, String artifactId, String version, TreeMap<String, String> qualifiers, String subpath) {
        try {
            return new PackageURL(PackageURL.StandardTypes.MAVEN, groupId, artifactId, version, qualifiers, subpath).canonicalize();
        } catch(MalformedPackageURLException e) {
          logger.warn("An unexpected issue occurred attempting to create a PackageURL for "
                + groupId + ":" + artifactId + ":" + version, e);
        }
        return null;
    }

    @Override
    public Component convertMavenDependency(Artifact artifact, Version schemaVersion, boolean includeLicenseText) {
        final Component component = new Component();
        component.setGroup(artifact.getGroupId());
        component.setName(artifact.getArtifactId());
        component.setVersion(artifact.getBaseVersion());
        component.setType(Component.Type.LIBRARY);
         
        try {
            logger.debug(BaseCycloneDxMojo.MESSAGE_CALCULATING_HASHES);
            component.setHashes(BomUtils.calculateHashes(artifact.getFile(), schemaVersion));
        } catch (IOException e) {
            logger.error("Error encountered calculating hashes", e);
        }
        if (Version.VERSION_10 == schemaVersion) {
            component.setModified(isModified(artifact));
        }
        component.setPurl(generatePackageUrl(artifact));
        if (Version.VERSION_10 != schemaVersion) {
            component.setBomRef(component.getPurl());
        }
        try {
            final MavenProject project = getEffectiveMavenProject(artifact);
            
            if (project != null) {
                String projectType = getPluginConfiguration(project, BaseCycloneDxMojo.PROJECT_TYPE);
                if (projectType != null) {
                    component.setType(resolveProjectType(projectType));
                }
                extractComponentMetadata(project, component, schemaVersion, includeLicenseText);
            }
        } catch (ProjectBuildingException e) {
            if (logger.isDebugEnabled()) {
                logger.warn("Unable to create Maven project for " + artifact.getId() + " from repository.", e);
            } else {
                logger.warn("Unable to create Maven project for " + artifact.getId() + " from repository.");
            }
        }
        return component;

    }

    public String getPluginConfiguration(MavenProject project, String property) {
        Plugin plugin = project.getPlugin(BaseCycloneDxMojo.CYCLONEDX_PLUGIN_KEY);
        Xpp3Dom configuration = (plugin == null) ? null : (Xpp3Dom) plugin.getConfiguration();
        Xpp3Dom value = (configuration == null) ? null : configuration.getChild(property);
        return (value == null) ? null : value.getValue();
    }

    private static void setExternalReferences(Component component, ExternalReference[] externalReferences) {
        if (externalReferences == null || externalReferences.length == 0) {
            return;
        }
        // We need a mutable `List`, hence `Arrays.asList()` won't work.
        List<ExternalReference> externalReferences_ = Arrays.stream(externalReferences).collect(Collectors.toList());
        component.setExternalReferences(externalReferences_);
    }

    private boolean isModified(Artifact artifact) {
        //todo: compare hashes + GAV with what the artifact says against Maven Central to determine if component has been modified.
        return false;
    }

    /**
     * Extracts data from a project and adds the data to the component.
     * @param project the project to extract data from
     * @param component the component to add data to
     */
    private void extractComponentMetadata(MavenProject project, Component component, Version schemaVersion, boolean includeLicenseText) {
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
            // If we don't already have license information, retrieve it, as long as it is not empty.
            if (project.getLicenses() != null && project.getLicenses().stream().anyMatch(l -> !isLicenseBlank(l))) {
                component.setLicenseChoice(resolveMavenLicenses(project.getLicenses(), schemaVersion, includeLicenseText));
            }
        }
        if (Version.VERSION_10 != schemaVersion) {
            addExternalReference(ExternalReference.Type.WEBSITE, project.getUrl(), component);
            if (project.getCiManagement() != null) {
                addExternalReference(ExternalReference.Type.BUILD_SYSTEM, project.getCiManagement().getUrl(), component);
            }
            if (project.getDistributionManagement() != null) {
                addExternalReference(ExternalReference.Type.DISTRIBUTION, project.getDistributionManagement().getDownloadUrl(), component);
                if (project.getDistributionManagement().getRepository() != null) {
                    ExternalReference.Type type =
                            (schemaVersion.getVersion() < 1.5) ? ExternalReference.Type.DISTRIBUTION : ExternalReference.Type.DISTRIBUTION_INTAKE;
                    addExternalReference(type, project.getDistributionManagement().getRepository().getUrl(), component);
                }
            }
            if (project.getIssueManagement() != null) {
                addExternalReference(ExternalReference.Type.ISSUE_TRACKER, project.getIssueManagement().getUrl(), component);
            }
            if (project.getMailingLists() != null && project.getMailingLists().size() > 0) {
                for (MailingList list : project.getMailingLists()) {
                    String url = list.getArchive();
                    if (url == null) {
                        url = list.getSubscribe();
                    }
                    addExternalReference(ExternalReference.Type.MAILING_LIST, url, component);
                }
            }
            if (project.getScm() != null) {
                addExternalReference(ExternalReference.Type.VCS, project.getScm().getUrl(), component);
            }
        }
    }

    /**
     * This method generates an 'effective pom' for an artifact.
     * @param artifact the artifact to generate an effective pom of
     * @throws ProjectBuildingException if an error is encountered
     */
    private MavenProject getEffectiveMavenProject(final Artifact artifact) throws ProjectBuildingException {
        final Artifact pomArtifact = repositorySystem.createProjectArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
        final ProjectBuildingResult build = mavenProjectBuilder.build(pomArtifact,
                session.getProjectBuildingRequest().setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL).setProcessPlugins(false)
        );
        return build.getProject();
    }

    private void addExternalReference(final ExternalReference.Type referenceType, final String url, final Component component) {
        if (isBlank(url) || doesComponentHaveExternalReference(component, referenceType)) {
            return;
        }
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

    private boolean doesComponentHaveExternalReference(final Component component, final ExternalReference.Type referenceType) {
        if (component.getExternalReferences() != null && !component.getExternalReferences().isEmpty()) {
            for (final ExternalReference ref : component.getExternalReferences()) {
                if (referenceType == ref.getType()) {
                    return true;
                }
            }
        }
        return false;
    }

    private LicenseChoice resolveMavenLicenses(final List<org.apache.maven.model.License> projectLicenses, final Version schemaVersion, boolean includeLicenseText) {
        final LicenseChoice licenseChoice = new LicenseChoice();
        for (org.apache.maven.model.License artifactLicense : projectLicenses) {
            boolean resolved = false;
            if (artifactLicense.getName() != null) {
                final LicenseChoice resolvedByName =
                    LicenseResolver.resolve(artifactLicense.getName(), includeLicenseText);
                resolved = resolveLicenseInfo(licenseChoice, resolvedByName, schemaVersion);
            }
            if (artifactLicense.getUrl() != null && !resolved) {
                final LicenseChoice resolvedByUrl =
                    LicenseResolver.resolve(artifactLicense.getUrl(), includeLicenseText);
                resolved = resolveLicenseInfo(licenseChoice, resolvedByUrl, schemaVersion);
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

    private boolean resolveLicenseInfo(final LicenseChoice licenseChoice, final LicenseChoice licenseChoiceToResolve, final Version schemaVersion) {
        if (licenseChoiceToResolve != null) {
            if (licenseChoiceToResolve.getLicenses() != null && !licenseChoiceToResolve.getLicenses().isEmpty()) {
                licenseChoice.addLicense(licenseChoiceToResolve.getLicenses().get(0));
                return true;
            }
            else if (licenseChoiceToResolve.getExpression() != null && Version.VERSION_10 != schemaVersion) {
                licenseChoice.setExpression(licenseChoiceToResolve.getExpression());
                return true;
            }
        }
        return false;
    }

    @Override
    public Metadata convertMavenProject(final MavenProject project, String projectType, Version schemaVersion, boolean includeLicenseText, ExternalReference[] externalReferences) {
        final Metadata metadata = new Metadata();

        // prepare properties and hash values from the current mojo
        final Properties properties = readPluginProperties();
        List<Hash> hashes = null;
        final Artifact self = new DefaultArtifact(properties.getProperty("groupId"), properties.getProperty("artifactId"),
                properties.getProperty("version"), Artifact.SCOPE_COMPILE, "jar", null, new DefaultArtifactHandler());
        final Artifact resolved = session.getLocalRepository().find(self);
        if (resolved != null) {
            try {
                resolved.setFile(new File(resolved.getFile() + ".jar"));
                hashes = BomUtils.calculateHashes(resolved.getFile(), schemaVersion);
            } catch (IOException e) {
                logger.warn("Unable to calculate hashes of self", e);
            }
        }
        if (schemaVersion.compareTo(Version.VERSION_15) < 0) {
            // CycloneDX up to 1.4+ use metadata.tools.tool
            final Tool tool = new Tool();
            tool.setVendor(properties.getProperty("vendor"));
            tool.setName(properties.getProperty("name"));
            tool.setVersion(properties.getProperty("version"));
            tool.setHashes(hashes);
            metadata.addTool(tool);
        } else {
            // CycloneDX 1.5+: use metadata.tools.component
            ToolInformation toolInfo = new ToolInformation();
            Component toolComponent = new Component();
            toolComponent.setType(Component.Type.LIBRARY);
            toolComponent.setGroup(properties.getProperty("groupId"));
            toolComponent.setName(properties.getProperty("artifactId"));
            toolComponent.setVersion(properties.getProperty("version"));
            toolComponent.setDescription(properties.getProperty("name"));
            toolComponent.setAuthor(properties.getProperty("vendor"));
            toolComponent.setHashes(hashes);
            toolInfo.setComponents(Collections.singletonList(toolComponent));
            metadata.setToolChoice(toolInfo);
        }

        final Component component = new Component();
        component.setGroup(project.getGroupId());
        component.setName(project.getArtifactId());
        component.setVersion(project.getVersion());
        component.setType(resolveProjectType(projectType));
        component.setPurl(generatePackageUrl(project.getArtifact()));
        component.setBomRef(component.getPurl());
        setExternalReferences(component, externalReferences);
        extractComponentMetadata(project, component, schemaVersion, includeLicenseText);
        metadata.setComponent(component);

        return metadata;
    }

    private Properties readPluginProperties() {
        final Properties props = new Properties();
        try {
            props.load(this.getClass().getClassLoader().getResourceAsStream("plugin.properties"));
        } catch (NullPointerException | IOException e) {
            logger.warn("Unable to load plugin.properties", e);
        }
        return props;
    }

    private Component.Type resolveProjectType(String projectType) {
        for (Component.Type type: Component.Type.values()) {
            if (type.getTypeName().equalsIgnoreCase(projectType)) {
                return type;
            }
        }
        logger.warn("Invalid project type. Defaulting to 'library'");
        logger.warn("Valid types are:");
        for (Component.Type type: Component.Type.values()) {
            logger.warn("  " + type.getTypeName());
        }
        return Component.Type.LIBRARY;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty() || s.trim().isEmpty();
    }

    private static boolean isLicenseBlank(org.apache.maven.model.License license) {
        return isBlank(license.getName()) && isBlank(license.getUrl());
    }
}
