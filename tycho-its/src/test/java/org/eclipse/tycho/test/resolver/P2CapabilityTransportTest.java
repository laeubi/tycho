/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Copilot - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.test.resolver;

import static org.junit.Assert.fail;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.eclipse.tycho.test.AbstractTychoIntegrationTest;
import org.junit.Test;

/**
 * Test for OSGi capability cycle detection with Provide-Capability and
 * Require-Capability. This test validates that Tycho properly detects and
 * reports dependency cycles when bundles have circular capability requirements.
 * 
 * The test creates two bundles with a cycle:
 * - provider.bundle: Provides a capability and requires consumer.bundle
 * - consumer.bundle: Requires the capability provided by provider.bundle
 * 
 * This ensures Tycho can detect cycles and provide meaningful error messages.
 */
public class P2CapabilityTransportTest extends AbstractTychoIntegrationTest {

	@Test
	public void testCapabilityProvideRequire() throws Exception {
		Verifier verifier = getVerifier("reactor.capability.cycle");
		try {
			verifier.executeGoal("verify");
			fail("Expected build to fail with cycle detection error");
		} catch (VerificationException e) {
			// Expected - verify the error message contains cycle information
			verifier.verifyTextInLog("Dependency cycle detected");
			verifier.verifyTextInLog("provider.bundle");
			verifier.verifyTextInLog("consumer.bundle");
		}
	}

}
