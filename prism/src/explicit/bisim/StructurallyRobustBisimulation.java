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

import prism.PrismComponent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StructurallyRobustBisimulation<Value> extends RobustBisimulation<Value>
{
	List<Set<Integer>> S;

	/**
	 * Construct a new StructurallyRobustBisimulation object.
	 */
	public StructurallyRobustBisimulation(PrismComponent parent)
	{
		super(parent);
	}

	protected void computeStablePairs()
	{
		/* initialize S to the bisimilar pairs */
		S = new ArrayList<Set<Integer>>(numStates);
		for (int state = 0; state < numStates; state++) {
			S.add(new HashSet<Integer>());
		}
		for (int block = 0; block < numBlocks; block++) {
			for (int source : R.get(block)) {
				for (int target : R.get(block)) {
					S.get(source).add(target);
				}
			}
		}
		boolean changed;
		do {
			changed = false;
			for (int state = 0; state < numStates; state++) {
				if (S.get(state).size() > 1) {
					int target = successors.get(state).get(0);
					for (int successor : successors.get(state)) {
						if (!S.get(target).contains(successor)) {
							changed = true;
							for (int remove : R.get(partition[state])) {
								S.get(remove).clear();
								S.get(remove).add(remove);
							}
							break;
						}
					}
				}
			}
		} while (changed);
	}

	/**
	 * Constructs the set Q = {(s, t) in R | exists a policy such that R
	 * supports a path from (s, t) to a stable pair in ⟨S x S,P⟩}.
	 *
	 * @return False if Q = R, true otherwise (i.e. R has changed)
	 */
	@Override
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
		}
		for (int block = 0; block < numBlocks; block++) {
			sizeR += R.get(block).size() * R.get(block).size();
		}
		if (S == null) {
			computeStablePairs();
		}
		/* initialize Q to the stable pairs */
		Q = new ArrayList<Set<Integer>>(numStates);
		int sizeQ = 0;
		for (int state = 0; state < numStates; state++) {
			HashSet<Integer> pairs = new HashSet<Integer>(S.get(state).size());
			for (int pair : S.get(state)) {
				pairs.add(pair);
			}
			Q.add(pairs);
			sizeQ += pairs.size();
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
							sizeQ += 2;
							changedQ = true;
						}
					}
				}
			}
		} while (changedQ);
		return sizeQ != sizeR; /* whether R has changed */
	}
}
