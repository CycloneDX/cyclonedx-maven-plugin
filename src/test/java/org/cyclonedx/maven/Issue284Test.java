package org.cyclonedx.maven;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.cyclonedx.maven.TestUtils.containsDependency;
import static org.cyclonedx.maven.TestUtils.getComponentNodes;
import static org.cyclonedx.maven.TestUtils.getDependencyNode;
import static org.cyclonedx.maven.TestUtils.getDependencyReferences;
import static org.cyclonedx.maven.TestUtils.getPUrlToIdentities;
import static org.cyclonedx.maven.TestUtils.readXML;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
        final Map<String, Collection<String>> purlToIdentities = getPUrlToIdentities(bom.getDocumentElement());

        final NodeList componentsList = bom.getElementsByTagName("components");
        assertEquals("Expected a single components element", 1, componentsList.getLength());
        final Element components = (Element)componentsList.item(0);

        final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
        assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
        final Element dependencies = (Element)dependenciesList.item(0);

        // BOM should not contain pkg:maven/com.example/issue284_provided_dependency@1.0.0?type=jar
        final Collection<Element> testIssue284ProvidedDependency1ComponentNodes = getComponentNodes(purlToIdentities, components, ISSUE284_PROVIDED_DEPENDENCY);
        assertNull("Unexpected provided_dependency:1.0.0 component discovered in BOM", testIssue284ProvidedDependency1ComponentNodes);

        /*
           <dependency ref="pkg:maven/com.example/issue284_dependency2@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/issue284_shared_dependency1@1.0.0?type=jar"/>
           </dependency>
        */
        final Element issue284Dependency2Node = getDependencyNode(purlToIdentities, dependencies, ISSUE284_DEPENDENCY2);
        Set<String> issue284Dependency2Dependencies = getDependencyReferences(issue284Dependency2Node);
        assertEquals("Invalid dependency count for issue284_dependency2", 1, issue284Dependency2Dependencies.size());
        containsDependency(purlToIdentities, issue284Dependency2Dependencies, ISSUE284_SHARED_DEPENDENCY1);

        /*
           <dependency ref="pkg:maven/com.example/issue284_shared_dependency1@1.0.0?type=jar">
             <dependency ref="pkg:maven/com.example/issue284_shared_dependency2@1.0.0?type=jar"/>
           </dependency>
         */
        final Element issue284SharedDependency1Node = getDependencyNode(purlToIdentities, dependencies, ISSUE284_SHARED_DEPENDENCY1);
        Set<String> issue284SharedDependency1Dependencies = getDependencyReferences(issue284SharedDependency1Node);
        assertEquals("Invalid dependency count for issue284_shared_dependency1", 1, issue284SharedDependency1Dependencies.size());
        containsDependency(purlToIdentities, issue284SharedDependency1Dependencies, ISSUE284_SHARED_DEPENDENCY2);
    }
}
