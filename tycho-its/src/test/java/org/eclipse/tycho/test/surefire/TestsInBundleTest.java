/*******************************************************************************
 * Copyright (c) 2021 Christoph Läubrich and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.test.surefire;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;

import org.apache.maven.shared.verifier.VerificationException;
import org.apache.maven.shared.verifier.Verifier;
import org.eclipse.tycho.test.AbstractTychoIntegrationTest;
import org.junit.Test;

public class TestsInBundleTest extends AbstractTychoIntegrationTest {

	@Test
	public void testCompile() throws Exception {
		Verifier verifier = getVerifier("surefire.combinedtests/bundle.test");
		verifier.addCliArguments("clean", "test-compile");
		verifier.execute();
		verifier.verifyErrorFreeLog();
		assertTrue("compiled class file does not exist",
				new File(verifier.getBasedir(), "target/classes/bundle/test/Counter.class").exists());
		assertTrue("compiled test-class file does not exist",
				new File(verifier.getBasedir(), "target/test-classes/bundle/test/AdderTest.class").exists());
	}

	@Test
	public void testCompile5() throws Exception {
		Verifier verifier = getVerifier("surefire.combinedtests/bundle5.test");
		verifier.addCliArguments("clean", "test-compile");
		verifier.execute();
		verifier.verifyErrorFreeLog();
		assertTrue("compiled class file does not exist",
				new File(verifier.getBasedir(), "target/classes/bundle/test/Counter.class").exists());
		assertTrue("compiled test-class file does not exist",
				new File(verifier.getBasedir(), "target/test-classes/bundle/test/AdderTest.class").exists());
	}

	@Test
	public void testCompile5WithoutVintage() throws Exception {
		Verifier verifier = getVerifier("surefire.combinedtests/bundle5.no.vintage.test");
		assertThrows("Compilation must fail because the usage of junit 4 annotations", VerificationException.class,
				() -> { verifier.addCliArguments("clean", "test-compile"); verifier.execute(); });
		verifier.verifyTextInLog("The import org.junit.Assert cannot be resolved");
		verifier.verifyTextInLog("The import org.junit.Test cannot be resolved");
	}

	@Test
	public void testTest() throws Exception {
		Verifier verifier = getVerifier("surefire.combinedtests/bundle.test");
		verifier.addCliArguments("clean", "test");
		verifier.execute();
		verifier.verifyErrorFreeLog();
		assertTrue("tests were not run",
				new File(verifier.getBasedir(), "target/surefire-reports/bundle.test.AdderTest.txt").exists());
	}

	@Test
	public void testIntegrationTest() throws Exception {
		Verifier verifier = getVerifier("surefire.combinedtests/bundle.test");
		verifier.addCliArguments("clean", "integration-test");
		verifier.execute();
		verifier.verifyErrorFreeLog();
		assertTrue("summary report not found",
				new File(verifier.getBasedir(), "target/failsafe-reports/failsafe-summary.xml").exists());
		verifier.verifyTextInLog("Tests run: 2, Failures: 1, Errors: 0, Skipped: 0");
		verifier.verifyTextInLog("OSGiRunningIT.willFail:30 This fail is intentional");
	}

	@Test
	public void testVerify() throws Exception {
		Verifier verifier = getVerifier("surefire.combinedtests/bundle.test");
		assertThrows("the build succeed but test-failures are expected!", VerificationException.class,
				() -> { verifier.addCliArguments("clean", "verify"); verifier.execute(); });
		// thats good indeed...
		verifier.verifyTextInLog("There are test failures");
	}

}
