package org.cyclonedx.maven;

import static io.takari.maven.testing.TestResources.assertFilesPresent;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

/**
 * test for https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/64
 * include test scoped dependencies
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class Issue64Test extends BaseMavenVerifier {

    public Issue64Test(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testPluginWithActiviti() throws Exception {
        File projDir = resources.getBasedir("issue-64");

        verifier
                .forProject(projDir)
                .withCliOption("-Dcurrent.version=" + getCurrentVersion()) // inject cyclonedx-maven-plugin version
                .withCliOption("-X") // debug
                .withCliOption("-B")
                .execute("clean", "verify")
                .assertErrorFreeLog();

        assertFileContains(projDir, "target/bom.xml", "junit");
    }

    private static void assertFileContains(File basedir, String expectedFile, String expectedContent) throws IOException {
        assertFilesPresent(basedir, expectedFile);
        String bomContents = fileRead(new File(basedir, expectedFile), true);
        assertTrue(String.format("%s contains %s", expectedFile, expectedContent), bomContents.contains(expectedContent));
    }
}
