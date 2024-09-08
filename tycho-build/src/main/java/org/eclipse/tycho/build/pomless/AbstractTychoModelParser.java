/*******************************************************************************
 * Copyright (c) 2024 Christoph Läubrich and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Christoph Läubrich - initial API and implementation 
 *******************************************************************************/
package org.eclipse.tycho.build.pomless;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.apache.maven.api.model.Model;
import org.apache.maven.api.services.Source;
import org.apache.maven.api.spi.ModelParser;
import org.apache.maven.api.spi.ModelParserException;

public class AbstractTychoModelParser implements ModelParser {

	@Override
	public Optional<Source> locate(Path dir) {
		System.out.println(getClass().getSimpleName() + ".locate(" + dir.getFileName() + ")");
		return Optional.empty();
	}

	@Override
	public Model parse(Source source, Map<String, ?> options) throws ModelParserException {
		System.out.println(getClass().getSimpleName() + ".parse()");
		throw new ModelParserException("Test me!");
	}

	@Override
	public Optional<Model> locateAndParse(Path dir, Map<String, ?> options) throws ModelParserException {
		System.out
				.println(getClass().getSimpleName() + ".locateAndParse(" + dir.getFileName() + " :: " + options + ")");
		return ModelParser.super.locateAndParse(dir, options);
	}

}
