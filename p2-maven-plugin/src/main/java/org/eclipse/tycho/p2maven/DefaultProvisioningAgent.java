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
 *    Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.p2maven;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.sisu.equinox.EquinoxServiceFactory;

@Component(role = IProvisioningAgent.class)
public class DefaultProvisioningAgent implements IProvisioningAgent {

	@Requirement(hint = P2Plugin.HINT)
	private EquinoxServiceFactory frameworkFactory;

	IProvisioningAgent getForeignProvisioningAgent() {
		return frameworkFactory.getService(IProvisioningAgent.class);
	}

	@Override
	public Object getService(String serviceName) {
		return getForeignProvisioningAgent().getService(serviceName);
	}

	@Override
	public void registerService(String serviceName, Object service) {
		getForeignProvisioningAgent().registerService(serviceName, service);
	}

	@Override
	public void stop() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void unregisterService(String serviceName, Object service) {
		getForeignProvisioningAgent().unregisterService(serviceName, service);
	}
}
