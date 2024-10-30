package org.cyclonedx.maven;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import static io.takari.maven.testing.TestResources.assertFilesPresent;
import static org.junit.Assert.assertFalse;

/**
 * Test for <a href="https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/382">issue #382</a>:
 * Plugin does not gracefully handle present, but empty license data
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class Issue382Test extends BaseMavenVerifier {

    public Issue382Test(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void test() throws Exception {
        File projDir = resources.getBasedir("issue-382");

        verifier
                .forProject(projDir)
                .withCliOption("-Dcurrent.version=" + getCurrentVersion()) // inject cyclonedx-maven-plugin version
                .withCliOption("-X") // debug
                .withCliOption("-B")
                .execute("clean", "verify")
                .assertErrorFreeLog();

        assertFileNotContains(projDir, "target/bom.xml", "The BOM does not conform to the CycloneDX BOM standard");
    }

    private static void assertFileNotContains(File basedir, String expectedFile, String expectedContent) throws IOException {
        assertFilesPresent(basedir, expectedFile);
        String bomContents = fileRead(new File(basedir, expectedFile), true);
        assertFalse(String.format("%s contains %s", expectedFile, expectedContent), bomContents.contains(expectedContent));
    }
}
