package org.eclipse.tycho.repository.plugin;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

import org.eclipse.equinox.p2.publisher.AbstractPublisherApplication;
import org.eclipse.equinox.p2.publisher.IPublisherAction;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

public final class P2Runner extends AbstractPublisherApplication implements Callable<String>, Serializable {

	private List<File> files;

	public P2Runner(List<File> files) {
		this.files = files;
	}

	@Override
	public String call() throws Exception {
		Bundle bundle = FrameworkUtil.getBundle(P2Runner.class);
		System.out.println("Hello from bundle " + bundle);
		Bundle[] bundles = bundle.getBundleContext().getBundles();
		for (Bundle b : bundles) {
			System.out.println(b);
		}
		System.out.println("------");
		for (File file : files) {
			System.out.println(file);
		}
		System.out.println("------");
		File destination = new File("/tmp/p2test");
		destination.mkdirs();
		Builder<String> arguments = Stream.builder();
		arguments.add("-artifactRepository");
		arguments.add(destination.toURI().toString());
		arguments.add("-metadataRepository");
		arguments.add(destination.toURI().toString());
		run(arguments.build().toArray(String[]::new));
		return "Hello";
	}

	@Override
	protected IPublisherAction[] createActions() {
		List<IPublisherAction> actions = new ArrayList<IPublisherAction>();
		for (File file : files) {
			actions.add(new BundlesAction(new File[] { file }));
		}
		return actions.toArray(IPublisherAction[]::new);
	}

}