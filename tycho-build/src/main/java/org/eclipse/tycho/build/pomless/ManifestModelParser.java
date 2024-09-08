package org.eclipse.tycho.build.pomless;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Model.Builder;
import org.apache.maven.api.model.Parent;
import org.apache.maven.api.services.ModelSource;
import org.apache.maven.api.services.Source;
import org.apache.maven.api.spi.ModelParserException;
import org.apache.maven.model.building.ModelProcessor;
import org.eclipse.tycho.PackagingType;
import org.eclipse.tycho.TychoConstants;

import aQute.bnd.osgi.BundleId;
import aQute.bnd.osgi.resource.ResourceBuilder;
import aQute.bnd.osgi.resource.ResourceUtils;
import aQute.bnd.service.resource.SupportingResource;

@Named
@Singleton
public class ManifestModelParser extends AbstractTychoModelParser {

	@Inject
	private ModelProcessor modelProcessor;

	@Override
	public Optional<Source> locate(Path dir) {
		Path manifestPath = dir.resolve(JarFile.MANIFEST_NAME);
		if (Files.isRegularFile(manifestPath)) {
			System.out.println("Return manifest source for " + dir.getFileName());
			return Optional.of(new ManifestSource(dir, manifestPath));
		} else {
			System.out.println("Return empty for " + dir);
		}
		return Optional.empty();
	}

	@Override
	public Model parse(Source source, Map<String, ?> options) throws ModelParserException {
		System.out.println("ManifestModelParser.parse(" + source.getLocation() + ") :: " + options);
		if (source instanceof ManifestSource manifest) {
			ResourceBuilder resourceBuilder = new ResourceBuilder();
			try {
				resourceBuilder.addManifest(manifest.getManifest());
			} catch (IOException e) {
				throw new ModelParserException(e);
			}
			SupportingResource resource = resourceBuilder.build();
			BundleId bundleId = ResourceUtils.getBundleId(resource);

			File parent = modelProcessor.locatePom(manifest.baseDir.resolve("..").toFile());
			// modelProcessor.
			
			Source source2 = source.resolve("..");
			Builder builder = Model.newBuilder();
			builder.artifactId(bundleId.getBsn());
//			builder.groupId("blabla");
			builder.version(
					bundleId.getVersion().replace(TychoConstants.SUFFIX_QUALIFIER, TychoConstants.SUFFIX_SNAPSHOT));
			builder.modelVersion("4.0.0");
			builder.packaging(PackagingType.TYPE_ECLIPSE_PLUGIN);
			org.apache.maven.api.model.Parent.Builder pb = Parent.newBuilder();
			// TODO parent G+A should be optional!
			// see https://issues.apache.org/jira/browse/MNG-8252
			pb.groupId("tycho-its-project.reactor.makeBehaviour");
			pb.artifactId("parent");
//			pb.version("1.0.0-SNAPSHOT");
			pb.relativePath("..");
			builder.parent(pb.build());
			// TODO as a workaround for https://issues.apache.org/jira/browse/MNG-8253 we
			// need to generate a tycho.pom file!
			return builder.build();
		}
		throw new ModelParserException("Invalid source type!");
	}

	private static final class ManifestSource implements ModelSource {

		private Path baseDir;
		private Path manifestPath;

		public ManifestSource(Path baseDir, Path manifestPath) {
			this.baseDir = baseDir;
			this.manifestPath = manifestPath;
		}

		public Manifest getManifest() throws IOException {
			Manifest manifest = new Manifest();
			try (InputStream stream = Files.newInputStream(manifestPath)) {
				manifest.read(stream);
			}
			return manifest;
		}

		@Override
		public Path getPath() {
			return manifestPath.getParent();
		}

		@Override
		public InputStream openStream() throws IOException {
			return Files.newInputStream(manifestPath);
		}

		@Override
		public String getLocation() {
			return manifestPath.toString();
		}

		@Override
		public Source resolve(String relative) {
			System.out.println("ManifestModelParser.ManifestSource.resolve(String) = " + relative);
			return null;
		}

		@Override
		public ModelSource resolve(ModelLocator modelLocator, String relative) {
			System.out.println("ManifestModelParser.ManifestSource.resolve(ModelLocator, String) = " + relative);
			// TODO Auto-generated method stub
			return null;
		}

	}
}
