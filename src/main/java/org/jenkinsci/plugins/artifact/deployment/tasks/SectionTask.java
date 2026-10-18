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
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.jenkinsci.plugins.artifact.deployment.steps.ACpsDeployTask;
import org.jenkinsci.plugins.artifact.deployment.steps.CpsDeployTask;
import org.jenkinsci.plugins.artifact.deployment.steps.DeployArtifactsStep;
import org.jenkinsci.plugins.artifact.deployment.steps.DeploymentExecChecks;
import org.jenkinsci.plugins.artifact.deployment.steps.Messages;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.cps.CpsThreadGroup;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import groovy.lang.Script;
import hudson.AbortException;
import hudson.Extension;
import hudson.Util;

import static org.jenkinsci.plugins.artifact.deployment.steps.DeployArtifactsStep.GROOVY_VAR__PIPE_TASKS;

public class SectionTask implements DeploymentTask {

	private static final long serialVersionUID = 1L;

	protected String name;
	protected List<CpsCheck> requirements = null;
	protected boolean skipOnFailedCheck = false;
	protected List<DeploymentTask> tasks = null;

	@DataBoundConstructor
	public SectionTask() {
	}

	public String getName() {
		return this.name;
	}

	public List<CpsCheck> getRequirements() {
		if (this.requirements == null)
			return new ArrayList<>(0);
		return new ArrayList<CpsCheck>(this.requirements);
	}

	public boolean getSkipOnFailedCheck() {
		return this.skipOnFailedCheck;
	}

	public List<DeploymentTask> getTasks() {
		if (this.tasks == null)
			return new ArrayList<>(0);
		return new ArrayList<DeploymentTask>(this.tasks);
	}

	@DataBoundSetter
	public void setName(@Nullable final String name) {
		this.name = Util.fixEmptyAndTrim(name);
	}

	@DataBoundSetter
	public void setRequirements(@NonNull final Collection<CpsCheck> requirements) {
		this.requirements = requirements == null ? null : new LinkedList<>(requirements);
	}

	@DataBoundSetter
	public void setSkipOnFailedCheck(final boolean skipOnFailedCheck) {
		this.skipOnFailedCheck = skipOnFailedCheck;
	}

	@DataBoundSetter
	public void setTasks(@NonNull final Collection<DeploymentTask> tasks) {
		this.tasks = tasks == null ? null : new LinkedList<>(tasks);
	}

	@Override
	public CpsDeployTask buildCpsTask() {
		return new CpsTask(this.name, getRequirements(), this.skipOnFailedCheck, getTasks());
	}

	@Extension
	public static class Descriptor extends hudson.model.Descriptor<DeploymentTask> {

		@Override
		public String getDisplayName() {
			return "Section";
		}
	}

	public static class CpsTask extends ACpsDeployTask {

		private static final long serialVersionUID = 1L;

		protected final String name;
		protected final List<CpsCheck> requirements;
		protected final boolean skipOnFailedCheck;
		protected final List<DeploymentTask> tasks;

		protected BodyExecution cpsBody = null;

		public CpsTask(final String name, final List<CpsCheck> requirements, final boolean skipOnFailedCheck,
				final List<DeploymentTask> tasks) {
			this.name = name;
			this.requirements = new ArrayList<>(requirements);
			this.skipOnFailedCheck = skipOnFailedCheck;
			this.tasks = new ArrayList<>(tasks);
		}

		@Override
		public String getDisplayName() {
			return "Section: " + this.name;
		}

		@Override
		public boolean run() throws Exception {
			final CpsStepContext context = getContext() instanceof CpsStepContext ctx ? ctx : null;
			if (context == null)
				throw new IllegalStateException("CpsStepContext required");
			final CpsThread thread = CpsThread.current();
			if (thread == null)
				throw new IllegalStateException("No current CPS thread");

			final CpsThreadGroup group = thread.getGroup();
			final CpsFlowExecution execution = thread.getExecution();

			// -- exec section
			final List<CpsDeployTask> tasks = new ArrayList<>();
			for (DeploymentTask taskConfig : this.tasks) {
				final CpsDeployTask task = taskConfig.buildCpsTask();
				if (task == null) {
					throw new AbortException(
							Messages.DeployArtifacts_Exec_ERR_DeploymentTaskNoCpsTask(taskConfig.getDescriptor()
									.getDisplayName()));
				}
				tasks.add(task);
			}

			final Script body = DeployArtifactsStep.loadScript(execution);

			// Internal script variables; never populate the Jenkinsfile's
			// binding with the deployment task list.
			body.getBinding()
					.setVariable(GROOVY_VAR__PIPE_TASKS, tasks);

			this.cpsBody = context.newBodyInvoker(group.export(body), true)
					.withDisplayName(getDisplayName() + " » Tasks")
					.withContext(new DeploymentExecChecks(this.requirements, this.skipOnFailedCheck))
					.withCallback(BodyExecutionCallback.wrap(getContext()))
					.start();
			return false;
		}

		@Override
		public void stop(final Throwable cause) throws Exception {
			if (this.cpsBody != null)
				this.cpsBody.cancel(cause);
		}
	}
}
