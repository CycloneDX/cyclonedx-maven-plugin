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

import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.impl.SinkEventAttributeSet;
import org.apache.maven.doxia.util.DoxiaUtils;
import org.apache.maven.reporting.AbstractMavenReportRenderer;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.Hash;
import org.cyclonedx.model.License;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.model.Metadata;
import org.cyclonedx.model.Property;
import org.cyclonedx.model.Service;
import org.cyclonedx.model.metadata.ToolInformation;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

/**
 * Renders a CycloneDX SBOM as a Doxia document for {@link CycloneDxReportMojo}: metadata, direct dependencies of the
 * component the SBOM describes, a summary table of the components, then the details of each component with what it
 * depends on and what requires it.
 * <p>
 * SBOM content comes from the POMs of the dependencies, so it is rendered as plain text only: never as markup, nor as
 * link pattern (see {@link #linkPatternedText(String)}). For the same reason, only http(s) URLs are rendered as links.
 */
class CycloneDxReportRenderer extends AbstractMavenReportRenderer {
    private static final String SPDX_LICENSE_URL = "https://spdx.org/licenses/";

    private static final Comparator<Component> COMPONENT_ORDER = Comparator
            .comparing((Component c) -> nullToEmpty(c.getGroup()), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(c -> nullToEmpty(c.getName()), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(c -> nullToEmpty(c.getVersion()))
            .thenComparing(CycloneDxReportRenderer::ref);

    private final Bom bom;

    private final ResourceBundle bundle;

    private final List<String> bomFileNames;

    private final Component mainComponent;

    private final List<Component> components;

    private final Map<String, Component> componentsByRef = new HashMap<>();

    private final Map<String, List<String>> dependsOn = new HashMap<>();

    private final Map<String, Set<String>> requiredBy = new HashMap<>();

    private int dependencyRelationships;

    /**
     * @param sink the sink to render to
     * @param bom the SBOM to render
     * @param bundle the localized texts of the report
     * @param bomFileNames the SBOM files available next to the report page, to link to
     */
    CycloneDxReportRenderer(final Sink sink, final Bom bom, final ResourceBundle bundle,
                            final List<String> bomFileNames) {
        super(sink);
        this.bom = bom;
        this.bundle = bundle;
        this.bomFileNames = bomFileNames;
        this.mainComponent = (bom.getMetadata() == null) ? null : bom.getMetadata().getComponent();

        this.components = new ArrayList<>(nonNull(bom.getComponents()));
        components.sort(COMPONENT_ORDER);
        for (Component component : components) {
            componentsByRef.putIfAbsent(ref(component), component);
        }

        final Comparator<String> byLabel = Comparator.comparing(this::refLabel, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Comparator.naturalOrder());
        for (Dependency dependency : nonNull(bom.getDependencies())) {
            if (dependency.getRef() == null) {
                continue;
            }
            final List<String> refs = new ArrayList<>();
            for (Dependency on : nonNull(dependency.getDependencies())) {
                if (on.getRef() != null) {
                    refs.add(on.getRef());
                    requiredBy.computeIfAbsent(on.getRef(), ref -> new TreeSet<>(byLabel)).add(dependency.getRef());
                }
            }
            dependsOn.put(dependency.getRef(), refs);
            dependencyRelationships += refs.size();
        }
    }

    @Override
    public String getTitle() {
        return bundle.getString("report.title");
    }

    @Override
    protected void renderBody() {
        startSection(getTitle());
        renderIntroduction();
        renderMetadata();
        renderDirectDependencies();
        renderComponents();
        renderComponentDetails();
        endSection();
    }

    private void renderIntroduction() {
        sink.paragraph();
        text(bundle.getString("report.intro"));
        if (!bomFileNames.isEmpty()) {
            text(" " + bundle.getString("report.download") + " ");
            for (int i = 0; i < bomFileNames.size(); i++) {
                if (i > 0) {
                    text(", ");
                }
                link(bomFileNames.get(i), bomFileNames.get(i));
            }
            text(".");
        }
        sink.paragraph_();

        paragraph(bundle.getString("report.disclaimer"));
    }

    private void renderMetadata() {
        startSection(bundle.getString("report.section.metadata"));
        startTable();
        row("report.metadata.specVersion", bom.getSpecVersion());
        row("report.metadata.serialNumber", bom.getSerialNumber());
        row("report.metadata.version", String.valueOf(bom.getVersion()));

        final Metadata metadata = bom.getMetadata();
        if (metadata != null) {
            row("report.metadata.timestamp", (metadata.getTimestamp() == null)
                    ? null : DateTimeFormatter.ISO_INSTANT.format(metadata.getTimestamp().toInstant()));
            if (mainComponent != null) {
                row("report.metadata.component", label(mainComponent));
                if (mainComponent.getType() != null) {
                    row("report.detail.type", mainComponent.getType().getTypeName());
                }
                if (mainComponent.getPurl() != null) {
                    startRow(bundle.getString("report.detail.purl"));
                    code(mainComponent.getPurl());
                    endRow();
                }
            }
            final List<String> tools = toolNames(metadata);
            if (!tools.isEmpty()) {
                startRow(bundle.getString("report.metadata.tools"));
                lines(tools);
                endRow();
            }
            for (Property property : nonNull(metadata.getProperties())) {
                startRow(property.getName());
                text(property.getValue());
                endRow();
            }
        }

        row("report.metadata.components", String.valueOf(components.size()));
        if (bom.getDependencies() != null) {
            row("report.metadata.dependencies", String.valueOf(dependencyRelationships));
        }
        endTable();
        endSection();
    }

    private void renderDirectDependencies() {
        final List<String> refs = (mainComponent == null) ? null : dependsOn.get(ref(mainComponent));
        if (refs == null) {
            // no dependency graph for the described component (as in CycloneDX 1.0 and 1.1)
            return;
        }
        startSection(bundle.getString("report.section.directDependencies"));
        if (refs.isEmpty()) {
            paragraph(bundle.getString("report.none"));
        } else {
            sink.list();
            for (String ref : refs) {
                sink.listItem();
                refLink(ref);
                sink.listItem_();
            }
            sink.list_();
        }
        endSection();
    }

    private void renderComponents() {
        startSection(bundle.getString("report.section.components"));
        if (components.isEmpty()) {
            paragraph(bundle.getString("report.noComponents"));
        } else {
            startTable();
            sink.tableRow();
            tableHeaderCell(bundle.getString("report.column.component"));
            tableHeaderCell(bundle.getString("report.column.version"));
            tableHeaderCell(bundle.getString("report.column.type"));
            tableHeaderCell(bundle.getString("report.column.scope"));
            tableHeaderCell(bundle.getString("report.column.licenses"));
            sink.tableRow_();
            for (Component component : components) {
                sink.tableRow();
                sink.tableCell();
                link("#" + anchor(ref(component)), name(component));
                sink.tableCell_();
                cell(version(component));
                cell((component.getType() == null) ? null : component.getType().getTypeName());
                cell((component.getScope() == null) ? null : component.getScope().getScopeName());
                sink.tableCell();
                licenses(component.getLicenses());
                sink.tableCell_();
                sink.tableRow_();
            }
            endTable();
        }
        endSection();
    }

    private void renderComponentDetails() {
        if (components.isEmpty()) {
            return;
        }
        startSection(bundle.getString("report.section.details"));
        for (Component component : components) {
            renderComponentDetails(component);
        }
        endSection();
    }

    private void renderComponentDetails(final Component component) {
        final String ref = ref(component);
        startSection(label(component), anchor(ref));
        startTable();
        if (component.getPurl() != null) {
            startRow(bundle.getString("report.detail.purl"));
            code(component.getPurl());
            endRow();
        }
        if (component.getBomRef() != null && !component.getBomRef().equals(component.getPurl())) {
            startRow(bundle.getString("report.detail.bomRef"));
            code(component.getBomRef());
            endRow();
        }
        if (component.getType() != null) {
            row("report.detail.type", component.getType().getTypeName());
        }
        if (component.getScope() != null) {
            row("report.detail.scope", component.getScope().getScopeName());
        }
        if (component.getIsExternal() != null) {
            row("report.detail.external", bundle.getString(component.getIsExternal() ? "report.yes" : "report.no"));
        }
        if (component.getVersionRange() != null) {
            row("report.detail.versionRange", component.getVersionRange());
        }
        if (component.getDescription() != null) {
            row("report.detail.description", component.getDescription());
        }
        if (component.getPublisher() != null) {
            row("report.detail.publisher", component.getPublisher());
        }
        if (hasLicenses(component.getLicenses())) {
            startRow(bundle.getString("report.detail.licenses"));
            licenses(component.getLicenses());
            endRow();
        }
        if (!nonNull(component.getHashes()).isEmpty()) {
            startRow(bundle.getString("report.detail.hashes"));
            hashes(component.getHashes());
            endRow();
        }
        if (!nonNull(component.getExternalReferences()).isEmpty()) {
            startRow(bundle.getString("report.detail.externalReferences"));
            externalReferences(component.getExternalReferences());
            endRow();
        }
        if (bom.getDependencies() != null) {
            startRow(bundle.getString("report.detail.dependsOn"));
            refLinks(dependsOn.get(ref));
            endRow();
            startRow(bundle.getString("report.detail.requiredBy"));
            refLinks(requiredBy.get(ref));
            endRow();
        }
        endTable();
        endSection();
    }

    // ----------------------------------------------------------------------
    // rendering helpers: every value goes through text(), that escapes it
    // ----------------------------------------------------------------------

    private void row(final String key, final String value) {
        startRow(bundle.getString(key));
        text(value);
        endRow();
    }

    private void startRow(final String header) {
        sink.tableRow();
        tableHeaderCell(header);
        sink.tableCell();
    }

    private void endRow() {
        sink.tableCell_();
        sink.tableRow_();
    }

    private void cell(final String value) {
        sink.tableCell();
        text(value);
        sink.tableCell_();
    }

    private void code(final String value) {
        sink.inline(SinkEventAttributeSet.Semantics.CODE);
        text(value);
        sink.inline_();
    }

    private void lines(final List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sink.lineBreak();
            }
            text(values.get(i));
        }
    }

