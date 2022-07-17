package org.eclipse.tycho.plexus.osgi;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;

final class PlexusServiceReference<S> implements ServiceReference<S> {

	final Hashtable<String, Object> properties = new Hashtable<String, Object>();
	final S service;

	PlexusServiceReference(S service) {
		this.service = service;
	}

	@Override
	public Object getProperty(String key) {
		if (Constants.SERVICE_ID.equals(key)) {
			// TODO assign an id for each service
			return 0L;
		}
		return properties.get(key);
	}

	@Override
	public String[] getPropertyKeys() {
		return new String[0];
	}

	@Override
	public Bundle getBundle() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Bundle[] getUsingBundles() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isAssignableTo(Bundle bundle, String className) {
		return true;
	}

	@Override
	public int compareTo(Object reference) {
		return 0;
	}

	@Override
	public Dictionary<String, Object> getProperties() {
		return properties;
	}

	@Override
	public <A> A adapt(Class<A> type) {
		return null;
	}

}