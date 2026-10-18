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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.jenkinsci.plugins.artifact.bundle.context.Bundle;
import org.jenkinsci.plugins.artifact.bundle.context.Bundles;
import org.jenkinsci.plugins.artifact.deployment.tasks.MetadataEntry;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.ProxyConfiguration;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.httpclient.RobustHTTPClient;
import jenkins.model.Jenkins;
import jenkins.util.JenkinsJVM;
import jenkins.util.SystemProperties;
import jenkins.util.VirtualFile;

public abstract class ACpsDeployTask implements CpsDeployTask {

	private static final long serialVersionUID = 1L;

	private StepContext context;
	private DeploymentExecData data;

	@Override
	public boolean exec(final StepContext context, final DeploymentExecData data) throws Exception {
		synchronized (this) {
			this.context = Objects.requireNonNull(context);
			this.data = Objects.requireNonNull(data);
			return run();
		}
	}

	public abstract boolean run() throws Exception;

	protected final StepContext getContext() {
		return this.context;
	}

	protected Logger getLogger() {
		return Logger.getLogger(getClass().getName());
	}

	protected Run<?, ?> getRun() throws IOException, InterruptedException {
		return this.context.get(Run.class);
	}

	protected EnvVars getEnv() throws IOException, InterruptedException {
		return this.context.get(EnvVars.class);
	}

	protected TaskListener getTaskListener() throws IOException, InterruptedException {
		return this.context.get(TaskListener.class);
	}

	protected Result getResult() throws IOException, InterruptedException {
		if (this.context.get(FlowExecution.class) instanceof CpsFlowExecution exec)
			return exec.getResult();
		// won't reach here but let's be sure
		return this.context.get(Run.class)
				.getResult();
	}

	protected void setResult(final Result result) throws IOException, InterruptedException {
		if (this.context.get(FlowExecution.class) instanceof CpsFlowExecution exec) {
			exec.setResult(result);
		} else {
			// won't reach here but let's be sure
			this.context.get(Run.class)
					.setResult(result);
		}
	}

	public DeploymentExecData getData() {
		return this.data;
	}

	public Map<String, Serializable> getOptions() {
		return this.data.getOptions();
	}

	public Bundle getBundle() {
		return this.data.getBundle();
	}

	protected void log(final Level level, final String msg, final Throwable t) {
		try {
			final PrintStream stream = getTaskListener().getLogger();
			final int lvl = level == null ? Level.INFO.intValue() : level.intValue();

			final String header;
			if (lvl <= Level.FINE.intValue())
				header = "DEBUG: ";
			else if (lvl <= Level.CONFIG.intValue())
				header = "CONF: ";
			else if (lvl <= Level.INFO.intValue())
				header = "";
			else if (lvl <= Level.WARNING.intValue())
				header = "WARN: ";
			else
				header = "ERROR: ";

			stream.println(header + msg);
			if (t != null)
				t.printStackTrace(stream);
			return;
		} catch (IOException | InterruptedException e) {
		}
		// ok, when it doesn't work we push it to the instance logger
		final LogRecord record = new LogRecord(level, msg);
		if (t != null)
			record.setThrown(t);
		getLogger().log(record);
	}

	// --- FlowNode Tools

	public static void markFlowNodeAsSkipped(final FlowNode node) throws IOException {
		addTagToFlowNode(node, "STAGE_STATUS", "SKIPPED_FOR_CONDITIONAL");
	}

	public static void addTagToFlowNode(final FlowNode node, final String tagName, final String tagValue)
			throws IOException {
		if (node == null)
			return;

		TagsAction tagsAction = node.getAction(TagsAction.class);
		if (tagsAction == null) {
			tagsAction = new TagsAction();
			tagsAction.addTag(tagName, tagValue);
			node.addAction(tagsAction);
		} else if (tagsAction.getTagValue(tagName) == null) {
			tagsAction.addTag(tagName, tagValue);
		}
		node.save();
	}

	// --- Jenkins Tools

	protected <C extends IdCredentials> void useCreds(final Class<C> type, final String id,
			final Function<C, Boolean> consumer) throws IOException, InterruptedException {
		final String credentialsId = id;
		if (credentialsId == null)
			return;
		final Run<?, ?> run = getRun();
		final C credentials = CredentialsProvider.findCredentialById(credentialsId, type, run);
		if (credentials == null)
			throw new AbortException(String.format("Credentials (id: %s) Unavailable!", credentialsId));
		if (consumer.apply(credentials))
			CredentialsProvider.track(run, credentials);
	}

