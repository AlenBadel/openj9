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

import j9vm.test.xlp.xlphelper.*;
import j9vm.test.xlp.xlphelper.XlpUtil.*;


public interface XlpTestInterface {

	// TODO: Change to private
	static final OSName osName = XlpUtil.getOperatingSystem();
	static final OSArch osArch = XlpUtil.getArchitecture();
	static final AddrMode addrMode = XlpUtil.getAddressingMode(osArch);

	/* Specify, and return a list of supported large pages on the component */
	public ArrayList<Pair<Long, String>> getSupportedLargePages() throws IllegalArgumentException;

	/* Specify, and return the preferred large page as <Page Size, Page Type> */
	default public Pair<Long, String> getPreferredLargePage() throws IllegalArgumentException {
		Pair<Long, String> preferredPage = null;

		switch(osArch) {
			case PPC:
				preferredPage = new Pair<Long, String>(64 * XlpUtil.ONE_KB, XlpUtil.XLP_PAGE_TYPE_NOT_USED);
				break;
			case S390X:
				if (osName == OSName.LINUX)
					preferredPage = new Pair<Long, String>(1 * XlpUtil.ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED);
				else if (osName == OSName.ZOS)
					preferredPage = new Pair<Long, String>(1 * XlpUtil.ONE_MB, XlpUtil.XLP_PAGE_TYPE_PAGEABLE);
				else {
					throw new IllegalArgumentException("Operating System not supported");
				}
				break;
			case X86:
				if (osName == OSName.LINUX)
					preferredPage = new Pair<Long, String>(2 * XlpUtil.ONE_MB, XlpUtil.XLP_PAGE_TYPE_NOT_USED);
				else if (osName != OSName.WINDOWS)
					throw new IllegalArgumentException("Operating System not supported");
				break;
			default:
				throw new IllegalArgumentException("Architecture not supported");
		}

		return preferredPage;
	}

	/* Specify, and return the default large pages supported on the platform */
	public ArrayList<Pair<Long, String>> getDefaultLargePages();
	/* Populate Test cases of the component */
	public ArrayList<XlpOption> populateXlpTestCases();

	/* Accessors */
	/* Return expected unsupported large pages */
	public String getUnsupportedLargePagesMsg();
	/* Return component label */
	public String getComponentLabel();
	/* Returns osName */
	public default OSName getOperatingSystem() {
		return osName;
	}
	/* Returns architecture */
	public default OSArch getArchitecture() {
		return osArch;
	}
	/* Returns Addressing Mode */
	public default AddrMode getAddressingMode() {
		return addrMode;
	}
}
