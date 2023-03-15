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
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) OWASP Foundation. All Rights Reserved.
 */
package org.cyclonedx.maven;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;

/**
 * This dependency selector forces the wrapped selector to treat all dependencies as if
 * they were at no longer direct dependencies.
 */
public class TransitiveDependencySelector implements DependencySelector {
    private final DependencySelector dependencySelector;

    public TransitiveDependencySelector(final DependencySelector dependencySelector) {
        this.dependencySelector = dependencySelector;
    }

    /*
     * This method is not invoked until after the root child selector has been derived, in our case
     * this will never be invoked as we hand over to the wrapped selector.
     */
    @Override
    public boolean selectDependency(final Dependency dependency) {
        throw new UnsupportedOperationException("Unimplemented method 'selectDependency'");
    }

    /*
     * This method is only invoked when deriving the root selector, we return the wrapped selector which
     * would normally be active for depth 2 instead of the root one.  This has the outcome of no longer treating
     * artifacts as direct dependencies.
     */
    @Override
    public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
        return dependencySelector.deriveChildSelector(context).deriveChildSelector(context);
    }
}
