//==============================================================================
//
//	Copyright (c) 2024-
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

import java.util.ArrayList;
import java.util.Arrays;
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
 * Performs robust bisimulation minimisation for explicit-state models.
 */
public class RobustBisimulation<Value> extends DefaultBisimulation<Value>
{
	/* Local storage of partition info */
	Map<Integer, List<Integer>> successors;
	List<List<Integer>> R;
	List<Set<Integer>> Q;

	/**
	 * Construct a new RobustBisimulation object.
	 */
	public RobustBisimulation(PrismComponent parent)
	{
		super(parent);
	}

	@Override
	protected boolean minimiseDTMC(DTMC<Value> dtmc)
	{
		return robustBisimilarity(dtmc, dtmc.getEvaluator(), 1);
	}

	@Override
	protected boolean minimiseCTMC(CTMC<Value> ctmc)
	{
		return robustBisimilarity(ctmc, ctmc.getEvaluator(), 0);
		//mainLog.println("Robust bisimilarity not yet supported for CTMCs: skipping minimisation.");
		//return false;
	}

	/**
	 * Computes robust probabilistic bisimilarity for the specified labelled Markov chain.
	 *
	 * @param model The DTMC or CTMC.
	 * @param eval  The evaluator to manipulate values.
	 * @param dtmc  1 if the model is a DTMC and 0 if it is a CTMC.
	 * @return true if the model was minimised, false otherwise.
	 */
	protected boolean robustBisimilarity(DTMC<Value> model, Evaluator<Value> eval, int dtmc)
	{
		bisimilarity(model, eval, dtmc);
		if (numStates == numBlocks) {
			return false;
		}
		robust(model, eval, dtmc);
		return numStates != numBlocks;
	}

	/**
	 * Computes robust probabilistic bisimilarity for the specified labelled Markov chain,
	 * given that probabilistic bisimilarity was already computed.
	 *
	 * @param model The DTMC or CTMC.
	 * @param eval  The evaluator to manipulate values.
	 * @param dtmc  1 if the model is a DTMC and 0 if it is a CTMC.
	 */
	protected void robust(DTMC<Value> model, Evaluator<Value> eval, int dtmc)
	{
		getSuccessors(model);
		while (this.filter()) {
			prune();
			bisimilarity(model, eval, dtmc);
		}
	}

	/**
	 * Computes the list of successors for each state.
	 *
	 * @param dtmc The DTMC
	 */
	protected void getSuccessors(DTMC<Value> dtmc)
	{
		successors = new HashMap<Integer, List<Integer>>(numStates);
		List<Integer> transitions;
		for (int source = 0; source < numStates; source++) {
			transitions = new ArrayList<Integer>();
			Iterator<Entry<Integer, Value>> iter = dtmc.getTransitionsIterator(source);
			while (iter.hasNext()) {
				transitions.add(iter.next().getKey());
			}
			successors.put(source, transitions);
		}
	}

	/**
	 * Constructs the set Q = {(s, t) in R | exists a policy such that R
	 * supports a path from (s, t) to a diagonal pair in ⟨S x S,P⟩}.
	 *
	 * @return False if Q = R, true otherwise (i.e. R has changed)
	 */
	protected boolean filter()
	{
		/* construct R from the partition */
		R = new ArrayList<List<Integer>>(numBlocks);
		for (int block = 0; block < numBlocks; block++) {
			R.add(new ArrayList<Integer>());
		}
		int sizeR = 0;
		for (int state = 0; state < numStates; state++) {
			R.get(partition[state]).add(state);
			sizeR += R.get(partition[state]).size();
		}
		/* initialize Q to the diagonal pairs */
		Q = new ArrayList<Set<Integer>>(numStates);
		int sizeQ = 0;
		for (int state = 0; state < numStates; state++) {
			Q.add(new HashSet<Integer>());
			Q.get(state).add(state);
			sizeQ++;
		}
		boolean changedQ;
		do {
			changedQ = false;
			for (int block = 0; block < numBlocks; block++) {
				for (int i = 0; i < R.get(block).size(); i++) {
					int s = R.get(block).get(i);
					for (int j = i + 1; j < R.get(block).size(); j++) {
						int t = R.get(block).get(j);
						if (!Q.get(s).contains(t) && preQ(s, t)) {
							Q.get(t).add(s);
							Q.get(s).add(t);
							sizeQ++;
							changedQ = true;
						}
					}
				}
			}
		} while (changedQ);
		return sizeQ != sizeR; /* whether R has changed */
	}

	/**
	 * Determines whether (s, t) is a predecessor of Q.
	 *
	 * @param s A state
	 * @param t A state
	 * @return True if (s, t) is a predecessor of some (u, v) in Q
	 */
	protected boolean preQ(int s, int t)
	{
		for (int successorS : successors.get(s)) {
			for (int successorT : successors.get(t)) {
				if (Q.get(successorS).contains(successorT)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Construct the new partition such that (s, t) are in the same block
	 * if and only if they have the same neighbourhood in Q.
	 */
	protected void prune()
	{
		/* construct the new partition */
		numBlocks = 0;
		Arrays.fill(partition, -1);
		for (int state = 0; state < numStates; state++) {
			if (partition[state] == -1) {
				partition[state] = numBlocks;
				for (int other : Q.get(state)) {
					if (partition[other] == -1 && Q.get(state).equals(Q.get(other))) {
						partition[other] = numBlocks;
					}
				}
				numBlocks++;
			}
		}
	}

	@Override
	protected boolean minimiseMDP(MDP<Value> mdp)
	{
		mainLog.println("Robust bisimilarity not yet supported for MDPs: skipping minimisation.");
		return false;
	}
}
