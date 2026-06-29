//==============================================================================
//	
//	Copyright (c) 2002-
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

package explicit.bisim;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import explicit.CTMC;
import explicit.CTMCSimple;
import explicit.Distribution;
import explicit.DTMC;
import explicit.DTMCSimple;
import explicit.MDP;
import explicit.MDPSimple;
import explicit.Model;
import explicit.ModelExplicit;
import explicit.rewards.Rewards;
import explicit.rewards.RewardsSimple;
import parser.State;
import prism.Evaluator;
import prism.PrismComponent;
import prism.PrismNotSupportedException;

/**
 * Class to perform bisimulation minimisation for explicit-state models.
 * It is only necessary for subclasses to implement the method(s) minimiseModel(Model)
 * where Model is the supported model type(s), for example {@link #minimiseDTMC(DTMC)};
 * the other methods have default implementations.
 */
public abstract class Bisimulation<Value> extends PrismComponent
{
	// Local storage of partition info
	protected int numStates;
	protected int[] partition;
	protected int numBlocks;
	protected boolean minimised;

	/** The signatures of each block in the final partition. */
	private Map<Integer, Distribution<Value>> probabilities;
	private Map<Integer, Map<Object, Set<Distribution<Value>>>> mdpProbabilities;

	/**
	 * Constructs a new Bisimulation object. Such a constructor must be defined in extending classes.
	 */
	public Bisimulation(PrismComponent parent)
	{
		super(parent);
	}

	/**
	 * Perform bisimulation minimisation on a model.
	 * Labels and rewards to be respected are those attached to the model.
	 *
	 * @param model The model
	 */
	public Model<Value> minimise(Model<Value> model) throws PrismNotSupportedException
	{
		switch (model.getModelType()) {
			case DTMC:
				return computeMinimisedDTMC((DTMC<Value>) model);
			case CTMC:
				return computeMinimisedCTMC((CTMC<Value>) model);
			case MDP:
				return computeMinimisedMDP((MDP<Value>) model);
			default:
				throw new PrismNotSupportedException("Bisimulation minimisation is not yet supported for " +
					model.getModelType() + "s");
		}
	}

	/**
	 * Perform bisimulation minimisation on a DTMC.
	 * Labels and rewards to be respected are those attached to the model.
	 *
	 * @param dtmc The DTMC
	 */
	protected DTMC<Value> computeMinimisedDTMC(DTMC<Value> dtmc)
	{
		long timer = System.currentTimeMillis();
		List<BitSet> propBSs = new ArrayList<>(dtmc.getLabelToStatesMap().values());
		initialisePartitionInfo(dtmc, propBSs); /* Create initial partition based on propositions */
		minimised = minimiseDTMC(dtmc);
		if (minimised) {
			DTMCSimple<Value> dtmcNew = buildReducedDTMC(dtmc);
			attachAnnotations(dtmc, dtmcNew);
			timer = System.currentTimeMillis() - timer;
			mainLog.println("Minimisation: " + numStates + " to " + numBlocks + " States");
			mainLog.println("Time for bisimulation computation: " + timer / 1000.0 + " seconds.");
			return dtmcNew;
		} /* If the state space was not minimised, do not create a reduced model */
		timer = System.currentTimeMillis() - timer;
		mainLog.println("Minimisation: " + numStates + " to " + numBlocks + " States");
		mainLog.println("Time for bisimulation computation: " + timer / 1000.0 + " seconds.");
		return dtmc;
	}

	/**
	 * Perform bisimulation minimisation on a CTMC.
	 * Labels and rewards to be respected are those attached to the model.
	 *
	 * @param ctmc The CTMC
	 */
	protected CTMC<Value> computeMinimisedCTMC(CTMC<Value> ctmc)
	{
		long timer = System.currentTimeMillis();
		List<BitSet> propBSs = new ArrayList<>(ctmc.getLabelToStatesMap().values());
		initialisePartitionInfo(ctmc, propBSs); /* Create initial partition based on propositions */
		minimised = minimiseCTMC(ctmc);
		if (minimised) {
			CTMCSimple<Value> ctmcNew = buildReducedCTMC(ctmc);
			attachAnnotations(ctmc, ctmcNew);
			timer = System.currentTimeMillis() - timer;
			mainLog.println("Minimisation: " + numStates + " to " + numBlocks + " States");
			mainLog.println("Time for bisimulation computation: " + timer / 1000.0 + " seconds.");
			return ctmcNew;
		} /* If the state space was not minimised, do not create a reduced model */
		timer = System.currentTimeMillis() - timer;
		mainLog.println("Minimisation: " + numStates + " to " + numBlocks + " States");
		mainLog.println("Time for bisimulation computation: " + timer / 1000.0 + " seconds.");
		return ctmc;
	}

