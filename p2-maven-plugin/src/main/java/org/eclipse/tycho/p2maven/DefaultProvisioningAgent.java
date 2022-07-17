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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.spi.IAgentServiceFactory;
import org.osgi.framework.launch.Framework;

@Component(role = IProvisioningAgent.class)
public class DefaultProvisioningAgent implements IProvisioningAgent {

	@Requirement
	private Logger log;

	// we require an OSGi framework, even if we don't use it directly
	@Requirement(hint = "plexus")
	private Framework osgi;

	@Requirement(role = IAgentServiceFactory.class)
	private Map<String, IAgentServiceFactory> factoryMap;

	private Map<String, Object> services = new ConcurrentHashMap<String, Object>();

	@Override
	public Object getService(String serviceName) {
		return services.computeIfAbsent(serviceName, role -> {
			IAgentServiceFactory serviceFactory = factoryMap.get(role);
			if (serviceFactory != null) {
				return serviceFactory.createService(DefaultProvisioningAgent.this);
			}
			log.error("Agent service " + serviceName + " not found!");
			return null;
		});
	}

	@Override
	public void registerService(String serviceName, Object service) {
		services.put(serviceName, service);
	}

	@Override
	public void stop() {

	}

	@Override
	public void unregisterService(String serviceName, Object service) {
		services.remove(serviceName);
	}

}
