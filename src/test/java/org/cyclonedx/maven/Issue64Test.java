package org.cyclonedx.maven;

import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.*;
import java.util.Properties;

import static io.takari.maven.testing.TestResources.assertFilesPresent;
import static org.junit.Assert.assertTrue;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class Issue64Test {

    @Rule
    public final TestResources resources = new TestResources(
            "target/test-classes",
            "target/test-classes/transformed-projects"
    );

    public final MavenRuntime verifier;

    public Issue64Test(MavenRuntimeBuilder runtimeBuilder)
            throws Exception {
        this.verifier = runtimeBuilder.build();
    }

    @Test
    public void testPluginWithActiviti() throws Exception {
        File projectDirTransformed = new File(
                "target/test-classes/transformed-projects/issue-64"
        );
        if (projectDirTransformed.exists()) {
            FileUtils.cleanDirectory(projectDirTransformed);
            projectDirTransformed.delete();
        }

        File projDir = resources.getBasedir("issue-64");

        Properties props = new Properties();

        props.load(Issue117Test.class.getClassLoader().getResourceAsStream("test.properties"));
        String projectVersion = String.class.cast(props.get("project.version"));
        verifier
                .forProject(projDir) //
                .withCliOption("-Dtest.input.version=" + projectVersion) // debug
                .withCliOption("-X") // debug
                .withCliOption("-B")
                .execute("clean", "verify")
                .assertErrorFreeLog();

        assertFileContains(projDir, "target/bom.xml", "junit");
    }

    private static void assertFileContains(File basedir, String expectedFile, String expectedContent) throws IOException {
        assertFilesPresent(basedir, expectedFile);
        String bomContents = fileRead(new File(basedir, expectedFile), true);
        assertTrue(String.format("%s contains %s", expectedFile, expectedContent), bomContents.contains(expectedContent));
    }

    // source: https://github.com/takari/takari-plugin-testing-project/blob/master/takari-plugin-testing/src/main/java/io/takari/maven/testing/AbstractTestResources.java#L103
    private static String fileRead(File file, boolean normalizeEOL) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            if (normalizeEOL) {
                String str;
                while ((str = r.readLine()) != null) {
                    sb.append(str).append('\n');
                }
            } else {
                int ch;
                while ((ch = r.read()) != -1) {
                    sb.append((char) ch);
                }
            }
        }
        return sb.toString();
    }

}
