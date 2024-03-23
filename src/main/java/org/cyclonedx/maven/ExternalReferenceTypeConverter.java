package org.cyclonedx.maven;

import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.converters.basic.AbstractBasicConverter;

import org.cyclonedx.model.ExternalReference;

/**
 * Custom Plexus BasicConverter to instantiate <code>ExternalReference.Type</code> from a String.
 *
 * @see ExternalReference.Type
 */
public class ExternalReferenceTypeConverter extends AbstractBasicConverter {

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
