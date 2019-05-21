/*******************************************************************************
 * Copyright (c) 2019, 2019 IBM Corp. and others
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

#include "codegen/J9PPCWatchedInstanceFieldSnippet.hpp"
//#include "codegen/Relocation.hpp"
#include "p/codegen/PPCTableOfConstants.hpp" //PTOC

uint8_t *TR::J9PPCWatchedInstanceFieldSnippet::emitSnippetBody()
    {

    // Call Base Class Emit Snippet to populate the snippet
    // Here Binary Buffer will be ahead by the size of the struct J9JITWatchedInstanceFieldData
    uint8_t * snippetLocation = (TR::J9WatchedInstanceFieldSnippet::emitSnippetBody() - TR::J9WatchedInstanceFieldSnippet::getLength(0));
    
    // Insert the Snippet Address into TOC or patch the materalisation instructions

    // Insert into TOC 
    if(TR::Compiler->target.is64Bit() && getTOCOffset() != PTOC_FULL_INDEX)
        {
        TR_PPCTableOfConstants::setTOCSlot(getTOCOffset(), (uintptrj_t)snippetLocation);
        printf("Set SnippetLocation: %p at TOC Offset :%d\n", (uintptrj_t)snippetLocation, getTOCOffset());
        }
    else if(getLowerInstruction() != NULL)
        {
        // Handle Nibles - Generation of instructions to materialise address
        int32_t *patchAddr = (int32_t *)getLowerInstruction()->getBinaryEncoding();
        intptrj_t addrValue = (intptrj_t)snippetLocation;

        if(TR::Compiler->target.is64Bit())
            {
            *patchAddr |= LO_VALUE(addrValue) & 0x0000ffff;
            addrValue = cg()->hiValue(addrValue);
            *(patchAddr-2) |= addrValue & 0x0000ffff;
            *(patchAddr-3) |= (addrValue>>16) & 0x0000ffff;
            *(patchAddr-4) |= (addrValue>>32) & 0x0000ffff;
            }
        else
            {
            // 32 Bit only handles nibbles. TOC is not enabled . 
            *(patchAddr) |= cg()->hiValue(addrValue) & 0x0000ffff;
            *(patchAddr-2) |= LO_VALUE(addrValue) & 0x0000ffff;
            }
        }

    //Restore cursor before returning
    return (snippetLocation + TR::J9WatchedInstanceFieldSnippet::getLength(0));
    }