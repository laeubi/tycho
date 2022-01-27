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
package org.eclipse.tycho.p2;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Disposable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.eclipse.equinox.internal.p2.director.DirectorActivator;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.osgi.framework.BundleContext;

@Component(role = IProvisioningAgent.class, hint = "plexus")
public class PlexusProvisioningAgent implements IProvisioningAgent, Disposable, Initializable {

	private ConcurrentMap<String, Object> serviceMap = new ConcurrentHashMap<String, Object>();

	@Requirement
	private Logger log;

	@Requirement
	private PlexusContainer container;

	@Requirement(hint = "plexus")
	private BundleContext bundleContext;

	@Override
	public Object getService(String serviceName) {

		return serviceMap.computeIfAbsent(serviceName, name -> {
			log.info("Lookup service " + name);
			try {
				return container.lookup(name, "p2");
			} catch (ComponentLookupException e) {
			}
			try {
				return container.lookup(name);
			} catch (ComponentLookupException e) {
				return null;
			}
		});
	}

	@Override
	public <T> T getService(Class<T> key) {
		return key.cast(serviceMap.computeIfAbsent(key.getName(), name -> {
			log.info("Lookup service " + name);
			try {
				return container.lookup(key, name, "p2");
			} catch (ComponentLookupException e) {
			}
			try {
				return container.lookup(key);
			} catch (ComponentLookupException e) {
				return null;
			}
		}));
	}

	@Override
	public void registerService(String serviceName, Object service) {
		serviceMap.put(serviceName, service);
	}

	@Override
	public void stop() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void unregisterService(String serviceName, Object service) {
		serviceMap.remove(serviceName);
	}

	@Override
	public void dispose() {
//		DirectorActivator.context = null;
		serviceMap.clear();
	}

	@Override
	public void initialize() throws InitializationException {
		// FIXME see https://bugs.eclipse.org/bugs/show_bug.cgi?id=578365
		DirectorActivator.context = bundleContext;
	}

}
