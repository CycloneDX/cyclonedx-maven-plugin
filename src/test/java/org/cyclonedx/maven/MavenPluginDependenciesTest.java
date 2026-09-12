package org.cyclonedx.maven;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Verifies Maven plugin dependencies are included in the generated BOM.
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class MavenPluginDependenciesTest extends BaseMavenVerifier {

    public MavenPluginDependenciesTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testMavenPluginDependencies() throws Exception {
        File projDir = resources.getBasedir("maven-plugin-dependencies");
        verifier
                .forProject(projDir)
                .withCliOption("-Dcurrent.version=" + getCurrentVersion())
                .withCliOption("-B")
                .execute("clean", "verify")
                .assertErrorFreeLog();
        File bomJsonFile = new File(projDir, "target/bom.json");
        assertBomContainsPluginDependency(
                bomJsonFile,
                "pkg:maven/org.apache.maven.plugins/maven-antrun-plugin@3.1.0?type=maven-plugin");
        assertBomContainsPluginDependency(
                bomJsonFile,
                "pkg:maven/org.apache.ant/ant@1.10.12?type=jar");
    }

    private static void assertBomContainsPluginDependency(
            File bomJsonFile,
            String purl) throws IOException {
        assertThatJson(fileRead(bomJsonFile, false))
                .inPath("$.components[*].purl")
                .isArray()
                .contains(purl);
    }

}
