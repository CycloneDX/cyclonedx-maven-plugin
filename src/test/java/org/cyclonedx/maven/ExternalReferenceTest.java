package org.cyclonedx.maven;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

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

        // Verify parent metadata
        assertExternalReferences(
                new File(projDir, "target/bom.json"),
                "$.metadata.component.externalReferences[?(@.type=='chat')].url",
                Collections.singleton("https://acme.com/parent"));

        // Verify parent components
        assertExternalReferences(
                new File(projDir, "target/bom.json"),
                "$.components[?(@.name=='child')].externalReferences[?(@.type=='chat')].url",
                Arrays.asList("https://acme.com/parent", "https://acme.com/child"));

        // Verify child metadata
        assertExternalReferences(
                new File(projDir, "child/target/bom.json"),
                "$.metadata.component.externalReferences[?(@.type=='chat')].url",
                Arrays.asList("https://acme.com/parent", "https://acme.com/child"));

    }

    private static void assertExternalReferences(File bomFile, String jsonPath, Iterable<Object> expectedValues) throws IOException {
        byte[] bomJsonBytes = Files.readAllBytes(bomFile.toPath());
        String bomJson = new String(bomJsonBytes, StandardCharsets.UTF_8);
        assertThatJson(bomJson)
                .inPath(jsonPath)
                .isArray()
                .containsOnlyOnceElementsOf(expectedValues);
    }

}