	/**
	 * Perform bisimulation minimisation on an MDP.
	 * Labels and rewards to be respected are those attached to the model.
	 *
	 * @param mdp The MDP
	 */
	protected MDP<Value> computeMinimisedMDP(MDP<Value> mdp)
	{
		long timer = System.currentTimeMillis();
		List<BitSet> propBSs = new ArrayList<>(mdp.getLabelToStatesMap().values());
		initialisePartitionInfo(mdp, propBSs); /* Create initial partition based on propositions */
		minimised = minimiseMDP(mdp);
		if (minimised) {
			MDPSimple<Value> mdpNew = buildReducedMDP(mdp);
			attachAnnotations(mdp, mdpNew);
			timer = System.currentTimeMillis() - timer;
			mainLog.println("Minimisation: " + numStates + " to " + numBlocks + " States");
			mainLog.println("Time for bisimulation computation: " + timer / 1000.0 + " seconds.");
			return mdpNew;
		} /* If the state space was not minimised, do not create a reduced model */
		timer = System.currentTimeMillis() - timer;
		mainLog.println("State space was not minimised.");
		mainLog.println("Time for bisimulation computation: " + timer / 1000.0 + " seconds.");
		return mdp;
	}

	/**
	 * Partitions the states of the specified labelled Markov chain, given the
	 * initial partition, updating {@code numBlocks} and {@code partition}.
	 * States are in the same set, that is, are mapped to the same integer, if
	 * and only if they are probabilistic bisimilar.
	 *
	 * @param dtmc The DTMC
	 * @return True if the state space is minimised, false otherwise.
	 */
	protected boolean minimiseDTMC(DTMC<Value> dtmc)
	{
		mainLog.println("This bisimulation method is not yet supported for DTMCs: skipping minimisation.");
		return false;
	}

	/**
	 * Partitions the states of the specified labelled Markov chain, given the
	 * initial partition, updating {@code numBlocks} and {@code partition}.
	 * States are in the same set, that is, are mapped to the same integer, if
	 * and only if they are probabilistic bisimilar.
	 *
	 * @param ctmc The CTMC
	 * @return True if the state space is minimised, false otherwise.
	 */
	protected boolean minimiseCTMC(CTMC<Value> ctmc)
	{
		mainLog.println("This bisimulation method is not yet supported for CTMCs: skipping minimisation.");
		return false;
	}

	/**
	 * Partitions the states of the Markov decision process, given the
	 * initial partition, updating {@code numBlocks} and {@code partition}.
	 * States are in the same set, that is, are mapped to the same integer, if
	 * and only if they are probabilistic bisimilar.
	 *
	 * Any transition rewards attached to the model are also respected.
	 *
	 * @param mdp The MDP
	 * @return True if the state space is minimised, false otherwise
	 */
	protected boolean minimiseMDP(MDP<Value> mdp)
	{
		mainLog.println("This bisimulation method is not yet supported for MDPs: skipping minimisation.");
		return false;
	}

	/**
	 * Build the reduced model.
	 *
	 * @return The reduced DTMC.
	 */
	protected DTMCSimple<Value> buildReducedDTMC(DTMC<Value> dtmc)
	{
		lifting(dtmc, dtmc.getEvaluator());
		DTMCSimple<Value> dtmcNew = new DTMCSimple<>(numBlocks);
		for (int i = 0; i < numBlocks; i++) {
			for (Map.Entry<Integer, Value> e : probabilities.get(i)) {
				dtmcNew.setProbability(i, e.getKey(), e.getValue());
				// mainLog.println(i + " -> " + e.getKey() + " : " + e.getValue());
			}
		}
		return dtmcNew;
	}

	/**
	 * Build the reduced model.
	 *
	 * @return The reduced CTMC.
	 */
	protected CTMCSimple<Value> buildReducedCTMC(CTMC<Value> ctmc)
	{
		lifting(ctmc, ctmc.getEvaluator());
		CTMCSimple<Value> ctmcNew = new CTMCSimple<>(numBlocks);
		for (int i = 0; i < numBlocks; i++) {
			for (Map.Entry<Integer, Value> e : probabilities.get(i)) {
				ctmcNew.setProbability(i, e.getKey(), e.getValue());
			}
		}
		return ctmcNew;
	}

