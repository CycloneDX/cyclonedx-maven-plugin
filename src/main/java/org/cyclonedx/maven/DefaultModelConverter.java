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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.cyclonedx.CycloneDxSchema;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.License;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.model.Metadata;
import org.cyclonedx.model.Tool;
import org.cyclonedx.util.BomUtils;
import org.cyclonedx.util.LicenseResolver;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;

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
    public Component convert(MojoExecution execution, Artifact artifact, CycloneDxSchema.Version schemaVersion, boolean includeLicenseText) {

        // Populate basic fields from the `Artifact` instance
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
        if (CycloneDxSchema.Version.VERSION_10 == schemaVersion) {
            component.setModified(isModified(artifact));
        }
        component.setPurl(generatePackageUrl(artifact));
        if (CycloneDxSchema.Version.VERSION_10 != schemaVersion) {
            component.setBomRef(component.getPurl());
        }

        // Read the project
        MavenProject project = null;
        try {
            project = getEffectiveMavenProject(artifact);
        } catch (ProjectBuildingException error) {
            if (logger.isDebugEnabled()) {
                logger.warn("Unable to create Maven project for `{}` from repository.", artifact.getId(), error);
            } else {
                logger.warn("Unable to create Maven project for `{}` from repository.", artifact.getId());
            }
        }

        if (project != null) {

            // Populate external references
            List<ExternalReference> externalReferences = extractExternalReferences(project, execution);
            component.setExternalReferences(externalReferences);

            // Extract the rest of the metadata for JARs, i.e., *described* artifacts
            if (isDescribedArtifact(artifact)) {
                extractComponentMetadata(project, component, schemaVersion, includeLicenseText);
            }

        }

        // Return the enriched component
        return component;

    }

    private List<ExternalReference> extractExternalReferences(MavenProject project, MojoExecution activeExecution) {
        Plugin activePlugin = activeExecution.getPlugin();
        return project
                .getBuild()
                .getPlugins()
                .stream()
                .filter(plugin -> activePlugin.getGroupId().equals(plugin.getGroupId()) && activePlugin.getArtifactId().equals(plugin.getArtifactId()))
                .findFirst()
                .map(plugin -> extractExternalReferences(plugin, activeExecution))
                .orElseGet(ArrayList::new);
    }

    private static List<ExternalReference> extractExternalReferences(Plugin plugin, MojoExecution activeExecution) {

        // Collect external references from the execution configuration
        List<ExternalReference> executionExternalReferences = plugin
                .getExecutions()
                .stream()
                .filter(execution -> activeExecution.getExecutionId().equals(execution.getId()))
                .flatMap(execution -> {
                    Xpp3Dom executionConfig = (Xpp3Dom) execution.getConfiguration();
                    return ExternalReferenceConfigDto.parseDom(executionConfig).stream();
                })
                .collect(Collectors.toList());

        // Collect external references from the plugin configuration
        Xpp3Dom pluginConfig = (Xpp3Dom) plugin.getConfiguration();
        List<ExternalReference> pluginExternalReferences = ExternalReferenceConfigDto.parseDom(pluginConfig);

        // Combine collected external references
        return Stream
                .concat(executionExternalReferences.stream(), pluginExternalReferences.stream())
                .distinct()
                .collect(Collectors.toList());

    }

    private static final class ExternalReferenceConfigDto {

        private static final XmlMapper MAPPER = XmlMapper
                .builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build();

        private static List<ExternalReference> parseDom(@Nullable Xpp3Dom dom) {
            if (dom == null) {
                return new ArrayList<>();
            }
            String xml = dom.toString();
            try {
                ExternalReferenceConfigDto dto = MAPPER.readValue(xml, ExternalReferenceConfigDto.class);
                @Nullable List<ExternalReference> externalReferences = dto.externalReferences;
                return externalReferences != null ? externalReferences : new ArrayList<>();
            } catch (JsonProcessingException error) {
                throw new RuntimeException(error);
            }
        }

        @JsonProperty
        private List<ExternalReference> externalReferences;

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
     * Extracts data from a project and adds the data to the component.
     * @param project the project to extract data from
     * @param component the component to add data to
     */
    private void extractComponentMetadata(MavenProject project, Component component, CycloneDxSchema.Version schemaVersion, boolean includeLicenseText) {
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
                component.setLicenseChoice(resolveMavenLicenses(project.getLicenses(), schemaVersion, includeLicenseText));
            }
        }
        if (CycloneDxSchema.Version.VERSION_10 != schemaVersion) {
            addExternalReference(ExternalReference.Type.WEBSITE, project.getUrl(), component);
            if (project.getCiManagement() != null) {
                addExternalReference(ExternalReference.Type.BUILD_SYSTEM, project.getCiManagement().getUrl(), component);
            }
            if (project.getDistributionManagement() != null) {
                addExternalReference(ExternalReference.Type.DISTRIBUTION, project.getDistributionManagement().getDownloadUrl(), component);
                if (project.getDistributionManagement().getRepository() != null) {
                    addExternalReference(ExternalReference.Type.DISTRIBUTION, project.getDistributionManagement().getRepository().getUrl(), component);
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
        if (isURLBlank(url) || doesComponentHaveExternalReference(component, referenceType)) {
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

    private LicenseChoice resolveMavenLicenses(final List<org.apache.maven.model.License> projectLicenses, final CycloneDxSchema.Version schemaVersion, boolean includeLicenseText) {
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

    private boolean resolveLicenseInfo(final LicenseChoice licenseChoice, final LicenseChoice licenseChoiceToResolve, final CycloneDxSchema.Version schemaVersion)
    {
        if (licenseChoiceToResolve != null) {
            if (licenseChoiceToResolve.getLicenses() != null && !licenseChoiceToResolve.getLicenses().isEmpty()) {
                licenseChoice.addLicense(licenseChoiceToResolve.getLicenses().get(0));
                return true;
            }
            else if (licenseChoiceToResolve.getExpression() != null && CycloneDxSchema.Version.VERSION_10 != schemaVersion) {
                licenseChoice.setExpression(licenseChoiceToResolve.getExpression());
                return true;
            }
        }
        return false;
    }

    @Override
    public Metadata convert(final MavenProject project, String projectType, MojoExecution execution, CycloneDxSchema.Version schemaVersion, boolean includeLicenseText) {
        final Tool tool = new Tool();
        final Properties properties = readPluginProperties();
        tool.setVendor(properties.getProperty("vendor"));
        tool.setName(properties.getProperty("name"));
        tool.setVersion(properties.getProperty("version"));
        // Attempt to add hash values from the current mojo
        final Artifact self = new DefaultArtifact(properties.getProperty("groupId"), properties.getProperty("artifactId"),
                properties.getProperty("version"), Artifact.SCOPE_COMPILE, "jar", null, new DefaultArtifactHandler());
        final Artifact resolved = session.getLocalRepository().find(self);
        if (resolved != null) {
            try {
                resolved.setFile(new File(resolved.getFile() + ".jar"));
                tool.setHashes(BomUtils.calculateHashes(resolved.getFile(), schemaVersion));
            } catch (IOException e) {
                logger.warn("Unable to calculate hashes of self", e);
            }
        }

        final Component component = new Component();
        component.setGroup(project.getGroupId());
        component.setName(project.getArtifactId());
        component.setVersion(project.getVersion());
        component.setType(resolveProjectType(projectType));
        component.setPurl(generatePackageUrl(project.getArtifact()));
        component.setBomRef(component.getPurl());

        List<ExternalReference> externalReferences = extractExternalReferences(project, execution);
        component.setExternalReferences(externalReferences);
        extractComponentMetadata(project, component, schemaVersion, includeLicenseText);

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

    private static boolean isURLBlank(String url) {
        return url == null || url.isEmpty() || url.trim().length() == 0;
    }
}
