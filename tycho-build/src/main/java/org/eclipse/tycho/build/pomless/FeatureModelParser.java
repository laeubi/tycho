package org.eclipse.tycho.build.pomless;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.api.model.Model;
import org.apache.maven.api.services.Source;
import org.apache.maven.api.spi.ModelParserException;

@Named
@Singleton
public class FeatureModelParser extends AbstractTychoModelParser {

	@Override
	public Optional<Source> locate(Path dir) {
		// TODO Auto-generated method stub
		return Optional.empty();
	}

	@Override
	public Model parse(Source source, Map<String, ?> options) throws ModelParserException {
		// TODO Auto-generated method stub
		return null;
	}

}
