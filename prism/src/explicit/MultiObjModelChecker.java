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

package explicit;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import acceptance.AcceptanceRabin;
import automata.DA;
import explicit.rewards.MCRewardsFromMDPRewards;
import explicit.rewards.MDPRewards;
import explicit.rewards.Rewards;
import explicit.rewards.RewardsSimple;
import parser.ast.Expression;
import parser.ast.ExpressionFunc;
import parser.ast.ExpressionQuant;
import parser.ast.ExpressionReward;
import explicit.ModelCheckerResult;
import prism.MultiObjModelCheckerUtils;
import prism.MultiObjQuery;
import prism.MultiObjQueryInstance;
import prism.Operator;
import prism.Point;
import prism.PrismComponent;
import prism.PrismException;
import prism.PrismNotSupportedException;
import prism.PrismSettings;
import prism.TileList;
import strat.MDStrategyArray;

/**
 * Multi-objective model checking for the explicit engine.
 *
 * <p>Currently supports R[C] (unbounded cumulative reward) objectives only.
 * P objectives, step-bounded R[C&lt;=k], and end-component handling are deferred.
 */
public class MultiObjModelChecker extends prism.MultiObjModelChecker
{
	/** The parent model checker providing access to settings, log, and single-objective solvers. */
	private final ProbModelChecker mc;

	public MultiObjModelChecker(PrismComponent parent, ProbModelChecker mc) throws PrismException
	{
		super(parent);
		this.mc = mc;
	}

	/**
	 * Top-level entry point for multi-objective model checking.
	 * Parses the query, validates it, and dispatches to Pareto or achievability computation.
	 *
	 * @param model          The explicit-state MDP
	 * @param expr           The multi(...) expression
	 * @param statesOfInterest States of interest (must contain the single initial state)
	 * @return TileList for Pareto queries; Double for achievability/numerical queries
	 */
	@SuppressWarnings("unchecked")
	public Object checkMultiObjective(explicit.MDP<?> model, ExpressionFunc expr, BitSet statesOfInterest) throws PrismException
	{
		MDP<Double> mdp = (MDP<Double>) model;
		int n = expr.getNumOperands();

		// Parse query
		MultiObjQuery moQuery = new MultiObjQuery(n);
		for (int i = 0; i < n; i++) {
			ExpressionQuant exprQuant = (ExpressionQuant) expr.getOperand(i);
			MultiObjModelCheckerUtils.extractOperatorAndStepBound(exprQuant, moQuery, mc.getConstantValues(), i);
		}
		MultiObjModelCheckerUtils.validateQueryStructure(moQuery);

		// P objectives require product MDP construction (not yet implemented)
		if (moQuery.numProbObjectives() > 0) {
			throw new PrismNotSupportedException("P objectives are not yet supported for multi-objective model checking with the explicit engine");
		}

		// Retrieve one reward structure per objective
		List<Rewards<Double>> rewardsList = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			ExpressionReward exprReward = (ExpressionReward) expr.getOperand(i);
			int r = exprReward.getRewardStructIndexByIndexObject(mc.getRewardGenerator(mdp), mc.getConstantValues());
			rewardsList.add((Rewards<Double>) mc.constructRewards(mdp, r));
		}

		// Bundle into an instance (no DRA, no probability targets for reward-only queries)
		DA<BitSet, AcceptanceRabin>[] dra = new DA[n];
		MultiObjQueryInstance<Rewards<Double>, BitSet> instance = new MultiObjQueryInstance<>(moQuery, dra, rewardsList);

		// Negate minimising rewards in-place, then canonicalise all to R_MAX / R_GE
		for (int i = 0; i < moQuery.numRewardObjectives(); i++) {
			if (moQuery.getRewardOperator(i) == Operator.R_LE || moQuery.getRewardOperator(i) == Operator.R_MIN) {
				instance.rewards.set(i, negateRewards(mdp, instance.rewards.get(i)));
			}
		}
		moQuery.makeAllRewardUp();

		// Build engine-specific weighted-sum solver and dispatch
		int initState = statesOfInterest.nextSetBit(0);
		WeightedObjectiveSolver solver = buildExplicitWeightedSolver(mdp, initState, instance);

