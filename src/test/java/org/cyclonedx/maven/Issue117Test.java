package org.cyclonedx.maven;

import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;
import java.io.File;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class Issue117Test {

    @Rule
    public final TestResources resources = new TestResources(
            "target/test-classes",
            "target/test-classes/transformed-projects"
    );

    public final MavenRuntime verifier;

    public Issue117Test(MavenRuntimeBuilder runtimeBuilder)
            throws Exception {
        this.verifier = runtimeBuilder.build(); //.withCliOptions(opts) // //
    }

    @Test
    public void testPluginWithActiviti() throws Exception {
        File projectDirTransformed = new File(
                "target/test-classes/transformed-projects/issue-117"
        );
        if (projectDirTransformed.exists()) {
            FileUtils.cleanDirectory(projectDirTransformed);
            projectDirTransformed.delete();
        }

        File projDir = resources.getBasedir("issue-117");

        Properties props = new Properties();

        props.load(Issue117Test.class.getClassLoader().getResourceAsStream("test.properties"));
        String projectVersion = (String) props.get("project.version");
        verifier
                .forProject(projDir) //
                .withCliOption("-Dtest.input.version=" + projectVersion) // debug
                .withCliOption("-X") // debug
                .withCliOption("-B")
                .execute("clean", "package")
                .assertErrorFreeLog();
    }
}
