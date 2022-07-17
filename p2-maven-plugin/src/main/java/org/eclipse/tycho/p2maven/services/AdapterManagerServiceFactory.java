package org.eclipse.tycho.p2maven.services;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.core.internal.runtime.AdapterManager;
import org.eclipse.core.runtime.IAdapterManager;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;

@Component(role = ServiceFactory.class, hint = "org.eclipse.core.runtime.IAdapterManager")
public class AdapterManagerServiceFactory implements ServiceFactory<IAdapterManager> {

	@Override
	public IAdapterManager getService(Bundle bundle, ServiceRegistration<IAdapterManager> registration) {
		return AdapterManager.getDefault();
	}

	@Override
	public void ungetService(Bundle bundle, ServiceRegistration<IAdapterManager> registration,
			IAdapterManager service) {
	}

}
