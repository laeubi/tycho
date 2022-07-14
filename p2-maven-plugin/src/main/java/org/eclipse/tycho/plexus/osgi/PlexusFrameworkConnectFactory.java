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
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Disposable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.connect.ConnectFrameworkFactory;
import org.osgi.framework.launch.Framework;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.util.tracker.ServiceTracker;

/**
 * The {@link PlexusFrameworkConnectFactory} creates a {@link Framework} using
 * the <a href=
 * "http://docs.osgi.org/specification/osgi.core/8.0.0/framework.connect.html#framework.connect">Connect
 * Specification</a> that allows to connect the plexus-world with the maven
 * world.
 */
@Component(role = PlexusFrameworkConnectFactory.class)
public class PlexusFrameworkConnectFactory implements Initializable, Disposable {

	@Requirement
	private Logger log;

	final Map<ClassLoader, Framework> frameworkMap = new HashMap<ClassLoader, Framework>();

	static PlexusFrameworkConnectFactory instance;

	public Framework getFramework(Class<?> contextClass) throws BundleException {
		return getFramework(contextClass.getClassLoader());
	}

	/**
	 * 
	 * 
	 * @param classloader
	 * @return get (or creates) the Framework that is made of the given classloader
	 * @throws BundleException
	 */
	public synchronized Framework getFramework(ClassLoader classloader) throws BundleException {
		Framework framework = frameworkMap.get(classloader);
		if (framework != null) {
			return framework;
		}
		Map<String, String> p = new HashMap<>();
		p.put(Constants.FRAMEWORK_STORAGE,
				System.getProperty("java.io.tmpdir") + File.separator + "plexus.osgi." + UUID.randomUUID());
		p.put(Constants.FRAMEWORK_BEGINNING_STARTLEVEL, "6");
		ServiceLoader<ConnectFrameworkFactory> sl = ServiceLoader.load(ConnectFrameworkFactory.class, classloader);
		ConnectFrameworkFactory factory = sl.iterator().next();
		PlexusModuleConnector connector = new PlexusModuleConnector(classloader, log);
		Framework osgiFramework = factory.newFramework(p, connector);
		osgiFramework.init(new FrameworkListener() {

			@Override
			public void frameworkEvent(FrameworkEvent event) {
				log.info(event.toString());
			}
		});
		frameworkMap.put(classloader, osgiFramework);
		connector.installBundles(osgiFramework.getBundleContext());
		osgiFramework.start();
		if (log.isDebugEnabled()) {
			printFrameworkState(osgiFramework);
		}
		return osgiFramework;

	}

	public void printFrameworkState(Framework framework) {
		Bundle[] bundles = framework.getBundleContext().getBundles();
		log.info("============ Framework Bundles ==================");
		Comparator<Bundle> bySymbolicName = Comparator.comparing(Bundle::getSymbolicName,
				String.CASE_INSENSITIVE_ORDER);
		Comparator<Bundle> byState = Comparator.comparingInt(Bundle::getState);
		Arrays.stream(bundles).sorted(byState.thenComparing(bySymbolicName)).forEachOrdered(bundle -> {
			log.info(toBundleState(bundle.getState()) + " | " + bundle.getSymbolicName());
		});
		ServiceTracker<ServiceComponentRuntime, ServiceComponentRuntime> st = new ServiceTracker<ServiceComponentRuntime, ServiceComponentRuntime>(
				framework.getBundleContext(), ServiceComponentRuntime.class, null);
		st.open();
		try {
			ServiceComponentRuntime componentRuntime = st.getService();
			if (componentRuntime != null) {
				log.info("============ Framework Components ==================");
				Collection<ComponentDescriptionDTO> descriptionDTOs = componentRuntime.getComponentDescriptionDTOs();
				Comparator<ComponentConfigurationDTO> byComponentName = Comparator
						.comparing(dto -> dto.description.name, String.CASE_INSENSITIVE_ORDER);
				Comparator<ComponentConfigurationDTO> byComponentState = Comparator.comparingInt(dto -> dto.state);
				descriptionDTOs.stream().flatMap(dto -> componentRuntime.getComponentConfigurationDTOs(dto).stream())
						.sorted(byComponentState.thenComparing(byComponentName)).forEachOrdered(dto -> {
							if (dto.state == ComponentConfigurationDTO.FAILED_ACTIVATION) {
								log.info(toComponentState(dto.state) + " | " + dto.description.name + " | "
										+ dto.failure);
							} else {
								log.info(toComponentState(dto.state) + " | " + dto.description.name);
							}
						});
			}
		} finally {
			st.close();
		}
	}

	private String toComponentState(int state) {
		switch (state) {
		case ComponentConfigurationDTO.ACTIVE:
			return "ACTIVE     ";
		case ComponentConfigurationDTO.FAILED_ACTIVATION:
			return "FAILED     ";
		case ComponentConfigurationDTO.SATISFIED:
			return "SATISFIED  ";
		case ComponentConfigurationDTO.UNSATISFIED_CONFIGURATION:
		case ComponentConfigurationDTO.UNSATISFIED_REFERENCE:
			return "UNSATISFIED";
		default:
			return String.valueOf(state);
		}
	}

	private String toBundleState(int state) {
		switch (state) {
		case Bundle.ACTIVE:
			return "ACTIVE   ";
		case Bundle.INSTALLED:
			return "INSTALLED";
		case Bundle.RESOLVED:
			return "RESOLVED ";
		case Bundle.STARTING:
			return "STARTING ";
		case Bundle.STOPPING:
			return "STOPPING ";
		default:
			return String.valueOf(state);
		}
	}

	@Override
	public void dispose() {
		frameworkMap.values().forEach(fw -> {
			String storage = fw.getBundleContext().getProperty(Constants.FRAMEWORK_STORAGE);
			try {
				fw.stop();
			} catch (BundleException e) {
			}
			try {
				fw.waitForStop(TimeUnit.SECONDS.toMillis(10));
			} catch (InterruptedException e) {
			}
			if (storage != null) {
				FileUtils.deleteQuietly(new File(storage));
			}
		});
		frameworkMap.clear();
		instance = null;
	}

	@Override
	public void initialize() throws InitializationException {
		instance = this;
	}

}
