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
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

import org.jenkinsci.plugins.artifact.deployment.tasks.CpsCheck;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.TaskListener;

public class ExecDeployTaskStep extends Step implements Serializable {

	private static final long serialVersionUID = 1L;

	protected final CpsDeployTask task;

	@DataBoundConstructor
	public ExecDeployTaskStep(final CpsDeployTask task) {
		this.task = Objects.requireNonNull(task);
	}

	public CpsDeployTask getTask() {
		return this.task;
	}

	@Override
	public StepExecution start(final StepContext context) throws Exception {
		return new Execution(context, this.task);
	}

	public static final class Execution extends StepExecution {

		private static final long serialVersionUID = 1L;

		private final CpsDeployTask task;

		Execution(final StepContext context, final CpsDeployTask task) {
			super(context);
			this.task = task;
		}

		@Override
		public boolean start() throws Exception {
			final StepContext context = getContext();
			// The context marker must be supplied by the trusted parent.
			final DeploymentExecData data = context.get(DeploymentExecData.class);
			if (data == null) {
				throw new SecurityException(Messages.ExecDeployTask_Exec_ERR_ContextNotTrusted());
			}

			// Label this step's flow node with the configured task name.
			final FlowNode node = context.get(FlowNode.class);
			final String displayName = this.task.getDisplayName();
			if (node != null && displayName != null) {
				node.addAction(new LabelAction(displayName));
			}

			// -- check requirements
			final DeploymentExecChecks checksDto = context.get(DeploymentExecChecks.class);
			if (checksDto != null) {
				Throwable checkError = null;
				for (Iterator<CpsCheck> i = checksDto.getChecks()
						.iterator(); i.hasNext();) {
					final CpsCheck cpsCheck = i.next();
					if (cpsCheck == null)
						continue;
					try {
						cpsCheck.check(context, data);
					} catch (Throwable e) {
						checkError = e;
						break;
					}
				}
				if (checkError != null) {
					if (checksDto.getSkipOnFail()) {
						ACpsDeployTask.markFlowNodeAsSkipped(node);
						context.get(TaskListener.class)
								.getLogger()
								.println("WARNING: " + checkError.getMessage());
						context.onSuccess(null);
						return true;
					} else {
						context.get(TaskListener.class)
								.error(checkError.getMessage());
						context.onFailure(checkError);
						return true;
					}
				}
			}

			// -- exec Task
			return this.task.exec(getContext(), data);
		}

		@Override
		public void stop(final Throwable cause) throws Exception {
			this.task.stop(cause);
			super.stop(cause);
		}
	}

	@Extension
	public static final class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "execDeployTask";
		}

		@Override
		public String getDisplayName() {
			return "Execute Deployment-Task";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return Set.of(DeploymentExecData.class);
		}
	}
}
