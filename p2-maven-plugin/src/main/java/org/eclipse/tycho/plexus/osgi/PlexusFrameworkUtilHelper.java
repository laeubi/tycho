package org.eclipse.tycho.plexus.osgi;

import java.util.Optional;

import org.osgi.framework.Bundle;
import org.osgi.framework.connect.FrameworkUtilHelper;
import org.osgi.framework.launch.Framework;

public class PlexusFrameworkUtilHelper implements FrameworkUtilHelper {

	@Override
	public Optional<Bundle> getBundle(Class<?> classFromBundle) {
		PlexusFrameworkConnectFactory factory = PlexusFrameworkConnectFactory.instance;
		if (factory != null) {
			Framework framework = factory.frameworkMap.get(classFromBundle.getClassLoader());
			if (framework != null) {
				String location = classFromBundle.getProtectionDomain().getCodeSource().getLocation().toString();
				for (Bundle bundle : framework.getBundleContext().getBundles()) {
					String bundleLocation = bundle.getLocation();
					if (locationsMatch(location, bundleLocation)) {
						return Optional.of(bundle);
					}
				}
			}
		}
		return Optional.empty();
	}

	private static boolean locationsMatch(String classLocation, String bundleLocation) {
		return classLocation.endsWith(bundleLocation);
	}

}
