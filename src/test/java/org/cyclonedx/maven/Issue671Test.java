package org.cyclonedx.maven;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeployResult;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallResult;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.resolution.VersionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test for <a href="https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/671">issue #671</a>:
 * Maven 4 compatibility - makeAggregateBom fails with ArtifactResult$NoRepository.
 *
 * <p>Maven 4 introduces {@code ArtifactResult$NoRepository} as a new repository type in the
 * resolver API (Maven Resolver 2.x). This test verifies that {@link DelegatingRepositorySystem}
 * gracefully handles {@link IllegalArgumentException} thrown during artifact resolution (which
 * is what happens when the Maven 4 resolver encounters the unsupported repository type).</p>
 */
class Issue671Test {

    /**
     * Verify that {@link DelegatingRepositorySystem#collectDependencies} does not propagate
     * {@link IllegalArgumentException} thrown by the delegate's {@code resolveArtifact}.
     *
     * <p>This simulates the Maven 4 scenario where resolving an artifact can throw
     * {@code IllegalArgumentException} due to the unrecognized {@code NoRepository} type.</p>
     */
    @Test
    void collectDependenciesShouldHandleIllegalArgumentExceptionFromResolveArtifact() {
        final Artifact rootArtifact = new DefaultArtifact("com.example", "root", "jar", "1.0");
        final Artifact childArtifact = new DefaultArtifact("com.example", "child", "jar", "1.0");

        // Create a dependency tree: root -> child
        final DependencyNode childNode = new DefaultDependencyNode(
                new Dependency(childArtifact, "compile"));
        final DependencyNode rootNode = new DefaultDependencyNode(
                new Dependency(rootArtifact, "compile"));
        rootNode.setChildren(Collections.singletonList(childNode));

        // Create a delegate that throws IllegalArgumentException on resolveArtifact
        // (simulating Maven 4's NoRepository handling)
        final RepositorySystem throwingDelegate = new StubRepositorySystem() {
            @Override
            public CollectResult collectDependencies(RepositorySystemSession session, CollectRequest request)
                    throws DependencyCollectionException {
                return new CollectResult(request).setRoot(rootNode);
            }

            @Override
            public ArtifactResult resolveArtifact(RepositorySystemSession session, ArtifactRequest request)
                    throws ArtifactResolutionException {
                throw new IllegalArgumentException(
                        "Unsupported repository type: class org.eclipse.aether.resolution.ArtifactResult$NoRepository");
            }
        };

        final DelegatingRepositorySystem delegating = new DelegatingRepositorySystem(throwingDelegate);

        // Should not throw - the IllegalArgumentException should be caught internally
        CollectResult result = assertDoesNotThrow(
                () -> delegating.collectDependencies(null, new CollectRequest()));

        assertNotNull(result);
        assertNotNull(result.getRoot());
    }

    /**
     * Verify that normal artifact resolution still works when no exception is thrown.
     */
    @Test
    void collectDependenciesShouldWorkNormallyWhenNoExceptionThrown() {
        final Artifact rootArtifact = new DefaultArtifact("com.example", "root", "jar", "1.0");
        final Artifact childArtifact = new DefaultArtifact("com.example", "child", "jar", "1.0");
        final Artifact resolvedChild = new DefaultArtifact("com.example", "child", "jar", "1.0")
                .setFile(new java.io.File("/tmp/child-1.0.jar"));

        final DependencyNode childNode = new DefaultDependencyNode(
                new Dependency(childArtifact, "compile"));
        final DependencyNode rootNode = new DefaultDependencyNode(
                new Dependency(rootArtifact, "compile"));
        rootNode.setChildren(Collections.singletonList(childNode));

        final RepositorySystem normalDelegate = new StubRepositorySystem() {
            @Override
            public CollectResult collectDependencies(RepositorySystemSession session, CollectRequest request)
                    throws DependencyCollectionException {
                return new CollectResult(request).setRoot(rootNode);
            }

            @Override
            public ArtifactResult resolveArtifact(RepositorySystemSession session, ArtifactRequest request)
                    throws ArtifactResolutionException {
                ArtifactResult artifactResult = new ArtifactResult(request);
                artifactResult.setArtifact(resolvedChild);
                return artifactResult;
            }
        };

        final DelegatingRepositorySystem delegating = new DelegatingRepositorySystem(normalDelegate);

        CollectResult result = assertDoesNotThrow(
                () -> delegating.collectDependencies(null, new CollectRequest()));

        assertNotNull(result);
        assertNotNull(result.getRoot());
    }

