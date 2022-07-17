package org.eclipse.tycho.plexus.osgi;

import java.util.Optional;

import org.osgi.framework.Bundle;
import org.osgi.framework.connect.FrameworkUtilHelper;
import org.osgi.framework.launch.Framework;

public class PlexusFrameworkUtilHelper implements FrameworkUtilHelper {

	@Override
	public Optional<Bundle> getBundle(Class<?> classFromBundle) {
		PlexusFrameworkFactory factory = PlexusFrameworkFactory.instance;
		if (factory != null) {
			Framework framework = factory.frameworkMap.get(classFromBundle.getClassLoader());
			if (framework != null) {
				String location = classFromBundle.getProtectionDomain().getCodeSource().getLocation().toString();
				for (Bundle bundle : framework.getBundleContext().getBundles()) {
					if (bundle.getLocation().contains(location)) {
						return Optional.of(bundle);
					}
				}
			}
		}
		return Optional.empty();
	}

}
