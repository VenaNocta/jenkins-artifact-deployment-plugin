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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.Util;

public class CustomMetadataEntry extends AMetadataEntry {

	private static final long serialVersionUID = 1L;

	private List<String> metakeys = null;

	@DataBoundConstructor
	public CustomMetadataEntry(final String key) {
		super(key);
	}

	public String getMetaKeyValues() {
		return String.join(", ", getMetaKeys());
	}

	@Override
	public List<String> getMetaKeys() {
		return this.metakeys == null ? new ArrayList<>(0) : new ArrayList<>(this.metakeys);
	}

	@DataBoundSetter
	public void setMetaKeyValues(final String values) {
		setMetaKeys(List.of(values.split(",")));
	}

	public void setMetaKeys(final List<String> metakeys) {
		this.metakeys = metakeys == null ? null
				: metakeys.stream()
						.map(Util::fixEmptyAndTrim)
						.filter(Objects::nonNull)
						.toList();
	}

	@Extension
	public static class Descriptor extends hudson.model.Descriptor<MetadataEntry> {

		@Override
		public String getDisplayName() {
			return "Custom Entry";
		}
	}
}
