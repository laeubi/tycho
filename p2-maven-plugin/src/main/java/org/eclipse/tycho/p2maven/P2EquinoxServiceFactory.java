/*******************************************************************************
 * Copyright (c) 2022 Christoph Läubrich and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.p2maven;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Disposable;
import org.eclipse.sisu.equinox.EquinoxServiceFactory;
import org.eclipse.sisu.equinox.embedder.EmbeddedEquinox;
import org.eclipse.sisu.equinox.embedder.EquinoxLifecycleListener;
import org.eclipse.tycho.plexus.osgi.PlexusFrameworkConnectFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.launch.Framework;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Component that makes the OSGi-Connect framework available to the sisu-equinox
 */
@Component(role = EquinoxServiceFactory.class, hint = P2Plugin.HINT)
public class P2EquinoxServiceFactory implements EquinoxServiceFactory, EmbeddedEquinox, Disposable {

	private static final Set<String> REQUIRED_ACTIVE_BUNDLES = Set.of(//
			"org.apache.felix.scr", //
			"org.eclipse.core.runtime", //
			"org.eclipse.ecf", //
			"org.eclipse.ecf.filetransfer", //
			"org.eclipse.ecf.provider.filetransfer", //
			"org.eclipse.ecf.identity" //
	);

	@Requirement
	private Logger log;

	@Requirement(role = EquinoxLifecycleListener.class)
	private Map<String, EquinoxLifecycleListener> lifecycleListeners;

	@Requirement
	private PlexusFrameworkConnectFactory frameworkFactory;

	private Framework framework;

	private Map<Class<?>, ServiceTracker<?, ?>> trackerMap = new ConcurrentHashMap<Class<?>, ServiceTracker<?, ?>>();

	private List<ServiceRegistration<?>> registeredServices = new ArrayList<ServiceRegistration<?>>();

	@Override
	public EquinoxServiceFactory getServiceFactory() {
		return this;
	}

	@Override
	public <T> T getService(Class<T> clazz) {
		return getService(clazz, null);
	}

	@Override
	public <T> T getService(Class<T> clazz, String filter) {
		try {
			ServiceTracker<?, ?> serviceTracker = trackerMap.computeIfAbsent(clazz, cls -> {
				ServiceTracker<?, ?> tracker = new ServiceTracker<>(getFramework().getBundleContext(), cls, null);
				tracker.open();
				return tracker;
			});
			if (filter == null) {
				return clazz.cast(serviceTracker.getService());
			}
			Filter f = getFramework().getBundleContext().createFilter(filter);
			for (var entry : serviceTracker.getTracked().entrySet()) {
				if (f.match(entry.getKey())) {
					return clazz.cast(entry.getValue());
				}
			}
			return null;
		} catch (InvalidSyntaxException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}

	@Override
	public <T> void registerService(Class<T> clazz, T service) {
		registerService(clazz, service, null);
	}

	@Override
	public <T> void registerService(Class<T> clazz, T service, Dictionary<String, ?> properties) {
		// TODO change to return the registration so caller has a way to get rid of the
		// service or update properties...
		registeredServices.add(getFramework().getBundleContext().registerService(clazz, service, properties));
	}

	@Override
	public void dispose() {
		for (ServiceRegistration<?> serviceRegistration : registeredServices) {
			try {
				serviceRegistration.unregister();
			} catch (IllegalStateException e) {
				// ignore then..
			}
		}
		trackerMap.values().forEach(ServiceTracker::close);
		registeredServices.clear();
		trackerMap.clear();
	}

	private synchronized Framework getFramework() {
		if (framework == null) {
			try {
				framework = frameworkFactory.getFramework(P2Plugin.class);
				for (Bundle bundle : getFramework().getBundleContext().getBundles()) {
					String symbolicName = bundle.getSymbolicName();
					if (symbolicName.startsWith("org.eclipse.equinox.")
							|| REQUIRED_ACTIVE_BUNDLES.contains(symbolicName)) {
						try {
							bundle.start();
						} catch (BundleException e) {
							String message = "Can't start required active bundle " + symbolicName + ": "
									+ e.getMessage();
							if (log.isDebugEnabled()) {
								log.error(message, e);
							} else {
								log.warn(message);
							}
						}
					}
				}
				for (EquinoxLifecycleListener listener : lifecycleListeners.values()) {
					listener.afterFrameworkStarted(this);
				}
			} catch (BundleException e) {
				throw new RuntimeException("Can't init framework", e);
			}
		}
		return framework;
	}

}
