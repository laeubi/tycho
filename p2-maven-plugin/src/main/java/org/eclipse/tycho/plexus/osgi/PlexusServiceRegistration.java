package org.eclipse.tycho.plexus.osgi;

import java.util.Dictionary;
import java.util.Enumeration;

import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

final class PlexusServiceRegistration<S> implements ServiceRegistration<S> {

	PlexusServiceReference<S> serviceReference;

	public PlexusServiceRegistration(S service, Dictionary<String, ?> properties) {
		serviceReference = new PlexusServiceReference<S>(service);
		if (properties != null) {
			Enumeration<String> keys = properties.keys();
			while (keys.hasMoreElements()) {
				String key = keys.nextElement();
				serviceReference.properties.put(key, properties.get(key));

			}
		}
	}

	@Override
	public ServiceReference<S> getReference() {
		return serviceReference;
	}

	@Override
	public void setProperties(Dictionary<String, ?> properties) {

	}

	@Override
	public void unregister() {
		// TODO
	}

}