/*******************************************************************************
 * Copyright (c) 2001, 2020 IBM Corp. and others
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which accompanies this
 * distribution and is available at https://www.eclipse.org/legal/epl-2.0/
 * or the Apache License, Version 2.0 which accompanies this distribution and
 * is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This Source Code may also be made available under the following
 * Secondary Licenses when the conditions for such availability set
 * forth in the Eclipse Public License, v. 2.0 are satisfied: GNU
 * General Public License, version 2 with the GNU Classpath
 * Exception [1] and GNU General Public License, version 2 with the
 * OpenJDK Assembly Exception [2].
 *
 * [1] https://www.gnu.org/software/classpath/license.html
 * [2] http://openjdk.java.net/legal/assembly-exception.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0 OR GPL-2.0 WITH Classpath-exception-2.0 OR LicenseRef-GPL-2.0 WITH Assembly-exception
 *******************************************************************************/
package j9vm.runner;
import java.io.*;
import java.util.Properties;
import java.util.Iterator;
import java.util.Map;
import java.lang.reflect.Method;

public class Runner {
	public static final String systemPropertyPrefix = "j9vm.";

	protected String className;
	protected String exeName;
	protected String bootClassPath;
	protected String userClassPath;
	protected String javaVersion;
	protected OutputCollector inCollector;
	protected OutputCollector errCollector;

	private final String heapOptions = "-Xms64m -Xmx64m";

	public Runner(String className, String exeName, String bootClassPath, String userClassPath, String javaVersion)  {
		super();
		this.className = className;
		this.exeName = exeName;
		this.bootClassPath = bootClassPath;
		this.userClassPath = userClassPath;
		this.javaVersion = javaVersion;
	}

	public String getBootClassPathOption () {
		if (bootClassPath == null)  return "";
		return "-Xbootclasspath:" + bootClassPath;
	}

	public String getUserClassPathOption () {
		if (userClassPath == null)  return "";
		return "-classpath " + userClassPath;
	}

	public String getJ9VMSystemPropertiesString() {
		String result = "";
		Properties systemProperties = System.getProperties();
		Iterator it = systemProperties.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry entry = (Map.Entry) it.next();
			String key = (String) entry.getKey();
			if(key.startsWith(systemPropertyPrefix)) {
				String value = (String) entry.getValue();
				result += "-D" + key + "=" + value + " ";
			}

		}
		return result;
	}

	public String getCustomCommandLineOptions() {
		/* For sub-classes to override, if desired. */
		return "";
	}

	public String getCommandLine() {
		return exeName + " " + heapOptions + " " + getCustomCommandLineOptions() + " "
			+ getJ9VMSystemPropertiesString() + " " + getBootClassPathOption() + " "
			+ getUserClassPathOption() + " ";
	}
	
	public String getTestClassArguments() {
		/* For sub-classes to override, if desired. */
		return "";
	}
	
	public int runCommandLine(String commandLine)  {
		System.out.println("command: " + commandLine);
		System.out.println();
		Process process;
		try  {
			process = Runtime.getRuntime().exec(commandLine);
		} catch (Throwable e)  {
			System.out.println("Exception starting process!");
			System.out.println("(" + e.getMessage() + ")");
			e.printStackTrace();
			return 99999;
		}

		BufferedInputStream inStream = new BufferedInputStream(process.getInputStream());
		BufferedInputStream errStream = new BufferedInputStream(process.getErrorStream());
		inCollector = new OutputCollector(inStream);
		errCollector = new OutputCollector(errStream);
		inCollector.start();
		errCollector.start();
		try  {
			process.waitFor();
			inCollector.join();
			errCollector.join();
		} catch (InterruptedException e)  {
			/* Nothing. */
		}
		/* Must release process resources here, or wimpy platforms
		   like Neutrino will run out of handles! */
		int retval = process.exitValue();
		process.destroy(); process = null;
		System.gc();
		return retval;
	}

	public boolean run()  {
		int retval = runCommandLine(getCommandLine() + " " + className + " " + getTestClassArguments());
		if ( 0 != retval ) {
			System.out.println("no-zero exit value: " + retval);
			return false;
		}
		return true;
	}

}
