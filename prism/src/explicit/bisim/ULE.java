//==============================================================================
//
//	Copyright (c) 2025-
//	Authors:
//	* Zainab Fatmi (University of Oxford)
//
//------------------------------------------------------------------------------
//
//	This file is part of PRISM.
//
//	PRISM is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version.
//
//	PRISM is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//	GNU General Public License for more details.
//
//	You should have received a copy of the GNU General Public License
//	along with PRISM; if not, write to the Free Software Foundation,
//	Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//==============================================================================

package explicit.bisim;

import explicit.DTMC;
import prism.PrismComponent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ULE<Value> extends Bisimulation<Value>
{

	public ULE(PrismComponent parent)
	{
		super(parent);
	}

	@Override
	protected boolean minimiseDTMC(DTMC<Value> dtmc)
	{
		int numberOfBlocksOld;
		int[] partitionOld;
		do {
			numberOfBlocksOld = numBlocks;
			numBlocks = 0;
			partitionOld = partition;
			partition = new int[numStates];
			/* blocks.get(b) is the list of sub-blocks that partition block b */
			Map<Integer, Map<Integer, Integer>> blocks = new HashMap<Integer, Map<Integer, Integer>>(numberOfBlocksOld);
			for (int source = 0; source < numStates; source++) {
				Iterator<Map.Entry<Integer, Value>> iter = dtmc.getTransitionsIterator(source);
				int successorBlock = -1;
				boolean oneSuccessorBlock = true;
				while (iter.hasNext() && oneSuccessorBlock) {
					Map.Entry<Integer, Value> transition = iter.next();
					if (successorBlock == -1) {
						successorBlock = partitionOld[transition.getKey()];
					} else if (successorBlock != partitionOld[transition.getKey()]) {
						oneSuccessorBlock = false;
					}
				}
				if (oneSuccessorBlock) {
					int blockOfSource = partitionOld[source];
					Map<Integer, Integer> subBlocks;
					if (blocks.containsKey(blockOfSource)) {
						subBlocks = blocks.get(blockOfSource);
					} else {
						subBlocks = new HashMap<Integer, Integer>();
						blocks.put(blockOfSource, subBlocks);
					}
					if (subBlocks.containsKey(successorBlock)) {
						partition[source] = subBlocks.get(successorBlock);
					} else {
						subBlocks.put(successorBlock, numBlocks);
						partition[source] = numBlocks;
						numBlocks++;
					}
				} else {
					partition[source] = numBlocks;
					numBlocks++;
				}
			}
		} while (numBlocks != numberOfBlocksOld);
		//printPartition();
		return numStates != numBlocks;
	}

}
