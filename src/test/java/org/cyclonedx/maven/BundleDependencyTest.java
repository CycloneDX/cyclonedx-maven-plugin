package org.cyclonedx.maven;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

/**
 * test for https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/272
 * dependency has a bundle packaging which causes Maven's ProjectBuildingException
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class BundleDependencyTest extends BaseMavenVerifier {

    public BundleDependencyTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testBundleDependencyDebug() throws Exception {
        File projDir = resources.getBasedir("bundle");

        verifier
                .forProject(projDir)
                .withCliOption("-Dcurrent.version=" + getCurrentVersion()) // inject cyclonedx-maven-plugin version
                .withCliOption("-B")
                .withCliOption("-X") // debug, will print the full stacktrace with error message if there is any model building issue
                .execute("clean", "verify")
                .assertErrorFreeLog();

        String bomContents = fileRead(new File(projDir, "target/bom.json"), true);
        assertTrue(bomContents.contains("\"description\" : \"snappy-java: A fast compression/decompression library\""));
    }
}
