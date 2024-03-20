package org.cyclonedx.maven;

import java.io.File;

import org.cyclonedx.CycloneDxSchema;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

/**
 * test for <a href="https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/280">issue #280</a>:
 * how to switch verbosity off by default, but let the user activate with CLI parameter
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class VerboseTest extends BaseMavenVerifier {

    public VerboseTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testVerboseOffByDefault() throws Exception {
        File projDir = resources.getBasedir("verbose");

        verifier
                .forProject(projDir)
                .withCliOption("-Dcurrent.version=" + getCurrentVersion()) // inject cyclonedx-maven-plugin version
                .withCliOption("-B")
                .execute("verify")
                .assertErrorFreeLog()
                .assertNoLogText("[INFO] CycloneDX: Parameters"); // check no verbose output by default given property defined in pom.xml
    }

    @Test
    public void testVerboseWithCli() throws Exception {
        File projDir = resources.getBasedir("verbose");

        verifier
                .forProject(projDir)
                .withCliOption("-Dcurrent.version=" + getCurrentVersion()) // inject cyclonedx-maven-plugin version
                .withCliOption("-B")
                .withCliOption("-Dcyclonedx.verbose") // activate verbose with CLI parameter
                .execute("verify")
                .assertErrorFreeLog()
                .assertLogText("[INFO] CycloneDX: Parameters"); // check goal verbose output
    }

    @Test
    public void testUnsupportedSchemaVersionCli() throws Exception {
        File projDir = resources.getBasedir("verbose");

        verifier
                .forProject(projDir)
                .withCliOption("-Dcurrent.version=" + getCurrentVersion()) // inject cyclonedx-maven-plugin version
                .withCliOption("-B")
                .withCliOption("-DschemaVersion=1.5.1")
                .execute("verify")
                .assertErrorFreeLog()
                .assertLogText("[WARNING] Invalid schemaVersion configured '1.5.1', using " + CycloneDxSchema.VERSION_LATEST.getVersionString()) // check warning on invalid schema version
                .assertLogText("[INFO] CycloneDX: Creating BOM version " + CycloneDxSchema.VERSION_LATEST.getVersionString() + " with 0 component(s)"); // and display effective schema version
    }
}
