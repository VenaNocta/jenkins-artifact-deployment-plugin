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

import org.jenkinsci.plugins.artifact.deployment.steps.DeploymentExecData;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayCause;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.AbortException;
import hudson.Extension;
import hudson.model.Run;

public class BlockReplayedCpsCheck implements CpsCheck {

	private static final long serialVersionUID = 1L;

	@DataBoundConstructor
	public BlockReplayedCpsCheck() {
	}

	@Override
	public void check(final StepContext context, final DeploymentExecData data) throws Exception {
		final Run<?, ?> run = context.get(Run.class);
		if (run.getCause(ReplayCause.class) != null)
			throw new AbortException("Check: Replayed Builds are prohibited!");
	}

	@Extension
	public static class Descriptor extends hudson.model.Descriptor<CpsCheck> {

		@Override
		public String getDisplayName() {
			return "Block Replayed Check";
		}
	}
}
