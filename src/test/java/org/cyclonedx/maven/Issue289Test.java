package org.cyclonedx.maven;

import java.io.File;

import static org.cyclonedx.maven.TestUtils.getComponentNode;
import static org.cyclonedx.maven.TestUtils.getDependencyNode;
import static org.cyclonedx.maven.TestUtils.readXML;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

/**
 * test for https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/289
 * NPE when handling relocations
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class Issue289Test extends BaseMavenVerifier {

    private static final String AXIS_AXIS_ANT_1_3 ="pkg:maven/axis/axis-ant@1.3?type=jar";
    private static final String AXIS_AXIS_ANT_1_4 ="pkg:maven/axis/axis-ant@1.4?type=jar";
    private static final String ORG_APACHE_AXIS_ANT_1_4 = "pkg:maven/org.apache.axis/axis-ant@1.4?type=jar";

    public Issue289Test(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testForRelocations() throws Exception {
        final File projDir = cleanAndBuild("issue-289", null);

        checkRelocatedArtifact(projDir);
        checkConflictWithNonRelocatedArtifact(projDir);
    }

    private void checkRelocatedArtifact(final File projDir) throws Exception {
        final Document bom = readXML(new File(projDir, "dependency1/target/bom.xml"));

        final NodeList componentsList = bom.getElementsByTagName("components");
        assertEquals("Expected a single components element", 1, componentsList.getLength());
        final Node components = componentsList.item(0);

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Node dependencies = dependenciesList.item(0);

        /*
         * BOM should contain a component and a dependency for pkg:maven/org.apache.axis/axis-ant@1.4?type=jar
         */
        final Node orgApacheAxisAntNode = getComponentNode(components, ORG_APACHE_AXIS_ANT_1_4);
        assertNotNull("Missing pkg:maven/org.apache.axis/axis-ant@1.4 component", orgApacheAxisAntNode);

        final Node orgApacheAxisAntDependencyNode = getDependencyNode(dependencies, ORG_APACHE_AXIS_ANT_1_4);
        assertNotNull("Missing pkg:maven/org.apache.axis/axis-ant@1.4 dependency", orgApacheAxisAntDependencyNode);

        /*
         * BOM should not contain a component nor a dependency for pkg:maven/axis/axis-ant@1.4?type=jar
         */
        final Node axisAxisAntNode = getComponentNode(components, AXIS_AXIS_ANT_1_4);
        assertNull("Unexpected pkg:maven/axis/axis-ant@1.4 component discovered in BOM", axisAxisAntNode);

        final Node axisAxisAntDependencyNode = getDependencyNode(dependencies, AXIS_AXIS_ANT_1_4);
        assertNull("Unexpected pkg:maven/axis/axis-ant@1.4 dependency discovered in BOM", axisAxisAntDependencyNode);
    }

    private void checkConflictWithNonRelocatedArtifact(final File projDir) throws Exception {
        final Document bom = readXML(new File(projDir, "dependency2/target/bom.xml"));

        final NodeList componentsList = bom.getElementsByTagName("components");
        assertEquals("Expected a single components element", 1, componentsList.getLength());
        final Node components = componentsList.item(0);

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Node dependencies = dependenciesList.item(0);

        /*
         * BOM should contain a component and a dependency for pkg:maven/axis/axis-ant@1.3?type=jar
         */
        final Node axisAxisAntNode = getComponentNode(components, AXIS_AXIS_ANT_1_3);
        assertNotNull("Missing pkg:maven/axis/axis-ant@1.3 component", axisAxisAntNode);

        final Node axisAxisAntDependencyNode = getDependencyNode(dependencies, AXIS_AXIS_ANT_1_3);
        assertNotNull("Missing pkg:maven/axis/axis-ant@1.3 dependency", axisAxisAntDependencyNode);

        /*
         * BOM should not contain a component nor a dependency for pkg:maven/org.apache.axis/axis-ant@1.4?type=jar
         */
        final Node orgApacheAxisAntNode = getComponentNode(components, ORG_APACHE_AXIS_ANT_1_4);
        assertNull("Unexpected pkg:maven/org.apache.axis/axis-ant@1.4 component discovered in BOM", orgApacheAxisAntNode);

        final Node orgApacheAxisAntDependencyNode = getDependencyNode(dependencies, ORG_APACHE_AXIS_ANT_1_4);
        assertNull("Unexpected pkg:maven/org.apache.axis/axis-ant@1.4 dependency discovered in BOM", orgApacheAxisAntDependencyNode);
    }
}
