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
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Disposable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.eclipse.core.internal.registry.ExtensionRegistry;
import org.eclipse.core.internal.registry.RegistryProviderFactory;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.spi.IRegistryProvider;
import org.eclipse.core.runtime.spi.RegistryContributor;
import org.eclipse.core.runtime.spi.RegistryStrategy;
import org.osgi.framework.Constants;

@Component(role = IRegistryProvider.class)
public class DefaultRegistryProvider implements IRegistryProvider, Initializable, Disposable {

	@Requirement
	private Logger log;

	private static final Collection<String> EXTENSION_DESCRIPTORS = List.of("plugin.xml", "fragment.xml");
	private IExtensionRegistry registry;

	@Override
	public IExtensionRegistry getRegistry() {
		if (registry == null) {

			registry = new ExtensionRegistry(new ClasspathRegistryStrategy(null, null, log), null, null);
		}
		return registry;

	}

	// TODO contribute this to equinox?
	private static class ClasspathRegistryStrategy extends RegistryStrategy {

		private Logger log;

		public ClasspathRegistryStrategy(File[] storageDirs, boolean[] cacheReadOnly, Logger log) {
			super(storageDirs, cacheReadOnly);
			this.log = log;
		}

		@Override
		public void onStart(IExtensionRegistry registry, boolean loadedFromCache) {
			super.onStart(registry, loadedFromCache);
			try {
				for (String descriptorFile : EXTENSION_DESCRIPTORS) {
					log.debug("Scanning for " + descriptorFile + " contributions...");
					Enumeration<URL> resources = DefaultRegistryProvider.class.getClassLoader()
							.getResources(descriptorFile);
					int id = 0;
					while (resources.hasMoreElements()) {
						URL url = resources.nextElement();
						log.debug("Processing " + url + " ...");
						Manifest manifest = readManifest(url);
						if (manifest == null) {
							continue;
						}
						String bundleId = null;
						String hostId = null;
						String hostName = null;
						// TODO handle fragments?
						bundleId = manifest.getMainAttributes().getValue(Constants.BUNDLE_SYMBOLICNAME).split(";")[0];
						if (bundleId == null) {
							continue;
						}
						RegistryContributor contributor = new RegistryContributor(String.valueOf(id++), bundleId,
								hostId, hostName);
						try (InputStream stream = url.openStream()) {
							if (registry.addContribution(stream, contributor, false, null, null, null)) {
								IExtensionPoint[] points = registry.getExtensionPoints(contributor);
								log.debug(points.length + " extension points discovered...");
							} else {
								log.error("Contributions can't be processed for " + url);
							}
						}
					}
				}

			} catch (IOException e) {
				log.error("Scanning for contributions failed!", e);
			}
		}

		private Manifest readManifest(URL base) {
			try {
				URL url = new URL(base, JarFile.MANIFEST_NAME);
				try (InputStream stream = url.openStream()) {
					return new Manifest(stream);
				}
			} catch (IOException e) {
				return null;
			}
		}

	}

	@Override
	public void initialize() throws InitializationException {
		try {
			if (RegistryProviderFactory.getDefault() == null) {
				RegistryProviderFactory.setDefault(this);
			}
		} catch (CoreException e) {
			throw new InitializationException("can't set default provider", e);
		}

	}

	@Override
	public void dispose() {
		if (RegistryProviderFactory.getDefault() == this) {
			RegistryProviderFactory.releaseDefault();
		}
	}

}
