/*******************************************************************************
 * Copyright (c) 2025 Christoph Läubrich and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.cleancode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CleanupPreferencesUpdater implements AutoCloseable {

	private Path prefsFile;
	private boolean updated = false;
	private List<String> lines;
	private Map<String, String> cleanUpProfile;

	public CleanupPreferencesUpdater(Path prefsFile, Map<String, String> cleanUpProfile) throws IOException {
		this.prefsFile = prefsFile;
		this.cleanUpProfile = cleanUpProfile;
		this.lines = Files.readAllLines(prefsFile, StandardCharsets.UTF_8);
	}

	public synchronized void updateProjectCleanupProfile() {
		List<String> newLines = updateProjectSettingsFile(null, lines);
		if (!newLines.equals(lines)) {
			updated = true;
			lines = newLines;
		}
	}

	public synchronized void updateSaveActions() {
		List<String> newLines = updateProjectSettingsFile("sp_", lines);
		if (!newLines.equals(lines)) {
			updated = true;
			lines = newLines;
		}
	}

	public synchronized boolean hasCleanupProfile() {
		for (String line : lines) {
			KV kv = parseLine(line);
			if (kv.key().equals("cleanup_profile")) {
				return true;
			}
		}
		return false;
	}

	public synchronized boolean hasSaveActions() {
		for (String line : lines) {
			KV kv = parseLine(line);
			if (kv.key().equals("sp_cleanup.on_save_use_additional_actions")) {
				return Boolean.parseBoolean(kv.value());
			}
		}
		return false;
	}

	private List<String> updateProjectSettingsFile(String prefix, List<String> lines) {
		List<String> updatedLines = new ArrayList<>();
		Set<String> missingKeys = new HashSet<>(cleanUpProfile.keySet());
		for (String line : lines) {
			KV kv = parseLine(line);
			if (!kv.matches(prefix)) {
				updatedLines.add(line);
				continue;
			}
			// Check if this line matches any key in the cleanup profile
			String key = kv.key(prefix);
			if (missingKeys.remove(key)) {
				updatedLines.add(kv.key() + "=" + cleanUpProfile.get(key));
			} else {
				updatedLines.add(line);
			}
		}
		// Add any keys from the profile that weren't found in the file
		for (String key : missingKeys) {
			String prefixed = prefix == null ? key : prefix + key;
			updatedLines.add(prefixed + "=" + cleanUpProfile.get(key));
		}
		return updatedLines;
	}

	@Override
	public synchronized void close() throws IOException {
		if (updated) {
			// Write the updated content back to the file with explicit charset
			Files.write(prefsFile, lines, StandardCharsets.UTF_8);
		}
	}

	private static KV parseLine(String line) {
		String[] kv = line.split("=", 2);
		if (kv.length != 2) {
			return new KV(line, null);
		}
		return new KV(kv[0], kv[1]);
	}

	private static final record KV(String key, String value) {

		public boolean matches(String prefix) {
			if (value == null) {
				return false;
			}
			return key(prefix).startsWith("cleanup.");
		}

		public String key(String prefix) {
			if (prefix == null || !key.startsWith(prefix)) {
				return key;
			}
			return key.substring(prefix.length());
		}

	}

}
