package org.cyclonedx.maven;

import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static org.junit.Assert.*;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class IssueTrustification1Test {

    private static final String SHARED_DEPENDENCY1 = "pkg:maven/com.example/shared_dependency1@1.0.0?type=jar";
    private static final String SHARED_DEPENDENCY2 = "pkg:maven/com.example/shared_dependency2@1.0.0?type=jar";
    private static final String TEST_NESTED_DEPENDENCY2 = "pkg:maven/com.example/test_nested_dependency2@1.0.0?type=jar";
    private static final String TEST_NESTED_DEPENDENCY3 = "pkg:maven/com.example/test_nested_dependency3@1.0.0?type=jar";
    private static final String SHARED_RUNTIME_DEPENDENCY1 = "pkg:maven/com.example/shared_runtime_dependency1@1.0.0?type=jar";
    private static final String SHARED_RUNTIME_DEPENDENCY2 = "pkg:maven/com.example/shared_runtime_dependency2@1.0.0?type=jar";

    @Rule
    public final TestResources resources = new TestResources(
            "target/test-classes",
            "target/test-classes/transformed-projects"
    );

    public final MavenRuntime verifier;

    public IssueTrustification1Test(MavenRuntimeBuilder runtimeBuilder)
            throws Exception {
        this.verifier = runtimeBuilder.build(); //.withCliOptions(opts) // //
    }

    /**
     * This test ensures that any dependencies obscured by <i>test</i> scope are discovered and present in the dependency graph
     * @throws Exception
     */
    @Test
    public void testConcealedTestArtifacts() throws Exception {
        final File projDir = cleanAndBuild();

        final Document bom = readXML(new File(projDir, "trustification/target/bom.xml"));

        /* BOM should contain dependency elements for
           <dependency ref="pkg:maven/com.example/shared_dependency1@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/shared_dependency2@1.0.0?type=jar"/>
             <dependency ref="pkg:maven/com.example/test_nested_dependency2@1.0.0?type=jar"/>
           </dependency>
           <dependency ref="pkg:maven/com.example/shared_dependency2@1.0.0?type=jar"/>
           <dependency ref="pkg:maven/com.example/test_nested_dependency2@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/test_nested_dependency3@1.0.0?type=jar"/>
           </dependency>
           <dependency ref="pkg:maven/com.example/test_nested_dependency3@1.0.0?type=jar"/>
        */
        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Node dependencies = dependenciesList.item(0);

        /*
           <dependency ref="pkg:maven/com.example/shared_dependency1@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/shared_dependency2@1.0.0?type=jar"/>
             <dependency ref="pkg:maven/com.example/test_nested_dependency2@1.0.0?type=jar"/>
           </dependency>
        */
        final Node sharedDependency1Node = getDependencyNode(dependencies, SHARED_DEPENDENCY1);
        assertNotNull("Missing shared_dependency1 dependency", sharedDependency1Node);
        final Set<String> testSharedDependency1Dependencies = getDependencyReferences(sharedDependency1Node);
        assertEquals("Invalid dependency count for shared_dependency1", 2, testSharedDependency1Dependencies.size());
        assertTrue("Missing shared_dependency2 dependency for shared_dependency1", testSharedDependency1Dependencies.contains(SHARED_DEPENDENCY2));
        assertTrue("Missing test_nested_dependency2 dependency for shared_dependency1", testSharedDependency1Dependencies.contains(TEST_NESTED_DEPENDENCY2));

        /*
           <dependency ref="pkg:maven/com.example/shared_dependency2@1.0.0?type=jar"/>
        */
        final Node sharedDependency2Node = getDependencyNode(dependencies, SHARED_DEPENDENCY2);
        assertNotNull("Missing shared_dependency2 dependency", sharedDependency2Node);
        final Set<String> testSharedDependency2Dependencies = getDependencyReferences(sharedDependency2Node);
        assertEquals("Invalid dependency count for shared_dependency2", 0, testSharedDependency2Dependencies.size());

        /*
           <dependency ref="pkg:maven/com.example/test_nested_dependency2@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/test_nested_dependency3@1.0.0?type=jar"/>
           </dependency>
        */
        final Node testNestedDependency2Node = getDependencyNode(dependencies, TEST_NESTED_DEPENDENCY2);
        assertNotNull("Missing test_nested_dependency2 dependency", testNestedDependency2Node);
        Set<String> testNestedDependency2Dependencies = getDependencyReferences(testNestedDependency2Node);
        assertEquals("Invalid dependency count for test_nested_dependency2", 1, testNestedDependency2Dependencies.size());
        assertTrue("Missing test_nested_dependency3 dependency for test_nested_dependency2", testNestedDependency2Dependencies.contains(TEST_NESTED_DEPENDENCY3));

        /*
           <dependency ref="pkg:maven/com.example/test_nested_dependency3@1.0.0?type=jar"/>
        */
        final Node testNestedDependency3Node = getDependencyNode(dependencies, TEST_NESTED_DEPENDENCY3);
        assertNotNull("Missing test_nested_dependency3 dependency", testNestedDependency3Node);
        Set<String> testNestedDependency3Dependencies = getDependencyReferences(testNestedDependency3Node);
        assertEquals("Invalid dependency count for test_nested_dependency3", 0, testNestedDependency3Dependencies.size());
    }


    /**
     * This test ensures that any dependencies obscured by <i>runtime</i> scope are discovered and present in the dependency graph
     * @throws Exception
     */
    @Test
    public void testConcealedRuntimeArtifacts() throws Exception {
        final File projDir = cleanAndBuild();

        final Document bom = readXML(new File(projDir, "trustification/target/bom.xml"));

        /* BOM should contain dependency elements for
           <dependency ref="pkg:maven/com.example/shared_runtime_dependency1@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/shared_runtime_dependency2@1.0.0?type=jar"/>
           </dependency>
           <dependency ref="pkg:maven/com.example/shared_runtime_dependency2@1.0.0?type=jar"/>
        */
        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Node dependencies = dependenciesList.item(0);

        /*
           <dependency ref="pkg:maven/com.example/shared_runtime_dependency1@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/shared_runtime_dependency2@1.0.0?type=jar"/>
           </dependency>
        */
        final Node sharedRuntimeDependency1Node = getDependencyNode(dependencies, SHARED_RUNTIME_DEPENDENCY1);
        assertNotNull("Missing shared_runtime_dependency1 dependency", sharedRuntimeDependency1Node);
        final Set<String> testSharedDependency1Dependencies = getDependencyReferences(sharedRuntimeDependency1Node);
        assertEquals("Invalid dependency count for shared_runtime_dependency1", 1, testSharedDependency1Dependencies.size());
        assertTrue("Missing shared_runtime_dependency2 dependency for shared_runtime_dependency1", testSharedDependency1Dependencies.contains(SHARED_RUNTIME_DEPENDENCY2));

        /*
           <dependency ref="pkg:maven/com.example/shared_runtime_dependency2@1.0.0?type=jar"/>
        */
        final Node sharedRuntimeDependency2Node = getDependencyNode(dependencies, SHARED_RUNTIME_DEPENDENCY2);
        assertNotNull("Missing shared_runtime_dependency2 dependency", sharedRuntimeDependency2Node);
        final Set<String> testSharedDependency2Dependencies = getDependencyReferences(sharedRuntimeDependency2Node);
        assertEquals("Invalid dependency count for shared_runtime_dependency2", 0, testSharedDependency2Dependencies.size());
    }

    private File cleanAndBuild() throws Exception {
        File projectDirTransformed = new File(
                "target/test-classes/transformed-projects/issue-trustification1"
        );
        if (projectDirTransformed.exists()) {
            FileUtils.cleanDirectory(projectDirTransformed);
            projectDirTransformed.delete();
        }

        File projDir = resources.getBasedir("issue-trustification1");

        Properties props = new Properties();

        props.load(IssueTrustification1Test.class.getClassLoader().getResourceAsStream("test.properties"));
        String projectVersion = (String) props.get("project.version");
        verifier
                .forProject(projDir) //
                .withCliOption("-Dtest.input.version=" + projectVersion) // debug
                .withCliOption("-X") // debug
                .withCliOption("-B")
                .execute("clean", "package")
                .assertErrorFreeLog();
        return projDir;
    }

    private static Node getDependencyNode(final Node dependencies, final String ref) {
        final NodeList children = dependencies.getChildNodes();
        final int numChildNodes = children.getLength();
        for (int index = 0 ; index < numChildNodes ; index++) {
            final Node child = children.item(index);
            if ((child.getNodeType() == Node.ELEMENT_NODE) && "dependency".equals(child.getNodeName())) {
                final Node refNode = child.getAttributes().getNamedItem("ref");
                if (ref.equals(refNode.getNodeValue())) {
                    return child;
                }
            }
        }
        return null;
    }

    private static Set<String> getDependencyReferences(final Node dependencies) {
        final Set<String> references = new HashSet<>();
        final NodeList children = dependencies.getChildNodes();
        final int numChildNodes = children.getLength();
        for (int index = 0 ; index < numChildNodes ; index++) {
            final Node child = children.item(index);
            if ((child.getNodeType() == Node.ELEMENT_NODE) && "dependency".equals(child.getNodeName())) {
                final Node refNode = child.getAttributes().getNamedItem("ref");
                if (refNode != null) {
                    references.add(refNode.getNodeValue());
                }
            }
        }
        return references;
    }

    private static Document readXML(File file) throws IOException, SAXException, ParserConfigurationException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        factory.setIgnoringComments(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setValidating(false);

        final DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(file);
    }
}
