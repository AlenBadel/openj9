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

import javax.xml.xpath.XPathEvaluationResult;

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

	class LargePageInfo {
		public long largePageSize;
		public String largePageSizeString;
		public LargePageInfo(String largePageSizeString, long largePageSize) {
			this.largePageSize = largePageSize;
			this.largePageSizeString = largePageSizeString;
		}
	}

	private LargePageInfo[] getLargePageSizes() {

		switch(osName) {
			case AIX:
				if (addrMode == AddrMode.BIT64)
					return new LargePageInfo [] { new LargePageInfo("4K", 4 * ONE_KB), new LargePageInfo("64K", 64 * ONE_KB), new LargePageInfo("7M", 7 * ONE_MB), new LargePageInfo("16M", 16 * ONE_MB), new LargePageInfo("16G", 16 * ONE_GB)};
				else 
					return new LargePageInfo [] { new LargePageInfo("4K", 4 * ONE_KB), new LargePageInfo("64K", 64 * ONE_KB), new LargePageInfo("7M", 7 * ONE_MB), new LargePageInfo("16M", 16 * ONE_MB)};
			case LINUX:
			case WINDOWS:
				return new LargePageInfo [] { new LargePageInfo("4K", 4 * ONE_KB), new LargePageInfo("2M", 2 * ONE_MB), new LargePageInfo("4M", 4 * ONE_MB), new LargePageInfo("7M", 7 * ONE_MB)};
			case ZOS:
				return new LargePageInfo [] { new LargePageInfo("4K", 4 * ONE_KB), new LargePageInfo("1M", 64 * ONE_KB), new LargePageInfo("7M", 7 * ONE_MB), new LargePageInfo("2G",2 * ONE_GB)};
			default:
				System.out.println("Unsupported Platform.");
				return null;
		}
	}

	/**
	 * Creates array list containing all Xlp options to be tested.
	 */
	protected void populateXlpOptionsList() {
		xlpOptionsList = new ArrayList<XlpOption>();
		LargePageInfo [] largePageSizesSupported = getLargePageSizes();

		/* Check for unsupported platform */
		if (largePageSizesSupported == null)
			return;

		/* Large Page Size Independant Test Cases */
		/* No Large Page option specified */
		xlpOptionsList.add(new XlpOption(null, true));

		/* -XX:+UseLargePages: Use Large Page default sizes */
		//xlpOptionsList.add(new XlpOption("-XX:+UseLargePages", true));
		/* -XX:-UseLargePages: Use Large Page Prefered page sizes */
		// TODO: Disable Not supported
		//xlpOptionsList.add(new XlpOption("-XX:-UseLargePages", false));
		/* -XX:+UseLargePagesCodeCache: Use Large Page default sizes */
		//xlpOptionsList.add(new XlpOption("-XX:+UseLargePagesCodeCache", true));
		/* -XX:-UseLargePageCodeCache: Use Prefered page sizes */
		// TODO: Disable Not Supported
		//xlpOptionsList.add(new XlpOption("-XX:-UseLargePagesCodeCache", false));

		/* TODO: Implement Premutations. There are many. */
		/* -XX:+UseLargePages -XX:-UseLargePages: Should use Prefered page sizes */
		//xlpOptionsList.add(new XlpOption("-XX:+UseLargePages -XX:-UseLargePages", false));
		/* -XX:-UseLargePages -XX:+UseLargePages: Should use Large Page default sizes */
		//xlpOptionsList.add(new XlpOption("-XX:-UseLargePages -XX:+UseLargePages", true));

		String expectedPageType = osName == OSName.ZOS ? XlpUtil.XLP_PAGE_TYPE_PAGEABLE : XlpUtil.XLP_PAGE_TYPE_NOT_USED;
		for (int i = 0; i < largePageSizesSupported.length; i++) {
			LargePageInfo lpInfo = largePageSizesSupported[i];

			/* -Xlp<Size> option */
			xlpOptionsList.add(new XlpOption("-Xlp"  + lpInfo.largePageSizeString, lpInfo.largePageSize, expectedPageType, true));

			/* -XX:LargePageSizeInBytes=<size> */
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytes=" + lpInfo.largePageSizeString, lpInfo.largePageSize, expectedPageType, true));
			
			/* -XX:LargePageSizeInBytesCodeCache=<size> */
			xlpOptionsList.add(new XlpOption("-XX:LargePageSizeInBytesCodeCache=" + lpInfo.largePageSizeString, lpInfo.largePageSize, expectedPageType, true));

			/* -Xlp:codecache:pagesize=<size> */
			// TODO: Z/OS Enhancement to allow this needs to be enabled.
			//xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=" + lpInfo.largePageSizeString, lpInfo.largePageSize, expectedPageType, true));

			/* Permutations of -Xlp -Xlp<size> */
			xlpOptionsList.add(new XlpOption("-Xlp -Xlp" + lpInfo.largePageSizeString, lpInfo.largePageSize, expectedPageType, true));
			xlpOptionsList.add(new XlpOption("-Xlp" + lpInfo.largePageSizeString + " -Xlp", lpInfo.largePageSize, expectedPageType, true));

			/* Permutations of -Xlp -Xlp:codecache:pagesize=<size> */
			xlpOptionsList.add(new XlpOption("-Xlp -Xlp:codecache:pagesize=" + lpInfo.largePageSizeString, lpInfo.largePageSize, expectedPageType, true));
			xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=" + lpInfo.largePageSizeString + " -Xlp", lpInfo.largePageSize, expectedPageType, true));

			if (i != 0) {
				LargePageInfo lpInfoPrev = largePageSizesSupported[i - 1];
				/* Permutations of -Xlp:codecache:pagesize=<size1> and -Xlp:codecache:pagesize=<size2> */
				xlpOptionsList.add(new XlpOption("Xlp:codecache:pagesize=" + lpInfoPrev.largePageSizeString + " -Xlp:codecache:pagesize=" + lpInfo.largePageSizeString, lpInfo.largePageSize, expectedPageType, true));
				xlpOptionsList.add(new XlpOption("Xlp:codecache:pagesize=" + lpInfo.largePageSizeString + " -Xlp:codecache:pagesize=" + lpInfoPrev.largePageSizeString, lpInfoPrev.largePageSize, expectedPageType, true));

				/* Permutations of -Xlp:<size1> and -Xlp:codecache:pagesize=<size2> */
				xlpOptionsList.add(new XlpOption("Xlp" + lpInfoPrev.largePageSizeString + " Xlp:codecache:pagesize=" + lpInfo.largePageSizeString, lpInfo.largePageSize, expectedPageType, true));
				xlpOptionsList.add(new XlpOption("Xlp" + lpInfo.largePageSizeString + " Xlp:codecache:pagesize=" + lpInfoPrev.largePageSizeString, lpInfoPrev.largePageSize, expectedPageType, true));
				xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=" + lpInfoPrev.largePageSizeString + " -Xlp" + lpInfo.largePageSizeString, lpInfo.largePageSize, expectedPageType, true));
				xlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=" + lpInfo.largePageSize + " -Xlp" + lpInfoPrev.largePageSizeString, lpInfo.largePageSize, expectedPageType, true));

				/* Permutations of -Xlp<size1> and -Xlp<size2> */
				xlpOptionsList.add(new XlpOption("-Xlp" + lpInfoPrev.largePageSizeString + " -Xlp" + lpInfo.largePageSizeString, lpInfo.largePageSize, expectedPageType, true));
				xlpOptionsList.add(new XlpOption("-Xlp" + lpInfo.largePageSizeString + " -Xlp" + lpInfoPrev.largePageSizeString, lpInfoPrev.largePageSize, expectedPageType, true));
				
				/* Permutations of -Xlp:codecache:pagesize=<size1>,pagesize=<size2> */
				//XlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=" + lpInfoPrev.largePageSizeString + "pagesize" + lpInfo.largePageSizeString, lpInfo.largePageSize, expectedPageType, true));
				//XlpOptionsList.add(new XlpOption("-Xlp:codecache:pagesize=" + lpInfo.largePageSizeString + "pagesize" + lpInfoPrev.largePageSizeString, lpInfoPrev.largePageSize, expectedPageType, true));
			}
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
		boolean success = false;
		for (commandIndex = 0; commandIndex < xlpOptionsList.size(); commandIndex++) {
			success = super.run();

			XlpOption xlpOption = xlpOptionsList.get(commandIndex);
			if (xlpOption.canFail())
				success = true;
			
			if (success == true) {
				byte[] stdOut = inCollector.getOutputAsByteArray();
				byte[] stdErr = errCollector.getOutputAsByteArray();
				try {
					success = analyze(stdOut, stdErr);
				} catch (Exception e) {
					success = false;
					System.out.println("Unexpected Exception:");
					e.printStackTrace();
				}
			}
			if (success == false) {
				break;
			}
		}
		return success;
	}
	
	public boolean analyze(byte[] stdOut, byte[] stdErr) throws IOException {
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
		do {
			inputLine = in.readLine();
			if (inputLine != null) {
				outputList.add(inputLine);
			}
		} while(inputLine != null);

		for (index = 0; index < outputList.size(); index++) {
			String line = ((String)outputList.get(index)).trim();
			if (line.startsWith("-Xlp:codecache:pagesize=")) {
				/*
				 * An example of -Xlp in -verbose:sizes on z/OS platform is:
				 *   	-Xlp:codecache:pagesize=1M,pageable          large page size for JIT code cache
				 *   		            available large page sizes for JIT code cache:
				 *   	                4K pageable
				 *                      1M pageable
				 */
		
				isVerbouseOutputPresent = true;
				
				/* Parse -Xlp:codecache statement to get page size and type used by JIT for code cache allocation */
				line = line.trim();
				/* Split around empty space and use first element to get page size and type */
				String codeCacheInfo = line.split(" ")[0].trim();
				int pageSizeBegin = codeCacheInfo.indexOf("=") + 1;
				int pageSizeEnd = 0;
				if (osName == OSName.ZOS) {
					pageSizeEnd = codeCacheInfo.indexOf(",");
					if (pageSizeEnd == -1) {
						System.out.println("ERROR: Error in parsing -Xlp:codecache statement. Did not find ','. ");
						error = true;
						errorLine = line;
						break;
					}
				} else {
					pageSizeEnd = codeCacheInfo.length();
				}
				String pageSizeString = codeCacheInfo.substring(pageSizeBegin, pageSizeEnd);
				pageSizeInVerbose = XlpUtil.pageSizeStringToLong(pageSizeString);
				if (pageSizeInVerbose == 0) {
					error = true;
					errorLine = line;
					break;
				}
				if (osName == OSName.ZOS) {
					pageTypeInVerbose = codeCacheInfo.substring(pageSizeEnd + 1);
				} else {
					pageTypeInVerbose = XlpUtil.XLP_PAGE_TYPE_NOT_USED;
				}
				break;
			}
			else if (line.contains(unsupportedOptionMsg)) {
				System.out.println("Xlp is not supported on this machine. Skipping Test.");
				return true;
			}
		}

		if (!error && isVerbouseOutputPresent) {
			/* skip 'available large page sizes:' */
			boolean firstEntryDone = false;
			index++;
			if (index < outputList.size()) {
				do {
					/* Read entry for first page size. It is treated as default page size. */
					index++;
					if (index >= outputList.size()) {
						/* Traversed all the statements in the outputList */
						break;
					}
					String line = outputList.get(index);
					line = line.trim();
					if (line.startsWith("-X")) {
						/* Some other option. End of list of supported page sizes, break out */
						break;
					}
					String pageSizeString = null;
					if (!firstEntryDone) {
						/* This is the first entry, treat it as default page size. 
						 * Note that it may get overwritten later.
						 */
						firstEntryDone = true;
						if (osName == OSName.ZOS) {
							/* Split around empty space to get page size and page type */
							pageSizeString = line.split(" ")[0];
							defaultPageType = line.split(" ")[1];
						} else {
							/* For non-zOS, only page size is present, page type is not used */ 
							pageSizeString = line;
							defaultPageType = XlpUtil.XLP_PAGE_TYPE_NOT_USED;
						}
						defaultPageSize = XlpUtil.pageSizeStringToLong(pageSizeString);
						if (defaultPageSize == 0) {
							/* Failed to get valid page size */
							error = true;
							errorLine = line;
							break;
						}
					} else {
						/* Overwrite default page size depending on preferences specific to each platform */
						
						if (osName == OSName.ZOS) {
							String pageTypeString;
							/* Split around empty space to get page size and page type */
							pageSizeString = line.split(" ")[0];
							pageTypeString = line.split(" ")[1];
							/* On z/OS, default or preferred page size is 1M pageable for JIT code cache. */
							if ((pageSizeString.equals("1M") == true) && (pageTypeString.equals(XlpUtil.XLP_PAGE_TYPE_PAGEABLE))) {
								defaultPageSize = ONE_MB;
								defaultPageType = "pageable";
								break;
							}
						} else if (osName == OSName.AIX) {
							/* On AIX, default or preferred page size is 64K for JIT code cache. */
							if (line.equals("64K")) {
								defaultPageSize = 64 * ONE_KB;
								break;
							}
						} else if (osName == OSName.LINUX)  {
							if (osArch == OSArch.X86) {
								if (line.equals("2M")) {
									/* On Linux x86 default or preferred page size is 2M for JIT code cache. */
									defaultPageSize = 2 * ONE_MB;
								}
							} else if (osArch == OSArch.S390X) {
								if (line.equals("1M")) {
									/* On zLinux default or preferred page size is 1M for JIT code cache. */
									defaultPageSize = ONE_MB;
								}
							}
						}
					}
				} while (true);
			}
			if (!firstEntryDone) {
				System.out.println("ERROR: Failed to find default page size in -verbose:sizes output");
				return false;
			}
		}

		if (!error) {
			XlpOption xlpOption = xlpOptionsList.get(commandIndex);
			String option = xlpOption.getOption();
			
			if (isVerbouseOutputPresent == true) {
				/* Following checks depend on -verbose:sizes output, 
				 * which is available only if the system supports large page size for executable pages. 
				 */
				if (option == null) {
					/* (pageSizeInVerbose, pageTypeInVerbose) should be same as (defaultPageSize, defaultPageType)
					 * if we are running without -Xlp option.
					 */
					if ((defaultPageSize != pageSizeInVerbose) || (!defaultPageType.equals(pageTypeInVerbose))) {
						System.out.println("ERROR: Without -Xlp JIT should use default page size and type for executable pages\n");
						System.out.println("\t Default page size and type for executable pages: " + defaultPageSize + " " + defaultPageType);
						System.out.println("\t Page size and type used by JIT for executable pages: " + pageSizeInVerbose + " " + pageTypeInVerbose);
						return false;
					} else {
						System.out.println("INFO: JIT is using default page size in absence of -Xlp:codecache option");
					}
				} else {
					/* Check if warning message should be printed */
					long optionPageSize = xlpOption.getPageSize();
					if (optionPageSize != 0) {
						String optionPageType = xlpOption.getPageType();
						if ((optionPageSize != pageSizeInVerbose) || (!optionPageType.equals(pageTypeInVerbose))) {
							/* Warning message should have been printed */
							boolean warningMsgFound = false;
							for (String line: outputList) {
								if (Pattern.matches(differentPageSizeWarningMsg, line)) {
									warningMsgFound = true;
									System.out.println("INFO: Found warning message for using different page size than specified\n");
									break;
								}
							}
							if (!warningMsgFound) {
								/* Print error message */
								System.out.println("ERROR: Page size and type in Xlp:codecache option is not same as used by JIT, but the expected warning message is not found");
								System.out.println("\tPage size and page type in Xlp:codecache option: " + optionPageSize + " " + optionPageType);
								System.out.println("\tPage size and page type used by JIT: " + pageSizeInVerbose + " " + pageTypeInVerbose);
								return false;
							}
						}
					}
				}
			}
		} else {
			/* Print the output statement where error occurred */
			if (errorLine != null) {
				System.out.println("Error in line: " + errorLine);
			}
			return false;
		}

		/* Check that XlpCodeCacheOptionsTest is correctly loaded and printed the message */
		boolean expectedMsgFound = false;
		for (String line: outputList) {
			if (line.indexOf(XlpCodeCacheOptionsTest.TEST_OUTPUT) != -1) {
				expectedMsgFound = true;
				break;
			}
		}
	
		if (expectedMsgFound) {
			System.out.println("INFO: Found expected message: " + XlpCodeCacheOptionsTest.TEST_OUTPUT);
		} else {
			System.out.println("ERROR: Did not find expected message: " + XlpCodeCacheOptionsTest.TEST_OUTPUT);
		}
		return true;
	}
}
