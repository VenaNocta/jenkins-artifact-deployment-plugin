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
package org.jenkinsci.plugins.artifact.deployment.steps;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.jenkinsci.plugins.artifact.bundle.context.Bundle;

public class DeploymentExecData implements Serializable {

	private static final long serialVersionUID = 1L;

	private final Map<String, Serializable> options = new LinkedHashMap<>();

	private final UUID pipeId;
	private final Bundle bundle;

	public DeploymentExecData(final UUID pipeId, final Map<String, Serializable> options, final Bundle bundle) {
		this.pipeId = pipeId;
		this.options.putAll(options);
		this.bundle = bundle;
	}

	public UUID getPipeId() {
		return this.pipeId;
	}

	public Bundle getBundle() {
		return this.bundle;
	}

	public Map<String, Serializable> getOptions() {
		return this.options;
	}
}
