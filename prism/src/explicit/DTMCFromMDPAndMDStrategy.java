//==============================================================================
//	
//	Copyright (c) 2013-
//	Authors:
//	* Dave Parker <d.a.parker@cs.bham.ac.uk> (University of Birmingham/Oxford)
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

import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import explicit.rewards.MCRewards;
import parser.State;
import parser.Values;
import prism.ModelInfo;
import prism.ModelInfoCopy;
import prism.ModelType;
import prism.PrismException;
import prism.PrismNotSupportedException;
import prism.RewardGenerator;
import strat.MDStrategy;

/**
* Explicit-state representation of a DTMC, constructed (implicitly)
* from an MDP and a memoryless deterministic (MD) adversary.
* This class is read-only: most of the data is pointers to other model info.
*/
public class DTMCFromMDPAndMDStrategy extends DTMCExplicit
{
	// Parent MDP
	protected MDP mdp;
	// MD strategy
	protected MDStrategy strat;

	/**
	 * Constructor: create from MDP and memoryless adversary.
	 */
	public DTMCFromMDPAndMDStrategy(MDP mdp, MDStrategy strat)
	{
		this.mdp = mdp;
		this.numStates = mdp.getNumStates();
		this.strat = strat;
	}

	@Override
	public void buildFromPrismExplicit(String filename) throws PrismException
	{
		throw new PrismNotSupportedException("Not supported");
	}

	// Accessors (for Model)

	public ModelType getModelType()
	{
		return ModelType.DTMC;
	}

	public int getNumStates()
	{
		return mdp.getNumStates();
	}

	public int getNumInitialStates()
	{
		return mdp.getNumInitialStates();
	}

	public Iterable<Integer> getInitialStates()
	{
		return mdp.getInitialStates();
	}

	public int getFirstInitialState()
	{
		return mdp.getFirstInitialState();
	}

	public boolean isInitialState(int i)
	{
		return mdp.isInitialState(i);
	}

	public boolean isDeadlockState(int i)
	{
		return mdp.isDeadlockState(i);
	}

	public List<State> getStatesList()
	{
		return mdp.getStatesList();
	}

	public Values getConstantValues()
	{
		return mdp.getConstantValues();
	}

	public int getNumTransitions()
	{
		int numTransitions = 0;
		for (int s = 0; s < numStates; s++)
			if (strat.isChoiceDefined(s))
				numTransitions += mdp.getNumTransitions(s, strat.getChoiceIndex(s));
		return numTransitions;
	}

	public SuccessorsIterator getSuccessors(final int s)
	{
		if (strat.isChoiceDefined(s)) {
			return mdp.getSuccessors(s, strat.getChoiceIndex(s));
		} else {
			return SuccessorsIterator.empty();
		}
	}

	public int getNumChoices(int s)
	{
		// Always 1 for a DTMC
		return 1;
	}

	public void findDeadlocks(boolean fix) throws PrismException
	{
		// No deadlocks by definition
	}

	public void checkForDeadlocks() throws PrismException
	{
		// No deadlocks by definition
	}

	public void checkForDeadlocks(BitSet except) throws PrismException
	{
		// No deadlocks by definition
	}

	@Override
	public String infoString()
	{
		return mdp.infoString() + " + " + "???"; // TODO
	}

	@Override
	public String infoStringTable()
	{
		return mdp.infoString() + " + " + "???\n"; // TODO
	}

	// Accessors (for DTMC)

	public int getNumTransitions(int s)
	{
		return strat.isChoiceDefined(s) ? mdp.getNumTransitions(s, strat.getChoiceIndex(s)) : 0;
	}

	public Iterator<Entry<Integer, Double>> getTransitionsIterator(int s)
	{
		if (strat.isChoiceDefined(s)) {
			return mdp.getTransitionsIterator(s, strat.getChoiceIndex(s));
		} else {
			// Empty iterator
			Map<Integer,Double> empty = Collections.emptyMap();
			return empty.entrySet().iterator();
		}
	}

	@Override
	public void forEachTransition(int s, TransitionConsumer c)
	{
		if (!strat.isChoiceDefined(s)) {
			return;
		}
		mdp.forEachTransition(s, strat.getChoiceIndex(s), c::accept);
	}

	@Override
	public double mvMultSingle(int s, double vect[])
	{
		return strat.isChoiceDefined(s) ? mdp.mvMultSingle(s, strat.getChoiceIndex(s), vect) : 0;
	}

