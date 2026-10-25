package org.cyclonedx.maven;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Verifies that only the configured hash algorithms are calculated.
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class HashAlgorithmsTest extends BaseMavenVerifier {

    private static final String HASH_ALGORITHMS_PATH = "$.components[*].hashes[*].alg";

    public HashAlgorithmsTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testDefaultCalculatesAllAlgorithms() throws Exception {
        File projDir = build(new String[0]);
        assertThatJson(readBom(projDir))
                .inPath(HASH_ALGORITHMS_PATH)
                .isArray()
                .contains("MD5", "SHA-1", "SHA-256", "SHA-512", "SHA3-512");
    }

    @Test
    public void testSelectedAlgorithms() throws Exception {
        File projDir = build(new String[]{"-DhashAlgorithms=SHA-256,sha-512"});
        assertThatJson(readBom(projDir))
                .inPath(HASH_ALGORITHMS_PATH)
                .isArray()
                .isNotEmpty()
                .containsOnly("SHA-256", "SHA-512");
    }

    @Test
    public void testUnknownAlgorithm() throws Exception {
        File projDir = resources.getBasedir("issue-420");
        verifier
                .forProject(projDir)
                .withCliOption("-Dcurrent.version=" + getCurrentVersion())
                .withCliOption("-B")
                .withCliOption("-Dmaven.test.skip")
                .withCliOption("-DhashAlgorithms=SHA-999")
                .execute("clean", "verify")
                .assertLogText("Unknown hash algorithm 'SHA-999'");
    }

    private File build(String[] cliOptions) throws Exception {
        File projDir = resources.getBasedir("issue-420");
        verifier
                .forProject(projDir)
                .withCliOption("-Dcurrent.version=" + getCurrentVersion())
                .withCliOption("-B")
                .withCliOption("-Dmaven.test.skip")
                .withCliOptions(cliOptions)
                .execute("clean", "verify")
                .assertErrorFreeLog();
        return projDir;
    }

    private static String readBom(File projDir) throws Exception {
        return new String(Files.readAllBytes(new File(projDir, "target/bom.json").toPath()), StandardCharsets.UTF_8);
    }
}
