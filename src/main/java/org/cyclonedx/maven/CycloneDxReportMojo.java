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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.cyclonedx.exception.ParseException;
import org.cyclonedx.model.Bom;
import org.cyclonedx.parsers.JsonParser;
import org.cyclonedx.parsers.Parser;
import org.cyclonedx.parsers.XmlParser;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Renders the CycloneDX SBOM of the project as a page of the Maven site: BOM metadata, components with their package
 * URL, licenses and hashes, and the dependency graph. The SBOM file is also copied next to the page, to be available
 * in its machine-readable form.
 * <p>
 * This report does not generate the SBOM: it reads the file written by the {@code makeBom}, {@code makeAggregateBom}
 * or {@code makePackageBom} goal, which therefore has to run before the site is rendered, for example with
 * {@code mvn package site}. The file is searched where these goals write it, as configured in the project
 * ({@code outputDirectory}, {@code outputName} and {@code outputFormat}), unless {@link #bomFile} is set. When no SBOM
 * file is found, the report is skipped.
 *
 * @since 2.10.0
 */
@Mojo(name = "report", defaultPhase = LifecyclePhase.SITE, threadSafe = true)
public class CycloneDxReportMojo extends AbstractMavenReport {
    /**
     * Goals of this plugin that write a BOM file.
     */
    private static final List<String> BOM_GOALS = Arrays.asList("makeBom", "makeAggregateBom", "makePackageBom");

    /**
     * The CycloneDX SBOM file to render, in JSON or XML format. When not set, the file is searched where the BOM goals
     * of this plugin configured in the project write it, then at the default location
     * <code>${project.build.directory}/bom.json</code> or <code>bom.xml</code>.
     *
     * @since 2.10.0
     */
    @Parameter(property = "cyclonedx.report.bomFile")
    private File bomFile;

    /**
     * Skip the report, like any goal of this plugin.
     *
     * @since 2.10.0
     */
    @Parameter(property = "cyclonedx.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    private boolean bomFileSearched;

    private File foundBomFile;

    @Override
    public String getOutputPath() {
        return "cyclonedx-sbom";
    }

    /**
     * @deprecated replaced by {@link #getOutputPath()} in Maven Reporting API, still required by its interface.
     */
    @Deprecated
    @Override
    public String getOutputName() {
        return getOutputPath();
    }

    @Override
    public String getName(Locale locale) {
        return getBundle(locale).getString("report.name");
    }

    @Override
    public String getDescription(Locale locale) {
        return getBundle(locale).getString("report.description");
    }

    @Override
    public boolean canGenerateReport() {
        return !skip && findBomFile() != null;
    }

    @Override
    protected void executeReport(Locale locale) throws MavenReportException {
        final File file = findBomFile();
        if (file == null) {
            // only possible when the report is invoked without checking canGenerateReport()
            throw new MavenReportException("No CycloneDX SBOM file found to render");
        }
        getLog().info("CycloneDX: Rendering SBOM " + file);

        final Bom bom;
        try {
            bom = readBom(file);
        } catch (ParseException | IOException e) {
            throw new MavenReportException("Unable to read CycloneDX SBOM " + file, e);
        }

        final List<String> copiedFiles = new ArrayList<>();
        for (File source : withOtherFormat(file)) {
            try {
                FileUtils.copyFile(source, new File(getReportOutputDirectory(), source.getName()));
            } catch (IOException e) {
                throw new MavenReportException("Unable to copy " + source + " to " + getReportOutputDirectory(), e);
            }
            copiedFiles.add(source.getName());
        }

        new CycloneDxReportRenderer(getSink(), bom, getBundle(locale), copiedFiles).render();
    }

    /**
     * Find the SBOM file to render, searching only once even if the site plugin calls {@link #canGenerateReport()}
     * several times.
     *
     * @return the SBOM file, or {@code null} if none is found
     */
    private File findBomFile() {
        if (!bomFileSearched) {
            bomFileSearched = true;
            final List<File> candidates = (bomFile != null)
                    ? Collections.singletonList(bomFile)
                    : candidateBomFiles(getProject(), session.getUserProperties());
            for (File candidate : candidates) {
                if (candidate.isFile()) {
                    foundBomFile = candidate;
                    break;
                }
            }
            if (foundBomFile == null) {
                getLog().info("Skipping CycloneDX report: no SBOM file found at " + candidates
                        + ". Generate it before the site, for example with \"mvn package site\", or configure bomFile.");
            }
        }
        return foundBomFile;
    }

    /**
     * List the files where the BOM goals of this plugin write the SBOM for the given project, in search order: first
     * from the configuration of each BOM goal execution, then from the goals default configuration (which also covers
     * goals invoked from the command line). For each location, the JSON file comes before the XML one.
     * <p>
     * Parameters are resolved like Maven does for the goals: execution configuration first, then user property, then
     * project property, then default value. Values that still contain an expression are ignored, as they cannot be
     * evaluated outside of the goal execution.
     *
     * @param project the Maven project
     * @param userProperties the user properties of the Maven session
     * @return the candidate SBOM files, existing or not
     */
    static List<File> candidateBomFiles(final MavenProject project, final Properties userProperties) {
        final Set<File> candidates = new LinkedHashSet<>();
        final Plugin plugin = project.getPlugin(BaseCycloneDxMojo.CYCLONEDX_PLUGIN_KEY);
        if (plugin != null) {
            for (PluginExecution execution : plugin.getExecutions()) {
                if (!Collections.disjoint(execution.getGoals(), BOM_GOALS)) {
                    addCandidates(candidates, project, userProperties, (Xpp3Dom) execution.getConfiguration());
                }
            }
        }
        addCandidates(candidates, project, userProperties, null);
        return new ArrayList<>(candidates);
    }

    private static void addCandidates(final Set<File> candidates, final MavenProject project,
                                      final Properties userProperties, final Xpp3Dom configuration) {
        final String directory = parameter(configuration, "outputDirectory", project, userProperties,
                project.getBuild().getDirectory());
        final String name = parameter(configuration, "outputName", project, userProperties, "bom");
        final String format = parameter(configuration, "outputFormat", project, userProperties, "all");
        if (directory == null || directory.contains("${") || name.contains("${")) {
            return;
        }

        File outputDirectory = new File(directory);
        if (!outputDirectory.isAbsolute() && project.getBasedir() != null) {
            outputDirectory = new File(project.getBasedir(), directory);
        }
        if ("all".equalsIgnoreCase(format) || "json".equalsIgnoreCase(format)) {
            candidates.add(new File(outputDirectory, name + ".json"));
        }
        if ("all".equalsIgnoreCase(format) || "xml".equalsIgnoreCase(format)) {
            candidates.add(new File(outputDirectory, name + ".xml"));
        }
    }

    private static String parameter(final Xpp3Dom configuration, final String name, final MavenProject project,
                                    final Properties userProperties, final String defaultValue) {
        final Xpp3Dom child = (configuration == null) ? null : configuration.getChild(name);
        String value = (child == null) ? null : child.getValue();
        if (isBlank(value)) {
            value = userProperties.getProperty(name);
        }
        if (isBlank(value)) {
            value = project.getProperties().getProperty(name);
        }
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static boolean isBlank(final String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * The SBOM file followed by the same SBOM in the other format, if the BOM goal wrote both.
     *
     * @param file the SBOM file to render
     * @return the SBOM files to copy next to the report page
     */
    static List<File> withOtherFormat(final File file) {
        final List<File> files = new ArrayList<>();
        files.add(file);
        final String name = file.getName();
        final String lowerCaseName = name.toLowerCase(Locale.ROOT);
        String otherName = null;
        if (lowerCaseName.endsWith(".json")) {
            otherName = name.substring(0, name.length() - ".json".length()) + ".xml";
        } else if (lowerCaseName.endsWith(".xml")) {
            otherName = name.substring(0, name.length() - ".xml".length()) + ".json";
        }
        if (otherName != null) {
            final File other = new File(file.getParentFile(), otherName);
            if (other.isFile()) {
                files.add(other);
            }
        }
        return files;
    }

    /**
     * Read a CycloneDX SBOM file, in JSON or XML format depending on its extension.
     * <p>
     * The CycloneDX parsers set the metadata timestamp to the parsing time when the file has none, which is the case of
     * reproducible SBOMs: the timestamp is removed in that case, to render only what the file contains.
     *
     * @param file the SBOM file
     * @return the SBOM
     * @throws ParseException if the file is not a valid SBOM
     * @throws IOException if the file cannot be read
     */
    static Bom readBom(final File file) throws ParseException, IOException {
        final Parser parser = isXml(file) ? new XmlParser() : new JsonParser();
        final Bom bom = parser.parse(file);
        if (bom.getMetadata() != null && !hasMetadataTimestamp(file)) {
            bom.getMetadata().setTimestamp(null);
        }
        return bom;
    }

    private static boolean hasMetadataTimestamp(final File file) throws IOException {
        if (!isXml(file)) {
            return new ObjectMapper().readTree(file).path("metadata").hasNonNull("timestamp");
        }
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            final Element root = factory.newDocumentBuilder().parse(file).getDocumentElement();
            for (Node metadata = root.getFirstChild(); metadata != null; metadata = metadata.getNextSibling()) {
                if ("metadata".equals(metadata.getLocalName())) {
                    for (Node child = metadata.getFirstChild(); child != null; child = child.getNextSibling()) {
                        if ("timestamp".equals(child.getLocalName())) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Unable to parse " + file, e);
        }
    }

    private static boolean isXml(final File file) {
        return file.getName().toLowerCase(Locale.ROOT).endsWith(".xml");
    }

    static ResourceBundle getBundle(final Locale locale) {
        return ResourceBundle.getBundle("cyclonedx-report", (locale == null) ? Locale.ROOT : locale,
                CycloneDxReportMojo.class.getClassLoader());
    }
}
