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
package org.eclipse.tycho.p2maven.services;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.locks.ReentrantLock;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.osgi.service.datalocation.Location;

@Component(role = Location.class)
public class DefaultLocation implements Location {

	private final ReentrantLock lock = new ReentrantLock();

	private Location parent;
	private URL defaultValue;
	private boolean readonly;

	public DefaultLocation() {
		this(null, null, true);
	}

	DefaultLocation(Location parent, URL defaultValue, boolean readonly) {
		this.parent = parent;
		this.defaultValue = defaultValue;
		this.readonly = readonly;
	}

	@Override
	public boolean allowsDefault() {
		return false;
	}

	@Override
	public URL getDefault() {
		return defaultValue;
	}

	@Override
	public Location getParentLocation() {
		return parent;
	}

	@Override
	public URL getURL() {
		return null;
	}

	@Override
	public boolean isSet() {
		return false;
	}

	@Override
	public boolean isReadOnly() {
		return readonly;
	}

	@Override
	public boolean setURL(URL value, boolean lock) throws IllegalStateException {
		return false;
	}

	@Override
	public boolean set(URL value, boolean lock) throws IllegalStateException, IOException {
		return false;
	}

	@Override
	public boolean set(URL value, boolean lock, String lockFilePath) throws IllegalStateException, IOException {
		return false;
	}

	@Override
	public boolean lock() throws IOException {
		return lock.tryLock();
	}

	@Override
	public void release() {
		lock.unlock();

	}

	@Override
	public boolean isLocked() throws IOException {
		return lock.isLocked();
	}

	@Override
	public Location createLocation(Location parent, URL defaultValue, boolean readonly) {
		return new DefaultLocation(parent, defaultValue, readonly);
	}

	@Override
	public URL getDataArea(String path) throws IOException {
		return null;
	}

}
