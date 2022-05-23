package org.cyclonedx.maven;

import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;
import java.io.*;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class Issue116Test {

    @Rule
    public final TestResources resources = new TestResources(
            "target/test-classes",
            "target/test-classes/transformed-projects"
    );

    public final MavenRuntime verifier;

    public Issue116Test(MavenRuntimeBuilder runtimeBuilder)
            throws Exception {
        this.verifier = runtimeBuilder.build(); //.withCliOptions(opts) // //
    }

    @Test
    public void testPluginWithActiviti() throws Exception {
        File projectDirTransformed = new File(
                "target/test-classes/transformed-projects/issue-116"
        );
        if (projectDirTransformed.exists()) {
            FileUtils.cleanDirectory(projectDirTransformed);
            projectDirTransformed.delete();
        }

        File projDir = resources.getBasedir("issue-116");

        Properties props = new Properties();

        props.load(Issue116Test.class.getClassLoader().getResourceAsStream("test.properties"));
        String projectVersion = (String) props.get("project.version");
        verifier
                .forProject(projDir) //
                .withCliOption("-Dtest.input.version=" + projectVersion) // debug
                .withCliOption("-X") // debug
                .withCliOption("-B")
                .execute("clean", "package")
                .assertErrorFreeLog();

        // assert commons-lang3 has appeared in the dependency graph multiple times
        String bomContents = fileRead(new File(projDir, "target/bom.xml"), true);
        int matches = StringUtils.countMatches(bomContents, "<dependency ref=\"pkg:maven/org.apache.commons/commons-lang3@3.1?type=jar\"/>");
        assertEquals(4, matches); // 1 for the definition, 3 for each of its usages
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
