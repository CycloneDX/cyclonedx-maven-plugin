package org.cyclonedx.maven;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests how {@link CycloneDxReportMojo} finds the SBOM file written by the BOM goals.
 */
class CycloneDxReportMojoTest {
    @TempDir
    File basedir;

    private MavenProject project(final Plugin... plugins) {
        final Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId("demo");
        model.setVersion("1.0");
        final Build build = new Build();
        build.setDirectory(file("target").getPath());
        build.setOutputDirectory(file("target/classes").getPath());
        for (Plugin plugin : plugins) {
            build.addPlugin(plugin);
        }
        model.setBuild(build);
        final MavenProject project = new MavenProject(model);
        project.setFile(file("pom.xml"));
        return project;
    }

    /**
     * @param goal the goal of the execution
     * @param configuration pairs of parameter name and value
     */
    private static Plugin cyclonedx(final String goal, final String... configuration) {
        final Xpp3Dom dom = new Xpp3Dom("configuration");
        for (int i = 0; i < configuration.length; i += 2) {
            final Xpp3Dom child = new Xpp3Dom(configuration[i]);
            child.setValue(configuration[i + 1]);
            dom.addChild(child);
        }
        final PluginExecution execution = new PluginExecution();
        execution.addGoal(goal);
        execution.setConfiguration(dom);
        final Plugin plugin = new Plugin();
        plugin.setGroupId("org.cyclonedx");
        plugin.setArtifactId("cyclonedx-maven-plugin");
        plugin.addExecution(execution);
        return plugin;
    }

    private File file(final String path) {
        return new File(basedir, path);
    }

    @Test
    void defaultLocation() {
        assertEquals(Arrays.asList(file("target/bom.json"), file("target/bom.xml")),
                CycloneDxReportMojo.candidateBomFiles(project(), new Properties()));
    }

    @Test
    void executionConfigurationComesFirst() {
        // configuration provided by spring-boot-starter-parent, after model interpolation
        final Plugin plugin = cyclonedx("makeAggregateBom",
                "outputDirectory", file("target/classes/META-INF/sbom").getPath(),
                "outputFormat", "json",
                "outputName", "application.cdx");

        assertEquals(Arrays.asList(file("target/classes/META-INF/sbom/application.cdx.json"),
                        file("target/bom.json"), file("target/bom.xml")),
                CycloneDxReportMojo.candidateBomFiles(project(plugin), new Properties()));
    }

    @Test
    void parametersResolvedFromUserThenProjectProperties() {
        final MavenProject project = project();
        project.getProperties().setProperty("outputDirectory", "sbom");
        project.getProperties().setProperty("outputName", "from-project");
        project.getProperties().setProperty("outputFormat", "xml");
        final Properties userProperties = new Properties();
        userProperties.setProperty("outputName", "from-cli");

        assertEquals(Arrays.asList(file("sbom/from-cli.xml")),
                CycloneDxReportMojo.candidateBomFiles(project, userProperties));
    }

    @Test
    void ignoresUnresolvedExpressionsAndOtherGoals() {
        final Plugin plugin = cyclonedx("makeBom", "outputDirectory", "${session.executionRootDirectory}/sbom");
        final PluginExecution report = new PluginExecution();
        report.addGoal("report");
        final Xpp3Dom configuration = new Xpp3Dom("configuration");
        final Xpp3Dom outputName = new Xpp3Dom("outputName");
        outputName.setValue("not-a-bom");
        configuration.addChild(outputName);
        report.setConfiguration(configuration);
        plugin.addExecution(report);

        assertEquals(Arrays.asList(file("target/bom.json"), file("target/bom.xml")),
                CycloneDxReportMojo.candidateBomFiles(project(plugin), new Properties()));
    }

    @Test
    void copiesTheOtherFormatWhenAvailable() throws Exception {
        final File json = file("bom.json");
        final File xml = file("bom.xml");
        final File cdx = file("application.cdx.json");
        Files.write(json.toPath(), new byte[0]);
        Files.write(xml.toPath(), new byte[0]);
        Files.write(cdx.toPath(), new byte[0]);

        assertEquals(Arrays.asList(json, xml), CycloneDxReportMojo.withOtherFormat(json));
        assertEquals(Arrays.asList(xml, json), CycloneDxReportMojo.withOtherFormat(xml));
        assertEquals(Arrays.asList(cdx), CycloneDxReportMojo.withOtherFormat(cdx));
    }
}
