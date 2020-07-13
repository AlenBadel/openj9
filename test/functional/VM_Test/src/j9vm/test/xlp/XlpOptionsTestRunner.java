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

package j9vm.test.xlp;

import java.io.*;
import java.util.*;

import j9vm.runner.Runner;
import j9vm.test.xlp.xlphelper.*;
import j9vm.test.xlp.xlphelper.XlpUtil.*;


public class XlpOptionsTestRunner extends Runner {

	//private String differentPageSizeWarningMsg = "(.)* Large page size (.)* is not a supported page size(.)*; using (.)* instead";

	private String customArguments = null;
	public XlpOptionsTestRunner(String className, String exeName,
			String bootClassPath, String userClassPath, String javaVersion) {
		super(className, exeName, bootClassPath, userClassPath, javaVersion);
		System.out.println("Init XlpTest");
	}

	/* Validate Supported Large Pages Verbose Output */
	private <T extends XlpTestInterface> boolean validateSupportedLargePagesInVerbose(ArrayList<Pair<Long, String>> supportedLargePagesInVerbose, T component) {
		/* Obtain the supported large pages on the platform */
		ArrayList<Pair<Long, String>> supportedLargePages = component.getSupportedLargePages();
		return supportedLargePages.containsAll(supportedLargePagesInVerbose);
	}

	public String getCustomCommandLineOptions() {
		return customArguments;
	}

	public boolean run() {
		System.out.println("Running XlpTest");
		XlpCodeCache codecache = new XlpCodeCache();

		/* Run all CodeCache Tests */
		ArrayList<XlpOption> codecacheTests = codecache.populateXlpTestCases();
		boolean success  = false;
		for (int commandIndex = 0; commandIndex < codecacheTests.size(); commandIndex++) {
			XlpOption xlpOption = codecacheTests.get(commandIndex);
			/* Update Command Line Test */
			setCustomCommandLineOptions(xlpOption);
			/* Run Command */
			super.run();

			/* Analyze the output */
			byte[] stdErr = errCollector.getOutputAsByteArray();
			try {
				success = analyze(xlpOption, stdErr, codecache);
			} catch (IOException e) {
				System.out.println("Encountered IOException During CodeCache Test. Running Test:" + xlpOption);
				e.printStackTrace();
				return false;
			}
			
			if (!success) {
				System.out.println("Failed CodeCache Test:" + xlpOption);
				return false;
			}
		}
		
		System.out.println("Passed all CodeCache Tests");

		/* Run all ObjectHeap Tests */
		XlpObjectHeap objectheap = new XlpObjectHeap();
		ArrayList<XlpOption> objectheapTests = objectheap.populateXlpTestCases();
		for (int commandIndex = 0; commandIndex < objectheapTests.size(); commandIndex++) {
			XlpOption xlpOption = objectheapTests.get(commandIndex);
			/* Update Command Line Test */
			setCustomCommandLineOptions(xlpOption);
			/* Run Command */
			super.run();

			/* Analyze the output */
			byte[] stdErr = errCollector.getOutputAsByteArray();
			try {
				success = analyze(xlpOption, stdErr, codecache);
			} catch (IOException e) {
				System.out.println("Encountered IOException During ObjectHeap Test. Running Test:" + xlpOption);
				e.printStackTrace();
				return false;
			}
			
			if (!success) {
				System.out.println("Failed ObjectHeap Test:" + xlpOption);
				return false;
			}
		}
		System.out.println("Passed All ObjectHeap Tests");
		return true;
	}

	private void setCustomCommandLineOptions(XlpOption xlpOption) {
		String customArguments = super.getCustomCommandLineOptions() + "-verbose:sizes " + "-verbose:gc ";
		String option = xlpOption.getOption();
		if (option != null) {
			customArguments += option;
		}
	}

	/* Validate Large Page */
	private <T extends XlpTestInterface> boolean validateLargePageInVerbose(Pair<Long, String> largePageExtracted, T component) {
		ArrayList<Pair<Long, String>> supportedLargePages = component.getSupportedLargePages();
		return supportedLargePages.contains(largePageExtracted);
	}

