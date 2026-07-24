package org.cyclonedx.maven;

import java.io.File;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

import static org.junit.Assert.assertTrue;

/**
 * Verifies that a valid BOM can be generated for every supported CycloneDX schema version,
 * in every output format supported by that schema version, and that BOM validation does not
 * emit "Unknown keyword" warnings
 * (regression for <a href="https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/564">issue #564</a>).
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class SchemaVersionsTest extends BaseMavenVerifier {

    private static final String[] XML_ONLY_VERSIONS = {"1.0", "1.1"};
    private static final String[] XML_AND_JSON_VERSIONS = {"1.2", "1.3", "1.4", "1.5", "1.6", "1.7"};

    public SchemaVersionsTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testXmlOnlySchemaVersions() throws Exception {
        for (String version : XML_ONLY_VERSIONS) {
            // JSON was introduced with CycloneDX 1.2: only XML is supported for older schema versions
            generateBom(version, "xml");
        }
    }

    @Test
    public void testXmlAndJsonSchemaVersions() throws Exception {
        for (String version : XML_AND_JSON_VERSIONS) {
            generateBom(version, "all");
        }
    }

    private void generateBom(String schemaVersion, String outputFormat) throws Exception {
        File projDir = resources.getBasedir("schema-versions");

        verifier
                .forProject(projDir)
                .withCliOption("-Dcurrent.version=" + getCurrentVersion()) // inject cyclonedx-maven-plugin version
                .withCliOption("-B")
                .withCliOption("-DschemaVersion=" + schemaVersion)
                .withCliOption("-DoutputFormat=" + outputFormat)
                .execute("verify")
                .assertErrorFreeLog()
                .assertNoLogText("[WARNING] Invalid schemaVersion") // requested version is supported as-is
                .assertNoLogText("Unknown keyword") // regression for issue #564
                .assertLogText("CycloneDX: Creating BOM version " + schemaVersion);

        final File xmlBom = new File(projDir, "target/bom.xml");
        assertTrue("XML BOM for schema version " + schemaVersion + " not generated", xmlBom.exists());
        assertTrue("XML BOM does not declare schema version " + schemaVersion,
                fileRead(xmlBom, true).contains("http://cyclonedx.org/schema/bom/" + schemaVersion));

        if ("all".equals(outputFormat)) {
            final File jsonBom = new File(projDir, "target/bom.json");
            assertTrue("JSON BOM for schema version " + schemaVersion + " not generated", jsonBom.exists());
            assertTrue("JSON BOM does not declare specVersion " + schemaVersion,
                    fileRead(jsonBom, true).contains("\"specVersion\" : \"" + schemaVersion + "\""));
        }
    }
}
