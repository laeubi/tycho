/*******************************************************************************
 * Copyright (c) 2008, 2011 Sonatype Inc. and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Sonatype Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.core.osgitools;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.tycho.PackagingType;
import org.eclipse.tycho.TychoProject;

@Component(role = TychoProject.class, hint = PackagingType.TYPE_ECLIPSE_TEST_PLUGIN)
public class OsgiTestBundleProject extends OsgiBundleProject {
}
