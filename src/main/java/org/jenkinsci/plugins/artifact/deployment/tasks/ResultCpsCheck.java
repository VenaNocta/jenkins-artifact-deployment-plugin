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

import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.artifact.deployment.steps.DeploymentExecData;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;

public class ResultCpsCheck implements CpsCheck {

	private static final long serialVersionUID = 1L;

	public static final String MATCH_TYPE__WORSE_THAN = "worse than";
	public static final String MATCH_TYPE__WORSE_OR_EQUAL = "worse or equal";
	public static final String MATCH_TYPE__EQUAL = "equal";
	public static final String MATCH_TYPE__BETTER_OR_EQUAL = "better or equal";
	public static final String MATCH_TYPE__BETTER_THAN = "better than";

	public static final String RESULT__SUCCESS = Result.SUCCESS.toString();
	public static final String RESULT__UNSTABLE = Result.UNSTABLE.toString();
	public static final String RESULT__FAILURE = Result.FAILURE.toString();
	public static final String RESULT__NOT_BUILT = Result.NOT_BUILT.toString();
	public static final String RESULT__ABORTED = Result.ABORTED.toString();

	public static final List<String> MATCH_TYPES = List.of( //
			MATCH_TYPE__WORSE_THAN, MATCH_TYPE__WORSE_OR_EQUAL, MATCH_TYPE__EQUAL, //
			MATCH_TYPE__BETTER_OR_EQUAL, MATCH_TYPE__BETTER_THAN);
	// would have loved to use Result.all but someone made it private!
	public static final List<String> RESULTS = List.of( //
			RESULT__SUCCESS, RESULT__UNSTABLE, RESULT__FAILURE, //
			RESULT__NOT_BUILT, RESULT__ABORTED);

	private final String matchType;
	private final String value;

	@DataBoundConstructor
	public ResultCpsCheck(String matchType, String value) {
		matchType = Util.fixEmptyAndTrim(matchType);
		value = Util.fixEmptyAndTrim(value);
		this.matchType = MATCH_TYPES.contains(matchType) ? matchType : MATCH_TYPE__EQUAL;
		this.value = RESULTS.contains(value) ? value : RESULT__SUCCESS;
	}

	public String getMatchType() {
		return this.matchType;
	}

	public String getValue() {
		return this.value;
	}

	@Override
	public void check(final StepContext context, final DeploymentExecData data) throws Exception {
		Result result = context.get(Run.class)
				.getResult();
		if (result == null)
			result = Result.SUCCESS;
		final Result value = Result.fromString(this.value);
		if (this.matchType == null || this.value == null) {
			// theoretically unreachable
			throw new AbortException(getDescriptor().getDisplayName() + " » Invalid Data provided!");
		}
		switch (this.matchType) {
		case MATCH_TYPE__WORSE_THAN:
			if (result.isWorseThan(value))
				return;
			break;
		case MATCH_TYPE__WORSE_OR_EQUAL:
			if (result.isWorseOrEqualTo(value))
				return;
			break;
		case MATCH_TYPE__EQUAL:
		default:
			if (result.equals(value))
				return;
			break;
		case MATCH_TYPE__BETTER_OR_EQUAL:
			if (result.isBetterOrEqualTo(value))
				return;
			break;
		case MATCH_TYPE__BETTER_THAN:
			if (result.isBetterThan(value))
				return;
			break;
		}
		throw new AbortException("Check: Result not " + this.matchType + " " + value.toString());
	}

	@Extension
	public static class Descriptor extends hudson.model.Descriptor<CpsCheck> {

		@Override
		public String getDisplayName() {
			return "Result Check";
		}

		public ListBoxModel doFillMatchTypeItems(@AncestorInPath Item item, @QueryParameter String matchType) {
			matchType = StringUtils.trimToNull(matchType);
			matchType = matchType != null && MATCH_TYPES.contains(matchType) ? matchType : MATCH_TYPE__EQUAL;
			final ListBoxModel dropdown = new ListBoxModel();
			for (String type : MATCH_TYPES) {
				dropdown.add(new Option(type, type, type == matchType));
			}
			return dropdown;
		}

		public ListBoxModel doFillValueItems(@AncestorInPath Item item, @QueryParameter String value) {
			value = StringUtils.trimToNull(value);
			value = value != null && RESULTS.contains(value) ? value : RESULT__SUCCESS;
			final ListBoxModel dropdown = new ListBoxModel();
			for (String type : RESULTS) {
				dropdown.add(new Option(type, type, type == value));
			}
			return dropdown;
		}
	}
}
