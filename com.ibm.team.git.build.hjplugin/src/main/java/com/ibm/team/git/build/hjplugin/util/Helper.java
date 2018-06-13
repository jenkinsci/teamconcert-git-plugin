/*******************************************************************************
 * Copyright (c) 2014, 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.git.build.hjplugin.util;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.team.git.build.hjplugin.RTCUtils;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.util.FormValidation;

public class Helper {

	private static final Logger LOGGER = Logger.getLogger(Helper.class.getName());

	/** 
	 * merge two results, if both are errors only one stack trace can be included
	 * @param firstCheck The first validation done
	 * @param secondCheck The second validation done
	 * @return The merge of the 2 validations with a concatenated message and the highest severity
	 */
	public static FormValidation mergeValidationResults(
			FormValidation firstCheck, FormValidation secondCheck) {
		Throwable errorCause = secondCheck.getCause();
		if (errorCause == null) {
			errorCause = firstCheck.getCause();
		}
		String message;
		String firstMessage = firstCheck.getMessage();
		String secondMessage = secondCheck.getMessage();
		if (firstCheck.kind.equals(FormValidation.Kind.OK) && (RTCUtils.IsNullOrEmpty(firstMessage))) {
			message = secondCheck.renderHtml();
		} else if (secondCheck.kind.equals(FormValidation.Kind.OK) && (RTCUtils.IsNullOrEmpty(secondMessage))) {
			message = firstCheck.renderHtml();
		} else {
			message = firstCheck.renderHtml() +  "<br/>" + secondCheck.renderHtml(); //$NON-NLS-1$
		}
		FormValidation.Kind kind;
		if (firstCheck.kind.equals(secondCheck.kind)) {
			kind = firstCheck.kind;
		} else if (firstCheck.kind.equals(FormValidation.Kind.OK)) {
			kind = secondCheck.kind;
		} else if (firstCheck.kind.equals(FormValidation.Kind.ERROR) || secondCheck.kind.equals(FormValidation.Kind.ERROR)) {
			kind = FormValidation.Kind.ERROR;
		} else {
			kind = FormValidation.Kind.WARNING;
		}
		
		return FormValidation.respond(kind, message);
	}
	
	/**
	 * Returns the value of the build parameter 
	 * 
	 * @param build The Jenkins build. Cannot be <code>null</code>
	 * @param property Name of the parameter. Cannot be <code>null</code>
	 * @param listener The listener for messages
	 * @return the value of the property if found or <code>null</code> if, the value is empty or not found.
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static String getStringBuildParameter(Run<?,?> build, String property, TaskListener listener) throws IOException, InterruptedException {
		 LOGGER.finest("Helper.getStringBuildProperty : Begin"); //$NON-NLS-1$
		 if (LOGGER.isLoggable(Level.FINE)) {
			 LOGGER.fine("Helper.getStringBuildProperty: Finding value for property '" + property + "' in the build environment variables.");	  //$NON-NLS-1$ //$NON-NLS-2$
		 }
		 String value = Util.fixEmptyAndTrim(build.getEnvironment(listener).get(property));
		 if (value == null) {
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.fine("Helper.getStringBuildProperty: Cannot find value for property '" + property + "' in the build environment variables. Looking in the build parameters."); //$NON-NLS-1$ //$NON-NLS-2$
			}
			// check if parameter is available from ParametersAction
			value = getValueFromParametersAction(build, property);			
			if (value == null) {
				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.fine("Helper.getStringBuildProperty: Cannot find value for property '" + property + "' in the build parameters."); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
		 }
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.fine("Helper.getStringBuildProperty: Value for property '" + property + "' is '" + value + "'."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		 return value;
	}
	
	private static String getValueFromParametersAction(Run<?, ?> build, String key) {
		LOGGER.finest("Helper.getValueFromParametersAction : Begin"); //$NON-NLS-1$
		String value = null;
		for (ParametersAction paction : build.getActions(ParametersAction.class)) {
			List<ParameterValue> pValues = paction.getParameters();
			if (pValues == null) {
				continue;
			}
			for (ParameterValue pv : pValues) {
				if (pv instanceof StringParameterValue && pv.getName().equals(key)) {
					value = Util.fixEmptyAndTrim((String)pv.getValue());
					if (value != null) {
						break;
					}
				}
			}
			if (value != null) {
				break;
			}
		}
		if (LOGGER.isLoggable(Level.FINE)) {
			if (value == null) {
				LOGGER.fine("Helper.getValueFromParametersAction : Unable to find a value for key : " + key); //$NON-NLS-1$
			} else {
				LOGGER.fine("Helper.getValueFromParametersAction : Found value : " + value + " for key : " + key);  //$NON-NLS-1$//$NON-NLS-2$
			}
		}
		return value;
	}
	
	/**
	 * This method resolves any references to the environment variables and build parameters in a given 
	 * value
	 * 
	 * @param build The Jenkins {@link Run} from which some details are obtained. Never <code>null</code>
	 * @param fieldValue The field's value
	 * @param listener Task listener. Never <code>null</code>
	 * @return All references to environment variables or build parameters replaced
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static String resolveFieldParameterToValue(Run<?, ?> build, String fieldValue, TaskListener listener)
			throws IOException, InterruptedException {
		if (fieldValue == null) {
			return null;
		}
		// Util.replaceMacro() replaces $$ with a single $
		// this replace is required to retain consecutive $, like $$, in the template string. 		
		fieldValue = fieldValue.replaceAll("\\$\\$", "\\$\\$\\$\\$"); //$NON-NLS-1$ //$NON-NLS-2$
		// lookup and resolve environment variables
		String s = build.getEnvironment(listener).expand(fieldValue);

		if (build instanceof AbstractBuild) {
			// Util.replaceMacro() replaces $$ with a single $
			// this replace is required to retain consecutive $, like $$, in the template string 	
			s = s.replaceAll("\\$\\$", "\\$\\$\\$\\$"); //$NON-NLS-1$ //$NON-NLS-2$
			// lookup and resolve build variables, Build variables include the parameters passed in the Build
			s = Util.replaceMacro(s, ((AbstractBuild<?, ?>)build).getBuildVariableResolver());
		}
        return s;
	}
}