    private void urlOrText(final String url) {
        if (isHttpUrl(url)) {
            link(url, url);
        } else {
            text(url);
        }
    }

    private void licenses(final LicenseChoice licenses) {
        if (!hasLicenses(licenses)) {
            text(null);
            return;
        }
        boolean first = true;
        for (License license : nonNull(licenses.getLicenses())) {
            if (!first) {
                sink.lineBreak();
            }
            first = false;
            if (!isBlank(license.getId())) {
                if (license.getId().startsWith("LicenseRef-")) {
                    text(license.getId());
                } else {
                    link(SPDX_LICENSE_URL + license.getId() + ".html", license.getId());
                }
            } else if (isHttpUrl(license.getUrl())) {
                link(license.getUrl(), isBlank(license.getName()) ? license.getUrl() : license.getName());
            } else {
                text(license.getName());
            }
        }
        if (licenses.getExpression() != null && !isBlank(licenses.getExpression().getValue())) {
            if (!first) {
                sink.lineBreak();
            }
            text(licenses.getExpression().getValue());
        }
    }

    private void hashes(final List<Hash> hashes) {
        for (int i = 0; i < hashes.size(); i++) {
            if (i > 0) {
                sink.lineBreak();
            }
            text(nullToEmpty(hashes.get(i).getAlgorithm()) + ": ");
            code(hashes.get(i).getValue());
        }
    }

