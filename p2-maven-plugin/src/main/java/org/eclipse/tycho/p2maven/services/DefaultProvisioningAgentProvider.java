package org.eclipse.tycho.p2maven.services;

import java.net.URI;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;

@Component(role = IProvisioningAgentProvider.class)
public class DefaultProvisioningAgentProvider implements IProvisioningAgentProvider {

	@Requirement
	private IProvisioningAgent agent;

	@Override
	public IProvisioningAgent createAgent(URI location) throws ProvisionException {
		return agent;
	}

}
