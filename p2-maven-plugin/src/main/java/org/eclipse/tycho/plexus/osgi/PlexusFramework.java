package org.eclipse.tycho.plexus.osgi;


import java.util.Hashtable;
import java.util.List;

import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Disposable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.eclipse.core.internal.adapter.AdapterManagerListener;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;

@Component(role = Framework.class, hint = PlexusFramework.HINT)
public class PlexusFramework extends PlexusBundle implements Initializable, Disposable, Framework {

	static final String HINT = "plexus";

	@Requirement
	private Logger log;

	@Requirement
	private PlexusContainer plexusContainer;

	// TODO replace with properties file!
	private List<BundleActivator> legacyActivators = List.of(
			// see https://github.com/eclipse-equinox/p2/issues/100
			new org.eclipse.pde.internal.publishing.Activator(), //
// TODO	java.lang.IllegalStateException: bundle org.eclipse.equinox.p2.repositoryis not started
//		      at org.eclipse.equinox.internal.p2.repository.Activator.getContext (Activator.java:65)
//		      at org.eclipse.equinox.internal.p2.repository.helpers.AbstractRepositoryManager.getPreferences (AbstractRepositoryManager.java:506)
//		      at org.eclipse.equinox.internal.p2.repository.helpers.AbstractRepositoryManager.remember (AbstractRepositoryManager.java:896)
			new org.eclipse.equinox.internal.p2.repository.Activator(), //
//TODO Caused by: java.lang.NullPointerException: Cannot invoke "org.osgi.framework.BundleContext.getProperty(String)" because the return value of "org.eclipse.equinox.internal.p2.artifact.repository.Activator.getContext()" is null
//			at org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository.<clinit>(CompositeArtifactRepository.java:46)
			new org.eclipse.equinox.internal.p2.artifact.repository.Activator(), //
//TODO java.lang.NullPointerException: Cannot invoke "org.osgi.framework.BundleContext.getDataFile(String)" because the return value of "org.eclipse.equinox.internal.p2.updatesite.Activator.getBundleContext()" is null
//		at org.eclipse.equinox.internal.p2.updatesite.metadata.UpdateSiteMetadataRepositoryFactory.getLocalRepositoryLocation(UpdateSiteMetadataRepositoryFactory.java:40)
//		at org.eclipse.equinox.internal.p2.updatesite.artifact.UpdateSiteArtifactRepositoryFactory.loadRepository(UpdateSiteArtifactRepositoryFactory.java:89)
			new org.eclipse.equinox.internal.p2.updatesite.Activator(), //
//TODO java.lang.NullPointerException: Cannot invoke "org.eclipse.equinox.internal.security.auth.AuthPlugin.getEnvironmentInfoService()" because the return value of "org.eclipse.equinox.internal.security.auth.AuthPlugin.getDefault()" is null
//	    at org.eclipse.equinox.internal.security.storage.SecurePreferencesMapper.open (SecurePreferencesMapper.java:79)
//	    at org.eclipse.equinox.internal.security.storage.SecurePreferencesMapper.getDefault (SecurePreferencesMapper.java:56)
//	    at org.eclipse.equinox.security.storage.SecurePreferencesFactory.getDefault (SecurePreferencesFactory.java:52)
//	    at org.eclipse.equinox.internal.p2.repository.Credentials.forLocation (Credentials.java:117)
			new org.eclipse.equinox.internal.security.auth.AuthPlugin(), //
//TODO java.lang.NullPointerException: Cannot invoke "org.eclipse.equinox.internal.p2.transport.ecf.Activator.getRetrieveFileTransferFactory()" because the return value of "org.eclipse.equinox.internal.p2.transport.ecf.Activator.getDefault()" is null
//	    at org.eclipse.equinox.internal.p2.transport.ecf.FileReader.sendRetrieveRequest (FileReader.java:430)
//	    at org.eclipse.equinox.internal.p2.transport.ecf.FileReader.readInto (FileReader.java:386)
//	    at org.eclipse.equinox.internal.p2.transport.ecf.RepositoryTransport.download (RepositoryTransport.java:107)
//	    at org.eclipse.equinox.internal.p2.repository.helpers.AbstractRepositoryManager.handleRemoteIndexFile (AbstractRepo
//TODO this currently also requires a bundle because it lookup the version there...
			new org.eclipse.equinox.internal.p2.transport.ecf.Activator(), //
// TODO ECF should not init this in the constructor but on first request...
			new org.eclipse.ecf.internal.core.ECFPlugin(),
// TODO do not register service in activator as inner class...
			new org.eclipse.ecf.internal.provider.filetransfer.Activator(),
//			// see https://bugs.eclipse.org/bugs/show_bug.cgi?id=578387
			new org.eclipse.equinox.internal.p2.director.DirectorActivator(),
			// TODO
			new org.eclipse.ecf.internal.core.identity.Activator(), //
			new org.eclipse.equinox.internal.p2.core.Activator());

	public PlexusFramework() {
		// TODO derive version from manifest
		super(Constants.SYSTEM_BUNDLE_ID,
				PlexusFramework.class.getProtectionDomain().getCodeSource().getLocation().toString(),
				Constants.SYSTEM_BUNDLE_SYMBOLICNAME, new Version("1"), new Hashtable<>(), null);
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
	public void initialize() throws InitializationException {
		for (BundleActivator bundleActivator : legacyActivators) {
			try {
				// TODO create a bundle(context) for each bundle?
				bundleActivator.start(getBundleContext());
			} catch (Exception e) {
				log.warn("Can't init " + bundleActivator.getClass() + "! (" + e + ")");
			}
		}
		// register adapter extensions... TODO can we simply start the activator?
		new AdapterManagerListener();
	}

	@Override
	public void dispose() {
		for (BundleActivator bundleActivator : legacyActivators) {
			try {
				bundleActivator.stop(getBundleContext());
			} catch (Exception e) {
				log.warn("Can't init " + bundleActivator.getClass() + "! (" + e + ")");
			}
		}
	}

	@Override
	public <A> A adapt(Class<A> type) {
		if (type.isInstance(plexusContainer)) {
			return type.cast(plexusContainer);
		}
		if (type.isInstance(log)) {
			return type.cast(log);
		}
		return super.adapt(type);
	}

}
