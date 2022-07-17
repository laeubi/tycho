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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.eclipse.core.internal.adapter.AdapterManagerListener;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.spi.IAgentServiceFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;

@Component(role = IProvisioningAgent.class)
public class DefaultProvisioningAgent implements IProvisioningAgent, Initializable {

	@Requirement
	private Logger log;

	@Requirement(hint = "plexus")
	private Framework framework;

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

	// TODO replace with properties file!
	private Set<String> requiredActiveBundles = Set.of(
			// see https://github.com/eclipse-equinox/p2/issues/100
			"org.eclipse.equinox.p2.publisher.eclipse", //
			// TODO java.lang.IllegalStateException: bundle
			// org.eclipse.equinox.p2.repositoryis not started
//			      at org.eclipse.equinox.internal.p2.repository.Activator.getContext (Activator.java:65)
//			      at org.eclipse.equinox.internal.p2.repository.helpers.AbstractRepositoryManager.getPreferences (AbstractRepositoryManager.java:506)
//			      at org.eclipse.equinox.internal.p2.repository.helpers.AbstractRepositoryManager.remember (AbstractRepositoryManager.java:896)
			"org.eclipse.equinox.p2.repository", //
			// TODO Caused by: java.lang.NullPointerException: Cannot invoke
			// "org.osgi.framework.BundleContext.getProperty(String)" because the return
			// value of
			// "org.eclipse.equinox.internal.p2.artifact.repository.Activator.getContext()"
			// is null
//				at org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository.<clinit>(CompositeArtifactRepository.java:46)
			"org.eclipse.equinox.p2.artifact.repository", //
			// TODO java.lang.NullPointerException: Cannot invoke
			// "org.osgi.framework.BundleContext.getDataFile(String)" because the return
			// value of
			// "org.eclipse.equinox.internal.p2.updatesite.Activator.getBundleContext()" is
			// null
//			at org.eclipse.equinox.internal.p2.updatesite.metadata.UpdateSiteMetadataRepositoryFactory.getLocalRepositoryLocation(UpdateSiteMetadataRepositoryFactory.java:40)
//			at org.eclipse.equinox.internal.p2.updatesite.artifact.UpdateSiteArtifactRepositoryFactory.loadRepository(UpdateSiteArtifactRepositoryFactory.java:89)
			"org.eclipse.equinox.p2.updatesite", //
			// TODO java.lang.NullPointerException: Cannot invoke
			// "org.eclipse.equinox.internal.security.auth.AuthPlugin.getEnvironmentInfoService()"
			// because the return value of
			// "org.eclipse.equinox.internal.security.auth.AuthPlugin.getDefault()" is null
//		    at org.eclipse.equinox.internal.security.storage.SecurePreferencesMapper.open (SecurePreferencesMapper.java:79)
//		    at org.eclipse.equinox.internal.security.storage.SecurePreferencesMapper.getDefault (SecurePreferencesMapper.java:56)
//		    at org.eclipse.equinox.security.storage.SecurePreferencesFactory.getDefault (SecurePreferencesFactory.java:52)
//		    at org.eclipse.equinox.internal.p2.repository.Credentials.forLocation (Credentials.java:117)
			"org.eclipse.equinox.security", //
			// TODO java.lang.NullPointerException: Cannot invoke
			// "org.eclipse.equinox.internal.p2.transport.ecf.Activator.getRetrieveFileTransferFactory()"
			// because the return value of
			// "org.eclipse.equinox.internal.p2.transport.ecf.Activator.getDefault()" is
			// null
//		    at org.eclipse.equinox.internal.p2.transport.ecf.FileReader.sendRetrieveRequest (FileReader.java:430)
//		    at org.eclipse.equinox.internal.p2.transport.ecf.FileReader.readInto (FileReader.java:386)
//		    at org.eclipse.equinox.internal.p2.transport.ecf.RepositoryTransport.download (RepositoryTransport.java:107)
//		    at org.eclipse.equinox.internal.p2.repository.helpers.AbstractRepositoryManager.handleRemoteIndexFile (AbstractRepo
			// TODO this currently also requires a bundle because it lookup the version
			// there...
			"org.eclipse.equinox.p2.transport.ecf", //
			// TODO ECF should not init this in the constructor but on first request...
			"org.eclipse.ecf",
			// TODO do not register service in activator as inner class...
			"org.eclipse.ecf.provider.filetransfer",
//				// see https://bugs.eclipse.org/bugs/show_bug.cgi?id=578387
			"org.eclipse.equinox.p2.director",
			// TODO
			"org.eclipse.ecf.identity", //
			"org.eclipse.equinox.p2.core");

	@Override
	public void initialize() throws InitializationException {
		for (Bundle bundle : framework.getBundleContext().getBundles()) {
			if (requiredActiveBundles.contains(bundle.getSymbolicName())) {
				try {
					bundle.start();
				} catch (BundleException e) {
					log.warn("Can't start required  active bundle " + bundle.getSymbolicName(), e);
				}
			}
		}
		// register adapter extensions... TODO can we simply start the activator?
		new AdapterManagerListener();
	}

}