    /**
     * Verify that {@link ArtifactResolutionException} continues to be caught (pre-existing behavior).
     */
    @Test
    void collectDependenciesShouldHandleArtifactResolutionException() {
        final Artifact rootArtifact = new DefaultArtifact("com.example", "root", "jar", "1.0");
        final Artifact childArtifact = new DefaultArtifact("com.example", "child", "jar", "1.0");

        final DependencyNode childNode = new DefaultDependencyNode(
                new Dependency(childArtifact, "compile"));
        final DependencyNode rootNode = new DefaultDependencyNode(
                new Dependency(rootArtifact, "compile"));
        rootNode.setChildren(Collections.singletonList(childNode));

        final RepositorySystem failingDelegate = new StubRepositorySystem() {
            @Override
            public CollectResult collectDependencies(RepositorySystemSession session, CollectRequest request)
                    throws DependencyCollectionException {
                return new CollectResult(request).setRoot(rootNode);
            }

            @Override
            public ArtifactResult resolveArtifact(RepositorySystemSession session, ArtifactRequest request)
                    throws ArtifactResolutionException {
                throw new ArtifactResolutionException(
                        Collections.singletonList(new ArtifactResult(request)),
                        "Could not resolve artifact");
            }
        };

        final DelegatingRepositorySystem delegating = new DelegatingRepositorySystem(failingDelegate);

        CollectResult result = assertDoesNotThrow(
                () -> delegating.collectDependencies(null, new CollectRequest()));

        assertNotNull(result);
        assertNotNull(result.getRoot());
    }

    /**
     * Minimal stub of {@link RepositorySystem} that throws {@link UnsupportedOperationException}
     * for all methods except those overridden in individual tests.
     */
    private static class StubRepositorySystem implements RepositorySystem {
        @Override
        public CollectResult collectDependencies(RepositorySystemSession session, CollectRequest request)
                throws DependencyCollectionException {
            throw new UnsupportedOperationException();
        }

        @Override
        public DeployResult deploy(RepositorySystemSession session, DeployRequest request)
                throws DeploymentException {
            throw new UnsupportedOperationException();
        }

        @Override
        public InstallResult install(RepositorySystemSession session, InstallRequest request)
                throws InstallationException {
            throw new UnsupportedOperationException();
        }

        @Override
        public RemoteRepository newDeploymentRepository(RepositorySystemSession session,
                RemoteRepository repository) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalRepositoryManager newLocalRepositoryManager(RepositorySystemSession session,
                LocalRepository localRepository) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RemoteRepository> newResolutionRepositories(RepositorySystemSession session,
                List<RemoteRepository> repositories) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SyncContext newSyncContext(RepositorySystemSession session, boolean shared) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ArtifactDescriptorResult readArtifactDescriptor(RepositorySystemSession session,
                ArtifactDescriptorRequest request) throws ArtifactDescriptorException {
            throw new UnsupportedOperationException();
        }

        @Override
        public ArtifactResult resolveArtifact(RepositorySystemSession session, ArtifactRequest request)
                throws ArtifactResolutionException {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ArtifactResult> resolveArtifacts(RepositorySystemSession session,
                Collection<? extends ArtifactRequest> requests) throws ArtifactResolutionException {
            throw new UnsupportedOperationException();
        }

        @Override
        public DependencyResult resolveDependencies(RepositorySystemSession session, DependencyRequest request)
                throws DependencyResolutionException {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<MetadataResult> resolveMetadata(RepositorySystemSession session,
                Collection<? extends MetadataRequest> requests) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VersionResult resolveVersion(RepositorySystemSession session, VersionRequest request)
                throws VersionResolutionException {
            throw new UnsupportedOperationException();
        }

        @Override
        public VersionRangeResult resolveVersionRange(RepositorySystemSession session, VersionRangeRequest request)
                throws VersionRangeResolutionException {
            throw new UnsupportedOperationException();
        }
    }
}