	@Override
	public double mvMultJacSingle(int s, double vect[])
	{
		return strat.isChoiceDefined(s) ? mdp.mvMultJacSingle(s, strat.getChoiceIndex(s), vect) : 0;
	}

	@Override
	public double mvMultRewSingle(int s, double vect[], MCRewards mcRewards)
	{
		return strat.isChoiceDefined(s) ? mdp.mvMultRewSingle(s, strat.getChoiceIndex(s), vect, mcRewards) : 0;
	}

	@Override
	public void vmMult(double vect[], double result[])
	{
		throw new RuntimeException("Not implemented yet");
	}

	@Override
	public String toString()
	{
		throw new RuntimeException("Not implemented yet");
	}

	@Override
	public boolean equals(Object o)
	{
		throw new RuntimeException("Not implemented yet");
	}
	
	/**
	 * Convert model info for the MDP into a version for the DTMC.
	 */
	public ModelInfo convertModelInfo(ModelInfo modelInfo)
	{
		return new ModelInfoCopy(modelInfo)
		{
			@Override
			public ModelType getModelType()
			{
				return ModelType.DTMC;
			}
		};
	}
	
	/**
	 * Convert a reward generator for the MDP into one for the DTMC.
	 */
	public RewardGenerator convertRewardGenerator(RewardGenerator mdpRewardGenerator)
	{
		return new RewardGenerator()
		{
			@Override
			public List<String> getRewardStructNames()
			{
				// Names preserved
				return mdpRewardGenerator.getRewardStructNames();
			}
			
			@Override
			public boolean rewardStructHasStateRewards(int r)
			{
				// MDP transition rewards are mapped to DTMC state rewards
				return mdpRewardGenerator.rewardStructHasStateRewards(r) || mdpRewardGenerator.rewardStructHasTransitionRewards(r);
			}
			
			@Override
			public boolean rewardStructHasTransitionRewards(int r)
			{
				// MDP transition rewards are mapped to DTMC state rewards, so none here
				return false;
			}
			
			@Override
			public boolean isRewardLookupSupported(RewardLookup lookup)
			{
				// Need to look up rewards by state index because strategy is indexed like that.
				return lookup == RewardLookup.BY_STATE;
			}
			
			@Override
			public double getStateReward(int r, int s) throws PrismException
			{
				// Get state reward
				double sr;
				if (mdpRewardGenerator.isRewardLookupSupported(RewardLookup.BY_STATE_INDEX)) {
					sr = mdpRewardGenerator.getStateReward(r, s);
				} else if (mdpRewardGenerator.isRewardLookupSupported(RewardLookup.BY_STATE)) {
					sr = mdpRewardGenerator.getStateReward(r, mdp.getStatesList().get(s));
				} else {
					throw new PrismException("Unsupported lookup mechanism for reward generator");
				}
				// Add chosen transition reward to state reward
				if (strat.isChoiceDefined(s)) {
					double tr;
					if (mdpRewardGenerator.isRewardLookupSupported(RewardLookup.BY_STATE_INDEX)) {
						tr = mdpRewardGenerator.getStateActionReward(r, s, strat.getChoiceAction(s));
					} else if (mdpRewardGenerator.isRewardLookupSupported(RewardLookup.BY_STATE)) {
						tr = mdpRewardGenerator.getStateActionReward(r, mdp.getStatesList().get(s), strat.getChoiceAction(s));
					} else {
						throw new PrismException("Unsupported lookup mechanism for reward generator");
					}
					return sr + tr;
				} else {
					return sr;
				}
			}

			/**
			 * Get the state-action reward of the {@code r}th reward structure for state {@code state} and action {@code action}
			 * ({@code r} is indexed from 0, not from 1 like at the user (property language) level).
			 * If a reward structure has no transition rewards, you can indicate this by implementing
			 * the method {@link #rewardStructHasTransitionRewards(int)}, which may improve efficiency
			 * and/or allow use of algorithms/implementations that do not support transition rewards rewards.
			 * @param r The index of the reward structure to use
			 * @param s The index of the state in which to evaluate the rewards
			 * @param action The outgoing action label 
			 */
			public double getStateActionReward(int r, int s, Object action) throws PrismException
			{
				// MDP transition rewards are mapped to DTMC state rewards, so none here
				return 0.0;
			}
		};
	}
}
