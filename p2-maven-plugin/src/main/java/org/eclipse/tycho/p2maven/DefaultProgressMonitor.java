/*******************************************************************************
 * Copyright (c) 2022 Christoph Läubrich and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.p2maven;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.core.runtime.IProgressMonitor;

@Component(role = IProgressMonitor.class, hint = "logger")
public class DefaultProgressMonitor implements IProgressMonitor {

	@Requirement
	private Logger log;

	@Override
	public void worked(int work) {

	}

	@Override
	public void subTask(String name) {
		if (name != null && !name.isBlank()) {
			log.debug(name);
		}
	}

	@Override
	public void setTaskName(String name) {
		if (name != null && !name.isBlank()) {
			log.info(name);
		}
	}

	@Override
	public void setCanceled(boolean value) {

	}

	@Override
	public boolean isCanceled() {
		return false;
	}

	@Override
	public void internalWorked(double work) {

	}

	@Override
	public void done() {

	}

	@Override
	public void beginTask(String name, int totalWork) {
		if (name != null && !name.isBlank()) {
			log.info(name);
		}
	}
}
