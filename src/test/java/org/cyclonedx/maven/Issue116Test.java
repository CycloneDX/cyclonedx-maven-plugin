package org.cyclonedx.maven;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

/**
 * test for https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/116
 * dependencies in BOM file are missing a reference
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class Issue116Test extends BaseMavenVerifier {

    public Issue116Test(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testPluginWithActiviti() throws Exception {
        File projDir = resources.getBasedir("issue-116");

        verifier
                .forProject(projDir)
                .withCliOption("-Dcurrent.version=" + getCurrentVersion()) // inject cyclonedx-maven-plugin version
                .withCliOption("-X") // debug
                .withCliOption("-B")
                .execute("clean", "package")
                .assertErrorFreeLog();

        // assert commons-lang3 has appeared in the dependency graph multiple times
        String bomContents = fileRead(new File(projDir, "target/bom.xml"), true);
        int matches = StringUtils.countMatches(bomContents, "<dependency ref=\"pkg:maven/org.apache.commons/commons-lang3@3.1?type=jar\"/>");
        assertEquals(4, matches); // 1 for the definition, 3 for each of its usages
    }
}
