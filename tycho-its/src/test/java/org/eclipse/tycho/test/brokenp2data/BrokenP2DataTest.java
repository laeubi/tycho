package org.eclipse.tycho.test.brokenp2data;

import java.util.List;

import org.apache.maven.shared.verifier.Verifier;
import org.eclipse.tycho.test.AbstractTychoIntegrationTest;
import org.junit.Test;

// See #2625
public class BrokenP2DataTest extends AbstractTychoIntegrationTest {

	@Test
	public void test() throws Exception {
		Verifier verifier = getVerifier("brokenp2data");

		verifier.addCliArguments("clean", "verify");

		verifier.execute();
		verifier.verifyErrorFreeLog();
	}
}
