package org.eclipse.tycho.plexus.osgi;

import java.io.File;
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
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.connect.ConnectFrameworkFactory;
import org.osgi.framework.launch.Framework;

/**
 * The {@link PlexusFrameworkFactory} creates a {@link Framework} using the
 * <a href=
 * "http://docs.osgi.org/specification/osgi.core/8.0.0/framework.connect.html#framework.connect">Connect
 * Specification</a> that allows to connect the plexus-world with the maven
 * world.
 */
@Component(role = PlexusFrameworkFactory.class)
public class PlexusFrameworkFactory implements Initializable, Disposable {

	private static final String FRAMEWORK_STORAGE = "org.osgi.framework.storage";

	@Requirement
	private Logger log;

	final Map<ClassLoader, Framework> frameworkMap = new HashMap<ClassLoader, Framework>();

	static PlexusFrameworkFactory instance;

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
		p.put(FRAMEWORK_STORAGE,
				System.getProperty("java.io.tmpdir") + File.separator + "plexus.osgi." + UUID.randomUUID());
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
		connector.installBundles(osgiFramework.getBundleContext());
		osgiFramework.start();
		Bundle[] bundles = osgiFramework.getBundleContext().getBundles();
		for (Bundle bundle : bundles) {
			System.out.println(
					bundle.getBundleId() + " | " + toBundleState(bundle.getState()) + " | "
							+ bundle.getSymbolicName());
		}
		return osgiFramework;

	}

	private String toBundleState(int state) {
		switch (state) {
		case Bundle.ACTIVE:
			return "ACTIVE";
		case Bundle.INSTALLED:
			return "INSTALLED";
		case Bundle.RESOLVED:
			return "RESOLVED";
		case Bundle.STARTING:
			return "STARTING";
		case Bundle.STOPPING:
			return "STOPPING";
		case Bundle.UNINSTALLED:
			return "UNINSTALLED";
		default:
			break;
		}
		return String.valueOf(state);
	}

	@Override
	public void dispose() {
		instance = null;
		frameworkMap.values().forEach(fw -> {
			String storage = fw.getBundleContext().getProperty(FRAMEWORK_STORAGE);
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
	}

	@Override
	public void initialize() throws InitializationException {
		instance = this;
	}

}
