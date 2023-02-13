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
 * test for https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/272
 * dependency has a bundle packaging which causes Maven's ProjectBuildingException
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class BundleDependencyTest extends BaseMavenVerifier {

    private final static String WARN = "[WARNING] Unable to create Maven project for org.xerial.snappy:snappy-java:jar:1.1.8.4 from repository.";

    public BundleDependencyTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testBundleDependency() throws Exception {
        File projDir = resources.getBasedir("bundle");

        verifier
                .forProject(projDir)
                .withCliOption("-Dcurrent.version=" + getCurrentVersion()) // inject cyclonedx-maven-plugin version
                .withCliOption("-B")
                .execute("clean", "verify")
                .assertErrorFreeLog()
                .assertLogText(WARN);
        // data expected from the MavenProject building is missing (was present in cyclonedx-maven-plugin 2.7.3, before https://github.com/CycloneDX/cyclonedx-maven-plugin/commit/374b3c53cbd28ffa7941d0aa7741f5b2405d83e4):
        /*
      "publisher" : "xerial.org",
      "description" : "snappy-java: A fast compression/decompression library",
      "licenses" : [
        {
          "license" : {
            "id" : "Apache-2.0",
            "url" : "https://www.apache.org/licenses/LICENSE-2.0"
          }
        }
      ],
      "externalReferences" : [
        {
          "type" : "website",
          "url" : "https://github.com/xerial/snappy-java"
        },
        {
          "type" : "vcs",
          "url" : "https://github.com/xerial/snappy-java"
        }
      ],
        */
    }

    @Test
    public void testBundleDependencyDebug() throws Exception {
        File projDir = resources.getBasedir("bundle");

        verifier
                .forProject(projDir)
                .withCliOption("-Dcurrent.version=" + getCurrentVersion()) // inject cyclonedx-maven-plugin version
                .withCliOption("-B")
                .withCliOption("-X") // debug, will print the full stacktrace with error message
                .execute("clean", "verify")
                .assertLogText(WARN)
                .assertLogText("[ERROR] Unknown packaging: bundle @ line 6, column 16");
    }
}