	private <T extends XlpTestInterface> boolean validateDefaultLargePage(Pair<Long, String> largePageUsed, ArrayList<Pair<Long, String>> verbosePageList, T component) {
		/* Some Platforms have special default pages */
		ArrayList<Pair<Long, String>> specialDefaultPages = component.getDefaultLargePages();

		/* Platforms with defined default pages, the used large page must be the leading configured default page */
		if (specialDefaultPages != null) {
			Collections.reverse(specialDefaultPages);
			for (Pair<Long, String> page : specialDefaultPages) {
				/* Default Page was configured. It must be chosen as the default page. */
				if (verbosePageList.contains(page)) {
					return page.equals(largePageUsed);
				}
			}
			return false;
		}

		/* Other platforms, they must use the default huge page size */
		if (verbosePageList.size() < 2) {
			System.out.println("Operating system does not have huge pages configured.");
			return false;
		}
		return largePageUsed.equals(verbosePageList.get(1));
	}

	private boolean validateLargePage(Pair<Long, String> currentPage, Pair<Long, String> expectedPage, ArrayList<Pair<Long, String>> supportedPageSizesInVerbose) {

		if (currentPage.equals(expectedPage))
			return true;
		
		if (supportedPageSizesInVerbose.contains(expectedPage)) {
			System.out.println("Expected Page was supported but not chosen.");
			return false;
		}

		if (currentPage.getKey() > expectedPage.getKey()) {
			System.out.println("Downgraded Page can not be greater than the expected page.");
			return false;
		}

		ListIterator<Pair<Long, String>> supportedPagesIterator = supportedPageSizesInVerbose.listIterator(supportedPageSizesInVerbose.size());
		while (supportedPagesIterator.hasPrevious()) {
			Pair<Long, String> page = supportedPagesIterator.previous();
			if (page.getKey() <= expectedPage.getKey()) {
				return page.equals(currentPage);
			}
		}

		System.out.println("Unexpected failure while validating large pages.");
		return false;
	}

	private <T extends XlpTestInterface> boolean verifyLargePageSize(long largePageExpected, long largePageInVerbose, ArrayList<Pair<Long, String>> supportedLargePagesInVerbose, T component) {
		OSName osName = component.getOperatingSystem();
		// Verbose Output size is equal to what was expected
		if (largePageExpected == largePageInVerbose)
			return true;
		
		// Find the valid downgraded page sizes
		long expectedDowngradedPageSize = 0;
		String expectedDowngradePageType = null;
		for (Pair<Long, String> supportedPage : supportedLargePagesInVerbose) {
			long pageSize = supportedPage.getKey();

			if (osName == OSName.ZOS) {
				String pageType = supportedPage.getValue();
			} else if (pageSize <= largePageExpected) {
				expectedDowngradedPageSize = pageSize;
			}
		}

		/* Check has it been downgraded successfully */
		if (expectedDowngradedPageSize == largePageInVerbose)
			return true;
		return false;
	}

