package org.cyclonedx.maven;

import java.util.Collections;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
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
}
