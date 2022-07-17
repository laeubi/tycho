package org.eclipse.tycho.plexus.osgi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.osgi.framework.Bundle;
import org.osgi.framework.connect.FrameworkUtilHelper;
import org.osgi.framework.launch.Framework;

public class PlexusFrameworkUtilHelper implements FrameworkUtilHelper {

	private static List<Framework> frameworks = new ArrayList<Framework>();

	@Override
	public Optional<Bundle> getBundle(Class<?> classFromBundle) {
		String location = classFromBundle.getProtectionDomain().getCodeSource().getLocation().toString();
		for (Framework framework : frameworks) {
			for (Bundle bundle : framework.getBundleContext().getBundles()) {
				if (bundle.getLocation().contains(location)) {
					return Optional.of(bundle);
				}
			}
		}
		return FrameworkUtilHelper.super.getBundle(classFromBundle);
	}

	public static void addFramework(PlexusFramework plexusFramework) {
		frameworks.add(plexusFramework);
	}

}
