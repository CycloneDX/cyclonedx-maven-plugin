package org.cyclonedx.maven;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;

/**
 * Workspace reader that answers version queries for the modules of the current reactor, so that
 * resolving them never reaches a repository.
 *
 * <p>Maven's own reactor reader only answers once a module has been packaged. {@code
 * makeAggregateBom} runs on the root project, which Maven builds first, so at that point no module
 * has been built and every one of them falls through to a remote SNAPSHOT update check -- one round
 * trip per module against every configured repository. See
 * <a href="https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/138">issue #138</a>.</p>
 *
 * <p>Only coordinates belonging to the reactor are answered here; everything else is delegated, so
 * dependencies outside the build keep their normal resolution and update policy.</p>
 */
class ReactorWorkspaceReader implements WorkspaceReader {
    private final WorkspaceReader delegate;
    private final Map<String, String> reactorVersions;
    private final WorkspaceRepository repository;

    ReactorWorkspaceReader(final WorkspaceReader delegate, final List<MavenProject> reactorProjects) {
        this.delegate = delegate;
        this.repository = (delegate != null) ? delegate.getRepository() : new WorkspaceRepository("reactor");
        final Map<String, String> versions = new HashMap<>();
        if (reactorProjects != null) {
            for (final MavenProject project : reactorProjects) {
                versions.put(project.getGroupId() + ':' + project.getArtifactId(), project.getVersion());
            }
        }
        this.reactorVersions = versions;
    }

    @Override
    public WorkspaceRepository getRepository() {
        return repository;
    }

    @Override
    public File findArtifact(final Artifact artifact) {
        // Left to the delegate: it returns the packaged file once the module has been built, and
        // null before that. A null here only means "no file yet", not "unknown version".
        return (delegate == null) ? null : delegate.findArtifact(artifact);
    }

    @Override
    public List<String> findVersions(final Artifact artifact) {
        final String version = reactorVersions.get(artifact.getGroupId() + ':' + artifact.getArtifactId());
        if (version != null && version.equals(artifact.getBaseVersion())) {
            return Collections.singletonList(version);
        }
        return (delegate == null) ? Collections.<String>emptyList() : delegate.findVersions(artifact);
    }
}