    private void externalReferences(final List<ExternalReference> references) {
        for (int i = 0; i < references.size(); i++) {
            final ExternalReference reference = references.get(i);
            if (i > 0) {
                sink.lineBreak();
            }
            if (reference.getType() != null) {
                text(reference.getType().getTypeName() + ": ");
            }
            urlOrText(reference.getUrl());
            if (!isBlank(reference.getComment())) {
                text(" (" + reference.getComment() + ")");
            }
        }
    }

    private void refLinks(final Collection<String> refs) {
        if (refs == null || refs.isEmpty()) {
            text(bundle.getString("report.none"));
            return;
        }
        boolean first = true;
        for (String ref : refs) {
            if (!first) {
                sink.lineBreak();
            }
            first = false;
            refLink(ref);
        }
    }

    /**
     * Render a reference to a component: a link to its details when it is listed in the components, its label when
     * it is the component described by the SBOM, the raw reference otherwise.
     */
    private void refLink(final String ref) {
        final Component component = componentsByRef.get(ref);
        if (component == null) {
            text(refLabel(ref));
        } else {
            link("#" + anchor(ref), label(component));
        }
    }

    private String refLabel(final String ref) {
        Component component = componentsByRef.get(ref);
        if (component == null && mainComponent != null && ref.equals(ref(mainComponent))) {
            component = mainComponent;
        }
        return (component == null) ? ref : label(component);
    }

