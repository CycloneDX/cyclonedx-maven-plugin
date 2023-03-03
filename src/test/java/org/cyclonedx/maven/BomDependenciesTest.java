package org.cyclonedx.maven;

import static org.cyclonedx.maven.TestUtils.getComponentNode;
import static org.cyclonedx.maven.TestUtils.getComponentReferences;
import static org.cyclonedx.maven.TestUtils.getDependencyNode;
import static org.cyclonedx.maven.TestUtils.getDependencyReferences;
import static org.cyclonedx.maven.TestUtils.readXML;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
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
        // Note: there are three dependencies for shared_dependency1, however one has runtime scope and should not be discovered
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
     * This test ensures that any dependencies hidden by <i>runtime</i> dependencies are discovered and present in the dependency graph
     * @throws Exception
     */
    private void checkHiddenRuntimeArtifacts(final File projDir) throws Exception {
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

    /**
     * This test ensures that the Components and Dependencies are consistent, and that all sub-dependencies exist at the top level.
     * @throws Exception
     */
    private void checkExtraneousComponents(final File projDir) throws Exception {
        final Document bom = readXML(new File(projDir, "trustification/target/bom.xml"));

        final NodeList metadataList = bom.getElementsByTagName("metadata");
        assertEquals("Expected a single metadata element", 1, metadataList.getLength());
        final Node metadata = metadataList.item(0);
        final Set<String> metadataComponentReferences = getComponentReferences(metadata);

        final NodeList componentsList = bom.getElementsByTagName("components");
        assertEquals("Expected a single components element", 1, componentsList.getLength());
        final Node components = componentsList.item(0);
        final Set<String> componentReferences = getComponentReferences(components);

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Node dependencies = dependenciesList.item(0);
        final Set<String> dependencyReferences = getDependencyReferences(dependencies);

        // Each dependency reference should have a component
        for (String dependencyRef: dependencyReferences) {
            assertTrue("Missing component for dependency reference " + dependencyRef,
                componentReferences.contains(dependencyRef) || metadataComponentReferences.contains(dependencyRef));
        }

        // Each component reference should have a top level dependency
        for (String componentRef: componentReferences) {
            assertNotNull("Missing top level dependency for component reference " + componentRef, getDependencyNode(dependencies, componentRef));
        }
    }

    /**
     * This test ensures that any <i>compile</i> dependencies matching top level <i>test</i> dependencies are discovered and present in the dependency graph
     * @throws Exception
     */
    private void checkTopLevelTestComponentsAsCompile(final File projDir) throws Exception {
        final Document bom = readXML(new File(projDir, "trustification/target/bom.xml"));

        // BOM should contain a component element for pkg:maven/com.example/test_compile_dependency@1.0.0?type=jar
        final NodeList componentsList = bom.getElementsByTagName("components");
        assertEquals("Expected a single components element", 1, componentsList.getLength());
        final Node components = componentsList.item(0);
        final Node testCompileDependencyNode = getComponentNode(components, TEST_COMPILE_DEPENDENCY);
        assertNotNull("Missing test_compile_dependency component", testCompileDependencyNode);
    }

    /**
     * This test ensures that any <i>compile</i> dependencies hidden by excluded types are included in the BOM if they are visible dependencies
     * @throws Exception
     */
    @Test
    public void testTypeExcludes() throws Exception {
        final File projDir = cleanAndBuild("bom-dependencies", new String[]{"test-jar"});

        final Document bom = readXML(new File(projDir, "trustification/target/bom.xml"));

        final NodeList componentsList = bom.getElementsByTagName("components");
        assertEquals("Expected a single components element", 1, componentsList.getLength());
        final Node components = componentsList.item(0);

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Node dependencies = dependenciesList.item(0);

        // BOM should not contain pkg:maven/com.example/type_dependency@1.0.0?classifier=tests&type=test-jar
        // component nor top level dependency because of type test-jar
        final Node testTypeDependencyComponentNode = getComponentNode(components, TYPE_DEPENDENCY);
        assertNull("Unexpected type_dependency component discovered in BOM", testTypeDependencyComponentNode);
        final Node testTypeDependencyNode = getDependencyNode(dependencies, TYPE_DEPENDENCY);
        assertNull("Unexpected type_dependency dependency discovered in BOM", testTypeDependencyNode);

        // BOM should contain pkg:maven/com.example/shared_type_dependency1@1.0.0?type=jar and shared_test_dependency2 and
        // pkg:maven/com.example/shared_type_dependency2@1.0.0?type=jar components/dependencies as they are referenced by dependency2
        final Node sharedTypeDependency1ComponentNode = getComponentNode(components, SHARED_TYPE_DEPENDENCY1);
        assertNotNull("Missing shared_type_dependency1 component", sharedTypeDependency1ComponentNode);
        final Node sharedTypeDependency2ComponentNode = getComponentNode(components, SHARED_TYPE_DEPENDENCY2);
        assertNotNull("Missing shared_type_dependency2 component", sharedTypeDependency2ComponentNode);
        /*
           <dependency ref="pkg:maven/com.example/shared_type_dependency1@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/shared_type_dependency2@1.0.0?type=jar"/>
           </dependency>
           <dependency ref="pkg:maven/com.example/shared_type_dependency2@1.0.0?type=jar"/>
        */
        final Node sharedTypeDependency1Node = getDependencyNode(dependencies, SHARED_TYPE_DEPENDENCY1);
        assertNotNull("Missing shared_type_dependency1 dependency", sharedTypeDependency1Node);
        Set<String> sharedTypeDependency1Dependencies = getDependencyReferences(sharedTypeDependency1Node);
        assertEquals("Invalid dependency count for shared_type_dependency1", 1, sharedTypeDependency1Dependencies.size());
        assertTrue("Missing shared_type_dependency2 dependency for shared_type_dependency1", sharedTypeDependency1Dependencies.contains(SHARED_TYPE_DEPENDENCY2));

        final Node sharedTypeDependency2Node = getDependencyNode(dependencies, SHARED_TYPE_DEPENDENCY2);
        assertNotNull("Missing shared_type_dependency2 dependency", sharedTypeDependency2Node);

        // BOM should not contain pkg:maven/com.example/shared_type_dependency3@1.0.0?type=jar nor
        // pkg:maven/com.example/shared_type_dependency4@1.0.0?type=jar components/dependencies
        // as they are only referenced via type_dependency
        final Node sharedTypeDependency3ComponentNode = getComponentNode(components, SHARED_TYPE_DEPENDENCY3);
        assertNull("Unexpected shared_type_dependency3 component discovered in BOM", sharedTypeDependency3ComponentNode);
        final Node sharedTypeDependency3Node = getDependencyNode(dependencies, SHARED_TYPE_DEPENDENCY3);
        assertNull("Unexpected shared_type_dependency3 dependency discovered in BOM", sharedTypeDependency3Node);

        final Node sharedTypeDependency4ComponentNode = getComponentNode(components, SHARED_TYPE_DEPENDENCY4);
        assertNull("Unexpected shared_type_dependency4 component discovered in BOM", sharedTypeDependency4ComponentNode);
        final Node sharedTypeDependency4Node = getDependencyNode(dependencies, SHARED_TYPE_DEPENDENCY4);
        assertNull("Unexpected shared_type_dependency4 dependency discovered in BOM", sharedTypeDependency4Node);
    }

    /**
     * This test ensures that transitive dependencies hidden under versioned components are included in the BOM.
     * @throws Exception
     */
    private void testHiddenVersionedTransitiveDependencies(final File projDir) throws Exception {
        // Note: checkExtraneousComponents will also catch missing versioned dependencies but doesn't check for transitive dependencies
        final Document bom = readXML(new File(projDir, "trustification/target/bom.xml"));

        final NodeList componentsList = bom.getElementsByTagName("components");
        assertEquals("Expected a single components element", 1, componentsList.getLength());
        final Node components = componentsList.item(0);

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Node dependencies = dependenciesList.item(0);

        // BOM should not contain pkg:maven/com.example/versioned_dependency@1.0.0?type=jar
        final Node testVersionedDependency1ComponentNode = getComponentNode(components, VERSIONED_DEPENDENCY1);
        assertNull("Unexpected versioned_dependency:1.0.0 component discovered in BOM", testVersionedDependency1ComponentNode);
        final Node testVersionedDependency1Node = getDependencyNode(dependencies, VERSIONED_DEPENDENCY1);
        assertNull("Unexpected versioned_dependency:1.0.0 dependency discovered in BOM", testVersionedDependency1Node);

        // BOM should contain pkg:maven/com.example/versioned_dependency@2.0.0?type=jar
        final Node testVersionedDependency2ComponentNode = getComponentNode(components, VERSIONED_DEPENDENCY2);
        assertNotNull("Missing versioned_dependency:2.0.0 component component", testVersionedDependency2ComponentNode);

        /*
           <dependency ref="pkg:maven/com.example/provided_dependency@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/versioned_dependency@2.0.0?type=jar"/>
           </dependency>
        */
        final Node providedDependencyNode = getDependencyNode(dependencies, PROVIDED_DEPENDENCY);
        assertNotNull("Missing provided_dependency dependency", providedDependencyNode);
        Set<String> providedDependencyDependencies = getDependencyReferences(providedDependencyNode);
        assertEquals("Invalid dependency count for provided_dependency", 1, providedDependencyDependencies.size());
        assertTrue("Missing versioned_dependency:2.0.0 dependency for provided_dependency", providedDependencyDependencies.contains(VERSIONED_DEPENDENCY2));

        /*
           <dependency ref="pkg:maven/com.example/versioned_dependency@2.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/dependency1@1.0.0?type=jar"/>
           </dependency>
        */
        final Node versionedDependencyNode = getDependencyNode(dependencies, VERSIONED_DEPENDENCY2);
        assertNotNull("Missing versioned_dependency dependency", versionedDependencyNode);
        Set<String> versionedDependencyDependencies = getDependencyReferences(versionedDependencyNode);
        assertEquals("Invalid dependency count for versioned_dependency", 1, versionedDependencyDependencies.size());
        assertTrue("Missing dependency1 dependency for versioned_dependency", versionedDependencyDependencies.contains(DEPENDENCY1));
    }
}
