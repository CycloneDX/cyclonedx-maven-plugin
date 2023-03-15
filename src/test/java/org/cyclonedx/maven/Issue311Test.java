package org.cyclonedx.maven;

import java.io.File;
import java.util.Set;

import static org.cyclonedx.maven.TestUtils.getComponentNode;
import static org.cyclonedx.maven.TestUtils.getDependencyNode;
import static org.cyclonedx.maven.TestUtils.getDependencyReferences;
import static org.cyclonedx.maven.TestUtils.readXML;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

/**
 * test for https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/311
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class Issue311Test extends BaseMavenVerifier {

    private static final String ISSUE311_LATEST = "pkg:maven/com.example/issue311_latest@1.0.0?type=jar";
    private static final String ISSUE311_RELEASE = "pkg:maven/com.example/issue311_release@1.0.0?type=jar";
    private static final String ISSUE311_DEPENDENCY = "pkg:maven/com.example/issue311_dependency@1.0.0?type=jar";

    public Issue311Test(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testLatestAndRelease() throws Exception {
        final File projDir = mvnBuild("issue-311", new String[]{"clean", "install"}, null, null, null);

        checkLatest(projDir);
        checkRelease(projDir);
    }

    private void checkLatest(final File projDir) throws Exception {
        final Document bom = readXML(new File(projDir, "latest/target/bom.xml"));

        final NodeList componentsList = bom.getElementsByTagName("components");
        assertEquals("Expected a single components element", 1, componentsList.getLength());
        final Node components = componentsList.item(0);

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Node dependencies = dependenciesList.item(0);

        // BOM should contain a component for pkg:maven/com.example/issue311_dependency@1.0.0?type=jar
        final Node dependencyNode = getComponentNode(components, ISSUE311_DEPENDENCY);
        assertNotNull("Missing issue311_dependency component", dependencyNode);

        /*
           <dependency ref="pkg:maven/com.example/issue311_latest@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/issue311_dependency@1.0.0?type=jar"/>
           </dependency>
           <dependency ref="pkg:maven/com.example/issue311_dependency@1.0.0?type=jar"/>
        */
        final Node latestDependencyNode = getDependencyNode(dependencies, ISSUE311_LATEST);
        assertNotNull("Missing issue311_latest dependency", latestDependencyNode);

        Set<String> latestDependencies = getDependencyReferences(latestDependencyNode);
        assertEquals("Invalid dependency count for shared_type_dependency1", 1, latestDependencies.size());
        assertTrue("Missing shared_type_dependency2 dependency for shared_type_dependency1", latestDependencies.contains(ISSUE311_DEPENDENCY));
    }

    private void checkRelease(final File projDir) throws Exception {
        final Document bom = readXML(new File(projDir, "release/target/bom.xml"));

        final NodeList componentsList = bom.getElementsByTagName("components");
        assertEquals("Expected a single components element", 1, componentsList.getLength());
        final Node components = componentsList.item(0);

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Node dependencies = dependenciesList.item(0);

        // BOM should contain a component for pkg:maven/com.example/issue311_dependency@1.0.0?type=jar
        final Node dependencyNode = getComponentNode(components, ISSUE311_DEPENDENCY);
        assertNotNull("Missing issue311_dependency component", dependencyNode);

        /*
           <dependency ref="pkg:maven/com.example/issue311_release@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/issue311_dependency@1.0.0?type=jar"/>
           </dependency>
           <dependency ref="pkg:maven/com.example/issue311_dependency@1.0.0?type=jar"/>
        */
        final Node releaseDependencyNode = getDependencyNode(dependencies, ISSUE311_RELEASE);
        assertNotNull("Missing issue311_release dependency", releaseDependencyNode);

        Set<String> releaseDependencies = getDependencyReferences(releaseDependencyNode);
        assertEquals("Invalid dependency count for shared_type_dependency1", 1, releaseDependencies.size());
        assertTrue("Missing shared_type_dependency2 dependency for shared_type_dependency1", releaseDependencies.contains(ISSUE311_DEPENDENCY));
    }
}
