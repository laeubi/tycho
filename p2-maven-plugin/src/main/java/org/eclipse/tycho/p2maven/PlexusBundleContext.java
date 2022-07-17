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
package org.eclipse.tycho.p2maven;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Disposable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.eclipse.core.internal.adapter.AdapterManagerListener;
import org.eclipse.osgi.internal.framework.FilterImpl;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
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

//FIXME this should not be necessary at all see https://bugs.eclipse.org/bugs/show_bug.cgi?id=578387
@Component(role = BundleContext.class, hint = "plexus")
public class PlexusBundleContext implements BundleContext, Initializable, Disposable, Bundle {

	@Requirement
	private Logger log;

	@Requirement
	private PlexusContainer plexusContainer;

	@Requirement
	private LegacySupport legacySupport;

	private Map<String, List<PlexusServiceRegistration<?>>> registrationMap = new ConcurrentHashMap<>();

	private List<BundleActivator> legacyActivators = List.of(
			// see https://github.com/eclipse-equinox/p2/issues/100
			new org.eclipse.pde.internal.publishing.Activator(), //
// TODO	java.lang.IllegalStateException: bundle org.eclipse.equinox.p2.repositoryis not started
//		      at org.eclipse.equinox.internal.p2.repository.Activator.getContext (Activator.java:65)
//		      at org.eclipse.equinox.internal.p2.repository.helpers.AbstractRepositoryManager.getPreferences (AbstractRepositoryManager.java:506)
//		      at org.eclipse.equinox.internal.p2.repository.helpers.AbstractRepositoryManager.remember (AbstractRepositoryManager.java:896)
			new org.eclipse.equinox.internal.p2.repository.Activator(), //
//TODO Caused by: java.lang.NullPointerException: Cannot invoke "org.osgi.framework.BundleContext.getProperty(String)" because the return value of "org.eclipse.equinox.internal.p2.artifact.repository.Activator.getContext()" is null
//			at org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository.<clinit>(CompositeArtifactRepository.java:46)
			new org.eclipse.equinox.internal.p2.artifact.repository.Activator(), //
//TODO java.lang.NullPointerException: Cannot invoke "org.osgi.framework.BundleContext.getDataFile(String)" because the return value of "org.eclipse.equinox.internal.p2.updatesite.Activator.getBundleContext()" is null
//		at org.eclipse.equinox.internal.p2.updatesite.metadata.UpdateSiteMetadataRepositoryFactory.getLocalRepositoryLocation(UpdateSiteMetadataRepositoryFactory.java:40)
//		at org.eclipse.equinox.internal.p2.updatesite.artifact.UpdateSiteArtifactRepositoryFactory.loadRepository(UpdateSiteArtifactRepositoryFactory.java:89)
			new org.eclipse.equinox.internal.p2.updatesite.Activator(), //
//TODO java.lang.NullPointerException: Cannot invoke "org.eclipse.equinox.internal.security.auth.AuthPlugin.getEnvironmentInfoService()" because the return value of "org.eclipse.equinox.internal.security.auth.AuthPlugin.getDefault()" is null
//	    at org.eclipse.equinox.internal.security.storage.SecurePreferencesMapper.open (SecurePreferencesMapper.java:79)
//	    at org.eclipse.equinox.internal.security.storage.SecurePreferencesMapper.getDefault (SecurePreferencesMapper.java:56)
//	    at org.eclipse.equinox.security.storage.SecurePreferencesFactory.getDefault (SecurePreferencesFactory.java:52)
//	    at org.eclipse.equinox.internal.p2.repository.Credentials.forLocation (Credentials.java:117)
			new org.eclipse.equinox.internal.security.auth.AuthPlugin(), //
//TODO java.lang.NullPointerException: Cannot invoke "org.eclipse.equinox.internal.p2.transport.ecf.Activator.getRetrieveFileTransferFactory()" because the return value of "org.eclipse.equinox.internal.p2.transport.ecf.Activator.getDefault()" is null
//	    at org.eclipse.equinox.internal.p2.transport.ecf.FileReader.sendRetrieveRequest (FileReader.java:430)
//	    at org.eclipse.equinox.internal.p2.transport.ecf.FileReader.readInto (FileReader.java:386)
//	    at org.eclipse.equinox.internal.p2.transport.ecf.RepositoryTransport.download (RepositoryTransport.java:107)
//	    at org.eclipse.equinox.internal.p2.repository.helpers.AbstractRepositoryManager.handleRemoteIndexFile (AbstractRepo
//TODO this currently also requires a bundle because it lookup the version there...
			new org.eclipse.equinox.internal.p2.transport.ecf.Activator(), //
// TODO ECF should not init this in the constructor but on first request...
			new org.eclipse.ecf.internal.core.ECFPlugin(),
// TODO do not register service in activator as inner class...
			new org.eclipse.ecf.internal.provider.filetransfer.Activator(),
//			// see https://bugs.eclipse.org/bugs/show_bug.cgi?id=578387
			new org.eclipse.equinox.internal.p2.director.DirectorActivator(),
			// TODO
			new org.eclipse.ecf.internal.core.identity.Activator(), //
			new org.eclipse.equinox.internal.p2.core.Activator());