	protected ProxyConfiguration getProxyConfiguration() {
		if (!JenkinsJVM.isJenkinsJVM())
			return null;
		Jenkins jenkins = Jenkins.getInstanceOrNull();
		return jenkins == null ? null : jenkins.proxy;
	}

	protected org.apache.http.HttpHost getProxyHost() {
		final ProxyConfiguration proxyConfig = getProxyConfiguration();
		if (proxyConfig == null)
			return null;
		final String hostname = proxyConfig.getName();
		if (hostname == null)
			return null;
		return new HttpHost(hostname, proxyConfig.getPort());
	}

	protected org.apache.http.auth.Credentials getProxyCredentials() {
		final ProxyConfiguration proxyConfig = getProxyConfiguration();
		if (proxyConfig == null)
			return null;
		final String user = proxyConfig.getUserName();
		if (user == null)
			return null;
		return new UsernamePasswordCredentials(user, proxyConfig.getSecretPassword()
				.getPlainText());
	}

	protected String getJenkinsUserAgent() {
		if (!JenkinsJVM.isJenkinsJVM()
				|| SystemProperties.getBoolean("jenkins.UserAgentURLConnectionDecorator.DISABLED"))
			return null;
		return "Jenkins/" + Jenkins.getVersion();
	}

	// --- Bundle Tools

	protected <S extends Serializable> S getEntry(final Map<String, Serializable> metadata, final Class<S> type,
			final String... keys) {
		return Bundles.getMetadataEntry(metadata, type, keys);
	}

	protected String getEntryString(final Map<String, Serializable> metadata, final String... keys) {
		return StringUtils.trimToNull(getEntry(metadata, String.class, keys));
	}

	protected Map<String, Serializable> resolveEntryData(final List<MetadataEntry> entries,
			final Map<String, Serializable> metadata) {
		final Map<String, Serializable> data = new LinkedHashMap<>();
		for (MetadataEntry ref : entries) {
			for (String key : ref.getMetaKeys()) {
				Serializable obj = metadata.get(key);
				if (obj != null) {
					if (obj instanceof String s)
						obj = s.trim();
					data.putIfAbsent(key, obj);
					break;
				}
			}
		}
		return data;
	}

	public static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{\\s*([^}]+?)\\s*}");

	protected String resolveText(final Map<String, Serializable> metadata, final String template) {
		final Matcher matcher = PLACEHOLDER.matcher(template);
		final StringBuffer result = new StringBuffer();

		while (matcher.find()) {
			final String key = matcher.group(1)
					.trim();
			final String value = String.valueOf(metadata.getOrDefault(key, ""));
			matcher.appendReplacement(result, Matcher.quoteReplacement(value));
		}

		matcher.appendTail(result);
		return result.toString();
	}

	protected String fullPath(final VirtualFile root, final VirtualFile file) {
		return fullPath(root, file, null);
	}

	protected String fullPath(final VirtualFile root, final VirtualFile file, final String prefix,
			final String suffix) {
		return fullPath(root, file, name -> String.format("%s%s.%s", prefix, name, suffix));
	}

	protected String fullPath(@NonNull final VirtualFile root, @NonNull final VirtualFile file,
			final Function<String, String> nameMapper) {
		return Bundles.fullPath(root, file, nameMapper);
	}

	protected byte[] download(final VirtualFile source, final String msgFmt) throws IOException, InterruptedException {
		final RobustHTTPClient httpClient = new RobustHTTPClient();
		final URL url = source.toExternalURL();

		try (final ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			if (url == null) {
				try (InputStream dataStream = source.open()) {
					dataStream.transferTo(output);
				}
			} else {
				httpClient.connect("download", String.format(msgFmt, RobustHTTPClient.sanitize(url)),
						client -> client.execute(new HttpGet(url.toString())), response -> {
							try (InputStream is = response.getEntity()
									.getContent()) {
								is.transferTo(output);
							}
						}, getTaskListener());
			}
			return output.toByteArray();
		}
	}
}
