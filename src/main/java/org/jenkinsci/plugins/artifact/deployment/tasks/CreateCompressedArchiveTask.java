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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.logging.Level;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.artifact.bundle.context.Bundle;
import org.jenkinsci.plugins.artifact.deployment.steps.ACpsDeployTask;
import org.jenkinsci.plugins.artifact.deployment.steps.CpsDeployTask;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.ArtifactManager;
import jenkins.model.Jenkins;
import jenkins.util.BuildListenerAdapter;
import jenkins.util.VirtualFile;

public class CreateCompressedArchiveTask implements DeploymentTask {

	private static final long serialVersionUID = 1L;

	private String archivePath = null;
	private String archiveName = CpsTask.DEFAULT_ARCHIVE_NAME;
	private boolean flattenFileTree = true;
	private boolean tarXz = true;
	private boolean zip = true;

	@DataBoundConstructor
	public CreateCompressedArchiveTask() {
	}

	public String getArchivePath() {
		return this.archivePath;
	}

	public String getArchiveName() {
		return this.archiveName;
	}

	public boolean getFlattenFileTree() {
		return this.flattenFileTree;
	}

	public boolean getTarXz() {
		return this.tarXz;
	}

	public boolean getZip() {
		return this.zip;
	}

	@DataBoundSetter
	public void setArchivePath(final String archivePath) {
		this.archivePath = Util.fixEmptyAndTrim(archivePath);
	}

	@DataBoundSetter
	public void setArchiveName(final String archiveName) {
		this.archiveName = Util.fixEmptyAndTrim(archiveName);
	}

	@DataBoundSetter
	public void setFlattenFileTree(final boolean flattenFileTree) {
		this.flattenFileTree = flattenFileTree;
	}

	@DataBoundSetter
	public void setTarXz(final boolean tarXz) {
		this.tarXz = tarXz;
	}

	@DataBoundSetter
	public void setZip(final boolean zip) {
		this.zip = zip;
	}

	@Override
	public CpsDeployTask buildCpsTask() {
		return new CpsTask(this.archivePath, this.archiveName, this.flattenFileTree, this.tarXz, this.zip);
	}

	@Extension
	public static class Descriptor extends hudson.model.Descriptor<DeploymentTask> {

		@Override
		public String getDisplayName() {
			return "Create Compressed-Archive";
		}
	}

	public static class CpsTask extends ACpsDeployTask {

		private static final long serialVersionUID = 1L;

		public static final String DEFAULT_ARCHIVE_NAME = "artifacts";

		private final String archivePath;
		private final String archiveName;
		private final boolean flattenFileTree;
		private final boolean tarXz;
		private final boolean zip;

		public CpsTask(final String archivePath, final String archiveName, final boolean flattenFileTree,
				final boolean tarXz, final boolean zip) {
			this.archivePath = archivePath;
			this.archiveName = archiveName;
			this.flattenFileTree = flattenFileTree;
			this.tarXz = tarXz;
			this.zip = zip;
		}

		@Override
		public String getDisplayName() {
			return "Create Compressed-Archive";
		}

		@Override
		public boolean run() throws Exception {
			if ((this.tarXz && this.zip) == false) {
				log(Level.WARNING, "Skipping Archive Creation, no compression format selected!", null);
				getContext().onSuccess(null);
				return true;
			}

			final Bundle bundle = getBundle();
			if (bundle.isEmpty()) {
				log(Level.WARNING, "Skipping Archive Creation of the empty Bundle!", null);
				getContext().onSuccess(null);
				return true;
			}
			final Run<?, ?> build = getRun();
			final FilePath ws = getWorkspace(build);
			final ArtifactManager artifactManager = build.pickArtifactManager();
			final TaskListener listener = getTaskListener();
			final PrintStream log = listener.getLogger();

			// index files
			final Map<VirtualFile, String> fileIndex = compileFileIndex(bundle.getArtifactIterator());

			// resolve archive basePath
			final String basePath = resolveBasePath();
			final Map<String, String> fileTracker = new LinkedHashMap<>();

			if (this.tarXz) {
				final String targetPath = DEFAULT_ARCHIVE_NAME + ".tar.xz";
				final String archivePath = basePath + ".tar.xz";
				final FilePath archiveFile = ws.child(targetPath);
				fileTracker.put(archivePath, targetPath);
				try (OutputStream dataStream = archiveFile.write()) {
					try (TarArchiveOutputStream archiveStream = new TarArchiveOutputStream(
							new XZCompressorOutputStream(new BufferedOutputStream(dataStream)))) {
						// enable long filename support
						archiveStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
						// append bundle entries
						for (Iterator<Map.Entry<VirtualFile, String>> i = fileIndex.entrySet()
								.iterator(); i.hasNext();) {
							appendBundleEntry(archiveStream, i.next(), CpsTask::createTarEntry);
						}
					}
				}
				log.println("Archive created: " + archivePath);
			}

			if (this.zip) {
				final String targetPath = DEFAULT_ARCHIVE_NAME + ".zip";
				final String archivePath = basePath + ".zip";
				final FilePath archiveFile = ws.child(targetPath);
				fileTracker.put(archivePath, targetPath);
				try (OutputStream dataStream = archiveFile.write()) {
					try (ZipArchiveOutputStream archiveStream = new ZipArchiveOutputStream(
							new BufferedOutputStream(dataStream))) {
						// append bundle entries
						for (Iterator<Map.Entry<VirtualFile, String>> i = fileIndex.entrySet()
								.iterator(); i.hasNext();) {
							appendBundleEntry(archiveStream, i.next(), CpsTask::createZipEntry);
						}
					}
				}
				log.println("Archive created: " + archivePath);
			}

			final Launcher launcher = ws.createLauncher(listener);
			artifactManager.archive(ws, launcher, BuildListenerAdapter.wrap(listener), fileTracker);
			ws.deleteRecursive();
			getContext().onSuccess(null);
			return true;
		}