    // ----------------------------------------------------------------------
    // model helpers
    // ----------------------------------------------------------------------

    /**
     * @return the reference used for the dependency graph: bom-ref, or purl when absent (as in CycloneDX 1.0)
     */
    private static String ref(final Component component) {
        if (component.getBomRef() != null) {
            return component.getBomRef();
        }
        if (component.getPurl() != null) {
            return component.getPurl();
        }
        return name(component) + '@' + nullToEmpty(component.getVersion());
    }

    static String anchor(final String ref) {
        return DoxiaUtils.encodeId(ref);
    }

    private static String name(final Component component) {
        return isBlank(component.getGroup())
                ? nullToEmpty(component.getName())
                : component.getGroup() + ':' + nullToEmpty(component.getName());
    }

    private static String label(final Component component) {
        return (component.getVersion() == null) ? name(component) : name(component) + ':' + component.getVersion();
    }

    private static String version(final Component component) {
        return (component.getVersion() == null) ? component.getVersionRange() : component.getVersion();
    }

    @SuppressWarnings("deprecation") // metadata.tools.tool is deprecated since CycloneDX 1.5, but used before
    private static List<String> toolNames(final Metadata metadata) {
        final List<String> names = new ArrayList<>();
        final ToolInformation toolInformation = metadata.getToolChoice();
        if (toolInformation != null) {
            for (Component tool : nonNull(toolInformation.getComponents())) {
                names.add(join(name(tool), tool.getVersion()));
            }
            for (Service tool : nonNull(toolInformation.getServices())) {
                names.add(join(isBlank(tool.getGroup()) ? tool.getName() : tool.getGroup() + ':' + tool.getName(),
                        tool.getVersion()));
            }
        }
        for (org.cyclonedx.model.Tool tool : nonNull(metadata.getTools())) {
            names.add(join(tool.getVendor(), tool.getName(), tool.getVersion()));
        }
        return names;
    }

    private static boolean hasLicenses(final LicenseChoice licenses) {
        return licenses != null && (!nonNull(licenses.getLicenses()).isEmpty()
                || (licenses.getExpression() != null && !isBlank(licenses.getExpression().getValue())));
    }

    private static boolean isHttpUrl(final String url) {
        if (url == null) {
            return false;
        }
        final String lowerCaseUrl = url.trim().toLowerCase(Locale.ROOT);
        return lowerCaseUrl.startsWith("https://") || lowerCaseUrl.startsWith("http://");
    }

    private static String join(final String... parts) {
        final StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!isBlank(part)) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(part.trim());
            }
        }
        return sb.toString();
    }

    private static boolean isBlank(final String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String nullToEmpty(final String value) {
        return (value == null) ? "" : value;
    }

    private static <T> List<T> nonNull(final List<T> list) {
        return (list == null) ? Collections.<T>emptyList() : list;
    }
}
