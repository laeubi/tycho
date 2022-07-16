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
package org.eclipse.tycho.p2maven.services;

import java.io.File;
import java.util.UUID;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.equinox.internal.p2.core.AgentLocation;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.spi.IAgentServiceFactory;

@Component(role = IAgentServiceFactory.class, hint = "org.eclipse.equinox.p2.core.IAgentLocation")
public class AgentLocationService implements IAgentServiceFactory {

	@Override
	public Object createService(IProvisioningAgent agent) {

		File file = new File(System.getProperty("java.io.tmpdir"), ".p2/" + UUID.randomUUID());
		file.mkdirs();
		return new AgentLocation(file.toURI());
	}

}
