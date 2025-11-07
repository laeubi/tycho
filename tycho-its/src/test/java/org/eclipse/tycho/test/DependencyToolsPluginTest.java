package org.eclipse.tycho.test;

import org.apache.maven.it.Verifier;
import org.eclipse.tycho.version.TychoVersion;
import org.junit.Test;

public class DependencyToolsPluginTest extends AbstractTychoIntegrationTest {

    @Test
    public void testUsage() throws Exception {
        Verifier verifier = getVerifier("dependency-tools.usage", false, true);
        // First build the project to ensure dependencies are resolved
        verifier.executeGoal("verify");
        verifier.verifyErrorFreeLog();
        
        // Now execute the usage goal via CLI
        verifier.executeGoal("org.eclipse.tycho.extras:tycho-dependency-tools-plugin:" 
                + TychoVersion.getTychoVersion() + ":usage");
        verifier.verifyErrorFreeLog();
        
        // Verify the log contains expected output
        verifier.verifyTextInLog("Scan reactor for dependencies...");
        verifier.verifyTextInLog("DEPENDENCIES USAGE REPORT");
        verifier.verifyTextInLog("Target:");
        
        // Verify that unit status is shown
        verifier.verifyTextInLog("USED");
    }
}
