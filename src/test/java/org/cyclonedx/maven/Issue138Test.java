package org.cyclonedx.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for <a href="https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/138">issue #138</a>
 * and <a href="https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/410">issue #410</a>.
 *
 * <p>{@code makeAggregateBom} is an aggregator bound to the root project, which Maven builds first,
 * so the reactor modules are usually not packaged yet when their dependencies get collected.
 * {@link DelegatingRepositorySystem} used to resolve every node of the graph through the repository
 * system, which for a reactor module means a remote SNAPSHOT update check followed by a download of
 * the <em>previously published</em> artifact. That is both slow (issue #138: one round trip per
 * module, reported at 15-30s each against Artifactory) and wrong (issue #410: the BOM ends up
 * carrying the hashes of the previous build).</p>
 *
 * <p>The reactor is authoritative for its own coordinates, so those nodes must never be resolved
 * from a repository.</p>
 */
class Issue138Test {

    private static final String GROUP = "com.example";
    private static final String VERSION = "1.0-SNAPSHOT";

    /**
     * A reactor module that has not been packaged yet must not be resolved from a repository, and
     * must be left without a file so that no hash of a stale artifact ends up in the BOM.
     */
    @Test
    void shouldNotResolveUnbuiltReactorModuleFromRepository() throws Exception {
        final DependencyNode child = node("child");
        final RecordingRepositorySystem delegate = new RecordingRepositorySystem(root(child));

        new DelegatingRepositorySystem(delegate, reactor(project("child", null)))
                .collectDependencies(null, new CollectRequest());

        assertEquals(Collections.emptyList(), delegate.resolved,
                "reactor modules must not be resolved through the repository system");
        assertNull(child.getArtifact().getFile(),
                "an unbuilt reactor module must have no file, rather than the previous build's artifact");
    }

    /**
     * Once the reactor has packaged the module, its own file must be used: that is what makes the
     * BOM hashes match the artifact this build actually produced.
     */
    @Test
    void shouldUseReactorFileWhenModuleIsAlreadyPackaged() throws Exception {
        final File packaged = new File("target/child-1.0-SNAPSHOT.jar").getAbsoluteFile();
        final DependencyNode child = node("child");
        final RecordingRepositorySystem delegate = new RecordingRepositorySystem(root(child));

        new DelegatingRepositorySystem(delegate, reactor(project("child", packaged)))
                .collectDependencies(null, new CollectRequest());

        assertEquals(Collections.emptyList(), delegate.resolved,
                "reactor modules must not be resolved through the repository system");
        assertEquals(packaged, child.getArtifact().getFile());
    }

    /**
     * Dependencies outside the reactor keep being resolved as before: the fix must not stop the
     * plugin from hashing third-party artifacts.
     */
    @Test
    void shouldStillResolveDependenciesOutsideTheReactor() throws Exception {
        final DependencyNode external = node("some-library");
        final RecordingRepositorySystem delegate = new RecordingRepositorySystem(root(external));

        new DelegatingRepositorySystem(delegate, reactor(project("child", null)))
                .collectDependencies(null, new CollectRequest());

        assertEquals(Collections.singletonList(GROUP + ":some-library:" + VERSION), delegate.resolved);
    }

    /**
     * With no reactor supplied the class must behave exactly as it did before, so that the
     * single-argument constructor stays usable.
     */
    @Test
    void shouldResolveEverythingWhenReactorIsUnknown() throws Exception {
        final DependencyNode child = node("child");
        final RecordingRepositorySystem delegate = new RecordingRepositorySystem(root(child));

        new DelegatingRepositorySystem(delegate).collectDependencies(null, new CollectRequest());

        assertTrue(delegate.resolved.contains(GROUP + ":child:" + VERSION));
    }

    private static DependencyNode node(final String artifactId) {
        return new DefaultDependencyNode(new Dependency(
                new org.eclipse.aether.artifact.DefaultArtifact(GROUP, artifactId, "jar", VERSION), "compile"));
    }

    private static DependencyNode root(final DependencyNode... children) {
        final DependencyNode root = new DefaultDependencyNode(new Dependency(
                new org.eclipse.aether.artifact.DefaultArtifact(GROUP, "root", "pom", VERSION), "compile"));
        root.setChildren(Arrays.asList(children));
        return root;
    }

    private static List<MavenProject> reactor(final MavenProject... projects) {
        return Arrays.asList(projects);
    }

    private static MavenProject project(final String artifactId, final File packagedFile) {
        final Model model = new Model();
        model.setGroupId(GROUP);
        model.setArtifactId(artifactId);
        model.setVersion(VERSION);
        model.setPackaging("jar");

        final MavenProject project = new MavenProject(model);
        final org.apache.maven.artifact.Artifact artifact = new DefaultArtifact(GROUP, artifactId,
                VersionRange.createFromVersion(VERSION), "compile", "jar", null, new DefaultArtifactHandler("jar"));
        artifact.setFile(packagedFile);
        project.setArtifact(artifact);
        return project;
    }

    /** Delegate that records which artifacts were asked for, so the test can assert none were. */
    private static final class RecordingRepositorySystem extends StubRepositorySystem {
        private final DependencyNode root;
        private final List<String> resolved = new ArrayList<>();

        RecordingRepositorySystem(final DependencyNode root) {
            this.root = root;
        }

        @Override
        public CollectResult collectDependencies(final RepositorySystemSession session, final CollectRequest request)
                throws DependencyCollectionException {
            return new CollectResult(request).setRoot(root);
        }

        @Override
        public ArtifactResult resolveArtifact(final RepositorySystemSession session, final ArtifactRequest request)
                throws ArtifactResolutionException {
            final org.eclipse.aether.artifact.Artifact artifact = request.getArtifact();
            resolved.add(artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getBaseVersion());
            final ArtifactResult result = new ArtifactResult(request);
            // Stand in for the previously published artifact a real repository would hand back.
            result.setArtifact(artifact.setFile(new File("previously-published.jar")));
            return result;
        }
    }
}
