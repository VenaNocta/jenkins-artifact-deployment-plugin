/*
 * Copyright © 2026 VenaNocta (venanocta@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkinsci.plugins.artifact.deployment.tasks;

import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

public class MetadataVersionEntry extends AStaticMetadataEntry {

	private static final long serialVersionUID = 1L;

	// NOTE: collect all known repo type version references
	public static final String[] METADATA_VERSION = //
			new String[] { "maven.version", "version" };

	@DataBoundConstructor
	public MetadataVersionEntry(final String key) {
		super(key);
	}

	@Override
	public List<String> getMetaKeys() {
		return List.of(METADATA_VERSION);
	}

	@Extension
	public static class Descriptor extends hudson.model.Descriptor<MetadataEntry> {

		@Override
		public String getDisplayName() {
			return "Version";
		}

		public List<String> getMetaKeys() {
			return List.of(METADATA_VERSION);
		}
	}
}
