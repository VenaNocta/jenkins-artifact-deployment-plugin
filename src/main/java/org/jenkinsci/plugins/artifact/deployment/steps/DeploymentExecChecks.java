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
import java.util.ArrayList;
import java.util.List;

import org.jenkinsci.plugins.artifact.deployment.tasks.CpsCheck;

public class DeploymentExecChecks implements Serializable {

	private static final long serialVersionUID = 1L;

	protected final List<CpsCheck> checks;
	protected final boolean skipOnFail;

	public DeploymentExecChecks(final List<CpsCheck> checks, final boolean skipOnFail) {
		this.checks = new ArrayList<>(checks);
		this.skipOnFail = skipOnFail;
	}

	public boolean getSkipOnFail() {
		return this.skipOnFail;
	}

	public List<CpsCheck> getChecks() {
		return this.checks;
	}
}
