package org.cyclonedx.maven;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Verifies that serial numbers are generated deterministically.
 *
 * @see <a href="https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/420">Issue 420</a>
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class Issue420Test extends BaseMavenVerifier {

    public Issue420Test(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testDefaults() throws Exception {
        test(new String[0], "urn:uuid:af111a48-2091-3e2e-ad2e-60b1975b651d");
    }

    @Test
    public void testDefaultsWhenSerialNumberIsDisabled() throws Exception {
        test(new String[]{"-DincludeBomSerialNumber=false"}, null);
    }

    @Test
    public void testWhenOutputTimestampIsSet() throws Exception {
        test(new String[]{"-Dproject.build.outputTimestamp=2023-11-08T00:00:00Z"}, "urn:uuid:3e383c4c-ef61-3eba-8214-3ecd46c4bbee");
    }

    @Test
    public void testWhenOutputTimestampIsSetAndSerialNumberIsDisabled() throws Exception {
        test(new String[]{"-Dproject.build.outputTimestamp=2023-11-08T00:00:00Z", "-DincludeBomSerialNumber=false"}, null);
    }

    private void test(String[] cliOptions, @Nullable String expectedSerialNumber) throws Exception {
        File projDir = resources.getBasedir("issue-420");
        verifier
                .forProject(projDir)
                .withCliOption("-Dcurrent.version=" + getCurrentVersion())
                .withCliOption("-B")
                .withCliOption("-Dmaven.test.skip")
                .withCliOptions(cliOptions)
                .execute("clean", "verify")
                .assertErrorFreeLog();
        assertSerialNumber(projDir, expectedSerialNumber);
    }

    private static void assertSerialNumber(File projDir, @Nullable String expectedSerialNumber) throws Exception {
        File bomFile = new File(projDir, "target/bom.json");
        String bomJson = new String(Files.readAllBytes(bomFile.toPath()), StandardCharsets.UTF_8);
        String serialNumberKey = "serialNumber";
        if (expectedSerialNumber == null) {
            assertThatJson(bomJson).isObject().doesNotContainKey(serialNumberKey);
        } else {
            assertThatJson(bomJson).isObject().containsEntry(serialNumberKey, expectedSerialNumber);
        }
    }

}
