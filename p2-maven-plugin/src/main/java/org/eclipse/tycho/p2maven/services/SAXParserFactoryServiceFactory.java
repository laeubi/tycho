package org.eclipse.tycho.p2maven.services;

import javax.xml.parsers.SAXParserFactory;

import org.codehaus.plexus.component.annotations.Component;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;

@Component(role = ServiceFactory.class, hint = "javax.xml.parsers.SAXParserFactory")
public class SAXParserFactoryServiceFactory implements ServiceFactory<SAXParserFactory> {

	@Override
	public SAXParserFactory getService(Bundle bundle, ServiceRegistration<SAXParserFactory> registration) {
		return SAXParserFactory.newInstance();
	}

	@Override
	public void ungetService(Bundle bundle, ServiceRegistration<SAXParserFactory> registration,
			SAXParserFactory service) {
	}

}
