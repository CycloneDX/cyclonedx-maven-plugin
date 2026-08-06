package org.cyclonedx.maven;

import java.io.File;
import java.util.Set;

import static org.cyclonedx.maven.TestUtils.getComponentNode;
import static org.cyclonedx.maven.TestUtils.getDependencyNode;
import static org.cyclonedx.maven.TestUtils.getDependencyReferences;
import static org.cyclonedx.maven.TestUtils.getElement;
import static org.cyclonedx.maven.TestUtils.readXML;

import static org.junit.Assert.assertEquals;
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
 * Tests for parent POM preservation with includeParentsAsComponents=false.
 * Verifies that parent POMs are tracked in dependencies but NOT included in components list.
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class ParentNoComponentTest extends BaseMavenVerifier {

    // PURLs for test components
    private static final String PARENT_POM = "pkg:maven/com.example/parent-pom@1.0.0?type=pom";
    private static final String CHILD_APP = "pkg:maven/com.example/child-app@1.0.0?type=jar";
    
    private static final String SLF4J_API = "pkg:maven/org.slf4j/slf4j-api@1.7.36?type=jar";
    private static final String GUAVA = "pkg:maven/com.google.guava/guava@31.1-jre?type=jar";

    public ParentNoComponentTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    /**
     * Test that parent POMs are NOT included as components when includeParentsAsComponents is false,
     * but they still appear in the dependency relationships.
     */
    @Test
    public void testParentNotInComponents() throws Exception {
        final File projDir = mvnBuild("parent-no-component", new String[]{"clean", "package"}, null, null, null);

        // Check the child application BOM
        final Document childBom = readXML(new File(projDir, "child-app/target/bom.xml"));

        final Element components = getElement(childBom.getDocumentElement(), "components");
        assertNotNull("Expected a components element", components);

        final Element dependencies = getElement(childBom.getDocumentElement(), "dependencies");
        assertNotNull("Expected a dependencies element", dependencies);

        // Verify that parent POM is NOT included as a component
        final Node parentComponent = getComponentNode(components, PARENT_POM);
        assertNull("Parent POM should NOT be included as a component when includeParentsAsComponents=false", parentComponent);

        // Verify that guava IS included as a component (it's a real dependency)
        final Node guavaComponent = getComponentNode(components, GUAVA);
        assertNotNull("Guava should be included as a component", guavaComponent);

        // Verify child's dependencies include the parent (even though parent is not a component)
        final Node childDeps = getDependencyNode(dependencies, CHILD_APP);
        assertNotNull("Child dependency node should exist", childDeps);
        
        Set<String> childDependencies = getDependencyReferences(childDeps);
        assertTrue("Child should depend on parent POM", childDependencies.contains(PARENT_POM));
        assertTrue("Child should have direct dependency on guava", childDependencies.contains(GUAVA));

        // Verify parent's dependencies exist even though parent is not a component
        final Node parentDeps = getDependencyNode(dependencies, PARENT_POM);
        assertNotNull("Parent dependency node should exist (even without component)", parentDeps);
        
        Set<String> parentDependencies = getDependencyReferences(parentDeps);
        assertTrue("Parent should have slf4j-api in its dependencies", parentDependencies.contains(SLF4J_API));
    }
}
