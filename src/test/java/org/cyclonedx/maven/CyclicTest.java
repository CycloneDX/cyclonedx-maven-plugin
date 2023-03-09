package org.cyclonedx.maven;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.cyclonedx.maven.TestUtils.containsDependency;
import static org.cyclonedx.maven.TestUtils.getComponentNode;
import static org.cyclonedx.maven.TestUtils.getDependencyNode;
import static org.cyclonedx.maven.TestUtils.getDependencyReferences;
import static org.cyclonedx.maven.TestUtils.getPUrlToIdentities;
import static org.cyclonedx.maven.TestUtils.readXML;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
            projDir = mvnBuild("cyclic", new String[]{"package"}, null, new String[] {"profile"});
        } catch (final Exception ex) {
            fail("Failed to generate SBOM", ex);
        }

        final Document bom = readXML(new File(projDir, "target/bom.xml"));
        final Map<String, Collection<String>> purlToIdentities = getPUrlToIdentities(bom.getDocumentElement());

        final NodeList componentsList = bom.getElementsByTagName("components");
        assertEquals("Expected a single components element", 1, componentsList.getLength());
        final Element components = (Element)componentsList.item(0);

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Element dependencies = (Element)dependenciesList.item(0);

        // BOM should contain pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_1&type=jar
        getComponentNode(purlToIdentities, components, CYCLIC_A_DEPENDENCY_CLASSIFIER_1);

        // BOM should contain pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_2&type=jar
        getComponentNode(purlToIdentities, components, CYCLIC_A_DEPENDENCY_CLASSIFIER_2);

        // BOM should contain pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_3&type=jar
        getComponentNode(purlToIdentities, components, CYCLIC_A_DEPENDENCY_CLASSIFIER_3);

        /*
          <dependency ref="pkg:maven/com.example.cyclic/cyclic_A@1.0.0?type=jar">
            <dependency ref="pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_1&amp;type=jar"/>
            <dependency ref="pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_2&amp;type=jar"/>
            <dependency ref="pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_3&amp;type=jar"/>
          </dependency>
        */
        final Element cyclicADependencyNode = getDependencyNode(purlToIdentities, dependencies, CYCLIC_A_DEPENDENCY);
        Set<String> cyclicADependencies = getDependencyReferences(cyclicADependencyNode);
        assertEquals("Invalid dependency count for cyclic_A:1.0.0", 3, cyclicADependencies.size());
        containsDependency(purlToIdentities, cyclicADependencies, CYCLIC_A_DEPENDENCY_CLASSIFIER_1);
        containsDependency(purlToIdentities, cyclicADependencies, CYCLIC_A_DEPENDENCY_CLASSIFIER_2);
        containsDependency(purlToIdentities, cyclicADependencies, CYCLIC_A_DEPENDENCY_CLASSIFIER_3);

        /*
          <dependency ref="pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_1&amp;type=jar"/>
         */
        final Element cyclicAClassifier1DependencyNode = getDependencyNode(purlToIdentities, dependencies, CYCLIC_A_DEPENDENCY_CLASSIFIER_1);
        Set<String> cyclicAClassifier1Dependencies = getDependencyReferences(cyclicAClassifier1DependencyNode);
        assertEquals("Invalid dependency count for cyclic_A:classifier_1:1.0.0", 0, cyclicAClassifier1Dependencies.size());

        /*
          <dependency ref="pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_2&amp;type=jar"/>
         */
        final Element cyclicAClassifier2DependencyNode = getDependencyNode(purlToIdentities, dependencies, CYCLIC_A_DEPENDENCY_CLASSIFIER_2);
        Set<String> cyclicAClassifier2Dependencies = getDependencyReferences(cyclicAClassifier2DependencyNode);
        assertEquals("Invalid dependency count for cyclic_A:classifier_2:1.0.0", 0, cyclicAClassifier2Dependencies.size());

        /*
          <dependency ref="pkg:maven/com.example.cyclic/cyclic_A@1.0.0?classifier=classifier_3&amp;type=jar"/>
         */
        final Element cyclicAClassifier3DependencyNode = getDependencyNode(purlToIdentities, dependencies, CYCLIC_A_DEPENDENCY_CLASSIFIER_3);
        Set<String> cyclicAClassifier3Dependencies = getDependencyReferences(cyclicAClassifier3DependencyNode);
        assertEquals("Invalid dependency count for cyclic_A:classifier_3:1.0.0", 0, cyclicAClassifier3Dependencies.size());
    }
}
