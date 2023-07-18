package org.eclipse.tycho.p2;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.tycho.MavenRepositoryLocation;
import org.eclipse.tycho.osgi.framework.EclipseApplication;
import org.eclipse.tycho.osgi.framework.EclipseApplicationFactory;

@Component(role = P2ApplicationResolver.class)
public class P2ApplicationResolver {

	@Requirement
	private EclipseApplicationFactory applicationFactory;

	@Requirement
	private Logger logger;

	private final Map<URI, EclipseApplication> cache = new ConcurrentHashMap<>();

	public EclipseApplication getP2Application(MavenRepositoryLocation apiToolsRepo) {
		return cache.computeIfAbsent(apiToolsRepo.getURL().normalize(), x -> {
			logger.info("Resolve P2 Application from " + apiToolsRepo + "...");
			EclipseApplication application = applicationFactory.createEclipseApplication(apiToolsRepo,
					"P2ToolsApplication");
			application.addBundle("org.eclipse.equinox.p2.core");
			application.addBundle("org.eclipse.equinox.p2.artifact.repository");
			application.addBundle("org.eclipse.equinox.p2.director");
			application.addBundle("org.eclipse.equinox.p2.engine");
			application.addBundle("org.eclipse.equinox.p2.jarprocessor");
			application.addBundle("org.eclipse.equinox.p2.metadata");
			application.addBundle("org.eclipse.equinox.p2.metadata.repository");
			application.addBundle("org.eclipse.equinox.p2.publisher");
			application.addBundle("org.eclipse.equinox.p2.publisher.eclipse");
			application.addBundle("org.eclipse.equinox.p2.repository");
			application.addBundle("org.eclipse.equinox.p2.updatesite");
			application.addBundle("org.eclipse.equinox.p2.touchpoint.natives");
			application.addBundle("org.eclipse.equinox.p2.touchpoint.eclipse");
			application.addBundle("org.eclipse.equinox.p2.garbagecollector");
			application.addBundle("org.eclipse.equinox.p2.director.app");
			application.addBundle("org.eclipse.equinox.p2.repository.tools");
			return application;
		});
	}

}
