package org.cyclonedx.maven;

import java.io.File;

import org.cyclonedx.Format;
import org.cyclonedx.Version;
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
 * Generated SBOMs are available in target/test-classes/transformed-projects/schema-versions-bom-*.*
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class SchemaVersionsTest extends BaseMavenVerifier {

    public SchemaVersionsTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testSchemaVersions() throws Exception {
        for (Version version : Version.values()) {
            String formats = version.getFormats().contains(Format.JSON) ? "all" : "xml";
            generateBom(version.getVersionString(), formats);
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
                .withCliOption("-DoutputName=schema-versions-bom-" + schemaVersion)
                .withCliOption("-DoutputDirectory=..")
                .execute("verify")
                .assertErrorFreeLog()
                .assertNoLogText("[WARNING] Invalid schemaVersion") // requested version is supported as-is
                .assertNoLogText("Unknown keyword") // regression for issue #564
                .assertLogText("CycloneDX: Creating BOM version " + schemaVersion);

        final File xmlBom = new File(projDir, "../schema-versions-bom-"+ schemaVersion + ".xml");
        assertTrue("XML BOM for schema version " + schemaVersion + " not generated", xmlBom.exists());
        assertTrue("XML BOM does not declare schema version " + schemaVersion,
                fileRead(xmlBom, true).contains("http://cyclonedx.org/schema/bom/" + schemaVersion));

        if ("all".equals(outputFormat)) {
            final File jsonBom = new File(projDir, "../schema-versions-bom-"+ schemaVersion + ".json");
            assertTrue("JSON BOM for schema version " + schemaVersion + " not generated", jsonBom.exists());
            assertTrue("JSON BOM does not declare specVersion " + schemaVersion,
                    fileRead(jsonBom, true).contains("\"specVersion\" : \"" + schemaVersion + "\""));
        }
    }
}
