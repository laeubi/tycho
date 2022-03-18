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
package org.eclipse.p2.maven;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Dictionary;

import javax.xml.parsers.SAXParserFactory;

import org.apache.sling.testing.mock.osgi.MockOsgi;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Disposable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
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

@Component(role = BundleContext.class, hint = "plexus")
public class PlexusBundleContext implements BundleContext, Initializable, Disposable {

	private BundleContext bundleContextMock = MockOsgi.newBundleContext();


	@Override
	public String getProperty(String key) {
		return System.getProperty(key);
	}

	@Override
	public void initialize() throws InitializationException {
		bundleContextMock.registerService(SAXParserFactory.class, SAXParserFactory.newInstance(), null);
	}

	@Override
	public void dispose() {
	}

	@Override
	public Bundle getBundle() {

		return bundleContextMock.getBundle();
	}

	@Override
	public Bundle installBundle(String location, InputStream input) throws BundleException {
		return bundleContextMock.installBundle(location, input);
	}

	@Override
	public Bundle installBundle(String location) throws BundleException {
		return bundleContextMock.installBundle(location);
	}

	@Override
	public Bundle getBundle(long id) {
		return bundleContextMock.getBundle(id);
	}

	@Override
	public Bundle[] getBundles() {
		return bundleContextMock.getBundles();
	}

	@Override
	public void addServiceListener(ServiceListener listener, String filter) throws InvalidSyntaxException {
		bundleContextMock.addServiceListener(listener, filter);
	}

	@Override
	public void addServiceListener(ServiceListener listener) {
		bundleContextMock.addServiceListener(listener);
	}

	@Override
	public void removeServiceListener(ServiceListener listener) {
		bundleContextMock.removeServiceListener(listener);
	}

	@Override
	public void addBundleListener(BundleListener listener) {
		bundleContextMock.addBundleListener(listener);
	}

	@Override
	public void removeBundleListener(BundleListener listener) {
		bundleContextMock.removeBundleListener(listener);
	}

	@Override
	public void addFrameworkListener(FrameworkListener listener) {
		bundleContextMock.addFrameworkListener(listener);
	}

	@Override
	public void removeFrameworkListener(FrameworkListener listener) {
		bundleContextMock.removeFrameworkListener(listener);
	}

	@Override
	public ServiceRegistration<?> registerService(String[] clazzes, Object service, Dictionary<String, ?> properties) {
		return bundleContextMock.registerService(clazzes, service, properties);
	}

	@Override
	public ServiceRegistration<?> registerService(String clazz, Object service, Dictionary<String, ?> properties) {
		return bundleContextMock.registerService(clazz, service, properties);
	}

	@Override
	public <S> ServiceRegistration<S> registerService(Class<S> clazz, S service, Dictionary<String, ?> properties) {
		return bundleContextMock.registerService(clazz, service, properties);
	}

	@Override
	public <S> ServiceRegistration<S> registerService(Class<S> clazz, ServiceFactory<S> factory,
			Dictionary<String, ?> properties) {
		return bundleContextMock.registerService(clazz, factory, properties);
	}

	@Override
	public ServiceReference<?>[] getServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
		return bundleContextMock.getServiceReferences(clazz, filter);
	}

	@Override
	public ServiceReference<?>[] getAllServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
		return bundleContextMock.getAllServiceReferences(clazz, filter);
	}

	@Override
	public ServiceReference<?> getServiceReference(String clazz) {
		return bundleContextMock.getServiceReference(clazz);
	}

	@Override
	public <S> ServiceReference<S> getServiceReference(Class<S> clazz) {
		return bundleContextMock.getServiceReference(clazz);
	}

	@Override
	public <S> Collection<ServiceReference<S>> getServiceReferences(Class<S> clazz, String filter)
			throws InvalidSyntaxException {
		return bundleContextMock.getServiceReferences(clazz, filter);
	}

	@Override
	public <S> S getService(ServiceReference<S> reference) {
		return bundleContextMock.getService(reference);
	}

	@Override
	public boolean ungetService(ServiceReference<?> reference) {
		return bundleContextMock.ungetService(reference);
	}

	@Override
	public <S> ServiceObjects<S> getServiceObjects(ServiceReference<S> reference) {
		return bundleContextMock.getServiceObjects(reference);
	}

	@Override
	public File getDataFile(String filename) {
		return bundleContextMock.getDataFile(filename);
	}

	@Override
	public Filter createFilter(String filter) throws InvalidSyntaxException {
		return bundleContextMock.createFilter(filter);
	}

	@Override
	public Bundle getBundle(String location) {
		return bundleContextMock.getBundle(location);
	}

}
