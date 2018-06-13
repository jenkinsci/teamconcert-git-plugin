/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.git.build.hjplugin.test;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.ibm.team.git.build.hjplugin.test.utils.Utils;
import com.ibm.team.git.build.hjplugin.util.Helper;

import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.queue.QueueTaskFuture;
import hudson.util.StreamTaskListener;

/**
 * Integration tests for {@link Helper} class
 *
 */
public class HelperIT {
	@Rule public JenkinsRule r = new JenkinsRule();

	@Test public void testResolveFieldNameFromBuildProperty() throws Exception {
		FreeStyleProject prj = this.r.createFreeStyleProject();
		prj.addProperty(new ParametersDefinitionProperty(Arrays.asList(new ParameterDefinition[] 
				{new StringParameterDefinition("updateWI", "1234")}))); //$NON-NLS-1$ //$NON-NLS-2$

		File pollingFile = Utils.getTemporaryFile();
		QueueTaskFuture<FreeStyleBuild> future = prj.scheduleBuild2(0, (Cause) null);
		while(!future.isDone()) {
			// Intentionally empty
		}
		FreeStyleBuild build = future.get();
		
		String workItem = Helper.resolveFieldParameterToValue(build, "${updateWI}", //$NON-NLS-1$
						(new StreamTaskListener(pollingFile, Charset.forName("UTF-8")))); //$NON-NLS-1$
		assertEquals("1234", workItem); //$NON-NLS-1$
	}
}
