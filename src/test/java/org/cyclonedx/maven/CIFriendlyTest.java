package org.cyclonedx.maven;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

/**
 * test for https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/263
 * when makeAggregateBom using CI-friendly versions, root component does not list modules as dependencies
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class CIFriendlyTest extends BaseMavenVerifier {

    public CIFriendlyTest(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        super(runtimeBuilder);
    }

    @Test
    public void testCIFriendlyaggregate() throws Exception {
        File projDir = resources.getBasedir("ci-friendly");

        verifier
                .forProject(projDir)
                .withCliOption("-Dcurrent.version=" + getCurrentVersion()) // inject cyclonedx-maven-plugin version
                .withCliOption("-B")
                .withCliOption("-Drevision=ci-friendly-revision")
                .execute("verify")
                .assertErrorFreeLog();

        String bom = fileRead(new File(projDir, "target/bom.xml"), true);
        String rootDependencies = bom.substring(bom.indexOf("<dependency ref=\"pkg:maven/com.example/issue-263@ci-friendly-revision?type=pom"), bom.indexOf("</dependency>") + 13);
        assertTrue("root dependencies must contain module-A@ci-friendly-revision", rootDependencies.contains("<dependency ref=\"pkg:maven/com.example/module-A@ci-friendly-revision?type=jar\"/>"));
        assertTrue("root dependencies must contain module-B@ci-friendly-revision", rootDependencies.contains("<dependency ref=\"pkg:maven/com.example/module-B@ci-friendly-revision?type=jar\"/>"));
    }
}