	@Override
	public String getProperty(String key) {
		return System.getProperty(key);
	}

	@Override
	public Bundle getBundle() {
		return this;
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
		return getBundle();
	}

	@Override
	public Bundle[] getBundles() {
		return new Bundle[0];
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
			Stream<PlexusServiceReference<?>> plexusServices = plexusContainer.lookupList(clazz).stream()
					.map(PlexusServiceReference::new);
			Stream<PlexusServiceReference<?>> dynamicServices = registrationMap
					.getOrDefault(clazz, Collections.emptyList()).stream()
					.map(sr -> sr.serviceReference);
			Stream<PlexusServiceReference<?>> factoryServices;
			try {
				// TODO more than one service factory!
				ServiceFactory<?> serviceFactory = plexusContainer.lookup(ServiceFactory.class, clazz);
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
			PlexusServiceReference<Object> reference = new PlexusServiceReference<>(plexusContainer.lookup(clazz));
			return reference;
		} catch (ComponentLookupException e) {
		}

		try {
			ServiceFactory<?> serviceFactory = plexusContainer.lookup(ServiceFactory.class, clazz);
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
		MavenSession session = legacySupport.getSession();
		if (session != null) {
			MavenProject project = session.getCurrentProject();
			if (project != null) {
				File directory = new File(project.getBuild().getDirectory());
				if (filename != null) {
					return new File(directory, filename);
				}
				return directory;
			}
		}
		return null;
	}

	@Override
	public Filter createFilter(String filter) throws InvalidSyntaxException {
		return FilterImpl.newInstance(filter);
	}

	@Override
	public Bundle getBundle(String location) {
		return getBundle();
	}

	@Override
	public void initialize() throws InitializationException {
		for (BundleActivator bundleActivator : legacyActivators) {
			try {
				bundleActivator.start(this);
			} catch (Exception e) {
				e.printStackTrace();
				log.warn("Can't init " + bundleActivator.getClass() + "! (" + e + ")");
			}
		}
		// register adapter extensions...
		new AdapterManagerListener();
	}

	@Override
	public void dispose() {
		for (BundleActivator bundleActivator : legacyActivators) {
			try {
				bundleActivator.stop(this);
			} catch (Exception e) {
				log.warn("Can't init " + bundleActivator.getClass() + "! (" + e + ")");
			}
		}
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

	@Override
	public int compareTo(Bundle o) {
		return 0;
	}

	@Override
	public int getState() {
		return Bundle.ACTIVE;
	}

	@Override
	public void start(int options) throws BundleException {
		throw new BundleException("Not possible");
	}

	@Override
	public void start() throws BundleException {
		throw new BundleException("Not possible");
	}

	@Override
	public void stop(int options) throws BundleException {
		throw new BundleException("Not possible");
	}

	@Override
	public void stop() throws BundleException {
		throw new BundleException("Not possible");

	}

	@Override
	public void update(InputStream input) throws BundleException {
		throw new BundleException("Not possible");

	}

	@Override
	public void update() throws BundleException {
		throw new BundleException("Not possible");

	}

	@Override
	public void uninstall() throws BundleException {
		throw new BundleException("Not possible");
	}

	@Override
	public Dictionary<String, String> getHeaders() {
		return new Hashtable<String, String>();
	}

	@Override
	public long getBundleId() {
		return 0;
	}

	@Override
	public String getLocation() {
		return null;
	}

	@Override
	public ServiceReference<?>[] getRegisteredServices() {
		return new ServiceReference<?>[0];
	}

	@Override
	public ServiceReference<?>[] getServicesInUse() {
		return new ServiceReference<?>[0];
	}

	@Override
	public boolean hasPermission(Object permission) {
		return true;
	}

	@Override
	public URL getResource(String name) {
		return getClass().getClassLoader().getResource(name);
	}

	@Override
	public Dictionary<String, String> getHeaders(String locale) {
		return getHeaders();
	}

	@Override
	public String getSymbolicName() {
		return null;
	}

	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		return getClass().getClassLoader().loadClass(name);
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		return getClass().getClassLoader().getResources(name);
	}

	@Override
	public Enumeration<String> getEntryPaths(String path) {
		return Collections.emptyEnumeration();
	}

	@Override
	public URL getEntry(String path) {
		return null;
	}

	@Override
	public long getLastModified() {
		return 0;
	}

	@Override
	public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
		return Collections.emptyEnumeration();
	}

	@Override
	public BundleContext getBundleContext() {
		return this;
	}

	@Override
	public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
		return Collections.emptyMap();
	}

	@Override
	public Version getVersion() {
		return new Version("1");
	}

	@Override
	public <A> A adapt(Class<A> type) {
		return null;
	}

}
