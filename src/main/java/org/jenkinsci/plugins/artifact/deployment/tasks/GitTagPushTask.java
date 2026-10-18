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

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.artifact.bundle.context.Bundle;
import org.jenkinsci.plugins.artifact.deployment.steps.ACpsDeployTask;
import org.jenkinsci.plugins.artifact.deployment.steps.CpsDeployTask;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.BranchJobProperty;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import jenkins.branch.Branch;
import jenkins.util.VirtualFile;

public class GitTagPushTask implements DeploymentTask {

	private static final long serialVersionUID = 1L;

	private List<MetadataEntry> entries;
	private String triggerKey;
	private String triggerValueRegex;
	private String tag;
	private String comment;
	private boolean force = true;

	@DataBoundConstructor
	public GitTagPushTask() {
	}

	public List<MetadataEntry> getEntries() {
		return this.entries == null ? new ArrayList<>(0) : new ArrayList<>(this.entries);
	}

	public String getTriggerKey() {
		return this.triggerKey;
	}

	public String getTriggerValueRegex() {
		return this.triggerValueRegex;
	}

	public String getTag() {
		return this.tag;
	}

	public String getComment() {
		return this.comment;
	}

	public boolean getForcePush() {
		return this.force;
	}

	@DataBoundSetter
	public void setEntries(final List<MetadataEntry> entries) {
		this.entries = new ArrayList<>(entries);
	}

	@DataBoundSetter
	public void setTriggerKey(@Nullable final String triggerKey) {
		this.triggerKey = Util.fixEmptyAndTrim(triggerKey);
	}

	@DataBoundSetter
	public void setTriggerValueRegex(@Nullable final String triggerValueRegex) {
		this.triggerValueRegex = Util.fixEmptyAndTrim(triggerValueRegex);
	}

	@DataBoundSetter
	public void setTag(@Nullable final String tag) {
		this.tag = Util.fixEmptyAndTrim(tag);
	}

	@DataBoundSetter
	public void setComment(@Nullable final String comment) {
		this.comment = Util.fixEmptyAndTrim(comment);
	}

	@DataBoundSetter
	public void setForcePush(final boolean force) {
		this.force = force;
	}

	@Override
	public CpsDeployTask buildCpsTask() {
		return new CpsTask(getEntries(), this.triggerKey, this.triggerValueRegex, this.tag, this.comment, this.force);
	}

	@Extension
	public static class Descriptor extends hudson.model.Descriptor<DeploymentTask> {

		@Override
		public String getDisplayName() {
			return "Git Push Tag";
		}
	}

	public static class CpsTask extends ACpsDeployTask {

		private static final long serialVersionUID = 1L;

		private final List<MetadataEntry> entries;
		private final String triggerKey;
		private final Pattern triggerPattern;
		private final String tag;
		private final String comment;
		private final boolean force;

		public CpsTask(final List<MetadataEntry> entries, final String triggerKey, String triggerValueRegex,
				final String tag, final String comment, final boolean force) {
			this.entries = new ArrayList<>(entries);
			this.triggerKey = triggerKey;
			this.tag = tag;
			this.comment = comment;
			this.force = force;
			// convert REGEX
			Pattern pattern;
			try {
				pattern = Pattern.compile(
						(triggerValueRegex = Util.fixEmptyAndTrim(triggerValueRegex)) == null ? "" : triggerValueRegex);
			} catch (PatternSyntaxException e) {
				pattern = null;
			}
			this.triggerPattern = pattern;
		}

		@Override
		public String getDisplayName() {
			return "Git push Tags";
		}

		@Override
		public boolean run() throws Exception {
			if (this.triggerPattern == null) {
				// theoretically unreachable
				throw new AbortException(getDisplayName() + " » Invalid Regex-Pattern provided!");
			}

			final StepContext ctx = getContext();
			final Run<?, ?> run = ctx.get(Run.class);
			final WorkflowJob job = run.getParent() instanceof WorkflowJob wfJob ? wfJob : null;
			if (job == null)
				throw new AbortException(getDisplayName() + ": not a Multibranch Pipeline!");

			final BranchJobProperty property = job.getProperty(BranchJobProperty.class);
			if (property == null)
				throw new AbortException(getDisplayName() + ": Branch-Property reference missing!");

			final Branch branch = property.getBranch();
			if (branch == null)
				throw new AbortException(getDisplayName() + ": Branch reference missing!");

			final GitSCM gitScm = branch.getScm() instanceof GitSCM scm ? scm : null;
			if (gitScm == null)
				throw new AbortException(getDisplayName() + " » Only supports GitSCM!");

			final Bundle bundle = getBundle();
			for (Iterator<Map.Entry<VirtualFile, Map<String, Serializable>>> i = //
					bundle.getArtifactIterator(); i.hasNext();) {
				final Map.Entry<VirtualFile, Map<String, Serializable>> entry = i.next();
				final Map<String, Serializable> metadata = entry.getValue();
				handleArtifact(entry.getKey(), resolveEntryData(this.entries, metadata), gitScm);
			}
			return true;
		}

		protected void handleArtifact(final VirtualFile artifact, final Map<String, Serializable> data,
				final GitSCM gitScm) throws IOException, InterruptedException {
			final String triggerValue = data.get(this.triggerKey) instanceof String s ? s : null;
			if (triggerValue == null || !this.triggerPattern.matcher(triggerValue)
					.matches())
				return;

			final StepContext ctx = getContext();
			final Run<?, ?> run = ctx.get(Run.class);

			final FilePath workspace = ctx.get(FilePath.class);
			final TaskListener listener = ctx.get(TaskListener.class);

			final GitClient git = gitScm.createClient(listener, run.getEnvironment(listener), run, workspace, null);

			// remote config
			RemoteConfig remote = gitScm.getRepositoryByName("origin");
			if (remote == null)
				throw new AbortException("Unknown remote: " + "origin");
			remote = gitScm.getParamExpandedRepo(run.getEnvironment(listener), remote);

			final URIish uri = remote.getURIs()
					.get(0);

			// create/update tag
			final String tag = resolveText(data, this.tag);
			git.tag(tag, resolveText(data, this.comment));

			// push tag
			git.push()
					.to(uri)
					.ref("refs/tags/" + tag)
					.force(this.force)
					.execute();

			listener.getLogger()
					.println("Successfully pushed tag: " + tag);
		}
	}
}
