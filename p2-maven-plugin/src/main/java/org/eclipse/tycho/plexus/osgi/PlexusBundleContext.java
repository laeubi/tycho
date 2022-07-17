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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.Logger;
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
import org.osgi.framework.Version;

class PlexusBundleContext implements BundleContext {

	private Map<String, List<PlexusServiceRegistration<?>>> registrationMap = new ConcurrentHashMap<>();

	private PlexusBundle plexusBundle;

	private PlexusBundle systemBundle;

	private Map<Long, PlexusBundle> bundleMap;

	PlexusBundleContext(PlexusBundle plexusBundle, PlexusBundle systemBundle) {
		this.plexusBundle = plexusBundle;
		this.systemBundle = systemBundle;
	}

	@Override
	public String getProperty(String key) {
		return System.getProperty(key);
	}

	@Override
	public PlexusBundle getBundle() {
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

	private PlexusBundle getSystemBundle() {
		if (systemBundle == null) {
			return getBundle();
		}
		return systemBundle;
	}

	@Override
	public Bundle[] getBundles() {
		PlexusBundle system = getSystemBundle();
		if (system == getBundle()) {
			Logger logger = system.adapt(Logger.class);
			synchronized (this) {
				if (bundleMap == null) {
					bundleMap = new HashMap<Long, PlexusBundle>();
					bundleMap.put(Constants.SYSTEM_BUNDLE_ID, getBundle());
					try {
						Enumeration<URL> resources = PlexusBundleContext.class.getClassLoader()
								.getResources(JarFile.MANIFEST_NAME);
						long id = Constants.SYSTEM_BUNDLE_ID + 1;
						while (resources.hasMoreElements()) {
							URL url = resources.nextElement();
							logger.debug("Scan " + url + " for bundle data...");
							Manifest manifest = readManifest(url);
							if (manifest == null) {
								continue;
							}
							try {
								Attributes mainAttributes = manifest.getMainAttributes();
								if (mainAttributes == null) {
									continue;
								}
								String bundleSymbolicName = mainAttributes.getValue(Constants.BUNDLE_SYMBOLICNAME);
								if (bundleSymbolicName == null) {
									continue;
								}
								bundleSymbolicName = bundleSymbolicName.split(";")[0];
								String version = mainAttributes.getValue(Constants.BUNDLE_VERSION);
								if (version == null) {
									version = "1";
								}
								logger.debug("Discovered bundle " + bundleSymbolicName + " with version " + version);
								Hashtable<String, String> headers = new Hashtable<>();
								for (Entry<Object, Object> entry : mainAttributes.entrySet()) {
									headers.put(entry.getKey().toString(), entry.getValue().toString());
								}
								PlexusBundle bundle = new PlexusBundle(id++, url.toString(), bundleSymbolicName,
										new Version(version), headers, system);
								bundleMap.put(bundle.getBundleId(), bundle);
							} catch (RuntimeException e) {
								logger.warn("Can't read " + url, e);
							}
						}
					} catch (IOException e) {
						logger.warn("Error reading bundle infos!", e);
					}
				}
				return bundleMap.values().toArray(Bundle[]::new);
			}
		}
		return system.getBundleContext().getBundles();
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
					.lookupList(clazz).stream().map(PlexusServiceReference::new);
			Stream<PlexusServiceReference<?>> dynamicServices = registrationMap
					.getOrDefault(clazz, Collections.emptyList()).stream().map(sr -> sr.serviceReference);
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

	private static Manifest readManifest(URL url) {
		try {
			try (InputStream stream = url.openStream()) {
				return new Manifest(stream);
			}
		} catch (IOException e) {
			return null;
		}
	}
}
