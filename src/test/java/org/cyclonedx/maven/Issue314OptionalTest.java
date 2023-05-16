package org.cyclonedx.maven;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.cyclonedx.maven.TestUtils.getComponentNode;
import static org.cyclonedx.maven.TestUtils.getElement;
import static org.cyclonedx.maven.TestUtils.readXML;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.cyclonedx.model.Component;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

/**
 * Test optional detection as Maven dependency optional vs bytecode analysis of unused.
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class Issue314OptionalTest extends BaseMavenVerifier {

    private static final String ISSUE_314_DEPENDENCY_B = "pkg:maven/com.example.issue_314/dependency_B@1.0.0?type=jar";
    private static final String ISSUE_314_DEPENDENCY_C = "pkg:maven/com.example.issue_314/dependency_C@1.0.0?type=jar";
    private static final String ISSUE_314_DEPENDENCY_D = "pkg:maven/com.example.issue_314/dependency_D@1.0.0?type=jar";

    public Issue314OptionalTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    /**
     * Validate the bytecode analysis components.
     * - No component should be marked as optional
     */
    @Test
    public void testBytecodeDependencyTree() throws Exception {
      final Map<String, String> properties = new HashMap<>();
      properties.put("detectUnusedForOptionalScope", "true");
      final File projDir = mvnBuild("issue-314", null, null, null, properties);

      final String requiredName = Component.Scope.REQUIRED.getScopeName();

      final Document bom = readXML(new File(projDir, "dependency_A/target/bom.xml"));

      final NodeList componentsList = bom.getElementsByTagName("components");
      assertEquals("Expected a single components element", 1, componentsList.getLength());
      final Element components = (Element)componentsList.item(0);

      final Element componentBNode = getComponentNode(components, ISSUE_314_DEPENDENCY_B);
      final Element componentBScope = getElement(componentBNode, "scope");
      if (componentBScope != null) {
        assertEquals("dependency_B scope should be " + requiredName, requiredName, componentBScope.getTextContent());
      }

      final Element componentCNode = getComponentNode(components, ISSUE_314_DEPENDENCY_C);
      final Element componentCScope = getElement(componentCNode, "scope");
      if (componentCScope != null) {
        assertEquals("dependency_C scope should be " + requiredName, requiredName, componentCScope.getTextContent());
      }

      final Element componentDNode = getComponentNode(components, ISSUE_314_DEPENDENCY_D);
      final Element componentDScope = getElement(componentDNode, "scope");
      if (componentDScope != null) {
        assertEquals("dependency_D scope should be " + requiredName, requiredName, componentDScope.getTextContent());
      }
  }

    /**
     * Validate the maven optional components.
     * - com.example.issue_314:dependency_C:1.0.0 and com.example.issue_314:dependency_D:1.0.0 *should* be marked as optional
     * because dependency_A declares dependency_C as optional, which depends on dependency_D
     */
    @Test
    public void testMavenOptionalDependencyTree() throws Exception {
      final Map<String, String> properties = new HashMap<>();
      properties.put("detectUnusedForOptionalScope", "false");
      final File projDir = mvnBuild("issue-314", null, null, null, properties);

      final String requiredName = Component.Scope.REQUIRED.getScopeName();
      final String optionalName = Component.Scope.OPTIONAL.getScopeName();

      final Document bom = readXML(new File(projDir, "dependency_A/target/bom.xml"));

      final NodeList componentsList = bom.getElementsByTagName("components");
      assertEquals("Expected a single components element", 1, componentsList.getLength());
      final Element components = (Element)componentsList.item(0);

      final Element componentBNode = getComponentNode(components, ISSUE_314_DEPENDENCY_B);
      final Element componentBScope = getElement(componentBNode, "scope");
      if (componentBScope != null) {
        assertEquals("dependency_B scope should be " + requiredName, requiredName, componentBScope.getTextContent());
      }

      final Element componentCNode = getComponentNode(components, ISSUE_314_DEPENDENCY_C);
      final Element componentCScope = getElement(componentCNode, "scope");
      assertNotNull("dependency_C is missing its scope", componentCScope);
      assertEquals("dependency_C scope should be " + optionalName, optionalName, componentCScope.getTextContent());

      final Element componentDNode = getComponentNode(components, ISSUE_314_DEPENDENCY_D);
      final Element componentDScope = getElement(componentDNode, "scope");
      assertNotNull("dependency_D is missing its scope", componentDScope);
      assertEquals("dependency_D scope should be " + optionalName, optionalName, componentDScope.getTextContent());
  }
}
