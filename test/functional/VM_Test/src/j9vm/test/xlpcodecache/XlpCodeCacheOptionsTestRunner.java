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

package j9vm.test.xlpcodecache;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Pattern;

import j9vm.runner.Runner;
import j9vm.test.xlphelper.*;

/**
 * This test checks -Xlp:codecache with different page size and page type.
 * A well formed -Xlp:codecache option should never cause JVM startup to fail.
 * In case of unsupported page size, JVM will try to use default large page size,
 * failing which it will use default page size.
 * 
 * Default/preferred page size on various platforms is as follows:
 * 		On AIX, it is 64K if available.
 * 		On Linux x86 (including amd64), it is 2M if available.
 * 		On zLinux, it is 1M if available.
 * 		On z/OS, it is 1M pageable if available.
 * 
 * -Xlp:codecache options with different page size and page type are added to an Xlp option array list.
 * For each entry in Xlp option array list, JVM is started along with -verbose:sizes option.
 * -verbose:sizes output prints the page size and type being used by JIT for allocating code cache.
 * An example output:
 * 		  -Xlp:codecache:pagesize=1M,pageable    large page size for JIT code cache
 *                available large page sizes for JIT code cache:
 *                4K pageable
 *                1M pageable
 *                
 * Note that this information is present in -verbose:sizes only if the system supports large page size for executable pages. 
 *            
 * Based on -verbose:sizes output, following checks are performed:             
 * 	- If JVM is running without -Xlp:codecache option, then page size and type in -verbose:sizes output
 * 		should be same as the default page size for executable pages.
 * 	- If the page size and type specified by -Xlp:codecache option is not same as page size and type
 * 		in -verbose:sizes output, then a warning message about using a different page size and type should be displayed. 
 * 
 * Finally, it checks JVM is able to startup and load XlpCodeCacheOptionsTest class correctly.
 */
public class XlpCodeCacheOptionsTestRunner extends Runner {
	private static final long ONE_KB = 1 * 1024;
	private static final long ONE_MB = 1 * 1024 * 1024;
	private static final long ONE_GB = 1 * 1024 * 1024 * 1024;
	
	private int commandIndex = 0;
	
	private ArrayList<XlpOption> xlpOptionsList = null;
	
	private long defaultPageSize = 4 * ONE_KB;
	private String defaultPageType = XlpUtil.XLP_PAGE_TYPE_NOT_USED;
	private String differentPageSizeWarningMsg = "(.)* Large page size (.)* is not a supported page size(.)*; using (.)* instead";
	private String unsupportedOptionMsg = "System configuration does not support option '-Xlp'";

	public XlpCodeCacheOptionsTestRunner(String className, String exeName,
			String bootClassPath, String userClassPath, String javaVersion) {
		super(className, exeName, bootClassPath, userClassPath, javaVersion);

		populateXlpOptionsList();
		
	}
	
