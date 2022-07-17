package org.eclipse.tycho.plexus.osgi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusContainer;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

class PlexusBundle implements Bundle {

	private static final String JAR_FILE_PREFIX = "jar:file:";
	private PlexusBundleContext bundleContext;
	private long bundleId;
	private String location;
	private long lastModified = System.currentTimeMillis();
	private String symbolicName;
	private Version version;
	private Dictionary<String, String> headers;

	private int state = RESOLVED;
	private BundleActivator bundleActivator;

	PlexusBundle(long bundleId, String location, String symbolicName, Version version,
			Dictionary<String, String> headers, PlexusBundle systemBundle) {
		this.bundleId = bundleId;
		this.location = location;
		this.symbolicName = symbolicName;
		this.version = version;
		this.headers = headers;
		bundleContext = new PlexusBundleContext(this, systemBundle);
	}

	@Override
	public File getDataFile(String filename) {
		MavenSession session = getBundleContext().getBundle(Constants.SYSTEM_BUNDLE_ID).adapt(MavenSession.class);
		if (session != null) {
			MavenProject project = session.getCurrentProject();
			if (project != null) {
				File directory = new File(project.getBuild().getDirectory());
				if (filename != null) {
					return new File(directory, filename);
				}
				return directory;
			}
		}
		return null;
	}

	@Override
	public int compareTo(Bundle o) {
		return Long.compare(getBundleId(), o.getBundleId());
	}

	@Override
	public synchronized int getState() {
		return state;
	}

	@Override
	public synchronized void start(int options) throws BundleException {
		if (state == RESOLVED) {
			state = ACTIVE;
			String activatorClass = getHeaders().get(Constants.BUNDLE_ACTIVATOR);
			if (activatorClass != null) {
				try {
					bundleActivator = (BundleActivator) loadClass(activatorClass).getConstructor().newInstance();
					bundleActivator.start(getBundleContext());
				} catch (Exception e) {
					throw new BundleException("can't activate bundle " + getSymbolicName(), e);
				}
			}
		}
	}

	@Override
	public synchronized void start() throws BundleException {
		start(0);
	}

	@Override
	public void stop(int options) throws BundleException {
		if (state == ACTIVE) {
			if (bundleActivator != null) {
				try {
					bundleActivator.stop(getBundleContext());
				} catch (Exception e) {
					throw new BundleException("can't stop bundle " + getSymbolicName(), e);
				} finally {
					bundleActivator = null;
				}
			}
			state = RESOLVED;
		}
	}

	@Override
	public void stop() throws BundleException {
		stop(0);
	}

	@Override
	public void update(InputStream input) throws BundleException {
		throw notImplementedBundleMethod();
	}

	@Override
	public void update() throws BundleException {
		throw notImplementedBundleMethod();
	}

	@Override
	public void uninstall() throws BundleException {
		throw new BundleException("A plexus bundle can not be uninstalled!");
	}

	@Override
	public Dictionary<String, String> getHeaders() {
		return headers;
	}

	@Override
	public long getBundleId() {
		return bundleId;
	}

	@Override
	public String getLocation() {
		return location;
	}

	@Override
	public ServiceReference<?>[] getRegisteredServices() {
		return new ServiceReference<?>[0];
	}

	@Override
	public ServiceReference<?>[] getServicesInUse() {
		return new ServiceReference<?>[0];
	}

	@Override
	public boolean hasPermission(Object permission) {
		return true;
	}

	@Override
	public URL getResource(String name) {
		return getClass().getClassLoader().getResource(name);
	}

	@Override
	public Dictionary<String, String> getHeaders(String locale) {
		return getHeaders();
	}

	@Override
	public String getSymbolicName() {
		return symbolicName;
	}

	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		return getClass().getClassLoader().loadClass(name);
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		return getClass().getClassLoader().getResources(name);
	}

	@Override
	public Enumeration<String> getEntryPaths(String path) {
		return Collections.emptyEnumeration();
	}

	@Override
	public URL getEntry(String path) {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		if (!path.isEmpty()) {
			String loc = getLocation();
			if (loc.startsWith(JAR_FILE_PREFIX)) {
				try (JarFile jarFile = new JarFile(loc.substring(JAR_FILE_PREFIX.length()).split("!")[0])) {
					if (jarFile.getEntry(path) != null) {
						URL url = new URL(new URL(loc), "/" + path);
						return url;
					}
				} catch (IOException e) {
				}
			}
		}
		return null;
	}

	@Override
	public long getLastModified() {
		return lastModified;
	}

	@Override
	public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
		return Collections.emptyEnumeration();
	}

	@Override
	public BundleContext getBundleContext() {
		return bundleContext;
	}

	@Override
	public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
		return Collections.emptyMap();
	}

	@Override
	public Version getVersion() {
		return version;
	}

	@Override
	public <A> A adapt(Class<A> type) {
		if (type == PlexusContainer.class) {
			return getBundleContext().getBundle(Constants.SYSTEM_BUNDLE_ID).adapt(type);
		}
		return null;
	}

	protected static BundleException notImplementedBundleMethod() {
		return new BundleException("no implemented");
	}

	@Override
	public String toString() {
		return "Plexus Bundle: " + getSymbolicName() + " @ " + getLocation();
	}
}
