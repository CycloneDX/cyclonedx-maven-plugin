package org.cyclonedx.maven;

import org.junit.runner.RunWith;


import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.cyclonedx.maven.TestUtils.readXML;


import java.io.File;
import java.io.IOException;



import org.junit.Test;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class Issue521Test extends BaseMavenVerifier {
    public Issue521Test(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testBomJsonContent() throws Exception {
        File projDir = cleanAndBuild("issue-521", null);
        checkBomXml(projDir, "target/bom.xml", "issue-521-app", "application");
        checkBomJson(projDir, "target/bom.json", "issue-521-app", "application");
    }

    private static void assertFileExists(File file) {
        assertTrue(file.exists(), String.format("File %s should exist", file));
    }

    private void checkBomJson(File basedir, String filePath, String expectedName, String expectedType) throws IOException {
        File bomJsonFile = new File(basedir, filePath);
        assertFileExists(bomJsonFile);

        // Leggi il contenuto del file bom.json e verifica la presenza dei campi desiderati
        String bomContents = fileRead(bomJsonFile, true);
        assertTrue(bomContents.contains("\"name\" : \"" + expectedName + "\""), 
            String.format("bom.json should contain a module with name '%s'", expectedName));
        assertTrue(bomContents.contains("\"type\" : \"" + expectedType + "\""), 
            String.format("bom.json should contain a module with type '%s'", expectedType));
    }

    private void checkBomXml(File basedir, String filePath, String expectedName, String expectedType) throws Exception {
        File bomXmlFile = new File(basedir, filePath);
        assertFileExists(bomXmlFile);

        final Document bom = readXML(bomXmlFile);

        final NodeList componentsList = bom.getElementsByTagName("component");
        assertTrue(componentsList.getLength() > 0, "Expected at least one component element");

        boolean found=false;
        for (int i = 0; i < componentsList.getLength(); i++) {
            Element component = (Element) componentsList.item(i);
            String name = component.getElementsByTagName("name").item(0).getTextContent();
            String type = component.getAttribute("type");

            if (expectedName.equals(name) && expectedType.equals(type)) {
                found = true;
                break;
            }
        }

        assertTrue(found, "Expected to find a component with name 'module1' and type 'application'");
    }
}
