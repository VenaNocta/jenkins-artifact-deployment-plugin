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
package org.jenkinsci.plugins.artifact.deployment.repos;

import java.util.UUID;

import org.jenkinsci.plugins.artifact.deployment.steps.CpsDeployTask;
import org.jenkinsci.plugins.artifact.deployment.tasks.DeploymentTask;
import org.jenkinsci.plugins.artifact.lifecycle.config.ArtifactRepository;
import org.jenkinsci.plugins.artifact.lifecycle.config.GlobalRepositoryIndex;
import org.jenkinsci.plugins.identifieddata.api.IdentifierUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.util.ListBoxModel;

public class SonatypeNexusUploadTask implements DeploymentTask {

	private static final long serialVersionUID = 1L;

	@CheckForNull
	protected UUID repoId;

	@DataBoundConstructor
	public SonatypeNexusUploadTask() {
	}

	@CheckForNull
	public UUID getRepoId() {
		return this.repoId;
	}

	public SonatypeNexusRepository getRepo() {
		final ArtifactRepository repository = GlobalRepositoryIndex.get()
				.getRepoById(this.repoId);
		if (SonatypeNexusRepository.isSupported(repository))
			return (SonatypeNexusRepository) repository;
		return null;
	}

	public void setRepoIdValue(final UUID value) {
		this.repoId = value;
	}

	@DataBoundSetter
	public void setRepoId(String value) {
		this.repoId = IdentifierUtils.fixInvalidUUID(value);
	}

	@Override
	public CpsDeployTask buildCpsTask() {
		return new SonatypeNexusUploader(getRepo());
	}

	public static boolean isSupported(final ArtifactRepository repository) {
		return repository instanceof SonatypeNexusRepository nexusRepo && nexusRepo.hasDeploymentSupport();
	}

	@Extension
	public static class Descriptor extends hudson.model.Descriptor<DeploymentTask> {

		@Override
		public String getDisplayName() {
			return "Sonatype Nexus Repository Uploader";
		}

		public ListBoxModel doFillRepoIdItems(@AncestorInPath final Item item, @QueryParameter final String repoId) {
			return IdentifierUtils.allValuesAsListBox(GlobalRepositoryIndex.get()
					.getRepos(SonatypeNexusUploadTask::isSupported), repoId, ArtifactRepository::getName, false);
		}
	}
}
