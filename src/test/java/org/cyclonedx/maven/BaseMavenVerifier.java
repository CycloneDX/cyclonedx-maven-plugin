package org.cyclonedx.maven;

import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenExecution;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Properties;
import org.junit.Rule;

public class BaseMavenVerifier {

    @Rule
    public final TestResources resources = new TestResources("target/test-classes", "target/test-classes/transformed-projects");

    public final MavenRuntime verifier;

    public BaseMavenVerifier(MavenRuntimeBuilder runtimeBuilder) throws Exception {
        this.verifier = runtimeBuilder/*.withCliOptions(opts)*/.build();
    }

    public String getCurrentVersion() throws IOException {
        // extract current cyclonedx-maven-plugin version from test.properties https://github.com/takari/takari-plugin-testing-project/blob/master/testproperties.md
        Properties props = new Properties();
        props.load(this.getClass().getClassLoader().getResourceAsStream("test.properties"));
        return (String) props.get("project.version");
    }

    // source: https://github.com/takari/takari-plugin-testing-project/blob/master/takari-plugin-testing/src/main/java/io/takari/maven/testing/AbstractTestResources.java#L103
    protected static String fileRead(File file, boolean normalizeEOL) throws IOException {
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

    protected File cleanAndBuild(final String project, final String[] excludeTypes) throws Exception {
        return cleanAndBuild(project, excludeTypes, null);
    }

    protected File cleanAndBuild(final String project, final String[] excludeTypes, final String[] profiles) throws Exception {
        return mvnBuild(project, null, excludeTypes, profiles, null);
    }

    protected File cleanAndBuild(final String project, final Map<String, String> properties, final String[] excludeTypes) throws Exception {
        return mvnBuild(project, null, excludeTypes, null, properties);
    }

    protected File mvnBuild(final String project, final String[] goals, final String[] excludeTypes, final String[] profiles, final Map<String, String> properties) throws Exception {
        File projDir = resources.getBasedir(project);

        MavenExecution execution = verifier
                .forProject(projDir)
                .withCliOption("-Dcurrent.version=" + getCurrentVersion()) // inject cyclonedx-maven-plugin version
                .withCliOption("-X") // debug
                .withCliOption("-B");
        if ((excludeTypes != null) && (excludeTypes.length > 0)) {
            execution = execution.withCliOption("-DexcludeTypes=" + String.join(",", excludeTypes));
        }
        if ((profiles != null) && (profiles.length > 0)) {
            execution.withCliOption("-P" + String.join(",", profiles));
        }
        if (properties != null) {
            for (Map.Entry<String, String> entry: properties.entrySet()) {
                execution.withCliOption("-D" + entry.getKey() + "=" + entry.getValue());
            }
        }
        if (goals != null && goals.length > 0) {
            execution.execute(goals).assertErrorFreeLog();
        } else {
            execution.execute("clean", "package").assertErrorFreeLog();
        }
        return projDir;
    }
}
