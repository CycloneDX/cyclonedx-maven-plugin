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

import org.codehaus.plexus.component.configurator.BasicComponentConfigurator;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.converters.basic.AbstractBasicConverter;
import org.cyclonedx.model.ExternalReference;

import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@Named("cyclonedx-mojo-component-configurator")
public class BaseCycloneDxMojoConfigurator extends BasicComponentConfigurator {
    public BaseCycloneDxMojoConfigurator() {
        converterLookup.registerConverter(new ExternalReferenceTypeConverter());
    }

    public static class ExternalReferenceTypeConverter extends AbstractBasicConverter {
        @Override
        public boolean canConvert(Class type) {
            return ExternalReference.Type.class.isAssignableFrom(type);
        }

        @Override
        public Object fromString(String string) throws ComponentConfigurationException {
            Object value = ExternalReference.Type.fromString(string);
            if (value == null) {
                throw new ComponentConfigurationException("Unsupported ExternalReference type: " + string);
            }
            return value;
        }
    }
}
