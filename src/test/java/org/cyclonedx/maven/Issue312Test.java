package org.cyclonedx.maven;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.cyclonedx.maven.TestUtils.getDependencyNode;
import static org.cyclonedx.maven.TestUtils.getDependencyReferences;
import static org.cyclonedx.maven.TestUtils.readXML;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
public class Issue312Test extends BaseMavenVerifier {

    private static final String ISSUE_312_DEPENDENCY_A = "pkg:maven/com.example.issue_312/dependency_A@1.0.0?type=jar";
    private static final String ISSUE_312_DEPENDENCY_B = "pkg:maven/com.example.issue_312/dependency_B@1.0.0?type=jar";
    private static final String ISSUE_312_DEPENDENCY_C1 = "pkg:maven/com.example.issue_312/dependency_C@1.0.0?type=jar";
    private static final String ISSUE_312_DEPENDENCY_C2 = "pkg:maven/com.example.issue_312/dependency_C@2.0.0?type=jar";
    private static final String ISSUE_312_DEPENDENCY_D = "pkg:maven/com.example.issue_312/dependency_D@1.0.0?type=jar";

    public Issue312Test(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    /**
     * Validate the hierarchy when generating the build dependency graph

       com.example.issue_312:dependency_A:jar:1.0.0
       +- com.example.issue_312:dependency_D:jar:1.0.0:provided
       |  \- com.example.issue_312:dependency_C:jar:2.0.0:compile (scope not updated to compile)
       \- com.example.issue_312:dependency_B:jar:1.0.0:compile
          \- (com.example.issue_312:dependency_C:jar:1.0.0:compile - omitted for conflict with 2.0.0)

     */
    @Test
    public void testBuildDependencyTree() throws Exception {
      final Map<String, String> properties = new HashMap<>();
      properties.put("generateConsumeTimeGraph", "false");
      final File projDir = mvnBuild("issue-312", null, null, null, properties);

      final Document bom = readXML(new File(projDir, "dependency_A/target/bom.xml"));

      final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
      assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
      final Element dependencies = (Element)dependenciesList.item(0);

      final Element dependencyANode = getDependencyNode(dependencies, ISSUE_312_DEPENDENCY_A);
      final Set<String> dependencyADependencies = getDependencyReferences(dependencyANode);
      assertEquals("Invalid dependency count for dependency_A", 2, dependencyADependencies.size());
      assertTrue("dependency_A has a missing dependency for dependency_D", dependencyADependencies.contains(ISSUE_312_DEPENDENCY_D));
      assertTrue("dependency_A has a missing dependency for dependency_B", dependencyADependencies.contains(ISSUE_312_DEPENDENCY_B));

      final Element dependencyDNode = getDependencyNode(dependencies, ISSUE_312_DEPENDENCY_D);
      final Set<String> dependencyDDependencies = getDependencyReferences(dependencyDNode);
      assertEquals("Invalid dependency count for dependency_D", 1, dependencyDDependencies.size());
      assertTrue("dependency_D has a missing dependency for dependency_C@2.0.0", dependencyDDependencies.contains(ISSUE_312_DEPENDENCY_C2));

      final Element dependencyBNode = getDependencyNode(dependencies, ISSUE_312_DEPENDENCY_B);
      final Set<String> dependencyBDependencies = getDependencyReferences(dependencyBNode);
      assertEquals("Invalid dependency count for dependency_B", 1, dependencyBDependencies.size());
      assertTrue("dependency_B has a missing dependency for dependency_C@2.0.0", dependencyBDependencies.contains(ISSUE_312_DEPENDENCY_C2));
  }

    /**
     * Validate the hierarchy when generating the build dependency graph

       com.example.issue_312:dependency_A:jar:1.0.0
       \- com.example.issue_312:dependency_B:jar:1.0.0:compile
          \- com.example.issue_312:dependency_C:jar:1.0.0:compile

     */
    @Test
    public void testAsADependencyDependencyTree() throws Exception {
      final Map<String, String> properties = new HashMap<>();
      properties.put("generateConsumeTimeGraph", "true");
      final File projDir = mvnBuild("issue-312", null, null, null, properties);

      final Document bom = readXML(new File(projDir, "dependency_A/target/bom.xml"));

      final NodeList dependenciesList = bom.getElementsByTagName("dependencies");
      assertEquals("Expected a single dependencies element", 1, dependenciesList.getLength());
      final Element dependencies = (Element)dependenciesList.item(0);

      final Element dependencyANode = getDependencyNode(dependencies, ISSUE_312_DEPENDENCY_A);
      final Set<String> dependencyADependencies = getDependencyReferences(dependencyANode);
      assertEquals("Invalid dependency count for dependency_A", 1, dependencyADependencies.size());
      assertTrue("dependency_A has a missing dependency for dependency_B", dependencyADependencies.contains(ISSUE_312_DEPENDENCY_B));

      final Element dependencyBNode = getDependencyNode(dependencies, ISSUE_312_DEPENDENCY_B);
      final Set<String> dependencyBDependencies = getDependencyReferences(dependencyBNode);
      assertEquals("Invalid dependency count for dependency_B", 1, dependencyBDependencies.size());
      assertTrue("dependency_B has a missing dependency for dependency_C@1.0.0", dependencyBDependencies.contains(ISSUE_312_DEPENDENCY_C1));
  }
}
