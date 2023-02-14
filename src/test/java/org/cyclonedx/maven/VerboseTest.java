package org.cyclonedx.maven;

import java.io.File;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

/**
 * test for https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/280
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
}
