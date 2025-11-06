/*******************************************************************************
 * Copyright (c) 2025 Christoph Läubrich and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.tycho.extras.pde;

import org.junit.jupiter.api.Test;

public class UsageReportTest {

    //TODO test use cases:
    // - Unit is used directly in the target so L > A and indirectly e.g. L > A , L > B > A then A must be reported as used and B is unused
    // - Location L > Unit A > Requires X, Y, Z Now if we find Y is used then A can not be reported as unused (as otherwhise Y would be missing) and X, Z should not be reported unused because they are implicitly included.
    // - In case of transitive inclusion e.g. Location L > Unit A > Requires X, Y, Z and Z is used we should print a message that the unit is INDIRECTLY used through the chain (intead of just used / unused)
    // - Provides nesting information so it needs to be Origin > Location [> possible nested ...with shortest path]
    // - deeper nesting is possible e.g. Location L > Unit A > Requires Unit B > Requires X, Y, Z then B is also not unsued and so on.
    // - If nothing is used, then only Unit A should be reported as unused not X;Y;Z or other transitive ones
    // - if we already have printed a unit with a shorter path used, it should not be reported again

    @Test
    void testIndirectUsedNotReported() {
        //TODO Just an example we need to mock IInstallableUnits + TargetDefinition + locations!
    }
}
