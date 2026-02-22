package org.eclipse.tycho.test.resolver;

import java.util.List;

import org.apache.maven.shared.verifier.Verifier;
import org.eclipse.tycho.test.AbstractTychoIntegrationTest;
import org.junit.Test;

public class JustJJRETest extends AbstractTychoIntegrationTest {

	@Test
	public void testProductWithJustJJREdifferentToRunningJVM() throws Exception {
		Verifier verifier = getVerifier("resolver.justjJRE");
		verifier.addCliArguments("clean", "verify");
		verifier.execute();
		verifier.verifyErrorFreeLog();
	}

}
