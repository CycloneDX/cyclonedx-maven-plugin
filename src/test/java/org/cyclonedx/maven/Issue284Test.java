package org.cyclonedx.maven;

import java.io.File;
import java.util.Set;

import static org.cyclonedx.maven.TestUtils.getComponentNode;
import static org.cyclonedx.maven.TestUtils.getDependencyNode;
import static org.cyclonedx.maven.TestUtils.getDependencyReferences;
import static org.cyclonedx.maven.TestUtils.readXML;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
 * test for https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/284
 * Fix filtering of scopes
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class Issue284Test extends BaseMavenVerifier {

    private static final String ISSUE284_PROVIDED_DEPENDENCY = "pkg:maven/com.example/issue284_provided_dependency@1.0.0?type=jar";
    private static final String ISSUE284_DEPENDENCY2 = "pkg:maven/com.example/issue284_dependency2@1.0.0?type=jar";
    private static final String ISSUE284_SHARED_DEPENDENCY1 = "pkg:maven/com.example/issue284_shared_dependency1@1.0.0?type=jar";
    private static final String ISSUE284_SHARED_DEPENDENCY2 = "pkg:maven/com.example/issue284_shared_dependency2@1.0.0?type=jar";



    public Issue284Test(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testScopeFiltering() throws Exception {
        final File projDir = cleanAndBuild("issue-284", null);

        final Document bom = readXML(new File(projDir, "issue284/target/bom.xml"));

        final NodeList componentsList = bom.getElementsByTagName("components");
        assertEquals("Expected a single components element", 1, componentsList.getLength());
        final Node components = componentsList.item(0);

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Node dependencies = dependenciesList.item(0);

        // BOM should not contain pkg:maven/com.example/issue284_provided_dependency@1.0.0?type=jar
        final Node testIssue284ProvidedDependency1ComponentNode = getComponentNode(components, ISSUE284_PROVIDED_DEPENDENCY);
        assertNull("Unexpected provided_dependency:1.0.0 component discovered in BOM", testIssue284ProvidedDependency1ComponentNode);

        /*
           <dependency ref="pkg:maven/com.example/issue284_dependency2@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/issue284_shared_dependency1@1.0.0?type=jar"/>
           </dependency>
        */
        final Node issue284Dependency2Node = getDependencyNode(dependencies, ISSUE284_DEPENDENCY2);
        assertNotNull("Missing issue284_dependency2 dependency", issue284Dependency2Node);
        Set<String> issue284Dependency2Dependencies = getDependencyReferences(issue284Dependency2Node);
        assertEquals("Invalid dependency count for issue284_dependency2", 1, issue284Dependency2Dependencies.size());
        assertTrue("Missing issue284_shared_dependency1 dependency for issue284_dependency2", issue284Dependency2Dependencies.contains(ISSUE284_SHARED_DEPENDENCY1));

        /*
           <dependency ref="pkg:maven/com.example/issue284_shared_dependency1@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/issue284_shared_dependency2@1.0.0?type=jar"/>
           </dependency>
         */
        final Node issue284SharedDependency1Node = getDependencyNode(dependencies, ISSUE284_SHARED_DEPENDENCY1);
        assertNotNull("Missing issue284_shared_dependency1 dependency", issue284SharedDependency1Node);
        Set<String> issue284SharedDependency1Dependencies = getDependencyReferences(issue284SharedDependency1Node);
        assertEquals("Invalid dependency count for issue284_shared_dependency1", 1, issue284SharedDependency1Dependencies.size());
        assertTrue("Missing issue284_shared_dependency2 dependency for issue284_shared_dependency1", issue284SharedDependency1Dependencies.contains(ISSUE284_SHARED_DEPENDENCY2));
    }
}