		protected String resolveBasePath() {
			final Map<String, Serializable> options = getOptions();
			String archivePath = Util.fixEmptyAndTrim(this.archivePath);
			if (archivePath != null)
				archivePath = Util.fixEmptyAndTrim(resolveText(options, archivePath));

			String archiveName = Util.fixEmptyAndTrim(this.archiveName);
			if (archiveName != null)
				archiveName = Util.fixEmptyAndTrim(resolveText(options, archiveName));
			if (archiveName == null)
				archiveName = DEFAULT_ARCHIVE_NAME;

			String basePath = archivePath == null ? archiveName : archivePath + '/' + archiveName;
			basePath = FilenameUtils.normalize(basePath, true);
			basePath = StringUtils.stripStart(basePath, "/~");
			if (StringUtils.isBlank(basePath))
				return DEFAULT_ARCHIVE_NAME;
			return basePath;
		}

		protected Map<VirtualFile, String> compileFileIndex(
				final Iterator<Map.Entry<VirtualFile, Map<String, Serializable>>> iterator) throws Exception {
			final Map<VirtualFile, String> fileIndex = new LinkedHashMap<>();
			while (iterator.hasNext()) {
				final Map.Entry<VirtualFile, Map<String, Serializable>> entry = iterator.next();
				final VirtualFile file = entry.getKey();
				final Map<String, Serializable> metadata = entry.getValue();
				final String filePath = getEntryString(metadata, Bundle.METADATA_FILE_PATH);
				final String artifactName = getEntryString(metadata, Bundle.METADATA_FILE_NAME);
				// validate
				if (file.length() == 0)
					log(Level.WARNING, String.format("Artifact (%s) is empty!", filePath + '/' + artifactName), null);
				// add artifact to archive
				final String entryPath = //
						(this.flattenFileTree || filePath.isBlank() ? "" : filePath + '/') + artifactName;
				fileIndex.put(file, entryPath);
			}
			return fileIndex;
		}

		protected <E extends ArchiveEntry, S extends ArchiveOutputStream<E>> void appendBundleEntry(
				final S archiveStream, final Map.Entry<VirtualFile, String> entry,
				final BiFunction<String, Long, E> entryFactory) throws Exception {
			final VirtualFile file = entry.getKey();
			// download artifact from artifact-manager
			final byte[] buffer;
			try {
				buffer = download(file, "Downloading %s from Archive to Memory");
			} catch (IOException | InterruptedException e) {
				throw new AbortException("Failed to load Artifact from Archive!");
			}
			// add artifact to archive
			appendArchiveEntry(archiveStream, entry.getValue(), buffer, file.length(), entryFactory);
		}

		protected <E extends ArchiveEntry, S extends ArchiveOutputStream<E>> void appendArchiveEntry(
				final S archiveStream, final String path, final byte[] data, final long size,
				final BiFunction<String, Long, E> entryFactory) throws IOException {
			final E entry = entryFactory.apply(path, size);
			archiveStream.putArchiveEntry(entry);
			archiveStream.write(data);
			archiveStream.closeArchiveEntry();
		}

		protected FilePath getWorkspace(final Run<?, ?> run) throws IOException, InterruptedException {
			final Node node = Jenkins.get();
			final FilePath wsRoot = node.createPath(run.getRootDir()
					.getAbsolutePath() + "/deployTasks");
			wsRoot.mkdirs();
			return wsRoot.createTempDir("createCompressedArchive", null);
		}

		protected static TarArchiveEntry createTarEntry(final String path, final long size) {
			final TarArchiveEntry entry = new TarArchiveEntry(path);
			entry.setSize(size);
			return entry;
		}

		protected static ZipArchiveEntry createZipEntry(final String path, final long size) {
			final ZipArchiveEntry entry = new ZipArchiveEntry(path);
			entry.setSize(size);
			return entry;
		}
	}
}