		int numNumerical = moQuery.numberOfNumerical();
		if (numNumerical >= 2) {
			// Pareto curve: seed with one extreme point per objective, then iterate
			List<Point> axisPoints = buildAxisInitialPoints(solver, 0, moQuery.numRewardObjectives());
			double tolerance = settings.getDouble(PrismSettings.PRISM_PARETO_EPSILON);
			int maxIters = settings.getInteger(PrismSettings.PRISM_MULTI_MAX_POINTS);
			TileList tileList = runParetoCurveIteration(solver, moQuery, axisPoints, tolerance, maxIters);
			List<Expression> exprs = new ArrayList<>(n);
			for (int i = 0; i < n; i++) exprs.add(expr.getOperand(i));
			tileList.setFormulas(exprs);
			return tileList;
		} else {
			int maxIters = settings.getInteger(PrismSettings.PRISM_MULTI_MAX_POINTS);
			double result = runAchievabilityIteration(solver, moQuery, maxIters);
			if (numNumerical == 0) {
				// Achievability query: runAchievabilityIteration returns 1.0 (feasible) or 0.0 (infeasible)
				return result >= 0.5;
			}
			// Single-numerical query: convert solver-space back for originally-minimising objectives
			if (moQuery.numRewardObjectives() > 0 && moQuery.isRewardNegated(0)) {
				result = -result;
			}
			return result;
		}
	}

	/**
	 * Build a negated copy of a reward structure (all values multiplied by -1).
	 * Used to convert minimising objectives to maximising before running the solver.
	 */
	private Rewards<Double> negateRewards(MDP<Double> mdp, Rewards<Double> rew)
	{
		int numStates = mdp.getNumStates();
		RewardsSimple<Double> neg = new RewardsSimple<>(numStates);
		for (int s = 0; s < numStates; s++) {
			double sr = rew.getStateReward(s);
			if (sr != 0) neg.setStateReward(s, -sr);
			for (int ch = 0; ch < mdp.getNumChoices(s); ch++) {
				double tr = rew.getTransitionReward(s, ch);
				if (tr != 0) neg.setTransitionReward(s, ch, -tr);
			}
		}
		return neg;
	}

	/**
	 * Build a {@link WeightedObjectiveSolver} for the explicit engine.
	 *
	 * <p>For each weight vector the returned solver:
	 * <ol>
	 *   <li>Constructs a combined weighted reward structure.</li>
	 *   <li>Solves the weighted-sum MDP to find the optimal policy.</li>
	 *   <li>Evaluates each individual reward objective under that fixed policy via DTMC VI.</li>
	 *   <li>Returns the per-objective values at the initial state.</li>
	 * </ol>
	 *
	 * <p>Callers must have already negated minimising reward structures and canonicalised
	 * {@code instance.moQuery} to R_MAX/R_GE via {@link MultiObjQuery#makeAllRewardUp()}.
	 */
	private WeightedObjectiveSolver buildExplicitWeightedSolver(MDP<Double> mdp, int initState,
	                                                             MultiObjQueryInstance<Rewards<Double>, BitSet> instance)
	        throws PrismException
	{
		int numStates = mdp.getNumStates();
		int dim = instance.moQuery.numRewardObjectives();
		List<Rewards<Double>> rewards = instance.rewards;

		// Step-bounded R[C<=k] requires a different VI loop — deferred
		for (int i = 0; i < dim; i++) {
			if (instance.moQuery.getRewardStepBound(i) != -1) {
				throw new PrismNotSupportedException("Step-bounded R[C<=k] objectives are not yet supported for multi-objective model checking with the explicit engine");
			}
		}

		MDPModelChecker mdpMC = (MDPModelChecker) mc;

		return weights -> {
			// Step A: build weighted reward structure
			RewardsSimple<Double> wRew = new RewardsSimple<>(numStates);
			for (int s = 0; s < numStates; s++) {
				double sr = 0.0;
				for (int ri = 0; ri < dim; ri++) {
					sr += weights[ri] * rewards.get(ri).getStateReward(s);
				}
				if (sr != 0) wRew.setStateReward(s, sr);
				for (int ch = 0; ch < mdp.getNumChoices(s); ch++) {
					double tr = 0.0;
					for (int ri = 0; ri < dim; ri++) {
						tr += weights[ri] * rewards.get(ri).getTransitionReward(s, ch);
					}
					if (tr != 0) wRew.setTransitionReward(s, ch, tr);
				}
			}

			// Step B: solve MDP weighted-sum — unbounded cumulative reward (R[C])
			// Request strategy output to avoid tie-breaking issues in post-hoc argmax extraction
			boolean prevGenStrat = mdpMC.genStrat;
			mdpMC.setGenStrat(true);
			ModelCheckerResult resW = mdpMC.computeTotalRewards(mdp, wRew, false);
			mdpMC.setGenStrat(prevGenStrat);

			// Step C: extract strategy from VI result (choice indices, -1/−2/−3 for undefined/arbitrary/unreachable)
			@SuppressWarnings("unchecked")
			MDStrategyArray<Double> mdStrat = (MDStrategyArray<Double>) resW.strat;
			int[] strat = new int[numStates];
			for (int s = 0; s < numStates; s++) {
				int c = mdStrat.getChoiceIndex(s, 0);
				strat[s] = (c >= 0) ? c : 0;
			}

			// Step D: evaluate each reward objective under the fixed policy via DTMC VI
			DTMC<Double> dtmc = new DTMCFromMDPMemorylessAdversary<>(mdp, strat);
			DTMCModelChecker dtmcMC = new DTMCModelChecker(mc);
			double[] objVals = new double[dim];
			for (int i = 0; i < dim; i++) {
				MCRewardsFromMDPRewards<Double> mcRew = new MCRewardsFromMDPRewards<>((MDPRewards<Double>) rewards.get(i), strat);
				ModelCheckerResult ri = dtmcMC.computeTotalRewards(dtmc, mcRew);
				objVals[i] = ri.soln[initState];
			}
			return objVals;
		};
	}
}
