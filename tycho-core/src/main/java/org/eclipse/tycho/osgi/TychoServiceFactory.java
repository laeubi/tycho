package org.eclipse.tycho.osgi;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.sisu.equinox.EquinoxServiceFactory;

@Component(role = EquinoxServiceFactory.class, hint = TychoServiceFactory.HINT)
public class TychoServiceFactory implements EquinoxServiceFactory {

    public static final String HINT = "tycho-core";
    @Requirement(hint = "connect")
    EquinoxServiceFactory delegate;

    @Override
    public <T> T getService(Class<T> clazz) {
        return delegate.getService(clazz);
    }

    @Override
    public <T> T getService(Class<T> clazz, String filter) {
        return delegate.getService(clazz, filter);
    }

}
