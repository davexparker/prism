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
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import acceptance.AcceptanceRabin;
import automata.DA;
import common.IterableStateSet;
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
import prism.Prism;
import prism.PrismComponent;
import prism.PrismException;
import prism.PrismNotSupportedException;
import prism.PrismSettings;
import prism.TileList;
import solver.LPSolver;
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

		int initState = statesOfInterest.nextSetBit(0);
		int numNumerical = moQuery.numberOfNumerical();

		// LP path: solve the entire query as a single occupancy-measure LP
		if (settings.getChoice(PrismSettings.PRISM_MDP_MULTI_SOLN_METHOD) == Prism.MDP_MULTI_LP) {
			if (numNumerical >= 2) {
				throw new PrismNotSupportedException("Pareto curve computation is not supported with linear programming for the explicit engine; use -valiter");
			}
			return checkMultiObjectiveLP(mdp, initState, instance);
		}

		// Value iteration path
		Object result = checkMultiObjectiveValIter(mdp, initState, instance);
		if (result instanceof TileList) {
			List<Expression> exprs = new ArrayList<>(expr.getNumOperands());
			for (int i = 0; i < expr.getNumOperands(); i++) exprs.add(expr.getOperand(i));
			((TileList) result).setFormulas(exprs);
		}
		return result;
	}

	/**
	 * Perform multi-objective model checking using value iteration (weighted-sum sweeps).
	 * Handles both Pareto curve generation (numNumerical >= 2) and achievability/numerical
	 * queries (numNumerical <= 1).
	 */
	private Object checkMultiObjectiveValIter(MDP<Double> mdp, int initState,
	                                           MultiObjQueryInstance<Rewards<Double>, BitSet> instance)
	        throws PrismException
	{
		MultiObjQuery moQuery = instance.moQuery;
		int numNumerical = moQuery.numberOfNumerical();
		WeightedObjectiveSolver solver = buildExplicitWeightedSolver(mdp, initState, instance);

		if (numNumerical >= 2) {
			// Pareto curve: seed with one extreme point per objective, then iterate
			List<Point> axisPoints = buildAxisInitialPoints(solver, 0, moQuery.numRewardObjectives());
			double tolerance = settings.getDouble(PrismSettings.PRISM_PARETO_EPSILON);
			int maxIters = settings.getInteger(PrismSettings.PRISM_MULTI_MAX_POINTS);
			return runParetoCurveIteration(solver, moQuery, axisPoints, tolerance, maxIters);
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
	 * Solve a multi-objective R[C] achievability or single-numerical query as a single
	 * occupancy-measure LP. Variables y(s,ch) represent the expected number of times
	 * action ch is taken at state s under the optimal policy before the trajectory absorbs.
	 *
	 * <p>Zero-reward MECs are handled by: (a) excluding their internal (MEC) actions from the
	 * LP, and (b) adding one extra "stay-in-MEC" variable per zero-reward MEC state. This
	 * allows the LP to model strategies that absorb into a zero-reward MEC without exiting,
	 * accumulating only the finite rewards earned before entering the MEC.
	 *
	 * <p>Concretely, for each zero-reward MEC state s with EC BitSet M:
	 * <ul>
	 *   <li>LP variables: one per NON-MEC exit action (ch where NOT allSuccessorsInSet(s,ch,M))
	 *       plus one extra y_extra(s) (coefficient 0 in objective/constraints).</li>
	 *   <li>Flow conservation: sum_exit y(s,exit) + y_extra(s) = inflow_from_non-MEC_sources.</li>
	 * </ul>
	 */
	@SuppressWarnings("unchecked")
	private Object checkMultiObjectiveLP(MDP<Double> mdp, int initState,
	                                      MultiObjQueryInstance<Rewards<Double>, BitSet> instance)
	        throws PrismException
	{
		MultiObjQuery moQuery = instance.moQuery;
		List<Rewards<Double>> rewards = instance.rewards;
		int n = mdp.getNumStates();
		int dim = moQuery.numRewardObjectives();
		int numNumerical = moQuery.numberOfNumerical();

		for (int i = 0; i < dim; i++) {
			if (moQuery.getRewardStepBound(i) != -1) {
				throw new PrismNotSupportedException("Step-bounded R[C<=k] objectives are not yet supported for multi-objective LP model checking with the explicit engine");
			}
		}

		// Find index of the numerical objective (R_MAX after makeAllRewardUp); -1 for achievability
		int numObjIdx = -1;
		for (int i = 0; i < dim; i++) {
			if (moQuery.getRewardOperator(i) == Operator.R_MAX) { numObjIdx = i; break; }
		}

		// Enumerate all MECs; classify as positive-reward (→ inf states) or zero-reward (→ EC sinks).
		// A MEC is positive-reward if any state has positive state reward or any MEC action
		// (with all successors within the MEC) has positive transition reward.
		MDPModelChecker mdpMC = (MDPModelChecker) mc;
		ECComputer ecs = ECComputer.createECComputer(this, mdp);
		BitSet positiveECs = new BitSet();
		// mecForState[s]: the zero-reward MEC BitSet containing s, or null if s is not in any zero-reward MEC
		BitSet[] mecForState = new BitSet[n];
		ecs.computeMECStatesStreaming(ec -> {
			boolean isPositive = false;
			outer:
			for (int state : new IterableStateSet(ec, n)) {
				for (int i = 0; i < dim; i++) {
					if (rewards.get(i).getStateReward(state) > 0) { isPositive = true; break outer; }
				}
				for (int ch = 0, nc = mdp.getNumChoices(state); ch < nc; ch++) {
					if (!mdp.allSuccessorsInSet(state, ch, ec)) continue; // not a MEC action
					for (int i = 0; i < dim; i++) {
						if (rewards.get(i).getTransitionReward(state, ch) > 0) { isPositive = true; break outer; }
					}
				}
			}
			if (isPositive) {
				positiveECs.or(ec);
			} else {
				// Zero-reward MEC: record membership for each state in the MEC
				BitSet ecCopy = (BitSet) ec.clone();
				for (int state : new IterableStateSet(ec, n)) {
					mecForState[state] = ecCopy;
				}
			}
		});

		// inf = {s : Pmax(s → positiveECs) > 0}
		BitSet inf = mdpMC.prob0(mdp, null, positiveECs, false, null);
		inf.flip(0, n);

		if (inf.get(initState)) {
			// Infinite reward is achievable from initState
			if (numNumerical == 0) return true;
			return Double.POSITIVE_INFINITY;
		}

		// Assign LP variable indices.
		// For inf states: no LP variables (excluded, treated as absorbing sinks).
		// For zero-reward MEC states s (mecForState[s] != null, not inf):
		//   - One LP variable per non-MEC exit action (ch with !allSuccessorsInSet(s,ch,mecForState[s]))
		//   - Plus one extra "stay-in-MEC" variable with zero reward (represents infinite looping in MEC)
		// For transient states (mecForState[s] == null, not inf):
		//   - One LP variable per choice (all actions)
		//
		// actionVar[s][ch] = LP variable index for (s,ch), or -1 if (s,ch) is a MEC action or s is inf.
		// extraVar[s] = LP variable index for the extra "stay" var of a zero-reward MEC state, or -1.
		int[][] actionVar = new int[n][];
		int[] extraVar = new int[n]; Arrays.fill(extraVar, -1);
		int numVars = 0;
		for (int s = 0; s < n; s++) {
			if (inf.get(s)) continue;
			int numChoices = mdp.getNumChoices(s);
			actionVar[s] = new int[numChoices];
			if (mecForState[s] == null) {
				// Transient state: all actions get LP variables
				for (int ch = 0; ch < numChoices; ch++) {
					actionVar[s][ch] = numVars++;
				}
			} else {
				// Zero-reward MEC state: only exit actions get LP variables, plus one extra
				BitSet myMec = mecForState[s];
				for (int ch = 0; ch < numChoices; ch++) {
					if (!mdp.allSuccessorsInSet(s, ch, myMec)) {
						actionVar[s][ch] = numVars++; // exit action
					} else {
						actionVar[s][ch] = -1; // MEC action: no LP variable
					}
				}
				extraVar[s] = numVars++; // extra "stay-in-MEC" variable
			}
		}

		if (numVars == 0) {
			// No LP variables: all states are inf or zero-reward MEC states with no exit actions
			if (numNumerical == 0) {
				// Check if reward constraints can be satisfied by zero reward
				for (int i = 0; i < dim; i++) {
					if (moQuery.getRewardOperator(i) == Operator.R_GE && moQuery.getRewardBound(i) > 0) {
						return false;
					}
				}
				return true;
			}
			return 0.0;
		}

		LPSolver lp = mc.createLPSolver(numVars);
		mainLog.println("Starting LP for multi-objective model checking (" + lp.getDisplayName()
				+ ", " + numVars + " vars, " + (numNumerical == 0 ? "achievability" : "numerical") + ")...");
		try {
			// Add LP variables in index order with their objective coefficients.
			// Extra variables (for zero-reward MEC "stay" actions) have zero coefficient.
			double[] objCoeffs = new double[numVars];
			if (numObjIdx >= 0) {
				for (int s = 0; s < n; s++) {
					if (actionVar[s] == null) continue;
					for (int ch = 0; ch < mdp.getNumChoices(s); ch++) {
						int vi = actionVar[s][ch];
						if (vi >= 0) {
							objCoeffs[vi] = rewards.get(numObjIdx).getStateReward(s)
							              + rewards.get(numObjIdx).getTransitionReward(s, ch);
						}
					}
				}
			}
			for (int v = 0; v < numVars; v++) {
				lp.addVar(0.0, Double.POSITIVE_INFINITY, objCoeffs[v]);
			}

			// Build flow conservation constraints using a sparse accumulator per state.
			// flowRow[s] maps LP variable index → net coefficient for state s's constraint.
			// Memory is O(transitions), not O(states × vars) as a dense matrix would be.
			//
			// Outflow from s: +1 per LP variable at s (put, since each vi is distinct here).
			// Inflow to t from (s,ch): merge -p for vi = actionVar[s][ch], using Double::sum
			// to correctly accumulate when the same vi contributes multiple times (e.g.
			// a transient state s with a partial self-loop uses merge for both outflow and inflow).
			@SuppressWarnings("unchecked")
			HashMap<Integer, Double>[] flowRow = new HashMap[n];
			for (int s = 0; s < n; s++) {
				if (inf.get(s) || actionVar[s] == null) continue;
				flowRow[s] = new HashMap<>();
				for (int ch = 0; ch < mdp.getNumChoices(s); ch++) {
					int vi = actionVar[s][ch];
					if (vi >= 0) flowRow[s].put(vi, 1.0); // outflow; vi distinct per state
				}
				if (extraVar[s] >= 0) flowRow[s].put(extraVar[s], 1.0);
			}
			for (int s = 0; s < n; s++) {
				if (actionVar[s] == null) continue;
				for (int ch = 0; ch < mdp.getNumChoices(s); ch++) {
					int vi = actionVar[s][ch];
					if (vi < 0) continue; // MEC action, no LP variable
					Iterator<Map.Entry<Integer, Double>> it = mdp.getTransitionsIterator(s, ch);
					while (it.hasNext()) {
						Map.Entry<Integer, Double> e = it.next();
						int t = e.getKey(); double p = e.getValue();
						if (inf.get(t) || actionVar[t] == null) continue;
						if (mecForState[s] != null && mecForState[s].get(t)) continue;
						flowRow[t].merge(vi, -p, Double::sum);
					}
				}
			}

			// Submit flow conservation equality constraints
			double[] cBuf = new double[numVars]; int[] vBuf = new int[numVars];
			for (int s = 0; s < n; s++) {
				if (actionVar[s] == null) continue;
				int count = 0;
				for (Map.Entry<Integer, Double> e : flowRow[s].entrySet()) {
					if (e.getValue() != 0.0) { vBuf[count] = e.getKey(); cBuf[count++] = e.getValue(); }
				}
				lp.addConstraint(count, cBuf, vBuf, '=', (s == initState) ? 1.0 : 0.0);
			}

			// Submit reward lower-bound constraints for each bounded (R_GE) objective.
			// Only regular action variables (not extra "stay" variables) contribute reward.
			for (int i = 0; i < dim; i++) {
				if (moQuery.getRewardOperator(i) == Operator.R_MAX) continue; // numerical, not a constraint
				double bound = moQuery.getRewardBound(i);
				int count = 0;
				for (int s = 0; s < n; s++) {
					if (actionVar[s] == null) continue;
					for (int ch = 0; ch < mdp.getNumChoices(s); ch++) {
						int vi = actionVar[s][ch];
						if (vi < 0) continue;
						double r = rewards.get(i).getStateReward(s) + rewards.get(i).getTransitionReward(s, ch);
						if (r != 0.0) { cBuf[count] = r; vBuf[count] = vi; count++; }
					}
				}
				lp.addConstraint(count, cBuf, vBuf, '>', bound);
			}

			// Solve (always maximise: objective=0 for achievability, numerical reward for numerical)
			double[] soln;
			try {
				soln = lp.solve(true);
			} catch (PrismException e) {
				String msg = e.getMessage();
				if (numNumerical == 0 && msg != null && msg.contains("infeasible")) {
					mainLog.println("LP infeasible; result is false.");
					return false;
				}
				throw e;
			}

			if (numNumerical == 0) {
				mainLog.println("LP feasible; result is true.");
				return true;
			}

			// Compute objective value from LP solution
			double objVal = 0.0;
			for (int s = 0; s < n; s++) {
				if (actionVar[s] == null) continue;
				for (int ch = 0; ch < mdp.getNumChoices(s); ch++) {
					int vi = actionVar[s][ch];
					if (vi < 0) continue;
					double r = rewards.get(numObjIdx).getStateReward(s)
					         + rewards.get(numObjIdx).getTransitionReward(s, ch);
					if (r != 0.0) objVal += r * soln[vi];
				}
			}
			// Undo negation if the objective was originally a minimisation
			if (moQuery.isRewardNegated(numObjIdx)) objVal = -objVal;
			mainLog.println("LP solved; result is " + objVal + ".");
			return objVal;

		} finally {
			lp.dispose();
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
