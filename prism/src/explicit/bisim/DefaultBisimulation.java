//==============================================================================
//	
//	Copyright (c) 2020-
//	Authors:
//	* Zainab Fatmi
//	* Franck van Breugel (York University)
//  * Dave Parker (University of Oxford)
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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import explicit.CTMC;
import explicit.DTMC;
import explicit.MDP;
import prism.Evaluator;
import prism.PrismComponent;

/**
 * Method to perform bisimulation minimisation for explicit-state models.
 */
public class DefaultBisimulation<Value> extends Bisimulation<Value>
{

	/**
	 * Constructs a new DefaultBisimulation object.
	 */
	public DefaultBisimulation(PrismComponent parent)
	{
		super(parent);
	}

	@Override
	protected boolean minimiseDTMC(DTMC<Value> dtmc)
	{
		bisimilarity(dtmc, dtmc.getEvaluator(), 1);
		return numStates != numBlocks;
	}

	@Override
	protected boolean minimiseCTMC(CTMC<Value> ctmc)
	{
		bisimilarity(ctmc, ctmc.getEvaluator(), 0);
		return numStates != numBlocks;
	}

	@Override
	protected boolean minimiseMDP(MDP<Value> mdp)
	{
		mdpBisimilarity(mdp, mdp.getEvaluator());
		return numStates != numBlocks;
	}

	/**
	 * Computes probabilistic bisimilarity for the specified labelled Markov chain.
	 *
	 * @param model The DTMC or CTMC.
	 * @param eval  The evaluator to manipulate values.
	 * @param dtmc  1 if the model is a DTMC and 0 if it is a CTMC.
	 */
	protected void bisimilarity(DTMC<Value> model, Evaluator<Value> eval, int dtmc)
	{
		/* the blocks used as splitters */
		BitSet splitters = new BitSet(numBlocks);
		/* if the model is a DTMC, exclude the last block which contains all states without a label */
		splitters.flip(0, numBlocks - dtmc);

		int numberOfBlocksOld;
		int[] partitionOld;
		BitSet splittersOld;
		do {
			numberOfBlocksOld = numBlocks;
			numBlocks = 0;
			partitionOld = partition;
			partition = new int[numStates];
			splittersOld = splitters;
			splitters = new BitSet(numberOfBlocksOld);

			/* blocks.get(b) is the list of sub-blocks that partition block b */
			List<List<SubBlock<Value>>> blocks = new ArrayList<List<SubBlock<Value>>>(numberOfBlocksOld);
			for (int b = 0; b < numberOfBlocksOld; b++) {
				List<SubBlock<Value>> empty = new ArrayList<SubBlock<Value>>();
				blocks.add(empty);
			}

			for (int source = 0; source < numStates; source++) {
				SubBlock<Value> subBlock = null; /* sub-block that contains the source state */
				Iterator<Entry<Integer, Value>> iter = model.getTransitionsIterator(source);
				while (iter.hasNext()) {
					Entry<Integer, Value> transition = iter.next();
					int blockOfTarget = partitionOld[transition.getKey()];
					if (splittersOld.get(blockOfTarget)) {
						Value probabilityOld;
						if (subBlock == null) {
							subBlock = new NonEmptySubBlock<Value>(eval);
							probabilityOld = eval.zero();
						} else {
							probabilityOld = subBlock.get(blockOfTarget);
							if (probabilityOld == null) {
								probabilityOld = eval.zero();
							}
						}
						subBlock.put(blockOfTarget, eval.add(probabilityOld, transition.getValue()));
					}
				}
				if (subBlock == null) {
					subBlock = new EmptySubBlock<Value>();
				}

				int blockOfSource = partitionOld[source];
				List<SubBlock<Value>> subBlocks = blocks.get(blockOfSource);
				int index = subBlocks.indexOf(subBlock);
				if (index == -1) { /* there is no sub-block with the same lifting as the source state */
					subBlock.setID(numBlocks);
					subBlocks.add(subBlock);
					partition[source] = numBlocks;
					/* the first sub-block is not used as a splitter in the next refinement step */
					if (subBlocks.size() > 1) {
						splitters.set(numBlocks);
					}
					numBlocks++;
				} else {
					partition[source] = subBlocks.get(index).getID();
				}
			}
		} while (numBlocks != numberOfBlocksOld);
	}

