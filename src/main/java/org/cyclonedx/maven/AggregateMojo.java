/*
 * This file is part of CycloneDX Maven Plugin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package org.cyclonedx.maven;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.cyclonedx.maven.model.Component;

/**
 * Maven Plugin that checks project dependencies and the dependencies of all
 * child modules to see if they have any known published vulnerabilities.
 *
 */
@Mojo(
        name = "aggregate",
        defaultPhase = LifecyclePhase.VERIFY,
        aggregator = true,
        requiresOnline = true
)
public class AggregateMojo extends BaseCycloneDxMojo {

    public void execute() throws MojoExecutionException {
        Set<Component> aggregateComponents = new LinkedHashSet<>();

        getLog().info(MESSAGE_RESOLVING_DEPS);
        if (getProject() != null && getProject().getArtifacts() != null) {
            for (Artifact artifact : getProject().getArtifacts()) {
                if (shouldInclude(artifact)) {
                    aggregateComponents.add(convert(artifact));
                }
            }
        }

        for (MavenProject childProject : getDescendants(this.getProject())) {
            if ("pom".equals(getProject().getPackaging())) {
                getLog().info(MESSAGE_SKIPPING_POM);
                continue;
            }
            logParameters();

            if (childProject != null && childProject.getArtifacts() != null) {
                Set<Component> projectComponents = new LinkedHashSet<>();
                getLog().info(MESSAGE_RESOLVING_DEPS + " for " + childProject.getArtifactId());
                for (Artifact artifact : childProject.getArtifacts()) {
                    if (shouldInclude(artifact)) {
                        Component component = convert(artifact);
                        aggregateComponents.add(component);
                        projectComponents.add(component);
                        super.execute(projectComponents);
                    }
                }
            }
        }

        super.execute(aggregateComponents);
    }

    /**
     * Returns a set containing all the descendant projects of the given
     * project.
     *
     * @param project the project for which all descendants will be returned
     * @return the set of descendant projects
     */
    protected Set<MavenProject> getDescendants(MavenProject project) {
        if (project == null) {
            return Collections.emptySet();
        }
        final Set<MavenProject> descendants = new HashSet<>();
        int size;
        if (getLog().isDebugEnabled()) {
            getLog().debug(String.format("Collecting descendants of %s", project.getName()));
        }
        for (String m : project.getModules()) {
            for (MavenProject mod : getReactorProjects()) {
                try {
                    File mpp = new File(project.getBasedir(), m);
                    mpp = mpp.getCanonicalFile();
                    if (mpp.compareTo(mod.getBasedir()) == 0 && descendants.add(mod)
                            && getLog().isDebugEnabled()) {
                        getLog().debug(String.format("Descendant module %s added", mod.getName()));

                    }
                } catch (IOException ex) {
                    if (getLog().isDebugEnabled()) {
                        getLog().debug("Unable to determine module path", ex);
                    }
                }
            }
        }
        do {
            size = descendants.size();
            for (MavenProject p : getReactorProjects()) {
                if (project.equals(p.getParent()) || descendants.contains(p.getParent())) {
                    if (descendants.add(p) && getLog().isDebugEnabled()) {
                        getLog().debug(String.format("Descendant %s added", p.getName()));

                    }
                    for (MavenProject modTest : getReactorProjects()) {
                        if (p.getModules() != null && p.getModules().contains(modTest.getName())
                                && descendants.add(modTest)
                                && getLog().isDebugEnabled()) {
                            getLog().debug(String.format("Descendant %s added", modTest.getName()));
                        }
                    }
                }
                final Set<MavenProject> addedDescendants = new HashSet<>();
                for (MavenProject dec : descendants) {
                    for (String mod : dec.getModules()) {
                        try {
                            File mpp = new File(dec.getBasedir(), mod);
                            mpp = mpp.getCanonicalFile();
                            if (mpp.compareTo(p.getBasedir()) == 0) {
                                addedDescendants.add(p);
                            }
                        } catch (IOException ex) {
                            if (getLog().isDebugEnabled()) {
                                getLog().debug("Unable to determine module path", ex);
                            }
                        }
                    }
                }
                for (MavenProject addedDescendant : addedDescendants) {
                    if (descendants.add(addedDescendant) && getLog().isDebugEnabled()) {
                        getLog().debug(String.format("Descendant module %s added", addedDescendant.getName()));
                    }
                }
            }
        } while (size != 0 && size != descendants.size());
        if (getLog().isDebugEnabled()) {
            getLog().debug(String.format("%s has %d children", project, descendants.size()));
        }
        return descendants;
    }

    /**
     * Test if the project has pom packaging
     *
     * @param mavenProject Project to test
     * @return <code>true</code> if it has a pom packaging; otherwise
     * <code>false</code>
     */
    protected boolean isMultiModule(MavenProject mavenProject) {
        return "pom".equals(mavenProject.getPackaging());
    }

}
