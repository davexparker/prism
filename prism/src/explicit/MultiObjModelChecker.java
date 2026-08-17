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
import java.util.function.BiPredicate;

import strat.MRStrategy;

import acceptance.AcceptanceRabin;
import automata.DA;
import common.IterableStateSet;
import explicit.modelviews.MDPDroppedChoicesCached;
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
		MDPModelChecker mdpMC = (MDPModelChecker) mc;
		MecClassification mecs = classifyMecs(mdp, rewards, moQuery, dim);
		BitSet positiveECs = new BitSet();
		for (int s = 0; s < n; s++) {
			if (mecs.positiveMecForState[s] != null) {
				positiveECs.set(s);
			}
		}
		// mecForState[s]: the zero-reward MEC BitSet containing s, or null if s is not in any zero-reward MEC
		BitSet[] mecForState = mecs.zeroMecForState;

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

			// Extract and store randomised strategy from LP solution if requested.
			// For each state s with LP variables, the randomised choice probability for
			// action ch is y(s,ch) / sum_ch' y(s,ch') (occupancy measure normalised by
			// total flow through s). For zero-reward MEC states the extra "stay" variable
			// contributes to the total; its residual probability is assigned to the first
			// MEC action (matching the _ec self-loop convention of the C++ LP exporter).
			if (mc.getGenStrat()) {
				MRStrategy<Double> strat = new MRStrategy<>(mdp);
				for (int s = 0; s < n; s++) {
					if (actionVar[s] == null) continue;
					double total = 0.0;
					for (int ch = 0; ch < mdp.getNumChoices(s); ch++) {
						int vi = actionVar[s][ch];
						if (vi >= 0) total += soln[vi];
					}
					if (extraVar[s] >= 0) total += soln[extraVar[s]];
					if (total <= 0.0) continue; // unreachable: strategy undefined
					for (int ch = 0; ch < mdp.getNumChoices(s); ch++) {
						int vi = actionVar[s][ch];
						if (vi >= 0 && soln[vi] > 0.0) {
							strat.setChoiceProbability(s, ch, soln[vi] / total);
						}
					}
					if (extraVar[s] >= 0 && soln[extraVar[s]] > 0.0) {
						double stayProb = soln[extraVar[s]] / total;
						BitSet myMec = mecForState[s];
						for (int ch = 0; ch < mdp.getNumChoices(s); ch++) {
							if (mdp.allSuccessorsInSet(s, ch, myMec)) {
								strat.setChoiceProbability(s, ch, stayProb);
								break;
							}
						}
					}
				}
				mc.result.setStrategy(strat);
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
	 * Classification of a model's maximal end components (MECs) by whether any of
	 * {@code rewards} has a positive value (state or MEC-internal transition reward)
	 * reachable while staying inside. Used by the LP solution method ({@link
	 * #checkMultiObjectiveLP}) to mark states from which infinite reward is achievable.
	 *
	 * <p>The symbolic engine uses the equivalent classification ({@code removeNonZeroMecsForMax})
	 * to physically prune the model once, up front, shared between its LP and value-iteration
	 * methods. The explicit engine's value-iteration path ({@link #buildExplicitWeightedSolver})
	 * does the same: it builds an {@link explicit.modelviews.MDPDroppedChoicesCached} view that
	 * structurally removes MEC-internal actions of positive MECs. This has to be done by removing
	 * the actions outright, not by penalising them in the weighted-sum reward: for memoryless
	 * policies, a single (state, choice) pair cannot distinguish "used once, necessarily, while
	 * passing through" from "used to loop forever", so a reward penalty on that pair would
	 * suppress legitimate one-off transits through the MEC along with genuine infinite loops.
	 */
	private static class MecClassification
	{
		/** For each state in a positive MEC, that MEC's BitSet; {@code null} otherwise. */
		final BitSet[] positiveMecForState;
		/** For each state in a zero-reward MEC, that MEC's BitSet; {@code null} otherwise. */
		final BitSet[] zeroMecForState;

		MecClassification(BitSet[] positiveMecForState, BitSet[] zeroMecForState)
		{
			this.positiveMecForState = positiveMecForState;
			this.zeroMecForState = zeroMecForState;
		}
	}

	/**
	 * Classify the MECs of {@code model}, across all of {@code rewards} (see
	 * {@link MecClassification}).
	 *
	 * <p>Reward objectives that were originally minimising have already been negated
	 * in-place (by {@link #checkMultiObjective}) before this is called, so their stored
	 * values are &le; 0 where the true (pre-negation) reward is &ge; 0: a true positive
	 * reward on such an objective therefore shows up here as a <em>negative</em> stored
	 * value, not a positive one. {@code moQuery.isRewardNegated(i)} tells us which sign to
	 * treat as "true positive reward" for objective {@code i}.
	 */
	private MecClassification classifyMecs(MDP<Double> mdp, List<Rewards<Double>> rewards, MultiObjQuery moQuery, int dim) throws PrismException
	{
		int n = mdp.getNumStates();
		BitSet[] positiveMecForState = new BitSet[n];
		BitSet[] zeroMecForState = new BitSet[n];
		ECComputer ecs = ECComputer.createECComputer(this, mdp);
		ecs.computeMECStatesStreaming(ec -> {
			boolean isPositive = false;
			outer:
			for (int state : new IterableStateSet(ec, n)) {
				for (int i = 0; i < dim; i++) {
					double sr = rewards.get(i).getStateReward(state);
					if (moQuery.isRewardNegated(i) ? (sr < 0) : (sr > 0)) { isPositive = true; break outer; }
				}
				for (int ch = 0, nc = mdp.getNumChoices(state); ch < nc; ch++) {
					if (!mdp.allSuccessorsInSet(state, ch, ec)) continue; // not a MEC action
					for (int i = 0; i < dim; i++) {
						double tr = rewards.get(i).getTransitionReward(state, ch);
						if (moQuery.isRewardNegated(i) ? (tr < 0) : (tr > 0)) { isPositive = true; break outer; }
					}
				}
			}
			BitSet ecCopy = (BitSet) ec.clone();
			for (int state : new IterableStateSet(ec, n)) {
				if (isPositive) {
					positiveMecForState[state] = ecCopy;
				} else {
					zeroMecForState[state] = ecCopy;
				}
			}
		});
		return new MecClassification(positiveMecForState, zeroMecForState);
	}

	/**
	 * Classify a model's maximal end components (MECs) by whether any objective that was
	 * <em>originally</em> maximising (R_MAX/R_GE, before {@link MultiObjQuery#makeAllRewardUp()}
	 * canonicalised every objective to maximising form) has positive reward (state or
	 * MEC-internal transition reward) reachable while staying inside.
	 *
	 * <p>Deliberately narrower than {@link #classifyMecs}: objectives that were originally
	 * minimising ({@code moQuery.isRewardNegated(i)}) are excluded entirely, not sign-flipped.
	 * A MEC whose stored (negated) reward is positive there would mean the true, original
	 * reward is negative — looping forever would drive the maximised (stored) value to
	 * <em>negative</em> infinity, which is a real, correctly-computed value a maximiser
	 * naturally avoids, not a hazard requiring pruning. This mirrors the symbolic engine's
	 * {@code hasMaxReward}/{@code removeNonZeroMecsForMax}, which is scoped the same way, via
	 * the pre-canonicalisation reward operator.
	 *
	 * @return For each state in a MEC positive under some originally-maximising objective, that
	 *         MEC's BitSet; {@code null} otherwise.
	 */
	private BitSet[] classifyPositiveMecsForPruning(NondetModel<Double> model, List<Rewards<Double>> rewards, MultiObjQuery moQuery, int dim) throws PrismException
	{
		int n = model.getNumStates();
		BitSet[] positiveMecForState = new BitSet[n];
		ECComputer ecs = ECComputer.createECComputer(this, model);
		ecs.computeMECStatesStreaming(ec -> {
			boolean isPositive = false;
			outer:
			for (int state : new IterableStateSet(ec, n)) {
				for (int i = 0; i < dim; i++) {
					if (moQuery.isRewardNegated(i)) continue;
					if (rewards.get(i).getStateReward(state) > 0) { isPositive = true; break outer; }
				}
				for (int ch = 0, nc = model.getNumChoices(state); ch < nc; ch++) {
					if (!model.allSuccessorsInSet(state, ch, ec)) continue; // not a MEC action
					for (int i = 0; i < dim; i++) {
						if (moQuery.isRewardNegated(i)) continue;
						if (rewards.get(i).getTransitionReward(state, ch) > 0) { isPositive = true; break outer; }
					}
				}
			}
			if (isPositive) {
				BitSet ecCopy = (BitSet) ec.clone();
				for (int state : new IterableStateSet(ec, n)) {
					positiveMecForState[state] = ecCopy;
				}
			}
		});
		return positiveMecForState;
	}

	/**
	 * Computes the value of a single choice for {@link #runWeightedMultiObjectiveVI}, given the
	 * current per-objective value estimates and the weight vector: fills {@code pd2} (pre-sized
	 * to the number of objectives, zeroed by the caller) with each objective's own value for
	 * this choice, and returns the combined value used to select the best action.
	 *
	 * <p>For MDP there is no uncertainty to resolve, so the combined value is simply the
	 * weighted sum of {@code pd2} once it is filled in. IMDP will need a genuinely separate,
	 * jointly-resolved quantity here — minimising a weighted sum of objectives jointly over
	 * interval uncertainty is not the same as separately minimising each objective and then
	 * combining — but that's not implemented by this refactor; the MDP case is the only
	 * consumer so far, and it doesn't have any uncertainty to resolve at all.
	 *
	 * <p>Does not include state reward — {@link #runWeightedMultiObjectiveVI} adds that
	 * uniformly, since it doesn't depend on how a transition's uncertainty resolves.
	 */
	@FunctionalInterface
	private interface ChoiceValueComputer
	{
		double compute(int s, int ch, double[][] psoln, double[] weights, double[] pd2);
	}

	/**
	 * Build a {@link WeightedObjectiveSolver} for the explicit engine.
	 *
	 * <p>Mirrors the symbolic engine's approach (see {@code symbolic.comp.MultiObjModelChecker
	 * #removeNonZeroMecsForMax} and {@code PS_NondetMultiObj[GS].cc}): a single precomputation
	 * pass, done once for the whole query (not per weight vector), permanently prunes MEC-internal
	 * actions that carry positive reward under any objective; then each weight vector is solved by
	 * {@link #runWeightedMultiObjectiveVI} — one VI/GS pass that picks the weighted-value-maximising
	 * action per state and accumulates every individual objective's value along that same action, in
	 * lock-step, rather than solving the weighted sum and separately re-evaluating objectives on a
	 * post-hoc extracted policy.
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

		// Upfront precomputation, once per query: find MECs with positive reward under any
		// objective that was *originally* maximising (R_MAX/R_GE before makeAllRewardUp()
		// canonicalised everything to maximising form). Objectives that were originally
		// minimising (R_MIN/R_LE, now stored negated) are deliberately excluded here: looping
		// forever in a MEC that is positive in the ORIGINAL sense means the negated (stored,
		// maximised) value there is unboundedly *negative* — a real, correctly-computed value
		// that a maximiser will naturally avoid, not a hazard that needs pruning (mirrors
		// symbolic's `hasMaxReward`/`removeNonZeroMecsForMax`, which is likewise scoped to
		// objectives whose ORIGINAL operator is R_MAX/R_GE).
		//
		// If a MEC positive under an originally-maximising objective is reachable from the
		// initial state, no weight vector can give a finite answer for that objective, so fail
		// fast (matching the symbolic engine's restriction). Otherwise permanently prune those
		// MEC-internal actions, so that no later weighted-sum solve — even one where that
		// objective's weight happens to be near zero — can be tricked into treating looping in
		// the MEC as free.
		BitSet[] positiveMecForState = classifyPositiveMecsForPruning(mdp, rewards, instance.moQuery, dim);
		BitSet positiveMecStates = new BitSet();
		for (int s = 0; s < numStates; s++) {
			if (positiveMecForState[s] != null) positiveMecStates.set(s);
		}
		BitSet reachable = mdpMC.prob0(mdp, null, positiveMecStates, false, null);
		reachable.flip(0, numStates);
		if (reachable.get(initState)) {
			throw new PrismNotSupportedException("Cannot use multi-objective model checking with maximising objectives and non-zero reward end components");
		}
		MDP<Double> prunedMdp = new MDPDroppedChoicesCached<>(mdp,
				(s, ch) -> positiveMecForState[s] != null && mdp.allSuccessorsInSet(s, ch, positiveMecForState[s]));

		boolean useGS = settings.getChoice(PrismSettings.PRISM_MDP_MULTI_SOLN_METHOD) == Prism.MDP_MULTI_GAUSSSEIDEL;

		// Fixed transition probabilities: no uncertainty to resolve, so the combined value is
		// just the weighted sum of the (single) per-objective values.
		ChoiceValueComputer choiceValue = (s, ch, psoln, w, pd2) -> {
			Iterator<Map.Entry<Integer, Double>> it = prunedMdp.getTransitionsIterator(s, ch);
			while (it.hasNext()) {
				Map.Entry<Integer, Double> e = it.next();
				int t = e.getKey();
				double p = e.getValue();
				for (int i = 0; i < dim; i++) {
					pd2[i] += p * psoln[i][t];
				}
			}
			double d2 = 0.0;
			for (int i = 0; i < dim; i++) {
				pd2[i] += rewards.get(i).getTransitionReward(s, ch);
				d2 += w[i] * pd2[i];
			}
			return d2;
		};

		return weights -> runWeightedMultiObjectiveVI(prunedMdp, rewards, weights, initState, useGS, null, choiceValue);
	}

	/**
	 * Solve a single weighted-sum query, computing the combined (weighted) value and every
	 * individual objective's value simultaneously in one VI/GS pass — mirroring the symbolic
	 * engine's {@code PS_NondetMultiObj[GS].cc} kernels. At each state and iteration, the action
	 * maximising the combined value ({@link ChoiceValueComputer}) is chosen; ties are broken in
	 * favour of whichever action is better for some individual objective (first-improving in
	 * objective index order). Because the same action is used both to determine the combined
	 * optimum and to accumulate each individual objective's value, there is no separate "extract
	 * a policy, then re-evaluate objectives on it" step — and so no risk of the extraction step
	 * picking a policy that is arbitrarily (or divergently) bad for an objective the combined
	 * value doesn't see.
	 *
	 * <p>The model-specific part of how a choice's value is computed (fixed transition
	 * probabilities for MDP; interval-uncertainty resolution for other model types) is supplied
	 * via {@code choiceValue}, so this loop itself stays model-agnostic.
	 *
	 * <p>Assumes {@code model} has already had MEC-internal actions with positive reward (under
	 * any objective) pruned by the caller (see {@link #buildExplicitWeightedSolver}), either
	 * structurally (a pruned model) or via {@code choiceAvailable}, so every value computed here
	 * is finite.
	 *
	 * @param model           The model to solve on (MEC-pruned already)
	 * @param rewards         One reward structure per objective, already canonicalised to maximising
	 * @param weights         Weight vector, one entry per objective
	 * @param initState       The initial state, whose values are returned
	 * @param useGS           Whether to use Gauss-Seidel (in-place) rather than value iteration (double-buffered)
	 * @param choiceAvailable Optional predicate for choices to skip (pruning); {@code null} means all available
	 * @param choiceValue     Computes a choice's combined value and fills its per-objective values
	 */
	private double[] runWeightedMultiObjectiveVI(NondetModel<Double> model, List<Rewards<Double>> rewards,
	                                              double[] weights, int initState, boolean useGS,
	                                              BiPredicate<Integer, Integer> choiceAvailable,
	                                              ChoiceValueComputer choiceValue)
	        throws PrismException
	{
		int n = model.getNumStates();
		int dim = rewards.size();

		double[] soln = new double[n];
		double[] soln2 = useGS ? soln : new double[n];
		double[][] psoln = new double[dim][n];
		double[][] psoln2 = new double[dim][];
		for (int i = 0; i < dim; i++) {
			psoln2[i] = useGS ? psoln[i] : new double[n];
		}

		double[] pd1 = new double[dim];
		double[] pd2 = new double[dim];
		double[] oldIndiv = new double[dim];
		double[] maxDiffIndiv = new double[dim];

		// Once the combined value has converged (weightedDone), the greedy per-iteration
		// tie-break is frozen into a fixed policy (strat[s] = the winning choice index at each
		// state, or NO_CHOICES if none were available) rather than kept re-deciding every
		// iteration. Two choices that are *exactly* tied on the combined value, but each better
		// than the other on a different individual objective, can otherwise make the tie-break's
		// pick permanently oscillate: feeding back choice A's values makes B look better next
		// sweep, and vice versa, forming a stable 2-cycle that never converges (found via random
		// stress-testing — the combined value settles in a handful of iterations while the
		// individual values cycle forever). Freezing is safe here — unlike the old decompose-then-
		// extract-then-separately-evaluate design this kernel replaced — because the frozen policy
		// was chosen *with* individual-objective-aware tie-breaking throughout the search, not via
		// a blind scalar-only solve; freezing only removes the (by-definition-tied, hence
		// value-neutral) re-litigation of ties once the combined optimum is already known.
		final int NO_CHOICES = -2;
		int[] strat = new int[n];
		Arrays.fill(strat, -1);
		boolean locked = false;

		boolean absolute = mc.termCrit == ProbModelChecker.TermCrit.ABSOLUTE;
		int iters = 0;
		boolean weightedDone = false;
		boolean done = false;
		while (!done && iters < mc.maxIters) {
			iters++;
			double maxDiffCombined = 0.0;
			Arrays.fill(maxDiffIndiv, 0.0);

			for (int s = 0; s < n; s++) {
				double oldCombined = soln[s];
				for (int i = 0; i < dim; i++) oldIndiv[i] = psoln[i][s];

				double d1 = Double.NEGATIVE_INFINITY;
				Arrays.fill(pd1, 0.0);
				boolean first = true;
				int bestCh = NO_CHOICES;
				int numChoices = locked ? (strat[s] == NO_CHOICES ? 0 : 1) : model.getNumChoices(s);
				for (int chIdx = 0; chIdx < numChoices; chIdx++) {
					int ch = locked ? strat[s] : chIdx;
					if (!locked && choiceAvailable != null && !choiceAvailable.test(s, ch)) continue;
					Arrays.fill(pd2, 0.0);
					double d2 = choiceValue.compute(s, ch, psoln, weights, pd2);
					// Treat d2/d1 as tied within a small relative tolerance, not exact equality:
					// two choices that are mathematically tied on the combined value can still
					// compute to slightly different floating-point results (different summation
					// order, accumulated rounding over many iterations, ...), especially once a
					// choice's own value is itself still converging. An exact-equality tie-break
					// would then let one choice "win" outright on a floating-point artifact,
					// permanently locking out the individual-objective comparison that's supposed
					// to arbitrate between genuinely-tied choices.
					double tol = mc.termCritParam * Math.max(1.0, Math.max(Math.abs(d1), Math.abs(d2)));
					boolean pickThis;
					if (first || d2 > d1 + tol) {
						pickThis = true;
					} else if (d2 < d1 - tol) {
						pickThis = false;
					} else {
						pickThis = false;
						for (int i = 0; i < dim; i++) {
							if (pd2[i] > pd1[i]) { pickThis = true; break; }
						}
					}
					if (pickThis) {
						d1 = d2;
						System.arraycopy(pd2, 0, pd1, 0, dim);
						bestCh = ch;
					}
					first = false;
				}
				if (first) {
					// No choices (e.g. all pruned as MEC-internal): treat as a zero-reward sink.
					d1 = 0.0;
					Arrays.fill(pd1, 0.0);
				}
				// State reward is earned once, regardless of choice
				for (int i = 0; i < dim; i++) {
					double sr = rewards.get(i).getStateReward(s);
					pd1[i] += sr;
					d1 += weights[i] * sr;
				}

				soln2[s] = d1;
				for (int i = 0; i < dim; i++) psoln2[i][s] = pd1[i];

				double diffC = absolute ? Math.abs(d1 - oldCombined) : Math.abs(d1 - oldCombined) / Math.abs(d1);
				if (!Double.isNaN(diffC)) maxDiffCombined = Math.max(maxDiffCombined, diffC);
				for (int i = 0; i < dim; i++) {
					double diffI = absolute ? Math.abs(pd1[i] - oldIndiv[i]) : Math.abs(pd1[i] - oldIndiv[i]) / Math.abs(pd1[i]);
					if (!Double.isNaN(diffI)) maxDiffIndiv[i] = Math.max(maxDiffIndiv[i], diffI);
				}

				if (weightedDone && !locked) {
					strat[s] = bestCh;
				}
			}

			// Two-phase convergence, mirroring the symbolic kernel: only start requiring the
			// individual objective values to stabilise once the combined (weighted) value already
			// has, since the greedy policy itself is still liable to change until then. Once that
			// phase begins, the policy computed in the sweep that just ran (populated into strat[]
			// above) is frozen for all subsequent sweeps — see the comment on strat[] above.
			if (!weightedDone) {
				weightedDone = maxDiffCombined <= mc.termCritParam;
			} else {
				locked = true;
				double maxDiffAll = 0.0;
				for (int i = 0; i < dim; i++) maxDiffAll = Math.max(maxDiffAll, maxDiffIndiv[i]);
				done = maxDiffAll <= mc.termCritParam;
			}

			if (!useGS) {
				double[] tmp = soln; soln = soln2; soln2 = tmp;
				for (int i = 0; i < dim; i++) {
					double[] t = psoln[i]; psoln[i] = psoln2[i]; psoln2[i] = t;
				}
			}
		}

		if (!done) {
			throw new PrismException("Iterative method did not converge within " + mc.maxIters + " iterations.\n"
					+ "Consider using a different numerical method or increasing the maximum number of iterations.");
		}

		double[] result = new double[dim];
		for (int i = 0; i < dim; i++) result[i] = psoln[i][initState];
		return result;
	}
}
