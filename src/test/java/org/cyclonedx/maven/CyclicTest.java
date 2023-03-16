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
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

/**
 * Test for cyclic dependencies on the same project
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.8.7"})
public class CyclicTest extends BaseMavenVerifier {
    private static final String CYCLIC_A_DEPENDENCY = "pkg:maven/com.example.cyclic/cyclic_A@1.0.0?type=jar";
    private static final String CYCLIC_A_DEPENDENCY_CLASSIFIER_1 = "pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_1&type=jar";
    private static final String CYCLIC_A_DEPENDENCY_CLASSIFIER_2 = "pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_2&type=jar";
    private static final String CYCLIC_A_DEPENDENCY_CLASSIFIER_3 = "pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_3&type=jar";

    public CyclicTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testCyclicDependency() throws Exception {
        cleanAndBuild("cyclic", null);
        File projDir = null;
        try {
            projDir = mvnBuild("cyclic", new String[]{"package"}, null, new String[] {"profile"}, null);
        } catch (final Exception ex) {
            fail("Failed to generate SBOM", ex);
        }

        final Document bom = readXML(new File(projDir, "target/bom.xml"));

        final NodeList componentsList = bom.getElementsByTagName("components");
        assertEquals("Expected a single components element", 1, componentsList.getLength());
        final Node components = componentsList.item(0);

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Node dependencies = dependenciesList.item(0);

        // BOM should contain pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_1&type=jar
        final Node cyclicAClassifier1ComponentNode = getComponentNode(components, CYCLIC_A_DEPENDENCY_CLASSIFIER_1);
        assertNotNull("Missing cyclic_A:classifier_1:1.0.0 component", cyclicAClassifier1ComponentNode);

        // BOM should contain pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_2&type=jar
        final Node cyclicAClassifier2ComponentNode = getComponentNode(components, CYCLIC_A_DEPENDENCY_CLASSIFIER_2);
        assertNotNull("Missing cyclic_A:classifier_2:1.0.0 component", cyclicAClassifier2ComponentNode);

        // BOM should contain pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_3&type=jar
        final Node cyclicAClassifier3ComponentNode = getComponentNode(components, CYCLIC_A_DEPENDENCY_CLASSIFIER_3);
        assertNotNull("Missing cyclic_A:classifier_3:1.0.0 component", cyclicAClassifier3ComponentNode);

        /*
          <dependency ref="pkg:maven/com.example.cyclic/cyclic_A@1.0.0?type=jar">
            <dependency ref="pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_1&amp;type=jar"/>
            <dependency ref="pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_2&amp;type=jar"/>
            <dependency ref="pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_3&amp;type=jar"/>
          </dependency>
        */
        final Node cyclicADependencyNode = getDependencyNode(dependencies, CYCLIC_A_DEPENDENCY);
        assertNotNull("Missing cyclic_A:1.0.0 dependency", cyclicADependencyNode);
        Set<String> cyclicADependencies = getDependencyReferences(cyclicADependencyNode);
        assertEquals("Invalid dependency count for cyclic_A:1.0.0", 3, cyclicADependencies.size());
        assertTrue("Missing cyclic_A:classifier_1:1.0.0 dependency for cyclic_A:1.0.0", cyclicADependencies.contains(CYCLIC_A_DEPENDENCY_CLASSIFIER_1));
        assertTrue("Missing cyclic_A:classifier_2:1.0.0 dependency for cyclic_A:1.0.0", cyclicADependencies.contains(CYCLIC_A_DEPENDENCY_CLASSIFIER_2));
        assertTrue("Missing cyclic_A:classifier_3:1.0.0 dependency for cyclic_A:1.0.0", cyclicADependencies.contains(CYCLIC_A_DEPENDENCY_CLASSIFIER_3));

        /*
          <dependency ref="pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_1&amp;type=jar"/>
         */
        final Node cyclicAClassifier1DependencyNode = getDependencyNode(dependencies, CYCLIC_A_DEPENDENCY_CLASSIFIER_1);
        assertNotNull("Missing cyclic_A:classifier_1:1.0.0 dependency", cyclicAClassifier1DependencyNode);
        Set<String> cyclicAClassifier1Dependencies = getDependencyReferences(cyclicAClassifier1DependencyNode);
        assertEquals("Invalid dependency count for cyclic_A:classifier_1:1.0.0", 0, cyclicAClassifier1Dependencies.size());

        /*
          <dependency ref="pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_2&amp;type=jar"/>
         */
        final Node cyclicAClassifier2DependencyNode = getDependencyNode(dependencies, CYCLIC_A_DEPENDENCY_CLASSIFIER_2);
        assertNotNull("Missing cyclic_A:classifier_2:1.0.0 dependency", cyclicAClassifier2DependencyNode);
        Set<String> cyclicAClassifier2Dependencies = getDependencyReferences(cyclicAClassifier2DependencyNode);
        assertEquals("Invalid dependency count for cyclic_A:classifier_2:1.0.0", 0, cyclicAClassifier2Dependencies.size());

        /*
          <dependency ref="pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_3&amp;type=jar"/>
         */
        final Node cyclicAClassifier3DependencyNode = getDependencyNode(dependencies, CYCLIC_A_DEPENDENCY_CLASSIFIER_3);
        assertNotNull("Missing cyclic_A:classifier_3:1.0.0 dependency", cyclicAClassifier3DependencyNode);
        Set<String> cyclicAClassifier3Dependencies = getDependencyReferences(cyclicAClassifier3DependencyNode);
        assertEquals("Invalid dependency count for cyclic_A:classifier_3:1.0.0", 0, cyclicAClassifier3Dependencies.size());
    }
}
