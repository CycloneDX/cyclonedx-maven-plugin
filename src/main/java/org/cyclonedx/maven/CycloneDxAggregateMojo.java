package org.cyclonedx.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.cyclonedx.maven.model.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Mojo(
        name = "makeAggregateBom",
        defaultPhase = LifecyclePhase.VERIFY,
        aggregator = true,
        requiresOnline = true,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class CycloneDxAggregateMojo extends BaseCycloneDxMojo {

    public void execute() throws MojoExecutionException{

        final Set<Component> components = getReactorProjects().stream()
                .flatMap(mavenProject -> mavenProject.getArtifacts().stream())
                .filter(this::shouldInclude)
                .map(this::convert)
                .collect(Collectors.toSet());

        super.execute(components);
    }
}

