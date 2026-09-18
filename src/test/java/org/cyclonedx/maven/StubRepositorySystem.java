package org.cyclonedx.maven;

import java.util.Collection;
import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeployResult;
import org.eclipse.aether.deployment.DeploymentException;
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

/**
 * Minimal stub of {@link RepositorySystem} that throws {@link UnsupportedOperationException}
 * for all methods except those overridden by individual tests.
 */
class StubRepositorySystem implements RepositorySystem {
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