	/**
	 * Creates array list containing all Xlp options to be tested.
	 */
	protected void populateXlpOptionsList() {
		xlpOptionsList = new ArrayList<XlpOption>();
		switch(osName) {

		case AIX:
			/* No -Xlp option */
			xlpOptionsList.add(new XlpOption(null, false));
			
			/* -XX:+UseLargePages */
			xlpOptionsList.add(new XlpOption("-XX:+UseLargePages", true));

			/* Test '-Xlp<size>'' options */
			xlpOptionsList.add(new XlpOption("-Xlp4K", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp64K", 64 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp16M", 16 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp7M", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			if (addrMode == AddrMode.BIT64) {
				xlpOptionsList.add(new XlpOption("-Xlp16G", 16 * ONE_GB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			}

			/* Test '-XX:LargePageSizeInBytes=<size>' options */
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=4K", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=64K", 64 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=16M", 16 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=7M", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));			
			if (addrMode == AddrMode.BIT64) {
				xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=16G", 16 * ONE_GB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			}

			/* Test '-Xlp:codecache:' options. Note that [non]pageable parameters are just ignored. */
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=4K", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=4K,pageable", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=4K,nonpageable", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=64K", 64 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=64K,pageable", 64 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=64K,nonpageable", 64 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=16M", 16 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=16M,pageable", 16 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=16M,nonpageable", 16 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=7M", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=7M,pageable", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=7M,nonpageable", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			if (addrMode == AddrMode.BIT64) {
				xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=16G", 16 * ONE_GB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
				xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=16G,pageable", 16 * ONE_GB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
				xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=16G,nonpageable", 16 * ONE_GB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));			
			}
			
			/* Test '-Xlp' with '-Xlp<size>' option. In such case -Xlp is ignored */
			xlpOptionsList.add(new XlpOption("-Xlp -Xlp4K", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp4K -Xlp", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp -Xlp7M", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp7M -Xlp", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));

			/* Test '-Xlp' with '-Xlp:codecache:pagesize=<size>' option. In such case '-Xlp' is ignored */
			xlpOptionsList.add(new XlpOption("-Xlp -Xlp:codecache:pagesize=4K", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=4K -Xlp", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp -Xlp:codecache:pagesize=7M", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=7M -Xlp", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			
			/* Test '-Xlp:codecache:pagesize=<size>' with '-Xlp:LargePageSizeInBytes=<size>' */
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=64K -XX:LargePageSizeInBytes=4K", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=4K -Xlp:codecache:pagesize=64K", 64 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));

			/* Test '-Xlp' with '-XX:LargePageSizeInBytes=<size>' */
			xlpOptionsList.add(new XlpOption("-Xlp -XX:LargePageSizeInBytes=4K", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			
			/* TODO: Test '-Xlp' with '-XX:LargePageSizeInBytes=<size>', and '-Xlp:codecache:pagesize=<size>' */

			/* Test '-Xlp<size>' with '-Xlp:codecache=pagesize<size>' option. In such cases rightmost option wins */
			xlpOptionsList.add(new XlpOption("-Xlp64K -Xlp:codecache:pagesize=16M", 16 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=16M -Xlp64K", 64 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp16M -Xlp:codecache:pagesize=7M", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=7M -Xlp16M", 16 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));

			/* Test '-Xlp<size>' with '-XX:LargePageSizeInBytes=<size>' */
			xlpOptionsList.add(new XlpOption("-Xlp64K -XX:LargePageSizeInBytes=4K", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=4K -Xlp64K", 64 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));

			/* TODO: Test '-Xlp<size>' with '-XX:LargePageSizeInBytes=<size>' and 'Xlp:codecache:pagesize=<size>' */

			/* Test Multiple '-XX:LargePageSizeInBytes=<size>'. Rightmost option wins */
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=64K -XX:LargePageSizeInBytes=16M", 16 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=16M -XX:LargePageSizeInBytes=64K", 64 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));

			/* Test multiple '-Xlp<size>' options. In such cases rightmost option wins */
			xlpOptionsList.add(new XlpOption("-Xlp64K -Xlp16M", 16 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp16M -Xlp64K", 64 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));

			/* Test multiple -Xlp:codecache: options. In such cases rightmost option wins */
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=64K -Xlp:codecache:pagesize=16M", 16 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=16M -Xlp:codecache:pagesize=64K", 64 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
		
			/* Test multiple pagesize parameters. In such cases rightmost parameter wins */
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=64K,pagesize=16M", 16 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=16M,pagesize=64K", 64 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			break;

		case LINUX:
		case WINDOWS:
			/* No -Xlp option */
			xlpOptionsList.add(new XlpOption(null, false));

			/* -XX:+UseLargePages */
			xlpOptionsList.add(new XlpOption("-XX:+UseLargePages", true));

			/* Test 'Xlp<size>' options */
			xlpOptionsList.add(new XlpOption("-Xlp4K", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp2M", 2 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp4M", 4 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp7M", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));

			/* Test '-XX:LargePageSizeInBytes=<size>' options */
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=4K", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=2M", 2 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=4M", 4 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=7M", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));	

			/* Test '-Xlp:codecache:' options. Note that [non]pageable parameters are just ignored. */
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=4K", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=4K,pageable", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=4K,nonpageable", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=2M", 2 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=2M,pageable", 2 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=2M,nonpageable", 2 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=4M", 4 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=4M,pageable", 4 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=4M,nonpageable", 4 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=7M", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=7M,pageable", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=7M,nonpageable", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));

			/* Test -Xlp with -Xlp<size> option. In such case -Xlp is ignored */
			xlpOptionsList.add(new XlpOption("-Xlp -Xlp4K", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp4K -Xlp", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp -Xlp7M", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp7M -Xlp", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
	
			/* Test '-Xlp' with '-Xlp:codecache:pagesize=<size>' option. In such case '-Xlp' is ignored */
			/* Test will fail if GC detects that -Xlp is not supported. I.e no large pages are available on the system. */
			xlpOptionsList.add(new XlpOption("-Xlp -Xlp:codecache:pagesize=4K", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=4K -Xlp", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp -Xlp:codecache:pagesize=7M", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=7M -Xlp", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));

			/* Test '-Xlp<size>' with '-XX:LargePageSizeInBytes=<size>' */
			xlpOptionsList.add(new XlpOption("-Xlp2M -XX:LargePageSizeInBytes=4K", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=4K -Xlp2M", 2 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));

			/* Test '-Xlp' with '-XX:LargePageSizeInBytes=<size>' */
			xlpOptionsList.add(new XlpOption("-Xlp -XX:LargePageSizeInBytes=4K", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));

			/* TODO: Test '-Xlp' with '-XX:LargePageSizeInBytes=<size>', and '-Xlp:codecache:pagesize=<size>' */
			
			/* Test '-Xlp<size>' with '-Xlp:codecache=pagesize<size>' option. In such cases rightmost option wins */
			/* Test will fail if GC detects that -Xlp is not supported. I.e no large pages are available on the system. */
			xlpOptionsList.add(new XlpOption("-Xlp2M -Xlp:codecache:pagesize=4M", 4 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=4M -Xlp2M", 2 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp2M -Xlp:codecache:pagesize=7M", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=7M -Xlp2M", 2 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));

			/* Test '-Xlp<size>' with '-XX:LargePageSizeInBytes=<size>' */
			xlpOptionsList.add(new XlpOption("-Xlp4M -XX:LargePageSizeInBytes=4K", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=4K -Xlp4M", 4 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));

			/* TODO: Test '-Xlp<size>' with '-XX:LargePageSizeInBytes=<size>' and 'Xlp:codecache:pagesize=<size>' */

			/* Test Multiple '-XX:LargePageSizeInBytes=<size>'. Rightmost option wins */
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=2M -XX:LargePageSizeInBytes=4M", 4 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=4M -XX:LargePageSizeInBytes=2M", 2 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));

			/* Test multiple '-Xlp<size>' options. In such cases rightmost option wins */
			xlpOptionsList.add(new XlpOption("-Xlp2M -Xlp4M", 4 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-Xlp4M -Xlp2M", 2 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));

			/* Test multiple -Xlp:codecache: options. In such cases rightmost option wins */
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=2M -Xlp:codecache:pagesize=4M", 4 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=4M -Xlp:codecache:pagesize=2M", 2 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));

			/* Test multiple pagesize parameters. In such cases rightmost parameter wins */
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=2M,pagesize=4M", 4 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=4M,pagesize=2M", 2 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			break;

		case ZOS:
			/* No -Xlp option */
			xlpOptionsList.add(new XlpOption(null, false));
			
			/* -XX:+UseLargePages */
			xlpOptionsList.add(new XlpOption("-XX:+UseLargePages", true));

			/* Test '-Xlp<size>' options */
			xlpOptionsList.add(new XlpOption("-Xlp4K", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE, true));
			xlpOptionsList.add(new XlpOption("-Xlp1M", ONE_MB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE, true));
			xlpOptionsList.add(new XlpOption("-Xlp2G", 2 * ONE_GB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE, true));
			xlpOptionsList.add(new XlpOption("-Xlp7M", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE, true));

			/* Test '-XX:LargePageSizeInBytes=<size>' options */
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=4K", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=1M", ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=2G", 2 * ONE_GB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=7M", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));

			/* Test '-Xlp:codecache:' options */
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=4K,pageable", 4 * ONE_KB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=1M,pageable", ONE_MB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=2G,pageable", 2 * ONE_GB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=7M,pageable", 7 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE, false));
	
			/* Test '-Xlp<size>' and '-XX:LargePageSizeInBytes=<size>'. Rightmost wins */
			xlpOptionsList.add(new XlpOption("-Xlp1M -XX:LargePageSizeInBytes=2G", 2 * ONE_GB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=2G -Xlp1M", 1 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));

			/* Test '-Xlp<size>' with '-XX:LargePageSizeInBytes=<size>' */
			xlpOptionsList.add(new XlpOption("-Xlp2G -XX:LargePageSizeInBytes=1M", 1 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=1M -Xlp2G", 2 * ONE_GB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));

			/* Test '-Xlp' with '-XX:LargePageSizeInBytes=<size>' */
			xlpOptionsList.add(new XlpOption("-Xlp -XX:LargePageSizeInBytes=1M", 1 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));

			/* TODO: Test '-Xlp' with '-XX:LargePageSizeInBytes=<size>', and '-Xlp:codecache:pagesize=<size>' */

			/* Test '-Xlp<size>' with '-Xlp:codecache=pagesize<size>' option. In such cases rightmost option wins */
			xlpOptionsList.add(new XlpOption("-Xlp1M -Xlp:codecache:pagesize=2G,pageable", 2 * ONE_GB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE, true));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=2G,pageable -Xlp1M", 1 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE, true));

			/* Test '-Xlp<size>' with '-XX:LargePageSizeInBytes=<size>' */
			xlpOptionsList.add(new XlpOption("-Xlp1M -XX:LargePageSizeInBytes=2G", 2 * ONE_GB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=2G -Xlp1M", 1 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, true));

			/* TODO: Test '-Xlp<size>' with '-XX:LargePageSizeInBytes=<size>' and 'Xlp:codecache:pagesize=<size>' */

			/* Test Multiple '-XX:LargePageSizeInBytes=<size>'. Rightmost option wins */
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=1M -XX:LargePageSizeInBytes=2G", 2 * ONE_GB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=2G -XX:LargePageSizeInBytes=1M", 1 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED, false));

			/* Test multiple '-Xlp<size>' options. In such cases rightmost option wins */
			xlpOptionsList.add(new XlpOption("-Xlp1M -Xlp2G", 2 * ONE_GB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE, true));
			xlpOptionsList.add(new XlpOption("-Xlp2G -Xlp1M", 1 * ONE_MB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE, true));

			/* Test multiple -Xlp:codecache: options. In such cases rightmost option wins */
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=1M,pageable -Xlp:codecache:pagesize=2G,pageable", 2 * ONE_GB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=2G,pageable -Xlp:codecache:pagesize=1M,pageable", ONE_MB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE, false));

			/* Test multiple pagesize and [non,]pageable parameters. In such cases rightmost parameter wins */
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=1M,pageable,pagesize=2G,pageable", 2 * ONE_GB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=2G,pagesize=1M,nonpageable,pageable", ONE_MB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE, false));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pageable,pagesize=2G,nonpageable,pagesize=1M", ONE_MB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE, false));
			break;

		default:
			System.out.println("WARNING: Failed to determine underlying OS. This test needs to know underlying OS.");
			break;
		}
	}
	
	/* Overrides method in Runner. */
	public String getCustomCommandLineOptions() {
		String customOptions = super.getCustomCommandLineOptions();
		
		customOptions += "-verbose:sizes ";
		String option = null;
		XlpOption xlpOption = xlpOptionsList.get(commandIndex);
		option = xlpOption.getOption();
		if (option != null) {
			/* XlpOption.option is null for 0th entry in xlpOptionsList.
			 * This corresponds to running without -Xlp:codecache option.
			 */
			customOptions += option;
		}
		
		return customOptions;
	}

	/* Overrides method in j9vm.runner.Runner. */
	public boolean run() {
		for (commandIndex = 0; commandIndex < xlpOptionsList.size(); commandIndex++) {
			XlpOption xlpOption = xlpOptionsList.get(commandIndex);

			super.run();

			/* All verbose logging is part of stdErr */
			byte[] stdErr = errCollector.getOutputAsByteArray();

			if (!analyze(stdErr, xlpOption))
				return false;
		}
		return true;
	}

	public boolean analyze(byte[] stdErr, XlpOption expectedResult) {
		BufferedReader in = new BufferedReader(new InputStreamReader(
				new ByteArrayInputStream(stdErr)));
		ArrayList<String> outputList = new ArrayList<String>();
		String inputLine = null;
		long pageSizeInVerbose = 0;
		String pageTypeInVerbose = null;
		String errorLine = null;
		boolean error = false;
		boolean isVerbouseOutputPresent = false;
		int index = 0;

		/* Add all output statements in a array list */
		// Debug
		System.out.println("Standard Error");
		do {
			try {
				inputLine = in.readLine();
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}

			if (inputLine != null) {
				outputList.add(inputLine);
				System.out.println(inputLine);
			}
		} while(inputLine != null);
		System.out.println("End Standard Error");

		/* Test State of CodeCache large pages. As well as integrity of the verbose output. */
		boolean isUseLargePagesFound = false;
		boolean	isUseLargePages = false;
		/* Test Large Page Size specified. */
		boolean isLargePageSizeInBytesFound = false;
		long largePageSize = 0;
		/* Test Wearnings Specified */
		boolean isWarningsFound = false;
		boolean isWarningsEnabled = false;
		/* Test Errors Specified */
		boolean isErrorsFound = false;
		boolean isErrorsEnabled = false;
		/* Z/OS Large Page Object Heap Type */
		boolean isPageTypeFound = false;
		boolean isPageTypePageable = false;

		/* Parse Output */
		for (index = 0; index < outputList.size(); index++) {
			String line = ((String)outputList.get(index)).trim();
			
			/* Verify CodeCache Large Page State */
			if (line.contains("UseLargePagesCodeCache")) {
				
				/* Integrity Check */
				if (isUseLargePagesFound) {
					System.out.println("ERROR: Found multiple -XX:[+/-]UseLargePagesCodeCache strings.");
					return false;
				}

				isUseLargePagesFound = true;

				/* Store Result */
				if (line.startsWith("-XX:+UseLargePagesCodeCache")) {
					isUseLargePages = true;
				} else if (!line.startsWith("-XX:-UseLargePagesCodeCache")) {
					System.out.println("ERROR: Malformed State String found.");
				}
			} else if (line.startsWith("-XX:LargePageSizeInBytesCodeCache=")) {

				/* Integrity Check */
				if (isLargePageSizeInBytesFound) {
					System.out.println("ERROR: Found multiple -XX:LargePageSizeInBytesCodeCache strings.");
					return false;
				}

				isLargePageSizeInBytesFound = true;

				/* Extract Page Size */
				int beginIndex = line.indexOf("=") + 1;
				String pageSizeString = line.substring(beginIndex, line.indexOf(" ", beginIndex));
				largePageSize = XlpUtil.pageSizeStringToLong(pageSizeString);
				/* Handle error in parsing */
				if (largePageSize == 0) {
					System.out.println("ERROR: -XX:LargePageSizeInBytesCodeCache= has an invalid page size.");
					return false;
				}
			} else if (line.contains("LargePageWarnings")) {
				
				/* Integrity Check */
				if (isWarningsFound) {
					System.out.println("ERROR: Found multiple -XX:[+/-]LargePageWarnings");
					return false;
				}
				isWarningsFound = true;

				/* Set Result */
				if (line.startsWith("-XX:+LargePageWarnings")) {
					isWarningsEnabled = true;
				} else if (!line.startsWith("-XX:-LargePageWarnings")) {
					System.out.println("ERROR: Malformed LargePageWarnings option found.");
					return false;
				}
			} else if (line.contains("LargePageErrors")) {

				/* Integrity Check */
				if (isErrorsFound) {
					System.out.println("ERROR: found multiple -XX:[+/-]LargePageErrors");
					return false;
				}
				isErrorsFound = true;

				/* Set Result */
				if (line.startsWith("-XX:+LargePageErrors")) {
					isErrorsEnabled = true;
				} else if (!line.startsWith("-XX:-LargePageErrors")) {
					System.out.println("ERROR: Malformed LargePageErrors option found.");
					return false;
				}
			} else if (osName == OSName.ZOS && line.startsWith("-XX:zOSLargePagesObjectHeapType=")) {
				
				/* Integrity Check */
				if (isPageTypeFound) {
					System.out.println("ERROR: found multiple -XX:zOSLargePagesObjectHeap options.");
					return false;
				}
				isPageTypeFound = true;

				/* Set Result */
				if (line.startsWith("-XX:zosLargePageObjectHeapType=pageable")) {
					isPageTypePageable = true;
				} else if (!line.startsWith("-XX:zosLargePageObjectHeapType=nonpageable")) {
					System.out.println("ERROR: Malformed -XX:zosLargePageObjectHeapType found.");
					return false;
				}
			}
		}

		/* DEBUG: Print Out All Verbose Parsed Info */
		

		/* Modify Expected Results */

		/* Compare with expected results */

	}
}
