package org.eclipse.tycho.plexus.osgi;


import java.util.Hashtable;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.LegacySupport;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;

@Component(role = Framework.class, hint = PlexusFramework.HINT)
public class PlexusFramework extends PlexusBundle implements Framework {

	public static final String HINT = "plexus";

	@Requirement
	private Logger log;

	@Requirement
	private PlexusContainer plexusContainer;

	@Requirement
	private LegacySupport legacySupport;

	public PlexusFramework() {
		// TODO derive version from manifest?
		super(Constants.SYSTEM_BUNDLE_ID,
				PlexusFramework.class.getProtectionDomain().getCodeSource().getLocation().toString(),
				Constants.SYSTEM_BUNDLE_SYMBOLICNAME, new Version("1"), new Hashtable<>(), null);
		PlexusFrameworkUtilHelper.addFramework(this);
	}

	@Override
	public void init() throws BundleException {
		init(new FrameworkListener[0]);
	}

	@Override
	public void init(FrameworkListener... listeners) throws BundleException {
		throw notImplementedBundleMethod();
	}

	@Override
	public FrameworkEvent waitForStop(long timeout) throws InterruptedException {
		return new FrameworkEvent(FrameworkEvent.ERROR, this, new UnsupportedOperationException());
	}

	@Override
	public <A> A adapt(Class<A> type) {
		if (type.isInstance(plexusContainer)) {
			return type.cast(plexusContainer);
		}
		if (type.isInstance(log)) {
			return type.cast(log);
		}
		if (MavenSession.class == type) {
			return type.cast(legacySupport.getSession());
		}
		return super.adapt(type);
	}

}
