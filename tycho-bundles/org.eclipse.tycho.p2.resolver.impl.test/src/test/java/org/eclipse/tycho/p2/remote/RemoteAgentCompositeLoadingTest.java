/*******************************************************************************
 * Copyright (c) 2013 SAP AG and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.p2.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.tycho.p2.impl.test.ResourceUtil;
import org.eclipse.tycho.testutil.LogVerifier;
import org.eclipse.tycho.testutil.MockMavenContext;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

@Ignore("This test currently only fails on the Jenkins CI")
public class RemoteAgentCompositeLoadingTest {

    @Rule
    public final TemporaryFolder tempManager = new TemporaryFolder();
    @Rule
    public final LogVerifier logVerifier = new LogVerifier();

    private RemoteAgent subject;

    @Before
    public void initSubject() throws Exception {
        File localRepo = tempManager.newFolder("localRepo");
        subject = new RemoteAgent(new MockMavenContext(localRepo, logVerifier.getLogger()));
    }

    @Test
    public void testLoadingCompositeRepositoryWithMissingChildFailsByDefault() throws IOException {
        /*
         * In Tycho, we want composite repositories to fail if they have missing children (and don't
         * explicitly specify the "p2.atomic.composite.loading" property).
         */
        try {
            subject.getService(IArtifactRepositoryManager.class).loadRepository(
                    ResourceUtil.resourceFile("repositories/composite/missingChildAndAtomicUnset").toURI(),
                    new NullProgressMonitor());
            fail("Exception was not thrown!");
        } catch (ProvisionException e) {
            assertEquals(ProvisionException.REPOSITORY_FAILED_READ, e.getStatus().getCode());
        }

    }

}
