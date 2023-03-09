package org.cyclonedx.maven;

import static org.cyclonedx.maven.TestUtils.containsDependency;
import static org.cyclonedx.maven.TestUtils.getComponentNode;
import static org.cyclonedx.maven.TestUtils.getComponentNodes;
import static org.cyclonedx.maven.TestUtils.getComponentReferences;
import static org.cyclonedx.maven.TestUtils.getDependencyNode;
import static org.cyclonedx.maven.TestUtils.getDependencyNodes;
import static org.cyclonedx.maven.TestUtils.getDependencyReferences;
import static org.cyclonedx.maven.TestUtils.getPUrlToIdentities;
import static org.cyclonedx.maven.TestUtils.readXML;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

/**
 * test for https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/256
 * 
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class BomDependenciesTest extends BaseMavenVerifier {

    private static final String SHARED_DEPENDENCY1 = "pkg:maven/com.example/shared_dependency1@1.0.0?type=jar";
    private static final String SHARED_DEPENDENCY2 = "pkg:maven/com.example/shared_dependency2@1.0.0?type=jar";
    private static final String TEST_NESTED_DEPENDENCY2 = "pkg:maven/com.example/test_nested_dependency2@1.0.0?type=jar";
    private static final String TEST_NESTED_DEPENDENCY3 = "pkg:maven/com.example/test_nested_dependency3@1.0.0?type=jar";
    private static final String SHARED_RUNTIME_DEPENDENCY1 = "pkg:maven/com.example/shared_runtime_dependency1@1.0.0?type=jar";
    private static final String SHARED_RUNTIME_DEPENDENCY2 = "pkg:maven/com.example/shared_runtime_dependency2@1.0.0?type=jar";
    private static final String TEST_COMPILE_DEPENDENCY = "pkg:maven/com.example/test_compile_dependency@1.0.0?type=jar";
    private static final String TYPE_DEPENDENCY = "pkg:maven/com.example/type_dependency@1.0.0?classifier=tests&type=test-jar";
    private static final String SHARED_TYPE_DEPENDENCY1 = "pkg:maven/com.example/shared_type_dependency1@1.0.0?type=jar";
    private static final String SHARED_TYPE_DEPENDENCY2 = "pkg:maven/com.example/shared_type_dependency2@1.0.0?type=jar";
    private static final String SHARED_TYPE_DEPENDENCY3 = "pkg:maven/com.example/shared_type_dependency3@1.0.0?type=jar";
    private static final String SHARED_TYPE_DEPENDENCY4 = "pkg:maven/com.example/shared_type_dependency4@1.0.0?type=jar";
    private static final String VERSIONED_DEPENDENCY1 = "pkg:maven/com.example/versioned_dependency@1.0.0?type=jar";
    private static final String VERSIONED_DEPENDENCY2 = "pkg:maven/com.example/versioned_dependency@2.0.0?type=jar";
    private static final String PROVIDED_DEPENDENCY = "pkg:maven/com.example/provided_dependency@1.0.0?type=jar";
    private static final String DEPENDENCY1 = "pkg:maven/com.example/dependency1@1.0.0?type=jar";

    public BomDependenciesTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testBomDependencies() throws Exception {
        final File projDir = cleanAndBuild("bom-dependencies", null);
        checkHiddenTestArtifacts(projDir);
        checkHiddenRuntimeArtifacts(projDir);
        checkExtraneousComponents(projDir);
        checkTopLevelTestComponentsAsCompile(projDir);
        testHiddenVersionedTransitiveDependencies(projDir);
    }

    /**
     * This test ensures that any dependencies hidden by <i>test</i> dependencies are discovered and present in the dependency graph
     * @throws Exception
     */
    private void checkHiddenTestArtifacts(final File projDir) throws Exception {
        final Document bom = readXML(new File(projDir, "trustification/target/bom.xml"));
        final Map<String, Collection<String>> purlToIdentities = getPUrlToIdentities(bom.getDocumentElement());

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
        final Element dependencies = (Element)dependenciesList.item(0);

        /*
           <dependency ref="pkg:maven/com.example/shared_dependency1@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/shared_dependency2@1.0.0?type=jar"/>
             <dependency ref="pkg:maven/com.example/test_nested_dependency2@1.0.0?type=jar"/>
           </dependency>
        */
        final Element sharedDependency1Node = getDependencyNode(purlToIdentities, dependencies, SHARED_DEPENDENCY1);
        // Note: there are three dependencies for shared_dependency1, however one has runtime scope and should not be discovered
        final Set<String> testSharedDependency1Dependencies = getDependencyReferences(sharedDependency1Node);
        assertEquals("Invalid dependency count for shared_dependency1", 2, testSharedDependency1Dependencies.size());
        containsDependency(purlToIdentities, testSharedDependency1Dependencies, SHARED_DEPENDENCY2);
        containsDependency(purlToIdentities, testSharedDependency1Dependencies, TEST_NESTED_DEPENDENCY2);

        /*
           <dependency ref="pkg:maven/com.example/shared_dependency2@1.0.0?type=jar"/>
        */
        final Element sharedDependency2Node = getDependencyNode(purlToIdentities, dependencies, SHARED_DEPENDENCY2);
        final Set<String> testSharedDependency2Dependencies = getDependencyReferences(sharedDependency2Node);
        assertEquals("Invalid dependency count for shared_dependency2", 0, testSharedDependency2Dependencies.size());

        /*
           <dependency ref="pkg:maven/com.example/test_nested_dependency2@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/test_nested_dependency3@1.0.0?type=jar"/>
           </dependency>
        */
        final Element testNestedDependency2Node = getDependencyNode(purlToIdentities, dependencies, TEST_NESTED_DEPENDENCY2);
        Set<String> testNestedDependency2Dependencies = getDependencyReferences(testNestedDependency2Node);
        assertEquals("Invalid dependency count for test_nested_dependency2", 1, testNestedDependency2Dependencies.size());
        containsDependency(purlToIdentities, testNestedDependency2Dependencies, TEST_NESTED_DEPENDENCY3);

        /*
           <dependency ref="pkg:maven/com.example/test_nested_dependency3@1.0.0?type=jar"/>
        */
        final Element testNestedDependency3Node = getDependencyNode(purlToIdentities, dependencies, TEST_NESTED_DEPENDENCY3);
        Set<String> testNestedDependency3Dependencies = getDependencyReferences(testNestedDependency3Node);
        assertEquals("Invalid dependency count for test_nested_dependency3", 0, testNestedDependency3Dependencies.size());
    }

    /**
     * This test ensures that any dependencies hidden by <i>runtime</i> dependencies are discovered and present in the dependency graph
     * @throws Exception
     */
    private void checkHiddenRuntimeArtifacts(final File projDir) throws Exception {
        final Document bom = readXML(new File(projDir, "trustification/target/bom.xml"));
        final Map<String, Collection<String>> purlToIdentities = getPUrlToIdentities(bom.getDocumentElement());

        /* BOM should contain dependency elements for
           <dependency ref="pkg:maven/com.example/shared_runtime_dependency1@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/shared_runtime_dependency2@1.0.0?type=jar"/>
           </dependency>
           <dependency ref="pkg:maven/com.example/shared_runtime_dependency2@1.0.0?type=jar"/>
        */
        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Element dependencies = (Element)dependenciesList.item(0);

        /*
           <dependency ref="pkg:maven/com.example/shared_runtime_dependency1@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/shared_runtime_dependency2@1.0.0?type=jar"/>
           </dependency>
        */
        final Element sharedRuntimeDependency1Node = getDependencyNode(purlToIdentities, dependencies, SHARED_RUNTIME_DEPENDENCY1);
        final Set<String> testSharedDependency1Dependencies = getDependencyReferences(sharedRuntimeDependency1Node);
        assertEquals("Invalid dependency count for shared_runtime_dependency1", 1, testSharedDependency1Dependencies.size());
        containsDependency(purlToIdentities, testSharedDependency1Dependencies, SHARED_RUNTIME_DEPENDENCY2);

        /*
           <dependency ref="pkg:maven/com.example/shared_runtime_dependency2@1.0.0?type=jar"/>
        */
        final Element sharedRuntimeDependency2Node = getDependencyNode(purlToIdentities, dependencies, SHARED_RUNTIME_DEPENDENCY2);
        final Set<String> testSharedDependency2Dependencies = getDependencyReferences(sharedRuntimeDependency2Node);
        assertEquals("Invalid dependency count for shared_runtime_dependency2", 0, testSharedDependency2Dependencies.size());
    }

    /**
     * This test ensures that the Components and Dependencies are consistent, and that all sub-dependencies exist at the top level.
     * @throws Exception
     */
    private void checkExtraneousComponents(final File projDir) throws Exception {
        final Document bom = readXML(new File(projDir, "trustification/target/bom.xml"));

        final NodeList metadataList = bom.getElementsByTagName("metadata");
        assertEquals("Expected a single metadata element", 1, metadataList.getLength());
        final Element metadata = (Element)metadataList.item(0);
        final Set<String> metadataComponentReferences = getComponentReferences(metadata);

        final NodeList componentsList = bom.getElementsByTagName("components");
        assertEquals("Expected a single components element", 1, componentsList.getLength());
        final Element components = (Element)componentsList.item(0);
        final Set<String> componentReferences = getComponentReferences(components);

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Element dependencies = (Element)dependenciesList.item(0);
        final Set<String> dependencyReferences = getDependencyReferences(dependencies);

        // Each dependency reference should have a component
        for (String dependencyRef: dependencyReferences) {
            assertTrue("Missing component for dependency reference " + dependencyRef,
                componentReferences.contains(dependencyRef) || metadataComponentReferences.contains(dependencyRef));
        }

        // Each component reference should have a top level dependency
        for (String componentRef: componentReferences) {
            assertTrue("Missing dependency for component reference " + componentRef, dependencyReferences.contains(componentRef));
        }
    }

    /**
     * This test ensures that any <i>compile</i> dependencies matching top level <i>test</i> dependencies are discovered and present in the dependency graph
     * @throws Exception
     */
    private void checkTopLevelTestComponentsAsCompile(final File projDir) throws Exception {
        final Document bom = readXML(new File(projDir, "trustification/target/bom.xml"));
        final Map<String, Collection<String>> purlToIdentities = getPUrlToIdentities(bom.getDocumentElement());

        // BOM should contain a component element for pkg:maven/com.example/test_compile_dependency@1.0.0?type=jar
        final NodeList componentsList = bom.getElementsByTagName("components");
        assertEquals("Expected a single components element", 1, componentsList.getLength());
        final Element components = (Element)componentsList.item(0);
        getComponentNode(purlToIdentities, components, TEST_COMPILE_DEPENDENCY);
    }

    /**
     * This test ensures that any <i>compile</i> dependencies hidden by excluded types are included in the BOM if they are visible dependencies
     * @throws Exception
     */
    @Test
    public void testTypeExcludes() throws Exception {
        final File projDir = cleanAndBuild("bom-dependencies", new String[]{"test-jar"});

        final Document bom = readXML(new File(projDir, "trustification/target/bom.xml"));
        final Map<String, Collection<String>> purlToIdentities = getPUrlToIdentities(bom.getDocumentElement());

        final NodeList componentsList = bom.getElementsByTagName("components");
        assertEquals("Expected a single components element", 1, componentsList.getLength());
        final Element components = (Element)componentsList.item(0);

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Element dependencies = (Element)dependenciesList.item(0);

        // BOM should not contain pkg:maven/com.example/type_dependency@1.0.0?classifier=tests&type=test-jar
        // component nor top level dependency because of type test-jar
        final Collection<Element> testTypeDependencyComponentNodes = getComponentNodes(purlToIdentities, components, TYPE_DEPENDENCY);
        assertNull("Unexpected type_dependency component discovered in BOM", testTypeDependencyComponentNodes);
        final Collection<Element> testTypeDependencyNodes = getDependencyNodes(purlToIdentities, dependencies, TYPE_DEPENDENCY);
        assertNull("Unexpected type_dependency dependency discovered in BOM", testTypeDependencyNodes);

        // BOM should contain pkg:maven/com.example/shared_type_dependency1@1.0.0?type=jar and shared_test_dependency2 and
        // pkg:maven/com.example/shared_type_dependency2@1.0.0?type=jar components/dependencies as they are referenced by dependency2
        getComponentNode(purlToIdentities, components, SHARED_TYPE_DEPENDENCY1);
        getComponentNode(purlToIdentities, components, SHARED_TYPE_DEPENDENCY2);
        /*
           <dependency ref="pkg:maven/com.example/shared_type_dependency1@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/shared_type_dependency2@1.0.0?type=jar"/>
           </dependency>
           <dependency ref="pkg:maven/com.example/shared_type_dependency2@1.0.0?type=jar"/>
        */
        final Element sharedTypeDependency1Node = getDependencyNode(purlToIdentities, dependencies, SHARED_TYPE_DEPENDENCY1);
        Set<String> sharedTypeDependency1Dependencies = getDependencyReferences(sharedTypeDependency1Node);
        assertEquals("Invalid dependency count for shared_type_dependency1", 1, sharedTypeDependency1Dependencies.size());
        containsDependency(purlToIdentities, sharedTypeDependency1Dependencies, SHARED_TYPE_DEPENDENCY2);

        getDependencyNode(purlToIdentities, dependencies, SHARED_TYPE_DEPENDENCY2);

        // BOM should not contain pkg:maven/com.example/shared_type_dependency3@1.0.0?type=jar nor
        // pkg:maven/com.example/shared_type_dependency4@1.0.0?type=jar components/dependencies
        // as they are only referenced via type_dependency
        final Collection<Element> sharedTypeDependency3ComponentNodes = getComponentNodes(purlToIdentities, components, SHARED_TYPE_DEPENDENCY3);
        assertNull("Unexpected shared_type_dependency3 component discovered in BOM", sharedTypeDependency3ComponentNodes);
        final Collection<Element> sharedTypeDependency3Nodes = getDependencyNodes(purlToIdentities, dependencies, SHARED_TYPE_DEPENDENCY3);
        assertNull("Unexpected shared_type_dependency3 dependency discovered in BOM", sharedTypeDependency3Nodes);

        final Collection<Element> sharedTypeDependency4ComponentNodes = getComponentNodes(purlToIdentities, components, SHARED_TYPE_DEPENDENCY4);
        assertNull("Unexpected shared_type_dependency4 component discovered in BOM", sharedTypeDependency4ComponentNodes);
        final Collection<Element> sharedTypeDependency4Nodes = getDependencyNodes(purlToIdentities, dependencies, SHARED_TYPE_DEPENDENCY4);
        assertNull("Unexpected shared_type_dependency4 dependency discovered in BOM", sharedTypeDependency4Nodes);
    }

    /**
     * This test ensures that transitive dependencies hidden under versioned components are included in the BOM.
     * @throws Exception
     */
    private void testHiddenVersionedTransitiveDependencies(final File projDir) throws Exception {
        // Note: checkExtraneousComponents will also catch missing versioned dependencies but doesn't check for transitive dependencies
        final Document bom = readXML(new File(projDir, "trustification/target/bom.xml"));
        final Map<String, Collection<String>> purlToIdentities = getPUrlToIdentities(bom.getDocumentElement());

        final NodeList componentsList = bom.getElementsByTagName("components");
        assertEquals("Expected a single components element", 1, componentsList.getLength());
        final Element components = (Element)componentsList.item(0);

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Element dependencies = (Element)dependenciesList.item(0);

        // BOM should not contain pkg:maven/com.example/versioned_dependency@1.0.0?type=jar
        final Collection<Element> testVersionedDependency1ComponentNodes = getComponentNodes(purlToIdentities, components, VERSIONED_DEPENDENCY1);
        assertNull("Unexpected versioned_dependency:1.0.0 component discovered in BOM", testVersionedDependency1ComponentNodes);
        final Collection<Element> testVersionedDependency1Nodes = getDependencyNodes(purlToIdentities, dependencies, VERSIONED_DEPENDENCY1);
        assertNull("Unexpected versioned_dependency:1.0.0 dependency discovered in BOM", testVersionedDependency1Nodes);

        // BOM should contain pkg:maven/com.example/versioned_dependency@2.0.0?type=jar
        getComponentNode(purlToIdentities, components, VERSIONED_DEPENDENCY2);

        /*
           <dependency ref="pkg:maven/com.example/provided_dependency@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/versioned_dependency@2.0.0?type=jar"/>
           </dependency>
        */
        final Element providedDependencyNode = getDependencyNode(purlToIdentities, dependencies, PROVIDED_DEPENDENCY);
        Set<String> providedDependencyDependencies = getDependencyReferences(providedDependencyNode);
        assertEquals("Invalid dependency count for provided_dependency", 1, providedDependencyDependencies.size());
        containsDependency(purlToIdentities, providedDependencyDependencies, VERSIONED_DEPENDENCY2);

        /*
           <dependency ref="pkg:maven/com.example/versioned_dependency@2.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/dependency1@1.0.0?type=jar"/>
           </dependency>
        */
        final Element versionedDependencyNode = getDependencyNode(purlToIdentities, dependencies, VERSIONED_DEPENDENCY2);
        Set<String> versionedDependencyDependencies = getDependencyReferences(versionedDependencyNode);
        assertEquals("Invalid dependency count for versioned_dependency", 1, versionedDependencyDependencies.size());
        containsDependency(purlToIdentities, versionedDependencyDependencies, DEPENDENCY1);
    }
}