	/**
	 * Build the reduced model.
	 *
	 * @return The reduced MDP.
	 */
	protected MDPSimple<Value> buildReducedMDP(MDP<Value> mdp)
	{
		mdpLifting(mdp, mdp.getEvaluator());
		MDPSimple<Value> mdpNew = new MDPSimple<>(numBlocks);
		for (int i = 0; i < numBlocks; i++) {
			for (Map.Entry<Object, Set<Distribution<Value>>> entry : mdpProbabilities.get(i).entrySet()) {
				for (Distribution<Value> distribution : entry.getValue()) {
					mdpNew.addActionLabelledChoice(i, distribution, entry.getKey());
				}
			}
		}
		return mdpNew;
	}

	/**
	 * Construct the initial partition based on a set of proposition bitsets and,
	 * if present, the state rewards attached to the model.
	 * Store info in {@code numStates}, {@code numBlocks} and {@code partition}.
	 */
	protected void initialisePartitionInfo(Model<Value> model, List<BitSet> propBSs)
	{
		BitSet bs1, bs0;
		numStates = model.getNumStates();
		partition = new int[numStates];
		Rewards<Value> rewards = model.getNumRewards() > 0 ? model.getRewards(0) : null;

		// Initial partition: reward BitSets if available, otherwise use the first proposition
		List<BitSet> all = new ArrayList<>();
		int propStart = 0;
		if (rewards != null && rewards.hasStateRewards()) {
			Map<Value, BitSet> rewardBSs = new HashMap<>();
			for (int s = 0; s < numStates; s++) {
				rewardBSs.computeIfAbsent(rewards.getStateReward(s), r -> new BitSet()).set(s);
			}
			all.addAll(rewardBSs.values());
		} else if (!propBSs.isEmpty()) {
			bs1 = (BitSet) propBSs.get(0).clone();
			bs0 = (BitSet) bs1.clone();
			bs0.flip(0, numStates);
			all.add(bs1);
			all.add(bs0);
			propStart = 1;
		} else {
			// no labels
			for (int s = 0; s < numStates; s++) {
				partition[s] = 0;
			}
			numBlocks = 1;
			return;
		}

		// Compute all non-empty combinations of propositions
		for (int i = propStart; i < propBSs.size(); i++) {
			BitSet bs = propBSs.get(i);
			int m = all.size();
			for (int j = 0; j < m; j++) {
				bs1 = all.get(j);
				bs0 = (BitSet) bs1.clone();
				bs0.andNot(bs);
				bs1.and(bs);
				if (bs1.isEmpty()) {
					all.set(j, bs0);
				} else {
					if (!bs0.isEmpty()) all.add(bs0);
				}
			}
		}

		// Construct initial partition
		numBlocks = all.size();
		for (int j = 0; j < numBlocks; j++) {
			BitSet bs = all.get(j);
			for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
				partition[i] = j;
			}
		}
	}

	/**
	 * Did bisimulation minimisation reduce the state space of the model?
	 */
	public boolean minimised()
	{
		return minimised;
	}

	/**
	 * Computes the signatures (lifting of the probability distribution) of each
	 * block in the current partition and stores it in {@code probabilities}.
	 *
	 * @param dtmc The DTMC
	 * @param eval the evaluator to manipulate values.
	 */
	protected void lifting(DTMC<Value> dtmc, Evaluator<Value> eval)
	{
		/* probabilities.get(s) is the signature of states in block s */
		probabilities = new HashMap<Integer, Distribution<Value>>(numBlocks);
		for (int source = 0; source < numStates; source++) {
			int blockOfSource = partition[source];
			if (!probabilities.containsKey(blockOfSource)) {
				Distribution<Value> distNew = new Distribution<Value>(eval);
				Iterator<Map.Entry<Integer, Value>> iter = dtmc.getTransitionsIterator(source);
				while (iter.hasNext()) {
					Map.Entry<Integer, Value> transition = iter.next();
					distNew.add(partition[transition.getKey()], transition.getValue());
				}
				probabilities.put(blockOfSource, distNew);
			}
		}
	}

	/**
	 * Computes the signatures (lifting of the probability distribution) of each
	 * block in the current partition and stores it in {@code probabilities}.
	 *
	 * @param mdp  The MDP
	 * @param eval the evaluator to manipulate values.
	 */
	protected void mdpLifting(MDP<Value> mdp, Evaluator<Value> eval)
	{
		/* mdpProbabilities.get(s) is the actions and associated signatures of states in block s */
		mdpProbabilities = new HashMap<Integer, Map<Object, Set<Distribution<Value>>>>(numBlocks);
		for (int source = 0; source < numStates; source++) {
			int blockOfSource = partition[source];
			if (!mdpProbabilities.containsKey(blockOfSource)) {
				int numActions = mdp.getNumChoices(source);
				Map<Object, Set<Distribution<Value>>> actionDistributions = new HashMap<Object, Set<Distribution<Value>>>(numActions);
				// System.out.println(blockOfSource + " " + numActions);
				for (int actionId = 0; actionId < numActions; actionId++) {
					Object action = mdp.getAction(source, actionId);
					// System.out.println(action);
					Distribution<Value> distNew = new Distribution<Value>(eval);
					Iterator<Map.Entry<Integer, Value>> iter = mdp.getTransitionsIterator(source, actionId);
					while (iter.hasNext()) {
						Map.Entry<Integer, Value> transition = iter.next();
						distNew.add(partition[transition.getKey()], transition.getValue());
					}
					if (!actionDistributions.containsKey(action)) {
						actionDistributions.put(action, new HashSet<Distribution<Value>>());
					}
					actionDistributions.get(action).add(distNew);
				}
				mdpProbabilities.put(blockOfSource, actionDistributions);
			}
		}
	}

	/**
	 * Attach a list of states to the minimised model by adding a representative state
	 * from the original model.
	 * Also attach information about the propositions (used for bisimulation
	 * minimisation) to the minimised model, in the form of labels (stored as BitSets),
	 * and, if present, the rewards attached to the original model.
	 *
	 * @param model     The original model
	 * @param modelNew  The minimised model
	 */
	protected void attachAnnotations(Model<Value> model, ModelExplicit<Value> modelNew)
	{
		// Attach states
		if (model.getStatesList() != null) {
			List<State> statesList = model.getStatesList();
			List<State> statesListNew = new ArrayList<State>(numBlocks);
			for (int i = 0; i < numBlocks; i++) {
				statesListNew.add(null);
			}
			for (int i = 0; i < numStates; i++) {
				if (statesListNew.get(partition[i]) == null) {
					statesListNew.set(partition[i], statesList.get(i));
				}
			}
			modelNew.setStatesList(statesListNew);
		}

		// Build/attach new labels
		List<String> propNames = new ArrayList<>();
		List<BitSet> propBSs = new ArrayList<>();
		model.getLabelToStatesMap().forEach((name, bs) -> { propNames.add(name); propBSs.add(bs); });
		int numProps = propBSs.size();
		for (int i = 0; i < numProps; i++) {
			String propName = propNames.get(i);
			BitSet propBS = propBSs.get(i);
			BitSet propBSnew = new BitSet();
			for (int j = propBS.nextSetBit(0); j >= 0; j = propBS.nextSetBit(j + 1)) {
				// mainLog.println(propName + ": " + partition[j]);
				propBSnew.set(partition[j]);
			}
			modelNew.addLabel(propName, propBSnew);
		}

		// Build state rewards for the minimised model
		if (model.getNumRewards() > 0) {
			Rewards<Value> rewards = model.getRewards(0);
			if (rewards.hasStateRewards()) {
				RewardsSimple<Value> rewardsNew = new RewardsSimple<Value>(numBlocks);
				for (int i = 0; i < numStates; i++) {
					rewardsNew.setStateReward(partition[i], rewards.getStateReward(i));
				}
				modelNew.addRewards(model.getRewardName(0), model.getRewardPosition(0), rewardsNew);
			}
		}
	}

	/**
	 * Display the current partition, showing the states in each block.
	 */
	@SuppressWarnings("unused")
	protected void printPartition()
	{
		for (int i = 0; i < numBlocks; i++) {
			mainLog.print(i + ":");
			for (int j = 0; j < numStates; j++)
				if (partition[j] == i)
					mainLog.print(" " + j);
			mainLog.println();
		}
	}
}
