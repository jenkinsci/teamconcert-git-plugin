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

package com.ibm.team.git.build.hjplugin.test.utils;

import java.io.File;
import java.io.IOException;

public class Utils {
	/**
	 * Returns a temporary file that will delete itself with JVM exists.
	 *  
	 * @return A File object that represents the temporary file.
	 * @throws IOException
	 */
	public static File getTemporaryFile() throws IOException  {
		File f = File.createTempFile("tmp", "log");  //$NON-NLS-1$//$NON-NLS-2$
		f.deleteOnExit();
		return f;
	}
}
