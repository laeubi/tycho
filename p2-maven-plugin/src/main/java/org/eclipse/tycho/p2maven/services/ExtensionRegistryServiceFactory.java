package org.eclipse.tycho.p2maven.services;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.spi.IRegistryProvider;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;

@Component(role = ServiceFactory.class, hint = "org.eclipse.core.runtime.IExtensionRegistry")
public class ExtensionRegistryServiceFactory implements ServiceFactory<IExtensionRegistry> {

	@Requirement
	private IRegistryProvider registryProvider;

	@Override
	public IExtensionRegistry getService(Bundle bundle, ServiceRegistration<IExtensionRegistry> registration) {
		return registryProvider.getRegistry();
	}

	@Override
	public void ungetService(Bundle bundle, ServiceRegistration<IExtensionRegistry> registration,
			IExtensionRegistry service) {
	}

}