	/**
	 * Computes probabilistic bisimilarity for the specified MDP.
	 *
	 * @param model The MDP.
	 * @param eval  The evaluator to manipulate values.
	 */
	protected void mdpBisimilarity(MDP<Value> model, Evaluator<Value> eval)
	{
		/* the existing states are action states, and we create a probabilistic state for each distribution */
		List<Map<Object, Set<Integer>>> actionStates = new ArrayList<Map<Object, Set<Integer>>>(numStates);
		List<Map<Integer, Value>> probabilisticStates = new ArrayList<Map<Integer, Value>>(numStates);
		int numProbabilisticStates = 0;
		for (int source = 0; source < numStates; source++) {
			Map<Object, Set<Integer>> actionState = new HashMap<Object, Set<Integer>>();
			for (int actionId = 0; actionId < model.getNumChoices(source); actionId++) {
				Object action = model.getAction(source, actionId);
				if (!actionState.containsKey(action)) {
					actionState.put(action, new HashSet<Integer>());
				}
				actionState.get(action).add(numProbabilisticStates);

				Map<Integer, Value> probabilisticState = new HashMap<Integer, Value>();
				Iterator<Entry<Integer, Value>> iter = model.getTransitionsIterator(source, actionId);
				while (iter.hasNext()) {
					Entry<Integer, Value> transition = iter.next();
					probabilisticState.put(transition.getKey(), transition.getValue());
				}
				probabilisticStates.add(probabilisticState);
				numProbabilisticStates++;
			}
			actionStates.add(actionState);
		}

		/* the action blocks used as splitters */
		BitSet splitters = new BitSet(numBlocks);
		splitters.flip(0, numBlocks - 1);
		int numBlocksOld;
		int[] partitionOld;
		BitSet splittersOld;

		/* set up the probabilistic blocks */
		int[] probabilisticPartition = new int[numProbabilisticStates];
		int numProbabilisticBlocks = 1;
		BitSet probabilisticSplitters = new BitSet(numProbabilisticBlocks);
		probabilisticSplitters.flip(0, numProbabilisticBlocks);
		int numProbabilisticBlocksOld;
		int[] probabilisticPartitionOld;
		BitSet probabilisticSplittersOld;

		do {
			numBlocksOld = numBlocks;
			numBlocks = 0;
			partitionOld = partition;
			partition = new int[numStates];
			splittersOld = splitters;
			splitters = new BitSet(numBlocksOld);

			/* actionBlocks.get(b) is the list of sub-blocks that partition block b */
			List<List<SubBlock<Value>>> actionBlocks = new ArrayList<List<SubBlock<Value>>>(numBlocksOld);
			for (int b = 0; b < numBlocksOld; b++)
				actionBlocks.add(new ArrayList<SubBlock<Value>>());

			numProbabilisticBlocksOld = numProbabilisticBlocks;
			numProbabilisticBlocks = 0;
			probabilisticPartitionOld = probabilisticPartition;
			probabilisticPartition = new int[numProbabilisticStates];
			probabilisticSplittersOld = probabilisticSplitters;

			List<List<SubBlock<Value>>> probabilisticBlocks = new ArrayList<List<SubBlock<Value>>>(numProbabilisticBlocksOld);
			for (int b = 0; b < numProbabilisticBlocksOld; b++)
				probabilisticBlocks.add(new ArrayList<SubBlock<Value>>());

			/* split probabilistic blocks according to the action splitters */
			for (int source = 0; source < numProbabilisticStates; source++) {
				SubBlock<Value> subBlock = null; /* sub-block that contains the source state */
				for (Entry<Integer, Value> transition : probabilisticStates.get(source).entrySet()) {
					int blockOfTarget = partitionOld[transition.getKey()];
					if (splittersOld.get(blockOfTarget)) {
						Value probabilityOld;
						if (subBlock == null) {
							subBlock = new NonEmptySubBlock<Value>(eval);
							probabilityOld = eval.zero();
						} else {
							probabilityOld = subBlock.get(blockOfTarget);
							if (probabilityOld == null) {
								probabilityOld = eval.zero();
							}
						}
						subBlock.put(blockOfTarget, eval.add(probabilityOld, transition.getValue()));
					}
				}
				if (subBlock == null) {
					subBlock = new EmptySubBlock<Value>();
				}

				int blockOfSource = probabilisticPartitionOld[source];
				List<SubBlock<Value>> subBlocks = probabilisticBlocks.get(blockOfSource);
				int index = subBlocks.indexOf(subBlock);
				if (index == -1) { /* there is no sub-block with the same lifting as the source state */
					subBlock.setID(numProbabilisticBlocks);
					subBlocks.add(subBlock);
					probabilisticPartition[source] = numProbabilisticBlocks;
					numProbabilisticBlocks++;
				} else {
					probabilisticPartition[source] = subBlocks.get(index).getID();
				}
			}
			/* use blocks that have been split as splitters in the next refinement step */
			probabilisticSplitters = new BitSet(numProbabilisticBlocks);
			for (int b = 0; b < numProbabilisticBlocksOld; b++) {
				if (probabilisticBlocks.get(b).size() > 1) {
					for (SubBlock<Value> subBlock : probabilisticBlocks.get(b)) {
						probabilisticSplitters.set(subBlock.getID());
					}
				}
			}

			/* split action blocks according to probabilistic splitters */
			for (int source = 0; source < numStates; source++) {
				SubBlock<Value> subBlock = null; /* sub-block that contains the source state */
				for (Entry<Object, Set<Integer>> entry : actionStates.get(source).entrySet()) {
					for (int target : entry.getValue()) {
						int blockOfTarget = probabilisticPartitionOld[target];
						if (probabilisticSplittersOld.get(blockOfTarget)) {
							if (subBlock == null)
								subBlock = new NonEmptyActionSubBlock<Value>();
							subBlock.add(entry.getKey(), blockOfTarget);
						}
					}
				}
				if (subBlock == null) {
					subBlock = new EmptySubBlock<Value>();
				}

				int blockOfSource = partitionOld[source];
				List<SubBlock<Value>> subBlocks = actionBlocks.get(blockOfSource);
				int index = subBlocks.indexOf(subBlock);
				if (index == -1) { /* there is no sub-block with the same lifting as the source state */
					subBlock.setID(numBlocks);
					subBlocks.add(subBlock);
					partition[source] = numBlocks;
					/* the first sub-block is not used as a splitter in the next refinement step */
					if (subBlocks.size() > 1) {
						splitters.set(numBlocks);
					}
					numBlocks++;
				} else {
					partition[source] = subBlocks.get(index).getID();
				}
			}
		} while (numBlocks + numProbabilisticBlocks != numBlocksOld + numProbabilisticBlocksOld);
	}
}
