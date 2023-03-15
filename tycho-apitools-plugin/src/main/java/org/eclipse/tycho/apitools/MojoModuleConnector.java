package org.eclipse.tycho.apitools;

import java.io.File;
import java.util.Map;
import java.util.Optional;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;
import org.osgi.framework.connect.ConnectModule;
import org.osgi.framework.connect.ModuleConnector;

public class MojoModuleConnector implements ModuleConnector {

	public MojoModuleConnector() {
	}

	@Override
	public void initialize(File storage, Map<String, String> configuration) {

	}

	@Override
	public Optional<ConnectModule> connect(String location) throws BundleException {
		return Optional.empty();
	}

	@Override
	public Optional<BundleActivator> newBundleActivator() {
		return Optional.empty();
	}

}
