package org.cyclonedx.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for {@link ReactorWorkspaceReader}, the fix for
 * <a href="https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/138">issue #138</a>.
 *
 * <p>Answering {@code findVersions} for reactor coordinates is what keeps Aether from reading
 * {@code maven-metadata.xml} out of every configured repository for modules the build is about to
 * produce. The delegation cases matter just as much: anything outside the reactor has to keep its
 * normal resolution, so that a project consuming third-party SNAPSHOTs still sees updates.</p>
 */
class ReactorWorkspaceReaderTest {

    private static final String GROUP = "com.example";

    @Test
    void answersForAReactorModule() {
        final RecordingWorkspaceReader delegate = new RecordingWorkspaceReader();
        final ReactorWorkspaceReader reader =
                new ReactorWorkspaceReader(delegate, reactor(project("child", "1.0-SNAPSHOT")));

        assertEquals(Collections.singletonList("1.0-SNAPSHOT"),
                reader.findVersions(artifact("child", "1.0-SNAPSHOT")));
        assertEquals(Collections.emptyList(), delegate.versionQueries,
                "a reactor module must be answered locally, without consulting anything else");
    }

    @Test
    void delegatesForArtifactsOutsideTheReactor() {
        final RecordingWorkspaceReader delegate = new RecordingWorkspaceReader();
        final ReactorWorkspaceReader reader =
                new ReactorWorkspaceReader(delegate, reactor(project("child", "1.0-SNAPSHOT")));

        reader.findVersions(artifact("third-party", "2.0-SNAPSHOT"));

        assertEquals(Collections.singletonList(GROUP + ":third-party:2.0-SNAPSHOT"), delegate.versionQueries,
                "third-party SNAPSHOTs must keep resolving normally, update policy included");
    }

    /**
     * Same groupId and artifactId as a reactor module but a different version: that is a real
     * dependency on another release, not the module being built, so it must not be short-circuited.
     */
    @Test
    void delegatesWhenOnlyTheVersionDiffers() {
        final RecordingWorkspaceReader delegate = new RecordingWorkspaceReader();
        final ReactorWorkspaceReader reader =
                new ReactorWorkspaceReader(delegate, reactor(project("child", "1.0-SNAPSHOT")));

        reader.findVersions(artifact("child", "0.9-SNAPSHOT"));

        assertEquals(Collections.singletonList(GROUP + ":child:0.9-SNAPSHOT"), delegate.versionQueries);
    }

    /**
     * Finding the file stays with the delegate: it returns the packaged artifact once the module has
     * been built and {@code null} before that, which is exactly the behaviour the BOM needs.
     */
    @Test
    void alwaysDelegatesFindArtifact() {
        final RecordingWorkspaceReader delegate = new RecordingWorkspaceReader();
        final ReactorWorkspaceReader reader =
                new ReactorWorkspaceReader(delegate, reactor(project("child", "1.0-SNAPSHOT")));

        assertSame(delegate.file, reader.findArtifact(artifact("child", "1.0-SNAPSHOT")));
        assertEquals(1, delegate.artifactQueries);
    }

    @Test
    void toleratesASessionWithoutAWorkspaceReader() {
        final ReactorWorkspaceReader reader =
                new ReactorWorkspaceReader(null, reactor(project("child", "1.0-SNAPSHOT")));

        assertNotNull(reader.getRepository());
        assertEquals(Collections.singletonList("1.0-SNAPSHOT"),
                reader.findVersions(artifact("child", "1.0-SNAPSHOT")));
        assertEquals(Collections.emptyList(), reader.findVersions(artifact("third-party", "2.0-SNAPSHOT")));
    }

    private static Artifact artifact(final String artifactId, final String version) {
        return new DefaultArtifact(GROUP, artifactId, "jar", version);
    }

    private static List<MavenProject> reactor(final MavenProject... projects) {
        return Arrays.asList(projects);
    }

    private static MavenProject project(final String artifactId, final String version) {
        final Model model = new Model();
        model.setGroupId(GROUP);
        model.setArtifactId(artifactId);
        model.setVersion(version);
        return new MavenProject(model);
    }

    /** Records what the reader passes through, so the tests can assert on delegation. */
    private static final class RecordingWorkspaceReader implements WorkspaceReader {
        private final File file = new File("delegate.jar");
        private final List<String> versionQueries = new ArrayList<>();
        private int artifactQueries;

        @Override
        public WorkspaceRepository getRepository() {
            return new WorkspaceRepository("delegate");
        }

        @Override
        public File findArtifact(final Artifact artifact) {
            artifactQueries++;
            return file;
        }

        @Override
        public List<String> findVersions(final Artifact artifact) {
            versionQueries.add(artifact.getGroupId() + ':' + artifact.getArtifactId() + ':'
                    + artifact.getBaseVersion());
            return Collections.emptyList();
        }
    }
}
