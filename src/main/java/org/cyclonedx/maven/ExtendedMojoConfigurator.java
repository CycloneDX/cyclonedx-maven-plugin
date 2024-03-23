package org.cyclonedx.maven;

import org.codehaus.plexus.component.configurator.BasicComponentConfigurator;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;

import javax.inject.Named;

@Named("cyclonedx-mojo-component-configurator")
public class ExtendedMojoConfigurator extends BasicComponentConfigurator implements Initializable {

    @Override
    public void initialize() throws InitializationException {
        converterLookup.registerConverter(new ExternalReferenceTypeConverter());
    }
}
