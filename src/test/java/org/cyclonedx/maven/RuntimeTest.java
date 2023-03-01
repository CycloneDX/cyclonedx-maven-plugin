package org.cyclonedx.maven;

import java.io.File;
import java.util.Set;

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
 * test for ensuring runtime artifacts are correctly filtered
 * Fix filtering of scopes
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class RuntimeTest extends BaseMavenVerifier {

    private static final String RUNTIME_DEPENDENCY = "pkg:maven/com.example/runtime_dependency@1.0.0?type=jar";
    private static final String RUNTIME_RUNTIME_DEPENDENCY = "pkg:maven/com.example/runtime_runtime_dependency@1.0.0?type=jar";
    private static final String RUNTIME_COMPILE = "pkg:maven/com.example/runtime_compile@1.0.0?type=jar";
    private static final String RUNTIME_PROVIDED = "pkg:maven/com.example/runtime_provided@1.0.0?type=jar";
    private static final String RUNTIME_TEST = "pkg:maven/com.example/runtime_test@1.0.0?type=jar";
    private static final String RUNTIME_SHARED_DEPENDENCY1 = "pkg:maven/com.example/runtime_shared_dependency1@1.0.0?type=jar";
    private static final Object RUNTIME_SHARED_DEPENDENCY2 = "pkg:maven/com.example/runtime_shared_dependency2@1.0.0?type=jar";

    public RuntimeTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testRuntime() throws Exception {
      final File projDir = cleanAndBuild("runtime", null);
      checkRuntimeCompile(projDir);
      checkRuntimeProvided(projDir);
      checkRuntimeTest(projDir);
    }

    public void checkRuntimeCompile(final File projDir) throws Exception {
        final Document bom = readXML(new File(projDir, "runtime_compile/target/bom.xml"));

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Node dependencies = dependenciesList.item(0);

        /*
           <dependency ref="pkg:maven/com.example/runtime_compile@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/runtime_runtime_dependency@1.0.0?type=jar"/>
             <dependency ref="pkg:maven/com.example/runtime_dependency@1.0.0?type=jar"/>
           </dependency>
         */
        final Node runtimeCompileNode = getDependencyNode(dependencies, RUNTIME_COMPILE);
        assertNotNull("Missing runtime_compile dependency", runtimeCompileNode);
        Set<String> runtimeCompileDependencies = getDependencyReferences(runtimeCompileNode);
        assertEquals("Invalid dependency count for runtime_compile", 2, runtimeCompileDependencies.size());
        assertTrue("Missing runtime_runtime_dependency dependency for runtime_compile", runtimeCompileDependencies.contains(RUNTIME_RUNTIME_DEPENDENCY));
        assertTrue("Missing runtime_dependency dependency for runtime_compile", runtimeCompileDependencies.contains(RUNTIME_DEPENDENCY));

        /*
           <dependency ref="pkg:maven/com.example/runtime_runtime_dependency@1.0.0?type=jar"/>
         */
        final Node runtimeRuntimeDependencyNode = getDependencyNode(dependencies, RUNTIME_RUNTIME_DEPENDENCY);
        assertNotNull("Missing runtime_runtime_dependency dependency", runtimeRuntimeDependencyNode);
        Set<String> runtimeRuntimeDependencyDependencies = getDependencyReferences(runtimeRuntimeDependencyNode);
        assertEquals("Invalid dependency count for runtime_runtime_dependency", 0, runtimeRuntimeDependencyDependencies.size());

        /*
           <dependency ref="pkg:maven/com.example/runtime_dependency@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/runtime_shared_dependency1@1.0.0?type=jar"/>
           </dependency>
         */
        final Node runtimeDependencyNode = getDependencyNode(dependencies, RUNTIME_DEPENDENCY);
        assertNotNull("Missing runtime_dependency dependency", runtimeDependencyNode);
        Set<String> runtimeDependencyDependencies = getDependencyReferences(runtimeDependencyNode);
        assertEquals("Invalid dependency count for runtime_dependency", 1, runtimeDependencyDependencies.size());
        assertTrue("Missing runtime_shared_dependency1 dependency for runtime_dependency", runtimeDependencyDependencies.contains(RUNTIME_SHARED_DEPENDENCY1));

        /*
           <dependency ref="pkg:maven/com.example/runtime_shared_dependency1@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/runtime_shared_dependency2@1.0.0?type=jar"/>
           </dependency>
         */
        final Node runtimeSharedDependency1Node = getDependencyNode(dependencies, RUNTIME_SHARED_DEPENDENCY1);
        assertNotNull("Missing runtime_shared_dependency1 dependency", runtimeSharedDependency1Node);
        Set<String> runtimeSharedDependency1Dependencies = getDependencyReferences(runtimeSharedDependency1Node);
        assertEquals("Invalid dependency count for runtime_shared_dependency1", 1, runtimeSharedDependency1Dependencies.size());
        assertTrue("Missing runtime_shared_dependency2 dependency for runtime_shared_dependency1", runtimeSharedDependency1Dependencies.contains(RUNTIME_SHARED_DEPENDENCY2));
    }

    public void checkRuntimeProvided(final File projDir) throws Exception {
        final Document bom = readXML(new File(projDir, "runtime_provided/target/bom.xml"));

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Node dependencies = dependenciesList.item(0);

        /*
           <dependency ref="pkg:maven/com.example/runtime_provided@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/runtime_shared_dependency1@1.0.0?type=jar"/>
             <dependency ref="pkg:maven/com.example/runtime_runtime_dependency@1.0.0?type=jar"/>
           </dependency>
         */
        final Node runtimeProvidedNode = getDependencyNode(dependencies, RUNTIME_PROVIDED);
        assertNotNull("Missing runtime_provided dependency", runtimeProvidedNode);
        Set<String> runtimeProvidedDependencies = getDependencyReferences(runtimeProvidedNode);
        assertEquals("Invalid dependency count for runtime_provided", 2, runtimeProvidedDependencies.size());
        assertTrue("Missing runtime_shared_dependency1 dependency for runtime_provided", runtimeProvidedDependencies.contains(RUNTIME_SHARED_DEPENDENCY1));
        assertTrue("Missing runtime_runtime_dependency dependency for runtime_provided", runtimeProvidedDependencies.contains(RUNTIME_RUNTIME_DEPENDENCY));

        /*
           <dependency ref="pkg:maven/com.example/runtime_shared_dependency1@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/runtime_shared_dependency2@1.0.0?type=jar"/>
           </dependency>
         */
        final Node runtimeSharedDependency1Node = getDependencyNode(dependencies, RUNTIME_SHARED_DEPENDENCY1);
        assertNotNull("Missing runtime_shared_dependency1 dependency", runtimeSharedDependency1Node);
        Set<String> runtimeSharedDependency1Dependencies = getDependencyReferences(runtimeSharedDependency1Node);
        assertEquals("Invalid dependency count for runtime_shared_dependency1", 1, runtimeSharedDependency1Dependencies.size());
        assertTrue("Missing runtime_shared_dependency2 dependency for runtime_shared_dependency1", runtimeSharedDependency1Dependencies.contains(RUNTIME_SHARED_DEPENDENCY2));

        /*
           <dependency ref="pkg:maven/com.example/runtime_runtime_dependency@1.0.0?type=jar"/>
         */
        final Node runtimeRuntimeDependencyNode = getDependencyNode(dependencies, RUNTIME_RUNTIME_DEPENDENCY);
        assertNotNull("Missing runtime_runtime_dependency dependency", runtimeRuntimeDependencyNode);
        Set<String> runtimeRuntimeDependencyDependencies = getDependencyReferences(runtimeRuntimeDependencyNode);
        assertEquals("Invalid dependency count for runtime_runtime_dependency", 0, runtimeRuntimeDependencyDependencies.size());
    }

    public void checkRuntimeTest(final File projDir) throws Exception {
        final Document bom = readXML(new File(projDir, "runtime_test/target/bom.xml"));

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Node dependencies = dependenciesList.item(0);

        /*
           <dependency ref="pkg:maven/com.example/runtime_test@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/runtime_shared_dependency1@1.0.0?type=jar"/>
             <dependency ref="pkg:maven/com.example/runtime_runtime_dependency@1.0.0?type=jar"/>
           </dependency>
         */
        final Node runtimeTestNode = getDependencyNode(dependencies, RUNTIME_TEST);
        assertNotNull("Missing runtime_test dependency", runtimeTestNode);
        Set<String> runtimeTestDependencies = getDependencyReferences(runtimeTestNode);
        assertEquals("Invalid dependency count for runtime_test", 2, runtimeTestDependencies.size());
        assertTrue("Missing runtime_shared_dependency1 dependency for runtime_test", runtimeTestDependencies.contains(RUNTIME_SHARED_DEPENDENCY1));
        assertTrue("Missing runtime_runtime_dependency dependency for runtime_test", runtimeTestDependencies.contains(RUNTIME_RUNTIME_DEPENDENCY));

        /*
           <dependency ref="pkg:maven/com.example/runtime_shared_dependency1@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/runtime_shared_dependency2@1.0.0?type=jar"/>
           </dependency>
         */
        final Node runtimeSharedDependency1Node = getDependencyNode(dependencies, RUNTIME_SHARED_DEPENDENCY1);
        assertNotNull("Missing runtime_shared_dependency1 dependency", runtimeSharedDependency1Node);
        Set<String> runtimeSharedDependency1Dependencies = getDependencyReferences(runtimeSharedDependency1Node);
        assertEquals("Invalid dependency count for runtime_shared_dependency1", 1, runtimeSharedDependency1Dependencies.size());
        assertTrue("Missing runtime_shared_dependency2 dependency for runtime_shared_dependency1", runtimeSharedDependency1Dependencies.contains(RUNTIME_SHARED_DEPENDENCY2));

        /*
           <dependency ref="pkg:maven/com.example/runtime_runtime_dependency@1.0.0?type=jar"/>
         */
        final Node runtimeRuntimeDependencyNode = getDependencyNode(dependencies, RUNTIME_RUNTIME_DEPENDENCY);
        assertNotNull("Missing runtime_runtime_dependency dependency", runtimeRuntimeDependencyNode);
        Set<String> runtimeRuntimeDependencyDependencies = getDependencyReferences(runtimeRuntimeDependencyNode);
        assertEquals("Invalid dependency count for runtime_runtime_dependency", 0, runtimeRuntimeDependencyDependencies.size());
    }
}
