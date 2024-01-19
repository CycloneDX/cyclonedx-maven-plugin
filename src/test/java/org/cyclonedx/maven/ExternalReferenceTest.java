package org.cyclonedx.maven;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Verifies external references are populated as expected.
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class ExternalReferenceTest extends BaseMavenVerifier {

    public ExternalReferenceTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testAddedExternalReferences() throws Exception {

        // Create the verifier
        File projDir = resources.getBasedir("external-reference");
        verifier
                .forProject(projDir)
                .withCliOption("-Dcyclonedx-maven-plugin.version=" + getCurrentVersion())
                .withCliOption("-X")
                .withCliOption("-B")
                .execute("clean", "verify")
                .assertErrorFreeLog();

        // Verify parent & child external references
        verifyParentExternalReferences(projDir);
        verifyChildExternalReferences(projDir);

    }

    private static void verifyParentExternalReferences(File projDir) {
        File bomJsonFile = new File(projDir, "target/bom.json");
        assertExternalReferences(bomJsonFile, "chat", "url", singleton("https://acme.com/parent"));
        assertExternalReferences(bomJsonFile, "website", "url", singleton("https://cyclonedx.org/acme"));
        assertExternalReferences(bomJsonFile, "vcs", "url", singleton("https://github.com/CycloneDX/cyclonedx-maven-plugin.git"));
        verifyCommonExternalReferences(bomJsonFile);
    }

    private static void verifyChildExternalReferences(File projDir) {
        File bomJsonFile = new File(projDir, "child/target/bom.json");
        assertExternalReferences(bomJsonFile, "chat", "url", asList("https://acme.com/parent", "https://acme.com/child"));
        assertExternalReferences(bomJsonFile, "website", "url", singleton("https://cyclonedx.org/acme/child"));
        assertExternalReferences(bomJsonFile, "vcs", "url", singleton("https://github.com/CycloneDX/cyclonedx-maven-plugin.git/child"));
        verifyCommonExternalReferences(bomJsonFile);
    }

    private static void verifyCommonExternalReferences(File bomJsonFile) {
        assertExternalReferences(bomJsonFile, "chat", "comment", singleton("optional comment"));
        assertExternalReferences(bomJsonFile, "release-notes", "url", singleton("https://github.com/CycloneDX/cyclonedx-maven-plugin/releases"));
        assertExternalReferences(bomJsonFile, "build-system", "url", singleton("https://github.com/CycloneDX/cyclonedx-maven-plugin/actions"));
        assertExternalReferences(bomJsonFile, "distribution", "url", singleton("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"));
        assertExternalReferences(bomJsonFile, "issue-tracker", "url", singleton("https://github.com/CycloneDX/cyclonedx-maven-plugin/issues"));
        assertExternalReferences(bomJsonFile, "mailing-list", "url", singleton("https://dev.ml.cyclonedx.org/archive"));
    }

    private static void assertExternalReferences(File bomJsonFile, String type, String key, Iterable<?> expectedValues) {
        assertExternalReferences(
                bomJsonFile,
                "$.metadata.component.externalReferences[?(@.type=='" + type + "')]." + key,
                expectedValues);
    }

    private static void assertExternalReferences(File bomFile, String jsonPath, Iterable<?> expectedValues) {
        byte[] bomJsonBytes;
        try {
            bomJsonBytes = Files.readAllBytes(bomFile.toPath());
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        String bomJson = new String(bomJsonBytes, StandardCharsets.UTF_8);
        assertThatJson(bomJson)
                .inPath(jsonPath)
                .isArray()
                .containsOnlyOnceElementsOf(expectedValues);
    }

}
