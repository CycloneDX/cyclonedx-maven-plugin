package org.cyclonedx.maven;

import static org.cyclonedx.maven.TestUtils.getElement;
import static org.cyclonedx.maven.TestUtils.readXML;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

/**
 * Tests for the {@code cyclonedx.skipArtifactDownload} flag introduced in 2.10.0.
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class SkipArtifactDownloadTest extends BaseMavenVerifier {

    private static final String FIXTURE = "skip-artifact-download";

    public SkipArtifactDownloadTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testHashesOmittedWhenFlagSet() throws Exception {
        final File projDir = cleanAndBuild(FIXTURE,
                Collections.singletonMap("cyclonedx.skipArtifactDownload", "true"),
                null);

        final Document bom = readXML(new File(projDir, "target/bom.xml"));
        final Element components = getElement(bom.getDocumentElement(), "components");
        assertNotNull("BOM must have a <components> section", components);

        final NodeList componentNodes = components.getElementsByTagName("component");
        assertTrue("BOM must contain at least one dependency component",
                componentNodes.getLength() > 0);

        int componentsWithHashes = 0;
        for (int i = 0; i < componentNodes.getLength(); i++) {
            final Node component = componentNodes.item(i);
            // Only count direct children <hashes>, not nested (e.g. inside <pedigree>).
            if (getDirectChild(component, "hashes") != null) {
                componentsWithHashes++;
            }
        }
        assertEquals("With skipArtifactDownload=true, no dependency component should have <hashes>",
                0, componentsWithHashes);

        // Sanity: purl, name, version, licenses still populated.
        final Node firstComponent = componentNodes.item(0);
        assertNotNull("component must still have a <purl>", getDirectChild(firstComponent, "purl"));
        assertNotNull("component must still have a <name>", getDirectChild(firstComponent, "name"));
        assertNotNull("component must still have a <version>", getDirectChild(firstComponent, "version"));

        // metadata.tools present — plugin self-component must still appear.
        final Element metadata = getElement(bom.getDocumentElement(), "metadata");
        assertNotNull(metadata);
        final Element tools = getElement(metadata, "tools");
        assertNotNull("metadata.tools present", tools);
        assertTrue("metadata.tools.tool present", tools.getElementsByTagName("tool").getLength() > 0);

        // Dependency graph still present.
        final Element dependencies = getElement(bom.getDocumentElement(), "dependencies");
        assertNotNull(dependencies);
        assertTrue(dependencies.getElementsByTagName("dependency").getLength() > 0);
    }

    @Test
    public void testHashesPresentWhenFlagUnset() throws Exception {
        final File projDir = cleanAndBuild(FIXTURE, null);

        final Document bom = readXML(new File(projDir, "target/bom.xml"));
        final Element components = getElement(bom.getDocumentElement(), "components");
        assertNotNull(components);

        final NodeList componentNodes = components.getElementsByTagName("component");
        assertTrue(componentNodes.getLength() > 0);

        int componentsWithHashes = 0;
        for (int i = 0; i < componentNodes.getLength(); i++) {
            if (getDirectChild(componentNodes.item(i), "hashes") != null) {
                componentsWithHashes++;
            }
        }
        assertTrue("Baseline: at least one dependency component should have <hashes> when the flag is unset",
                componentsWithHashes > 0);
    }

    @Test
    public void testDetectUnusedForOptionalScopeForceDisabledWhenFlagSet() throws Exception {
        // With both flags set the plugin should warn and override detectUnusedForOptionalScope
        // to false, and the build should still succeed.
        final java.util.Map<String, String> props = new java.util.LinkedHashMap<>();
        props.put("cyclonedx.skipArtifactDownload", "true");
        props.put("detectUnusedForOptionalScope", "true");

        final File projDir = cleanAndBuild(FIXTURE, props, null);

        final Document bom = readXML(new File(projDir, "target/bom.xml"));
        final Element components = getElement(bom.getDocumentElement(), "components");
        assertNotNull(components);

        // BOM is still produced — and still has no hashes because skipArtifactDownload wins.
        final NodeList componentNodes = components.getElementsByTagName("component");
        assertTrue(componentNodes.getLength() > 0);
        for (int i = 0; i < componentNodes.getLength(); i++) {
            assertNull("skipArtifactDownload still suppresses hashes when both flags are set",
                    getDirectChild(componentNodes.item(i), "hashes"));
        }

        // maven.optional.unused property should NOT have been written — since the analyzer was disabled.
        final Element metadata = getElement(bom.getDocumentElement(), "metadata");
        final Element properties = metadata == null ? null : getElement(metadata, "properties");
        if (properties != null) {
            final NodeList propertyNodes = properties.getElementsByTagName("property");
            for (int i = 0; i < propertyNodes.getLength(); i++) {
                final Node name = propertyNodes.item(i).getAttributes().getNamedItem("name");
                if (name != null) {
                    assertFalse("maven.optional.unused should not be emitted when analyzer was force-disabled",
                            "maven.optional.unused".equals(name.getNodeValue()));
                }
            }
        }
    }

    private static Node getDirectChild(final Node parent, final String name) {
        final NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && name.equals(child.getNodeName())) {
                return child;
            }
        }
        return null;
    }
}
