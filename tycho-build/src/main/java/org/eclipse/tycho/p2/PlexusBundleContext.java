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
package org.eclipse.tycho.p2;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.osgi.internal.framework.FilterImpl;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

//FIXME this should not be necessary at all see https://bugs.eclipse.org/bugs/show_bug.cgi?id=578387
@Component(role = BundleContext.class, hint = "plexus")
public class PlexusBundleContext implements BundleContext {

	@Override
	public String getProperty(String key) {
		return System.getProperty(key);
	}

	@Override
	public Bundle getBundle() {
		throw new IllegalStateException("this is not OSGi!");
	}

	@Override
	public Bundle installBundle(String location, InputStream input) throws BundleException {
		throw new BundleException("this context does not support installations");
	}

	@Override
	public Bundle installBundle(String location) throws BundleException {
		throw new BundleException("this context does not support installations");
	}

	@Override
	public Bundle getBundle(long id) {
		return getBundle();
	}

	@Override
	public Bundle[] getBundles() {
		return new Bundle[0];
	}

	@Override
	public void addServiceListener(ServiceListener listener, String filter) throws InvalidSyntaxException {

	}

	@Override
	public void addServiceListener(ServiceListener listener) {

	}

	@Override
	public void removeServiceListener(ServiceListener listener) {

	}

	@Override
	public void addBundleListener(BundleListener listener) {

	}

	@Override
	public void removeBundleListener(BundleListener listener) {

	}

	@Override
	public void addFrameworkListener(FrameworkListener listener) {

	}

	@Override
	public void removeFrameworkListener(FrameworkListener listener) {

	}

	@Override
	public ServiceRegistration<?> registerService(String[] clazzes, Object service, Dictionary<String, ?> properties) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ServiceRegistration<?> registerService(String clazz, Object service, Dictionary<String, ?> properties) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <S> ServiceRegistration<S> registerService(Class<S> clazz, S service, Dictionary<String, ?> properties) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <S> ServiceRegistration<S> registerService(Class<S> clazz, ServiceFactory<S> factory,
			Dictionary<String, ?> properties) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ServiceReference<?>[] getServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ServiceReference<?>[] getAllServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ServiceReference<?> getServiceReference(String clazz) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <S> ServiceReference<S> getServiceReference(Class<S> clazz) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <S> Collection<ServiceReference<S>> getServiceReferences(Class<S> clazz, String filter)
			throws InvalidSyntaxException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <S> S getService(ServiceReference<S> reference) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean ungetService(ServiceReference<?> reference) {
		return true;
	}

	@Override
	public <S> ServiceObjects<S> getServiceObjects(ServiceReference<S> reference) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public File getDataFile(String filename) {
		return null;
	}

	@Override
	public Filter createFilter(String filter) throws InvalidSyntaxException {
		return FilterImpl.newInstance(filter);
	}

	@Override
	public Bundle getBundle(String location) {
		return getBundle();
	}

	private class PlexusServiceReference<S> implements ServiceReference<S> {

		@Override
		public Object getProperty(String key) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String[] getPropertyKeys() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Bundle getBundle() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Bundle[] getUsingBundles() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public boolean isAssignableTo(Bundle bundle, String className) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public int compareTo(Object reference) {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public Dictionary<String, Object> getProperties() {
			return new Hashtable<String, Object>();
		}

		@Override
		public <A> A adapt(Class<A> type) {
			return null;
		}

	}

}
