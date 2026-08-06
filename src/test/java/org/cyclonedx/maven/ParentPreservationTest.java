package org.cyclonedx.maven;

import java.io.File;
import java.util.Set;

import static org.cyclonedx.maven.TestUtils.getComponentNode;
import static org.cyclonedx.maven.TestUtils.getDependencyNode;
import static org.cyclonedx.maven.TestUtils.getDependencyReferences;
import static org.cyclonedx.maven.TestUtils.getElement;
import static org.cyclonedx.maven.TestUtils.readXML;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
 * Tests for parent POM preservation functionality.
 * Verifies that when preserveParentReferences is enabled, parent POMs are properly tracked
 * and dependencies are correctly attributed to their declaring POM.
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class ParentPreservationTest extends BaseMavenVerifier {

    // PURLs for test components
    private static final String GRANDPARENT_POM = "pkg:maven/com.example/grandparent@1.0.0?type=pom";
    private static final String PARENT_POM = "pkg:maven/com.example/parent@1.0.0?type=pom";
    private static final String CHILD_JAR = "pkg:maven/com.example/child@1.0.0?type=jar";
    private static final String STANDALONE_JAR = "pkg:maven/com.example/standalone@1.0.0?type=jar";
    
    private static final String COMMONS_IO = "pkg:maven/commons-io/commons-io@2.11.0?type=jar";
    private static final String COMMONS_CODEC = "pkg:maven/commons-codec/commons-codec@1.15?type=jar";
    private static final String JUNIT = "pkg:maven/junit/junit@4.13.2?type=jar";
    private static final String COMMONS_LANG3 = "pkg:maven/org.apache.commons/commons-lang3@3.12.0?type=jar";

    public ParentPreservationTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    /**
     * Test that parent POMs are included as components when includeParentsAsComponents is true.
     */
    @Test
    public void testParentPreservationWithParentComponents() throws Exception {
        final File projDir = mvnBuild("parent-preservation", new String[]{"clean", "package"}, null, null, null);

        // Check the child project BOM
        final Document childBom = readXML(new File(projDir, "child/target/bom.xml"));

        final Element components = getElement(childBom.getDocumentElement(), "components");
        assertNotNull("Expected a components element", components);

        final Element dependencies = getElement(childBom.getDocumentElement(), "dependencies");
        assertNotNull("Expected a dependencies element", dependencies);

        // Verify that parent POM is included as a component
        final Node parentComponent = getComponentNode(components, PARENT_POM);
        assertNotNull("Parent POM should be included as a component", parentComponent);

        // Verify that grandparent POM is included as a component
        final Node grandparentComponent = getComponentNode(components, GRANDPARENT_POM);
        assertNotNull("Grandparent POM should be included as a component", grandparentComponent);

        // Verify child's dependencies include the parent
        final Node childDeps = getDependencyNode(dependencies, CHILD_JAR);
        assertNotNull("Child dependency node should exist", childDeps);
        
        Set<String> childDependencies = getDependencyReferences(childDeps);
        assertTrue("Child should depend on parent POM", childDependencies.contains(PARENT_POM));
        assertTrue("Child should have direct dependency on commons-lang3", childDependencies.contains(COMMONS_LANG3));

        // Verify parent's dependencies include the grandparent and commons-codec
        final Node parentDeps = getDependencyNode(dependencies, PARENT_POM);
        assertNotNull("Parent dependency node should exist", parentDeps);
        
        Set<String> parentDependencies = getDependencyReferences(parentDeps);
        assertTrue("Parent should depend on grandparent POM", parentDependencies.contains(GRANDPARENT_POM));
        assertTrue("Parent should have commons-codec", parentDependencies.contains(COMMONS_CODEC));

        // Verify grandparent's dependencies include commons-io
        final Node grandparentDeps = getDependencyNode(dependencies, GRANDPARENT_POM);
        assertNotNull("Grandparent dependency node should exist", grandparentDeps);
        
        Set<String> grandparentDependencies = getDependencyReferences(grandparentDeps);
        assertTrue("Grandparent should have commons-io", grandparentDependencies.contains(COMMONS_IO));
    }

    /**
     * Test that standalone project without parent dependencies works correctly.
     */
    @Test
    public void testStandaloneProjectWithParentPreservation() throws Exception {
        final File projDir = mvnBuild("parent-preservation", new String[]{"clean", "package"}, null, null, null);

        // Check the standalone project BOM
        final Document standaloneBom = readXML(new File(projDir, "standalone/target/bom.xml"));

        final Element dependencies = getElement(standaloneBom.getDocumentElement(), "dependencies");
        assertNotNull("Expected a dependencies element", dependencies);

        // Verify standalone project's dependencies
        final Node standaloneDeps = getDependencyNode(dependencies, STANDALONE_JAR);
        assertNotNull("Standalone dependency node should exist", standaloneDeps);
        
        Set<String> standaloneDependencies = getDependencyReferences(standaloneDeps);
        assertTrue("Standalone should have direct dependency on commons-lang3", standaloneDependencies.contains(COMMONS_LANG3));
    }
}
