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
package org.eclipse.tycho.plexus.osgi;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.codehaus.plexus.logging.Logger;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.connect.ConnectContent;
import org.osgi.framework.connect.ConnectModule;
import org.osgi.framework.connect.ModuleConnector;

/**
 * The PlexusModuleConnector scans a linear classpath for bundles and install
 * them as {@link ConnectContent} into the given {@link BundleContext}
 */
final class PlexusModuleConnector implements ModuleConnector {

	private static final Set<String> BLACK_LIST = Set.of("org.eclipse.osgi", "org.eclipse.osgi; singleton:=true");

	private static final Set<String> AUTO_START = Set.of("org.apache.felix.scr");

	private static final String JAR_FILE_PREFIX = "jar:file:";

	private ClassLoader classloader;

	private Map<String, PlexusConnectContent> modulesMap = new HashMap<>();

	private Logger logger;

	private File storage;

	public PlexusModuleConnector(ClassLoader classloader, Logger logger) {
		this.classloader = classloader;
		this.logger = logger;
	}

	public void installBundles(BundleContext bundleContext) {
		Enumeration<URL> resources;
		try {
			resources = classloader.getResources(JarFile.MANIFEST_NAME);
		} catch (IOException e) {
			logger.error("Can't load resources for classloader " + classloader);
			return;
		}
		while (resources.hasMoreElements()) {
			String location = resources.nextElement().toExternalForm();
			logger.debug("Scan " + location + " for bundle data...");
			if (location.startsWith(JAR_FILE_PREFIX)) {
				String name = location.substring(JAR_FILE_PREFIX.length()).split("!")[0];
				try {
					JarFile jarFile = new JarFile(name);
					try {
						Manifest manifest = jarFile.getManifest();
						Attributes mainAttributes = manifest.getMainAttributes();
						if (mainAttributes == null) {
							jarFile.close();
							continue;
						}
						String bundleSymbolicName = mainAttributes.getValue(Constants.BUNDLE_SYMBOLICNAME);
						if (bundleSymbolicName == null || BLACK_LIST.contains(bundleSymbolicName)) {
							jarFile.close();
							continue;
						}
						logger.debug("Discovered bundle " + bundleSymbolicName + " @ " + name);
						modulesMap.put(name, new PlexusConnectContent(jarFile, classloader));
						try {
							Bundle bundle = bundleContext.installBundle(name);
							if (AUTO_START.contains(bundle.getSymbolicName())) {
								try {
									bundle.start();
								} catch (BundleException e) {
									logger.warn("Can't auto-start bundle " + bundle.getSymbolicName(), e);
								}
							}
						} catch (BundleException e) {
							logger.warn("Can't install bundle at " + name, e);
							jarFile.close();
							modulesMap.remove(name);
						}
					} catch (IOException e) {
						jarFile.close();
					}
				} catch (IOException e) {
					logger.warn("Can't open jar at " + name, e);
				}
			}
		}
	}

	@Override
	public Optional<ConnectModule> connect(String location) throws BundleException {
		return Optional.ofNullable(modulesMap.get(location));
	}

	@Override
	public void initialize(File storage, Map<String, String> configuration) {
		this.storage = storage;
	}

	@Override
	public Optional<BundleActivator> newBundleActivator() {
		return Optional.empty();
	}

	public File getStorage() {
		return storage;
	}
}