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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.artifact.bundle.context.Bundle;
import org.jenkinsci.plugins.artifact.bundle.context.BundleContext;
import org.jenkinsci.plugins.artifact.bundle.context.Bundles;
import org.jenkinsci.plugins.artifact.bundle.steps.BundleStepExecution;
import org.jenkinsci.plugins.artifact.deployment.config.DeploymentPipeIndex;
import org.jenkinsci.plugins.artifact.deployment.config.PipeConfig;
import org.jenkinsci.plugins.artifact.deployment.tasks.DeploymentTask;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.cps.CpsThreadGroup;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.collect.ImmutableSet;

import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.lang.Script;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.ArtifactManager;

public class DeployArtifactsStep extends Step implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final String GROOVY_SCRIPT__DEPLOY_PIPE_BODY = "DeployPipeBody.groovy";
	public static final String GROOVY_VAR__PIPE_TASKS = "deploymentPipeTasks";

	private final Map<String, Serializable> options = new ConcurrentHashMap<>();
	private final Map<String, Serializable> metadata = new ConcurrentHashMap<>();

	private String pipe = null;

	/**
	 * Comma- or space-separated list of patterns of files/directories to be
	 * archived.
	 */
	private String artifacts = null;

	private String bundle = null;

	@DataBoundConstructor
	public DeployArtifactsStep(final String pipe) {
		this.pipe = Util.fixEmptyAndTrim(pipe);
	}

	public String getPipe() {
		return this.pipe;
	}

	public String getArtifacts() {
		return this.artifacts;
	}

	public Map<String, Serializable> getOptions() {
		return this.options;
	}

	public Map<String, Serializable> getMetadata() {
		return this.metadata;
	}

	public String getBundle() {
		return bundle;
	}

	@DataBoundSetter
	// backward compatibility
	@Deprecated(forRemoval = true)
	public void setRepo(final String repo) {
		this.pipe = Util.fixEmptyAndTrim(repo);
	}

	@DataBoundSetter
	public void setArtifacts(final String artifacts) {
		this.artifacts = StringUtils.trimToNull(artifacts);
	}

	@DataBoundSetter
	public void setOptions(final Map<String, Serializable> options) {
		if (options == null)
			this.options.clear();
		else
			this.options.putAll(options);
	}

	@DataBoundSetter
	public void setMetadata(final Map<String, Serializable> metadata) {
		if (metadata == null)
			this.metadata.clear();
		else
			this.metadata.putAll(metadata);
	}

	@DataBoundSetter
	public void setBundle(final String bundle) {
		this.bundle = StringUtils.trimToNull(bundle);
	}

	@Override
	public StepExecution start(final StepContext context) throws Exception {
		final PipeConfig pipe = DeploymentPipeIndex.getResolver()
				.findEntryByName(context.get(Run.class)
						.getParent(), this.pipe);
		if (pipe == null)
			return new InvalidStepExecution(context) {
				private static final long serialVersionUID = 1L;

				protected void log(TaskListener listener) {
					listener.error(Messages.DeployArtifacts_Exec_ERR_PipeNotFound(DeployArtifactsStep.this.pipe));
				}
			};

		return new Execution(context, this, pipe);
	}

	public static final class Execution extends BundleStepExecution {

		private static final long serialVersionUID = 1L;

		private final DeployArtifactsStep step;
		private final PipeConfig pipe;

		protected BodyExecution cpsBody = null;

		protected Execution(final StepContext context, final DeployArtifactsStep step, final PipeConfig pipe) {
			super(context);
			this.step = step;
			this.pipe = pipe;
		}

		@NonNull
		protected Bundle getSelectedBundle() throws IOException, InterruptedException {
			final String artifacts = getArtifactPaths(this.step.getArtifacts());
			final BundleContext context = getBundleContext();
			Bundle bundle = context == null ? null : context.getBundle(this.step.getBundle());

			if (artifacts != null) {
				if (bundle == null)
					// create anonymous Bundle
					bundle = new Bundle(null);
				else
					bundle = bundle.copy();

				final ArtifactManager artifactManager = getRun().pickArtifactManager();
				Bundles.indexArtifacts(bundle, artifactManager, artifacts, null, true, this.step.getMetadata());
			}
			// if nothing special is selected, return "default" Bundle
			return bundle == null ? (context == null ? new Bundle(null) : context.getBundle()) : bundle;
		}

		@Override
		public boolean start() throws Exception {
			final CpsStepContext context = getContext() instanceof CpsStepContext ctx ? ctx : null;
			if (context == null)
				throw new IllegalStateException("deployArtifacts requires workflow-cps");
			final CpsThread thread = CpsThread.current();
			if (thread == null) {
				throw new IllegalStateException("No current CPS thread");
			}

			final CpsThreadGroup group = thread.getGroup();
			final CpsFlowExecution execution = thread.getExecution();

			final List<CpsDeployTask> tasks = new ArrayList<>();

			for (DeploymentTask taskConfig : this.pipe.getTasks()) {
				final CpsDeployTask task = taskConfig.buildCpsTask();
				if (task == null) {
					throw new AbortException(
							Messages.DeployArtifacts_Exec_ERR_DeploymentTaskNoCpsTask(taskConfig.getDescriptor()
									.getDisplayName()));
				}
				tasks.add(task);
			}

			final InputStream resource = getClass().getResourceAsStream(GROOVY_SCRIPT__DEPLOY_PIPE_BODY);
			if (resource == null) {
				throw new IllegalStateException("Missing " + GROOVY_SCRIPT__DEPLOY_PIPE_BODY);
			}

			final Script body;
			try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
				body = execution.getShell()
						.parse(reader, GROOVY_SCRIPT__DEPLOY_PIPE_BODY);
			}

			// Internal script variables; never populate the Jenkinsfile's
			// binding with the deployment task list.
			body.getBinding()
					.setVariable(GROOVY_VAR__PIPE_TASKS, tasks);

			final DeploymentExecData data = new DeploymentExecData( //
					this.pipe.getUuid(), this.step.getOptions(), getSelectedBundle());

			this.cpsBody = context.newBodyInvoker(group.export(body), true)
					.withDisplayName(this.step.getDescriptor()
							.getDisplayName() + " » Tasks")
					.withContext(data)
					.withCallback(BodyExecutionCallback.wrap(getContext()))
					.start();
			return false;
		}

		@Override
		public void stop(final Throwable cause) throws Exception {
			if (this.cpsBody != null)
				this.cpsBody.cancel(cause);
			super.stop(cause);
		}
	}

	public abstract static class InvalidStepExecution extends StepExecution {

		private static final long serialVersionUID = 1L;

		public InvalidStepExecution(@NonNull final StepContext context) {
			super(context);
		}

		protected abstract void log(final TaskListener listener);

		@Override
		public boolean start() throws Exception {
			log(getContext().get(TaskListener.class));
			getContext().setResult(Result.FAILURE);
			return true;
		}

	}

	@Extension
	public static class DeployArtifactDescriptor extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "deployArtifacts";
		}

		@Override
		public String getDisplayName() {
			return Messages.DeployArtifacts_DisplayName();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Run.class, EnvVars.class, TaskListener.class);
		}

		@Override
		public Set<? extends Class<?>> getProvidedContext() {
			return ImmutableSet.of();
		}
	}
}
