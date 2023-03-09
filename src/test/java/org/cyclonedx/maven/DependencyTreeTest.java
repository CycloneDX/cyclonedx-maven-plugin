package org.cyclonedx.maven;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.cyclonedx.maven.TestUtils.containsDependency;
import static org.cyclonedx.maven.TestUtils.getComponentNode;
import static org.cyclonedx.maven.TestUtils.getComponentNodes;
import static org.cyclonedx.maven.TestUtils.getDependencyNode;
import static org.cyclonedx.maven.TestUtils.getDependencyNodeByIdentity;
import static org.cyclonedx.maven.TestUtils.getDependencyReferences;
import static org.cyclonedx.maven.TestUtils.getPUrlToIdentities;
import static org.cyclonedx.maven.TestUtils.readXML;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

/**
 * Fix BOM handling of conflicting dependency tree graphs
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class DependencyTreeTest extends BaseMavenVerifier {

    private static final String EXCLUSION_DEPENDENCY_A = "pkg:maven/com.example.dependency_trees.exclusion/dependency_A@1.0.0?type=jar";
    private static final String EXCLUSION_DEPENDENCY_B = "pkg:maven/com.example.dependency_trees.exclusion/dependency_B@1.0.0?type=jar";
    private static final String EXCLUSION_DEPENDENCY_C = "pkg:maven/com.example.dependency_trees.exclusion/dependency_C@1.0.0?type=jar";
    private static final String EXCLUSION_DEPENDENCY_D = "pkg:maven/com.example.dependency_trees.exclusion/dependency_D@1.0.0?type=jar";
    private static final String EXCLUSION_DEPENDENCY_E = "pkg:maven/com.example.dependency_trees.exclusion/dependency_E@1.0.0?type=jar";
    private static final String EXCLUSION_DEPENDENCY_F = "pkg:maven/com.example.dependency_trees.exclusion/dependency_F@1.0.0?type=jar";

    private static final String MANAGED_DEPENDENCY_A = "pkg:maven/com.example.dependency_trees.managed/dependency_A@1.0.0?type=jar";
    private static final String MANAGED_DEPENDENCY_B = "pkg:maven/com.example.dependency_trees.managed/dependency_B@1.0.0?type=jar";
    private static final String MANAGED_DEPENDENCY_C1 = "pkg:maven/com.example.dependency_trees.managed/dependency_C@1.0.0?type=jar";
    private static final String MANAGED_DEPENDENCY_C2 = "pkg:maven/com.example.dependency_trees.managed/dependency_C@2.0.0?type=jar";
    private static final String MANAGED_DEPENDENCY_D = "pkg:maven/com.example.dependency_trees.managed/dependency_D@1.0.0?type=jar";
    private static final String MANAGED_DEPENDENCY_E = "pkg:maven/com.example.dependency_trees.managed/dependency_E@1.0.0?type=jar";

    public DependencyTreeTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testDependencyTrees() throws Exception {
      final File projDir = cleanAndBuild("dependency_trees", null);
      checkExclusion(projDir);
      checkManaged(projDir);
    }

    /**
     * Test for alternative dependency trees generated through exclusions in the hierarchy
     */
    public void checkExclusion(final File projDir) throws Exception {
      final Document bom = readXML(new File(projDir, "target/bom.xml"));
      final Map<String, Collection<String>> purlToIdentities = getPUrlToIdentities(bom.getDocumentElement());

      final NodeList componentsList = bom.getElementsByTagName("components");
      assertEquals("Expected a single components element", 1, componentsList.getLength());
      final Element components = (Element)componentsList.item(0);

      final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
      assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
      final Element dependencies = (Element)dependenciesList.item(0);

      /*
        We create an aggregated BOM containing two dependency hierarchies which deviate at dependency_B.

        The first graph is rooted at dependency_A and includes dependency_E as below

        com.example.dependency_trees.exclusion:dependency_A:jar:1.0.0
        \- com.example.dependency_trees.exclusion:dependency_B:jar:1.0.0:compile
           +- com.example.dependency_trees.exclusion:dependency_C:jar:1.0.0:compile
           |  \- com.example.dependency_trees.exclusion:dependency_D:jar:1.0.0:compile
           \- com.example.dependency_trees.exclusion:dependency_E:jar:1.0.0:compile

        The second graph is rooted at dependency_F and excludes dependency_E as below

        com.example.dependency_trees.exclusion:dependency_F:jar:1.0.0
        \- com.example.dependency_trees.exclusion:dependency_B:jar:1.0.0:compile
           \- com.example.dependency_trees.exclusion:dependency_C:jar:1.0.0:compile
              \- com.example.dependency_trees.exclusion:dependency_D:jar:1.0.0:compile
       */

      // Ensure there are two components for Dependency_B
      final Collection<Element> dependencyBNodes = getComponentNodes(purlToIdentities, components, EXCLUSION_DEPENDENCY_B);
      assertNotNull("Could not find components for dependency_B", dependencyBNodes);
      assertEquals("Incorrect component count for dependency_B", 2, dependencyBNodes.size());
      // Ensure there is a single component for Dependency_C
      getComponentNode(purlToIdentities, components, EXCLUSION_DEPENDENCY_C);
      // Ensure there is a single component for Dependency_D
      getComponentNode(purlToIdentities, components, EXCLUSION_DEPENDENCY_D);

      // Check the first graph

      final Element firstDepA = getDependencyNode(purlToIdentities, dependencies, EXCLUSION_DEPENDENCY_A);
      final Element firstDepADepB = getDependencyNode(purlToIdentities, firstDepA, EXCLUSION_DEPENDENCY_B);

      final String firstDepBPUrl = firstDepADepB.getAttribute("ref");
      final Element firstDepB = getDependencyNodeByIdentity(dependencies, firstDepBPUrl);
      final Set<String> firstDepBDependencies = getDependencyReferences(firstDepB);
      assertEquals("Invalid dependency count for dependency_B", 2, firstDepBDependencies.size());
      containsDependency(purlToIdentities, firstDepBDependencies, EXCLUSION_DEPENDENCY_C);
      containsDependency(purlToIdentities, firstDepBDependencies, EXCLUSION_DEPENDENCY_E);

      // Check the second graph
      final Element secondDepF = getDependencyNode(purlToIdentities, dependencies, EXCLUSION_DEPENDENCY_F);
      final Element secondDepFDepB = getDependencyNode(purlToIdentities, secondDepF, EXCLUSION_DEPENDENCY_B);

      final String secondDepBPUrl = secondDepFDepB.getAttribute("ref");
      final Element secondDepB = getDependencyNodeByIdentity(dependencies, secondDepBPUrl);
      final Set<String> secondDepBDependencies = getDependencyReferences(secondDepB);
      assertEquals("Invalid dependency count for dependency_B", 1, secondDepBDependencies.size());
      containsDependency(purlToIdentities, secondDepBDependencies, EXCLUSION_DEPENDENCY_C);

      // Assert dependencies have different purls
      assertNotEquals("Dependency B purls should be distinct", firstDepBPUrl, secondDepBPUrl);
  }

    /**
     * Test for alternative dependency trees generated through managed dependencies in the hierarchy
     */
    public void checkManaged(final File projDir) throws Exception {
      final Document bom = readXML(new File(projDir, "target/bom.xml"));
      final Map<String, Collection<String>> purlToIdentities = getPUrlToIdentities(bom.getDocumentElement());

      final NodeList componentsList = bom.getElementsByTagName("components");
      assertEquals("Expected a single components element", 1, componentsList.getLength());
      final Element components = (Element)componentsList.item(0);

      final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
      assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
      final Element dependencies = (Element)dependenciesList.item(0);

      /*
        We create an aggregated BOM containing two dependency hierarchies which deviate at dependency_B.

        The first graph is rooted at dependency_A and includes dependency_C with version 1.0.0 as below

        com.example.dependency_trees.managed:dependency_A:jar:1.0.0
        \- com.example.dependency_trees.managed:dependency_B:jar:1.0.0:compile
           \- com.example.dependency_trees.managed:dependency_C:jar:1.0.0:compile
              \- com.example.dependency_trees.managed:dependency_D:jar:1.0.0:compile

        The second graph is rooted at dependency_E and includes dependency_C with version 2.0.0 as below

        com.example.dependency_trees.managed:dependency_E:jar:1.0.0
        \- com.example.dependency_trees.managed:dependency_B:jar:1.0.0:compile
           \- com.example.dependency_trees.managed:dependency_C:jar:2.0.0:compile
              \- com.example.dependency_trees.managed:dependency_D:jar:1.0.0:compile
       */

      // Ensure there are two components for Dependency_B
      final Collection<Element> dependencyBNodes = getComponentNodes(purlToIdentities, components, MANAGED_DEPENDENCY_B);
      assertNotNull("Could not find components for dependency_B", dependencyBNodes);
      assertEquals("Incorrect component count for dependency_B", 2, dependencyBNodes.size());
      // Ensure there are two components for Dependency_C
      getComponentNode(purlToIdentities, components, MANAGED_DEPENDENCY_C1);
      getComponentNode(purlToIdentities, components, MANAGED_DEPENDENCY_C2);
      // Ensure there is a single component for Dependency_D
      getComponentNode(purlToIdentities, components, MANAGED_DEPENDENCY_D);

      // Check the first graph

      final Element firstDepA = getDependencyNode(purlToIdentities, dependencies, MANAGED_DEPENDENCY_A);
      final Element firstDepADepB = getDependencyNode(purlToIdentities, firstDepA, MANAGED_DEPENDENCY_B);

      final String firstDepBPUrl = firstDepADepB.getAttribute("ref");
      final Element firstDepB = getDependencyNodeByIdentity(dependencies, firstDepBPUrl);
      final Set<String> firstDepBDependencies = getDependencyReferences(firstDepB);
      assertEquals("Invalid dependency count for dependency_B", 1, firstDepBDependencies.size());
      containsDependency(purlToIdentities, firstDepBDependencies, MANAGED_DEPENDENCY_C1);

      // Check the second graph
      final Element secondDepE = getDependencyNode(purlToIdentities, dependencies, MANAGED_DEPENDENCY_E);
      final Element secondDepEDepB = getDependencyNode(purlToIdentities, secondDepE, MANAGED_DEPENDENCY_B);

      final String secondDepBPUrl = secondDepEDepB.getAttribute("ref");
      final Element secondDepB = getDependencyNodeByIdentity(dependencies, secondDepBPUrl);
      final Set<String> secondDepBDependencies = getDependencyReferences(secondDepB);
      assertEquals("Invalid dependency count for dependency_B", 1, secondDepBDependencies.size());
      containsDependency(purlToIdentities, secondDepBDependencies, MANAGED_DEPENDENCY_C2);

      // Assert dependencies have different purls
      assertNotEquals("Dependency B purls should be distinct", firstDepBPUrl, secondDepBPUrl);
  }
}
