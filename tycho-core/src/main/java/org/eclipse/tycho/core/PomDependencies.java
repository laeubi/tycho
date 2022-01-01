/*******************************************************************************
 * Copyright (c) 2008, 2022 Sonatype Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype Inc. - initial API and implementation
 *    Christoph Läubrich - [Bug 550169] - Improve Tychos handling of includeSource="true" in target definition
 *                         [Bug 567098] - pomDependencies=consider should wrap non-osgi jars
 *******************************************************************************/
package org.eclipse.tycho.core;

public enum PomDependencies {
    /**
     * pom dependencies are ignored
     */
    ignore,
    /**
     * pom dependencies are considered if the are already valid osgi artifacts. p2 metadata may be
     * generated if missing
     */
    consider,
    /**
     * pom dependencies are used and wrapped into OSGi bundles if necessary. p2 metadata may be
     * generated if missing.
     */
    wrapAsBundle;
}
