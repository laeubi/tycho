/*******************************************************************************
 * Copyright (c) 2022 Christoph Läubrich and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.plexus.osgi;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.osgi.internal.framework.FilterImpl;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

class PlexusBundleContext implements BundleContext {

	private Map<String, List<PlexusServiceRegistration<?>>> registrationMap = new ConcurrentHashMap<>();

	private PlexusBundle plexusBundle;

	private PlexusBundle systemBundle;

	PlexusBundleContext(PlexusBundle plexusBundle, PlexusBundle systemBundle) {
		this.plexusBundle = plexusBundle;
		this.systemBundle = systemBundle;
	}

	@Override
	public String getProperty(String key) {
		return System.getProperty(key);
	}

	@Override
	public Bundle getBundle() {
		return plexusBundle;
	}

	@Override
	public Bundle installBundle(String location, InputStream input) throws BundleException {
		throw new BundleException("this context does not support installations");
	}

	@Override
	public Bundle installBundle(String location) throws BundleException {
		throw new BundleException("this context does not support installations");
	}

	@Override
	public Bundle getBundle(long id) {
		if (id == Constants.SYSTEM_BUNDLE_ID) {
			return getSystemBundle();
		}
		for (Bundle bundle : getBundles()) {
			if (bundle.getBundleId() == id) {
				return bundle;
			}
		}
		return null;
	}

	private Bundle getSystemBundle() {
		if (systemBundle == null) {
			return getBundle();
		}
		return systemBundle;
	}

	@Override
	public Bundle[] getBundles() {
		if (systemBundle == null) {
			// TODO discover more....
			return new Bundle[] { getSystemBundle() };
		}
		return getBundle(0).getBundleContext().getBundles();
	}

	@Override
	public void addServiceListener(ServiceListener listener, String filter) throws InvalidSyntaxException {

	}

	@Override
	public void addServiceListener(ServiceListener listener) {

	}

	@Override
	public void removeServiceListener(ServiceListener listener) {

	}

	@Override
	public void addBundleListener(BundleListener listener) {

	}

	@Override
	public void removeBundleListener(BundleListener listener) {

	}

	@Override
	public void addFrameworkListener(FrameworkListener listener) {

	}

	@Override
	public void removeFrameworkListener(FrameworkListener listener) {

	}

	@Override
	public ServiceRegistration<?> registerService(String[] clazzes, Object service, Dictionary<String, ?> properties) {
		PlexusServiceRegistration<?> registration = new PlexusServiceRegistration<>(service, properties);
		for (String clazz : clazzes) {
			registrationMap.computeIfAbsent(clazz, nil -> new CopyOnWriteArrayList<>()).add(registration);
		}
		return registration;
	}

	@Override
	public ServiceRegistration<?> registerService(String clazz, Object service, Dictionary<String, ?> properties) {
		return registerService(new String[] { clazz }, service, properties);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <S> ServiceRegistration<S> registerService(Class<S> clazz, S service, Dictionary<String, ?> properties) {
		return (ServiceRegistration<S>) registerService(clazz.getName(), service, properties);
	}

	@Override
	public <S> ServiceRegistration<S> registerService(Class<S> clazz, ServiceFactory<S> factory,
			Dictionary<String, ?> properties) {
		// TODO!
		throw new IllegalStateException("Not implemented");
	}

	@Override
	public ServiceReference<?>[] getServiceReferences(String clazz, String filter) {
		try {
			Stream<PlexusServiceReference<?>> plexusServices = getBundle().adapt(PlexusContainer.class)
					.lookupList(clazz).stream()
					.map(PlexusServiceReference::new);
			Stream<PlexusServiceReference<?>> dynamicServices = registrationMap
					.getOrDefault(clazz, Collections.emptyList()).stream()
					.map(sr -> sr.serviceReference);
			Stream<PlexusServiceReference<?>> factoryServices;
			try {
				// TODO more than one service factory!
				ServiceFactory<?> serviceFactory = getBundle().adapt(PlexusContainer.class).lookup(ServiceFactory.class,
						clazz);
				PlexusServiceReference<Object> reference = new PlexusServiceReference<>(
						serviceFactory.getService(getBundle(), new PlexusServiceRegistration<>(null, null)));
				factoryServices = Stream.of(reference);
			} catch (ComponentLookupException e) {
				factoryServices = Stream.empty();
			}
			// TODO service factories?
			Stream<?> allServices = Stream.concat(dynamicServices, Stream.concat(plexusServices, factoryServices));
			if (filter != null) {
				// FIXME some kind of filter support?
			}
			// TODO sorting
			ServiceReference<?>[] serviceReferences = allServices.toArray(ServiceReference[]::new);
			return serviceReferences;
		} catch (ComponentLookupException e) {
			return new ServiceReference<?>[0];
		}
	}

	@Override
	public ServiceReference<?>[] getAllServiceReferences(String clazz, String filter) {
		return getServiceReferences(clazz, filter);
	}

	@Override
	public ServiceReference<?> getServiceReference(String clazz) {
		for (PlexusServiceRegistration<?> registration : registrationMap.getOrDefault(clazz, Collections.emptyList())) {
			return registration.getReference();
		}
		try {
			PlexusServiceReference<Object> reference = new PlexusServiceReference<>(
					getBundle().adapt(PlexusContainer.class).lookup(clazz));
			return reference;
		} catch (ComponentLookupException e) {
		}

		try {
			ServiceFactory<?> serviceFactory = getBundle().adapt(PlexusContainer.class).lookup(ServiceFactory.class,
					clazz);
			PlexusServiceReference<Object> reference = new PlexusServiceReference<>(
					serviceFactory.getService(getBundle(), new PlexusServiceRegistration<>(null, null)));
			return reference;
		} catch (ComponentLookupException e) {
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <S> ServiceReference<S> getServiceReference(Class<S> clazz) {
		return (ServiceReference<S>) getServiceReference(clazz.getName());
	}

	@SuppressWarnings("unchecked")
	@Override
	public <S> Collection<ServiceReference<S>> getServiceReferences(Class<S> clazz, String filter)
			throws InvalidSyntaxException {
		@SuppressWarnings("rawtypes")
		ServiceReference[] references = getServiceReferences(clazz.getName(), filter);
		return Arrays.asList(references);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public <S> S getService(ServiceReference<S> reference) {
		if (reference instanceof PlexusServiceReference<?>) {
			return (S) ((PlexusServiceReference) reference).service;
		}
		return null;
	}

	@Override
	public boolean ungetService(ServiceReference<?> reference) {
		return true;
	}

	@Override
	public <S> ServiceObjects<S> getServiceObjects(ServiceReference<S> reference) {
		throw new IllegalStateException("this is not OSGi!");
	}

	@Override
	public File getDataFile(String filename) {
		return plexusBundle.getDataFile(filename);
	}

	@Override
	public Filter createFilter(String filter) throws InvalidSyntaxException {
		return FilterImpl.newInstance(filter);
	}

	@Override
	public Bundle getBundle(String location) {
		return getBundle();
	}

	private static final class PlexusServiceRegistration<S> implements ServiceRegistration<S> {

		private PlexusServiceReference<S> serviceReference;

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

	private static final class PlexusServiceReference<S> implements ServiceReference<S> {

		private final Hashtable<String, Object> properties = new Hashtable<String, Object>();
		private final S service;

		public PlexusServiceReference(S service) {
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
}
