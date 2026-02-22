package org.eclipse.tycho.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.shared.verifier.VerificationException;
import org.apache.maven.shared.verifier.Verifier;
import org.eclipse.tycho.TargetEnvironment;
import org.junit.Test;

public class P2DirectorPluginTest extends AbstractTychoIntegrationTest {

	@Test
	public void testDirectorStandaloneWindows() throws Exception {
		Verifier verifier = getVerifier("tycho-p2-director-plugin/director-goal-standalone", true, true);
		verifier.addCliArgument("-Pdirector-windows");
		verifier.addCliArgument("package");
		verifier.execute();
		verifier.verifyErrorFreeLog();

		assertTrue(Files.isDirectory(Path.of(verifier.getBasedir(), "target", "productwindows", "plugins")));
	}

	@Test
	public void testDirectorStandaloneLinux() throws Exception {
		Verifier verifier = getVerifier("tycho-p2-director-plugin/director-goal-standalone", true, true);
		verifier.addCliArgument("-Pdirector-linux");
		verifier.addCliArgument("package");
		verifier.execute();
		verifier.verifyErrorFreeLog();

		assertTrue(Files.isDirectory(Path.of(verifier.getBasedir(), "target", "productlinux", "plugins")));
	}

	@Test
	public void testDirectorStandaloneMacOsDestinationWithAppSuffix() throws Exception {
		Verifier verifier = getVerifier("tycho-p2-director-plugin/director-goal-standalone", true, true);
		verifier.addCliArgument("-Pdirector-macos-destination-with-app-suffix");
		verifier.addCliArgument("package");
		verifier.execute();
		verifier.verifyErrorFreeLog();

		assertTrue(Files.isDirectory(
				Path.of(verifier.getBasedir(), "target", "productmacos.app", "Contents", "Eclipse", "plugins")));
	}

	@Test
	public void testDirectorStandaloneMacOsDestinationWithoutAppSuffix() throws Exception {
		Verifier verifier = getVerifier("tycho-p2-director-plugin/director-goal-standalone", true, true);
		verifier.addCliArgument("-Pdirector-macos-destination-without-app-suffix");
		verifier.addCliArgument("package");
		verifier.execute();
		verifier.verifyErrorFreeLog();

		assertTrue(Files.isDirectory(Path.of(verifier.getBasedir(), "target", "productmacos", "Eclipse.app", "Contents",
				"Eclipse", "plugins")));
	}

	@Test
	public void testDirectorStandaloneMacOsDestinationWithFullBundlePath() throws Exception {
		Verifier verifier = getVerifier("tycho-p2-director-plugin/director-goal-standalone", true, true);
		verifier.addCliArgument("-Pdirector-macos-destination-with-full-bundle-path");
		verifier.addCliArgument("package");
		verifier.execute();
		verifier.verifyErrorFreeLog();

		assertTrue(Files.isDirectory(
				Path.of(verifier.getBasedir(), "target", "productmacos.app", "Contents", "Eclipse", "plugins")));
	}

	@Test
	public void testDirectorStandaloneUsingRunningEnvironment() throws Exception {
		Verifier verifier = getVerifier("tycho-p2-director-plugin/director-goal-standalone", true, true);
		verifier.addCliArgument("-Pdirector-running-environment");
		verifier.addCliArgument("package");
		verifier.execute();
		verifier.verifyErrorFreeLog();

		if ("macosx".equals(TargetEnvironment.getRunningEnvironment().getOs())) {
			assertTrue(Files.isDirectory(Path.of(verifier.getBasedir(), "target", "product", "Eclipse.app", "Contents",
					"Eclipse", "plugins")));
		} else {
			assertTrue(Files.isDirectory(Path.of(verifier.getBasedir(), "target", "product", "plugins")));
		}
	}

	@Test
	public void testDirectorStandaloneInconsistentP2Options() throws Exception {
		Verifier verifier = getVerifier("tycho-p2-director-plugin/director-goal-standalone", true, true);
		verifier.addCliArgument("-Pdirector-iconsistent-p2-arguments");
		try {
			verifier.addCliArgument("package");
			verifier.execute();
			fail(VerificationException.class.getName() + " expected");
		} catch (VerificationException e) {
		}
		verifier.verifyTextInLog(
				"p2os / p2ws / p2arch must be mutually specified, p2os=win32 given, p2ws missing, p2arch missing");
		assertFalse(Files.isDirectory(Path.of(verifier.getBasedir(), "target", "product")));
	}

}
