//==============================================================================
//	
//	Copyright (c) 2016-
//	Authors:
//	* Joachim Klein <klein@tcs.inf.tu-dresden.de> (TU Dresden)
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


package explicit;

import java.util.ArrayList;
import java.util.BitSet;

import prism.PrismException;
import strat.MDStrategy;

/**
 * This is a minimal implementation of an explicitly stored labeled transition system.
 * Each target state of an edge is modeled as a choice, with a single transition for
 * this choice.
 */
public class LTSExplicit<Value> extends ModelExplicit<Value> implements LTS<Value>
{
	protected ArrayList<ArrayList<Integer>> successors = new ArrayList<ArrayList<Integer>>();
	protected int numTransitions = 0;
	protected int maxNumChoices = 0;

	public LTSExplicit()
	{
		initialise(0);
	}

	public int addState()
	{
		successors.add(new ArrayList<Integer>());
		return numStates++;
	}

	public void addEdge(int s, int t)
	{
		// we don't care if an edge from s to t already exists
		successors.get(s).add(t);
		numTransitions++;
		maxNumChoices = Math.max(getNumChoices(s), maxNumChoices);
	}

	@Override
	public int getNumChoices(int s)
	{
		// one choice per successor for s
		return successors.get(s).size();
	}

	@Override
	public int getMaxNumChoices()
	{
		return maxNumChoices;
	}

	@Override
	public int getNumChoices()
	{
		return numTransitions;
	}

	@Override
	public Object getAction(int s, int i)
	{
		// we don't have action labels
		return null;
	}

	@Override
	public boolean areAllChoiceActionsUnique()
	{
		// as we don't assign action labels, they are not unique
		return false;
	}

	@Override
	public int getNumTransitions(int s, int i)
	{
		if (i < getNumChoices(s)) {
			// one transition per choice
			return 1;
		}
		throw new IllegalArgumentException();
	}

	@Override
	public boolean allSuccessorsInSet(int s, int i, BitSet set)
	{
		// single successor for s, i
		return set.get(successors.get(s).get(i));
	}

	@Override
	public boolean someSuccessorsInSet(int s, int i, BitSet set)
	{
		// single successor for s, i
		return set.get(successors.get(s).get(i));
	}

	@Override
	public SuccessorsIterator getSuccessors(int s, int i)
	{
		// single successor for s, i
		return SuccessorsIterator.fromSingleton(successors.get(s).get(i));
	}

	@Override
	public Model<Value> constructInducedModel(MDStrategy strat)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public SuccessorsIterator getSuccessors(int s)
	{
		return SuccessorsIterator.from(successors.get(s).iterator(), false);
	}

	@Override
	public void findDeadlocks(boolean fix) throws PrismException
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void buildFromPrismExplicit(String filename) throws PrismException
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public int getNumTransitions()
	{
		return numTransitions;
	}

	@Override
	public int getNumTransitions(int s)
	{
		return getNumChoices(s);
	}

	@Override
	public void checkForDeadlocks(BitSet except) throws PrismException
	{
		throw new UnsupportedOperationException();
	}
}
