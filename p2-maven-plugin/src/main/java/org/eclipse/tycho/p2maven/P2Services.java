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

import java.util.Set;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Disposable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.tycho.plexus.osgi.PlexusFrameworkConnectFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.osgi.util.tracker.ServiceTracker;

@Component(role = P2Services.class)
public class P2Services implements Initializable, Disposable {

	private static final Set<String> REQUIRED_ACTIVE_BUNDLES = Set.of(//
			"org.apache.felix.scr", //
			"org.eclipse.core.runtime",//
			"org.eclipse.ecf", //
			"org.eclipse.ecf.filetransfer", //
			"org.eclipse.ecf.provider.filetransfer", //
			"org.eclipse.ecf.identity" //
	);

	@Requirement
	private Logger log;

	@Requirement
	private PlexusFrameworkConnectFactory frameworkFactory;

	private ServiceTracker<IProvisioningAgent, IProvisioningAgent> provisioningAgentTracker;

	public IProvisioningAgent getProvisioningAgent() {
		return provisioningAgentTracker.getService();
	}

	@Override
	public void initialize() throws InitializationException {
		try {
			Framework fw = frameworkFactory.getFramework(P2Plugin.class.getClassLoader());
			for (Bundle bundle : fw.getBundleContext().getBundles()) {
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
			provisioningAgentTracker = new ServiceTracker<>(
					fw.getBundleContext(), IProvisioningAgent.class, null);
			provisioningAgentTracker.open();
		} catch (BundleException e) {
			throw new InitializationException("can't get OSGi framework!", e);
		}

	}

	@Override
	public void dispose() {
		provisioningAgentTracker.close();

	}
}