	public <T extends XlpTestInterface> boolean analyze(XlpOption xlpOption, byte[] stdErr, T component) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(
				new ByteArrayInputStream(stdErr)));
		ArrayList<String> outputList = new ArrayList<String>();
		OSName osName = component.getOperatingSystem();
		/* Fast-forward to where a JVM Error occurs, or the -XX:[+/-]UseLargePagesCodeCache header occurs. */
		String inputLine = null;
		String unsupportedOptionMsg = component.getUnsupportedLargePagesMsg();
		String labelMsg = component.getComponentLabel();
		while ((inputLine = in.readLine()) != null) {
			System.out.println("inputLine:" + inputLine);
			if (inputLine.contains(unsupportedOptionMsg)) {
				System.out.println("Large Pages is not supported on this machine. Skipping Test.");
				return true;
			} else if (inputLine.matches("-XX:(\\+|\\-)" + labelMsg)) {
				outputList.add(inputLine);
				break;
			}
		}

		System.out.println("DEBUGTEST Label Stage Contents:" + outputList);
		/* Check for improper end */
		if (inputLine == null) {
			System.out.println("Could not find either the unsupported message, or the large page header.");
			return false;
		}

		/* Store Related Verbose output, up until the next verbose output is reached. */
		while (((inputLine = in.readLine()) != null) && (!inputLine.contains("-X"))) {
			outputList.add(inputLine);
		}

		/* Check for improper end of -Verbose:sizes */
		if (inputLine == null) {
			System.out.println("Could not find the proper end of verbose:sizes output.");
			return false;
		}
		in.close();
		System.out.println("DEBUGTEST Final Stage Contents:"  + outputList);

		/* Extract Parameters */
		int lineIndex = 0;

		/* Extract Sign -XX:[+/-]UseLargePagesCodeCache */
		inputLine = ((String)outputList.get(lineIndex)).trim();
		boolean isLargePagesEnabled = inputLine.charAt(4) == '+' ? true : false;
		lineIndex++;

		/* Test State of LP */
		if (xlpOption.getXlpState() != isLargePagesEnabled) {
			System.out.println("Large Page State Failure. Expected:" + xlpOption.getXlpState() + " Got:" + isLargePagesEnabled);
			return false;
		}

		/* Extract Page Size Used */
		inputLine = ((String)outputList.get(lineIndex)).trim();
		if (!inputLine.contains("large page size")) {
			System.out.println("Found Malformed verbose:sizes output");
			return false;
		}
		String pageSizeString = inputLine.substring(0, inputLine.indexOf(" ") - 1);
		long pageSizeInVerbose = XlpUtil.pageSizeStringToLong(pageSizeString);
		if (0 == pageSizeInVerbose) {
			System.out.println("Found Malformed CodeCache Page Size. Page Size Found:" + inputLine);
			return false;
		}
		lineIndex++;

		/* Z/Os Only: Extract Page Type Used */
		String pageTypeInVerbose = XlpUtil.XLP_PAGE_TYPE_NOT_USED;
		if (osName == OSName.ZOS) {
			inputLine = ((String)outputList.get(lineIndex)).trim();
			if (!inputLine.contains("large page type")) {
				System.out.println("Found Malformed verbose:sizes. Could not find large page type.");
				return false;
			}
			pageTypeInVerbose = inputLine.substring(0, inputLine.indexOf(" ") - 1);
			if (!(pageTypeInVerbose.equals("pageable") || pageTypeInVerbose.equals("nonpageable"))) {
				System.out.println("Found Malformed CodeCache Page Type. Got:" + pageTypeInVerbose);
				return false;
			}
			lineIndex++;
		}

		/* Extract Supported Page Sizes */
		ArrayList<Pair<Long, String>> supportedPageSizesInVerbose = new ArrayList<>();
		inputLine = ((String)outputList.get(lineIndex)).trim();
		if (!inputLine.contains("available large pages for JIT code cache")) {
			System.out.println("Found Malformed -Verbose:sizes output. Expected Large Pages for JIT code cache");
			return false;
		}
		for (lineIndex++; lineIndex < outputList.size(); lineIndex++) {
			inputLine = ((String)outputList.get(lineIndex)).trim();
			String lpSize = inputLine.substring(0, inputLine.indexOf(" ") - 1);
			String lpType = (osName != OSName.ZOS) ? "Unused" : inputLine.substring(inputLine.indexOf(" ") - 1, inputLine.length() - 1);
			long pageSize = XlpUtil.pageSizeStringToLong(inputLine);
			if (0 == pageSize) {
				System.out.println("Found Malformed -Verbose:sizes output. Expected Large Page Size");
				return false;
			}
			supportedPageSizesInVerbose.add(new Pair<>(pageSize, lpType));
		}

		/* Verify Extracted Supported Page Sizes */
		if (!validateSupportedLargePagesInVerbose(supportedPageSizesInVerbose, component)) {
			System.out.println("Extracted Supported Page Sizes within -verbose:sizes did not match arch spec");
			return false;
		}

		Pair<Long, String> currentPage = new Pair<>(pageSizeInVerbose, pageTypeInVerbose);
		/* Verify currently used page */
		if (!validateLargePageInVerbose(currentPage, component)) {
			System.out.println("Extracted In-Use Large Page did not match arch spec");
			return false;
		}

		/* Large Pages Disabled, then large page should equal the preferred large page */
		if (!xlpOption.getXlpState()) {
			Pair<Long, String> preferredLargePage = component.getPreferredLargePage();
			if (!preferredLargePage.equals(currentPage)) {
				System.out.println("Large Pages is disabled, but size is the expected preferred page for the arch");
				return false;
			}
			return true;
		}

		/* Large Pages Enabled, and expecting Default Arch Page Size */
		if (xlpOption.getExpectedPage() == null) {
			if (!validateDefaultLargePage(currentPage, supportedPageSizesInVerbose, component)) {
				System.out.println("System was not using default large page to match arch spec.");
				return false;
			}
			return true;
		}

		/* Large Pages with specified page configuration */
		Pair<Long, String> expectedPage = xlpOption.getExpectedPage();
		return validateLargePage(currentPage, expectedPage, supportedPageSizesInVerbose);
	}
}
