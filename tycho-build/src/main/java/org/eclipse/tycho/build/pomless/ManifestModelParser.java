package org.eclipse.tycho.build.pomless;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Model.Builder;
import org.apache.maven.api.services.Source;
import org.apache.maven.api.spi.ModelParserException;

@Named
@Singleton
public class ManifestModelParser extends AbstractTychoModelParser {

	@Override
	public Optional<Source> locate(Path dir) {
		Path manifestPath = dir.resolve(JarFile.MANIFEST_NAME);
		if (Files.isRegularFile(manifestPath)) {
			System.out.println("Return manifest source");
			return Optional.of(new ManifestSource(dir, manifestPath));
		}
		return super.locate(dir);
	}

	@Override
	public Model parse(Source source, Map<String, ?> options) throws ModelParserException {
		System.out.println("ManifestModelParser.parse(" + source.getLocation() + ") :: " + options);
		if (source instanceof ManifestSource manifest) {
			Builder builder = Model.newBuilder();
			builder.artifactId("blabla");
			builder.groupId("blabla");
			return builder.build();
		}
		throw new ModelParserException("Invalid source type!");
	}

	private static final class ManifestSource implements Source {

		private Path dir;
		private Path manifestPath;

		public ManifestSource(Path dir, Path manifestPath) {
			this.dir = dir;
			this.manifestPath = manifestPath;
		}

		@Override
		public Path getPath() {
			return dir;
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

//		@Override
//		public ModelSource resolve(ModelLocator modelLocator, String relative) {
//			System.out.println("ManifestModelParser.ManifestSource.resolve(ModelLocator, String) = " + relative);
//			// TODO Auto-generated method stub
//			return null;
//		}

	}
}
