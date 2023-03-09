package org.cyclonedx.maven;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.cyclonedx.maven.TestUtils.containsDependency;
import static org.cyclonedx.maven.TestUtils.getDependencyNode;
import static org.cyclonedx.maven.TestUtils.getDependencyReferences;
import static org.cyclonedx.maven.TestUtils.getPUrlToIdentities;
import static org.cyclonedx.maven.TestUtils.readXML;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
    private static final String RUNTIME_SHARED_DEPENDENCY2 = "pkg:maven/com.example/runtime_shared_dependency2@1.0.0?type=jar";

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
        final Map<String, Collection<String>> purlToIdentities = getPUrlToIdentities(bom.getDocumentElement());

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Element dependencies = (Element)dependenciesList.item(0);

        /*
           <dependency ref="pkg:maven/com.example/runtime_compile@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/runtime_runtime_dependency@1.0.0?type=jar"/>
             <dependency ref="pkg:maven/com.example/runtime_dependency@1.0.0?type=jar"/>
           </dependency>
         */
        final Element runtimeCompileNode = getDependencyNode(purlToIdentities, dependencies, RUNTIME_COMPILE);
        Set<String> runtimeCompileDependencies = getDependencyReferences(runtimeCompileNode);
        assertEquals("Invalid dependency count for runtime_compile", 2, runtimeCompileDependencies.size());
        containsDependency(purlToIdentities, runtimeCompileDependencies, RUNTIME_RUNTIME_DEPENDENCY);
        containsDependency(purlToIdentities, runtimeCompileDependencies, RUNTIME_DEPENDENCY);

        /*
           <dependency ref="pkg:maven/com.example/runtime_runtime_dependency@1.0.0?type=jar"/>
         */
        final Element runtimeRuntimeDependencyNode = getDependencyNode(purlToIdentities, dependencies, RUNTIME_RUNTIME_DEPENDENCY);
        Set<String> runtimeRuntimeDependencyDependencies = getDependencyReferences(runtimeRuntimeDependencyNode);
        assertEquals("Invalid dependency count for runtime_runtime_dependency", 0, runtimeRuntimeDependencyDependencies.size());

        /*
           <dependency ref="pkg:maven/com.example/runtime_dependency@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/runtime_shared_dependency1@1.0.0?type=jar"/>
           </dependency>
         */
        final Element runtimeDependencyNode = getDependencyNode(purlToIdentities, dependencies, RUNTIME_DEPENDENCY);
        Set<String> runtimeDependencyDependencies = getDependencyReferences(runtimeDependencyNode);
        assertEquals("Invalid dependency count for runtime_dependency", 1, runtimeDependencyDependencies.size());
        containsDependency(purlToIdentities, runtimeDependencyDependencies, RUNTIME_SHARED_DEPENDENCY1);

        /*
           <dependency ref="pkg:maven/com.example/runtime_shared_dependency1@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/runtime_shared_dependency2@1.0.0?type=jar"/>
           </dependency>
         */
        final Element runtimeSharedDependency1Node = getDependencyNode(purlToIdentities, dependencies, RUNTIME_SHARED_DEPENDENCY1);
        Set<String> runtimeSharedDependency1Dependencies = getDependencyReferences(runtimeSharedDependency1Node);
        assertEquals("Invalid dependency count for runtime_shared_dependency1", 1, runtimeSharedDependency1Dependencies.size());
        containsDependency(purlToIdentities, runtimeSharedDependency1Dependencies, RUNTIME_SHARED_DEPENDENCY2);
    }

    public void checkRuntimeProvided(final File projDir) throws Exception {
        final Document bom = readXML(new File(projDir, "runtime_provided/target/bom.xml"));
        final Map<String, Collection<String>> purlToIdentities = getPUrlToIdentities(bom.getDocumentElement());

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Element dependencies = (Element)dependenciesList.item(0);

        /*
           <dependency ref="pkg:maven/com.example/runtime_provided@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/runtime_shared_dependency1@1.0.0?type=jar"/>
             <dependency ref="pkg:maven/com.example/runtime_runtime_dependency@1.0.0?type=jar"/>
           </dependency>
         */
        final Element runtimeProvidedNode = getDependencyNode(purlToIdentities, dependencies, RUNTIME_PROVIDED);
        Set<String> runtimeProvidedDependencies = getDependencyReferences(runtimeProvidedNode);
        assertEquals("Invalid dependency count for runtime_provided", 2, runtimeProvidedDependencies.size());
        containsDependency(purlToIdentities, runtimeProvidedDependencies, RUNTIME_SHARED_DEPENDENCY1);
        containsDependency(purlToIdentities, runtimeProvidedDependencies, RUNTIME_RUNTIME_DEPENDENCY);

        /*
           <dependency ref="pkg:maven/com.example/runtime_shared_dependency1@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/runtime_shared_dependency2@1.0.0?type=jar"/>
           </dependency>
         */
        final Element runtimeSharedDependency1Node = getDependencyNode(purlToIdentities, dependencies, RUNTIME_SHARED_DEPENDENCY1);
        Set<String> runtimeSharedDependency1Dependencies = getDependencyReferences(runtimeSharedDependency1Node);
        assertEquals("Invalid dependency count for runtime_shared_dependency1", 1, runtimeSharedDependency1Dependencies.size());
        containsDependency(purlToIdentities, runtimeSharedDependency1Dependencies, RUNTIME_SHARED_DEPENDENCY2);

        /*
           <dependency ref="pkg:maven/com.example/runtime_runtime_dependency@1.0.0?type=jar"/>
         */
        final Element runtimeRuntimeDependencyNode = getDependencyNode(purlToIdentities, dependencies, RUNTIME_RUNTIME_DEPENDENCY);
        Set<String> runtimeRuntimeDependencyDependencies = getDependencyReferences(runtimeRuntimeDependencyNode);
        assertEquals("Invalid dependency count for runtime_runtime_dependency", 0, runtimeRuntimeDependencyDependencies.size());
    }

    public void checkRuntimeTest(final File projDir) throws Exception {
        final Document bom = readXML(new File(projDir, "runtime_test/target/bom.xml"));
        final Map<String, Collection<String>> purlToIdentities = getPUrlToIdentities(bom.getDocumentElement());

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Element dependencies = (Element)dependenciesList.item(0);

        /*
           <dependency ref="pkg:maven/com.example/runtime_test@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/runtime_shared_dependency1@1.0.0?type=jar"/>
             <dependency ref="pkg:maven/com.example/runtime_runtime_dependency@1.0.0?type=jar"/>
           </dependency>
         */
        final Element runtimeTestNode = getDependencyNode(purlToIdentities, dependencies, RUNTIME_TEST);
        Set<String> runtimeTestDependencies = getDependencyReferences(runtimeTestNode);
        assertEquals("Invalid dependency count for runtime_test", 2, runtimeTestDependencies.size());
        containsDependency(purlToIdentities, runtimeTestDependencies, RUNTIME_SHARED_DEPENDENCY1);
        containsDependency(purlToIdentities, runtimeTestDependencies, RUNTIME_RUNTIME_DEPENDENCY);

        /*
           <dependency ref="pkg:maven/com.example/runtime_shared_dependency1@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/runtime_shared_dependency2@1.0.0?type=jar"/>
           </dependency>
         */
        final Element runtimeSharedDependency1Node = getDependencyNode(purlToIdentities, dependencies, RUNTIME_SHARED_DEPENDENCY1);
        Set<String> runtimeSharedDependency1Dependencies = getDependencyReferences(runtimeSharedDependency1Node);
        assertEquals("Invalid dependency count for runtime_shared_dependency1", 1, runtimeSharedDependency1Dependencies.size());
        containsDependency(purlToIdentities, runtimeSharedDependency1Dependencies, RUNTIME_SHARED_DEPENDENCY2);

        /*
           <dependency ref="pkg:maven/com.example/runtime_runtime_dependency@1.0.0?type=jar"/>
         */
        final Element runtimeRuntimeDependencyNode = getDependencyNode(purlToIdentities, dependencies, RUNTIME_RUNTIME_DEPENDENCY);
        Set<String> runtimeRuntimeDependencyDependencies = getDependencyReferences(runtimeRuntimeDependencyNode);
        assertEquals("Invalid dependency count for runtime_runtime_dependency", 0, runtimeRuntimeDependencyDependencies.size());
    }
}
