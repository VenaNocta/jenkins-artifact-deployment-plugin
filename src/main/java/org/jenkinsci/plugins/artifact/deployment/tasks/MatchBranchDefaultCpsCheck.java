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
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.BranchJobProperty;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.AbortException;
import hudson.model.Run;
import jenkins.branch.Branch;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;

public class MatchBranchDefaultCpsCheck implements CpsCheck {

	private static final long serialVersionUID = 1L;

	@DataBoundConstructor
	public MatchBranchDefaultCpsCheck() {
	}

	@Override
	public void check(final StepContext context, final DeploymentExecData data) throws Exception {
		final Run<?, ?> run = context.get(Run.class);
		final WorkflowJob job = run.getParent() instanceof WorkflowJob wfJob ? wfJob : null;
		if (job == null)
			throw new AbortException("Branch Check: not a Multibranch Pipeline!");

		final BranchJobProperty property = job.getProperty(BranchJobProperty.class);
		if (property == null)
			throw new AbortException("Branch Check: Branch-Property reference missing!");

		final Branch branch = property.getBranch();
		if (branch == null)
			throw new AbortException("Branch Check: Branch reference missing!");
		if (branch.getAction(PrimaryInstanceMetadataAction.class) == null)
			throw new AbortException("Branch Check: Active != Default");
	}

	@OptionalExtension(requirePlugins = { "workflow-multibranch" })
	public static class Descriptor extends hudson.model.Descriptor<CpsCheck> {

		@Override
		public String getDisplayName() {
			return "Branch Check: Active == Default";
		}
	}
}
