/*******************************************************************************
 * Copyright (c) 2022 Christoph Läubrich and others.
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
package org.eclipse.ui;

import org.eclipse.ui.testing.TestableObject;

/**
 * Surrogate object in case optional import can not be resolved
 */
public class PlatformUI {

    public static TestableObject getTestableObject() {
        throw new IllegalStateException(
                "Can't create TestableObject make sure bundle 'org.eclipse.ui.workbench' is available in your test setup");
    }

}
