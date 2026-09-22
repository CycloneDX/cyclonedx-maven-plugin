package org.cyclonedx.maven;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.cyclonedx.maven.TestUtils.readXML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test for <a href="https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/632">issue #632</a>:
 * projectType configured in pluginManagement must be applied to components in an aggregate BOM.
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class Issue632Test extends BaseMavenVerifier {

    public Issue632Test(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testProjectTypeFromPluginManagement() throws Exception {
        File projDir = cleanAndBuild("issue-632", null);

        assertComponentTypeInXml(new File(projDir, "target/bom.xml"), "issue-632", "application");
        assertComponentTypeInXml(new File(projDir, "target/bom.xml"), "issue-632-app", "application");
        assertJsonValue(new File(projDir, "target/bom.json"), "$.metadata.component.type", "application");
        assertComponentTypeInJson(new File(projDir, "target/bom.json"), "issue-632-app", "application");
    }

    private static void assertComponentTypeInXml(File bomFile, String componentName, String expectedType)
            throws Exception {
        Document bom = readXML(bomFile);
        NodeList components = bom.getElementsByTagName("component");
        for (int i = 0; i < components.getLength(); i++) {
            Element component = (Element) components.item(i);
            Element name = (Element) component.getElementsByTagName("name").item(0);
            if (name != null && componentName.equals(name.getTextContent())) {
                assertEquals(expectedType, component.getAttribute("type"));
                return;
            }
        }
        throw new AssertionError("Missing component named " + componentName);
    }

    private static void assertComponentTypeInJson(File bomFile, String componentName, String expectedType)
            throws IOException {
        assertTrue("BOM JSON should exist", bomFile.isFile());
        String bomJson = new String(Files.readAllBytes(bomFile.toPath()), StandardCharsets.UTF_8);
        assertThatJson(bomJson)
                .inPath("$.components[?(@.name == '" + componentName + "')].type")
                .isArray()
                .containsExactly(expectedType);
    }

    private static void assertJsonValue(File bomFile, String jsonPath, String expectedValue)
            throws IOException {
        assertTrue("BOM JSON should exist", bomFile.isFile());
        String bomJson = new String(Files.readAllBytes(bomFile.toPath()), StandardCharsets.UTF_8);
        assertThatJson(bomJson).inPath(jsonPath).isEqualTo(expectedValue);
    }
}
