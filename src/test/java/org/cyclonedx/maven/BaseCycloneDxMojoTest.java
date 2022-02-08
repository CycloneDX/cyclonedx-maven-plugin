/*
 * Copyright (c) ACTICO GmbH, Germany. All rights reserved.
 */
package org.cyclonedx.maven;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.Test;

public class BaseCycloneDxMojoTest {

  @Test
  public void shouldIncludeBasedOnExcludePattern() {
    Artifact artifact = mock(Artifact.class);
    when(artifact.getType()).thenReturn("jar");
    when(artifact.getArtifactId()).thenReturn("com.test.me");
    when(artifact.getGroupId()).thenReturn("org.foo");
    when(artifact.getScope()).thenReturn(Artifact.SCOPE_COMPILE);
    assertTrue(new Stub(null).shouldInclude(artifact));

    assertTrue(new Stub("::war").shouldInclude(artifact));
    assertFalse(new Stub("::jar").shouldInclude(artifact));
    assertFalse(new Stub(":com.test*:").shouldInclude(artifact));
    assertFalse(new Stub("org.*::").shouldInclude(artifact));
    assertTrue(new Stub("com.*::").shouldInclude(artifact));
  }

  static class Stub extends BaseCycloneDxMojo {

    Stub(String excludeComponents) {
      this.excludeComponents = excludeComponents;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {}
  }
}
