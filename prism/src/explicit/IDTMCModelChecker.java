//==============================================================================
//	
//	Copyright (c) 2020-
//	Authors:
//	* Dave Parker <d.a.parker@cs.bham.ac.uk> (University of Birmingham)
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import common.IntSet;
import common.Interval;
import parser.State;
import prism.PrismComponent;
import prism.PrismException;
import prism.PrismNotSupportedException;

/**
 * Explicit-state model checker for interval discrete-time Markov chains (IDTMCs).
 */
public class IDTMCModelChecker extends ProbModelChecker
{
	/**
	 * Create a new IDTMCModelChecker, inherit basic state from parent (unless null).
	 */
	public IDTMCModelChecker(PrismComponent parent) throws PrismException
	{
		super(parent);
	}

	// Numerical computation functions

	/**
	 * Compute reachability probabilities.
	 * i.e. compute the probability of reaching a state in {@code target}.
	 * @param idtmc The IDTMC
	 * @param target Target states
	 * @param minMax Min/max info
	 */
	public ModelCheckerResult computeReachProbs(IDTMC idtmc, BitSet target, MinMax minMax) throws PrismException
	{
		return computeUntilProbs(idtmc, null, target, minMax);
	}

	/**
	 * Compute until probabilities.
	 * i.e. compute the probability of reaching a state in {@code target},
	 * while remaining in those in {@code remain}.
	 * @param idtmc The IDTMC
	 * @param remain Remain in these states (optional: null means "all")
	 * @param target Target states
	 * @param minMax Min/max info
	 */
	public ModelCheckerResult computeUntilProbs(IDTMC idtmc, BitSet remain, BitSet target, MinMax minMax) throws PrismException
	{
		ModelCheckerResult res = null;
		BitSet no, yes;
		int n, numYes, numNo;
		long timer, timerProb0, timerProb1;
		PredecessorRelation pre = null;
		// Local copy of setting
		LinEqMethod linEqMethod = this.linEqMethod;

		// Check for any zero lower probability bounds (not supported
		// since this approach assumes the graph structure remains static)
		checkForZeroLowerBoundsInProbabilities(idtmc);
		
		// Switch to a supported method, if necessary
		MDPSolnMethod mdpSolnMethod = this.mdpSolnMethod;
		switch (linEqMethod)
		{
		case POWER:
		case GAUSS_SEIDEL:
		case BACKWARDS_GAUSS_SEIDEL:
		case JACOBI:
			break; // supported
		default:
			linEqMethod = LinEqMethod.GAUSS_SEIDEL;
			mainLog.printWarning("Switching to linear equation solution method \"" + linEqMethod.fullName() + "\"");
		}

		if (doIntervalIteration && (!precomp || !prob0 || !prob1)) {
			throw new PrismNotSupportedException("Interval iteration requires precomputations to be active");
		}

		// Start probabilistic reachability
		timer = System.currentTimeMillis();
		mainLog.println("\nStarting probabilistic reachability...");

		// Check for deadlocks in non-target state (because breaks e.g. prob1)
		idtmc.checkForDeadlocks(target);

		// Store num states
		n = idtmc.getNumStates();

		if (precomp && (prob0 || prob1) && preRel) {
			pre = idtmc.getPredecessorRelation(this, true);
		}

		// Skip precomputation
		no = new BitSet();
		yes = (BitSet) target.clone();

		// Print results of precomputation
		numYes = yes.cardinality();
		numNo = no.cardinality();
		mainLog.println("target=" + target.cardinality() + ", yes=" + numYes + ", no=" + numNo + ", maybe=" + (n - (numYes + numNo)));


		BitSet unknown;
		int i;
		double initVal;

		// Start value iteration
		timer = System.currentTimeMillis();
		mainLog.println("Starting value iteration...");

		// Store num states
		n = idtmc.getNumStates();

		// Initialise solution vectors
		double[] init = new double[n];
		for (i = 0; i < n; i++)
			init[i] = yes.get(i) ? 1.0 : no.get(i) ? 0.0 : 0.0;

		// Determine set of states actually need to compute values for
		unknown = new BitSet();
		unknown.set(0, n);
		unknown.andNot(yes);
		unknown.andNot(no);

		IterationMethod iterationMethod = null;
		switch (linEqMethod) {
		case POWER:
			iterationMethod = new IterationMethodPower(termCrit == TermCrit.ABSOLUTE, termCritParam);
			break;
		case JACOBI:
			iterationMethod = new IterationMethodJacobi(termCrit == TermCrit.ABSOLUTE, termCritParam);
			break;
		case GAUSS_SEIDEL:
			iterationMethod = new IterationMethodGS(termCrit == TermCrit.ABSOLUTE, termCritParam, false);
			break;
		default:
			throw new PrismException("Unknown MDP solution method " + linEqMethod.fullName());
		}
		
		IterationMethod.IterationValIter iterationReachProbs = iterationMethod.forMvMultMinMaxUnc(idtmc, minMax);
		iterationReachProbs.init(init);

		IntSet unknownStates = IntSet.asIntSet(unknown);

		// run the actual value iteration
		res = iterationMethod.doValueIteration(this, "???", iterationReachProbs, unknownStates, timer, null);
		
		// Finished probabilistic reachability
		timer = System.currentTimeMillis() - timer;
		mainLog.println("Probabilistic reachability took " + timer / 1000.0 + " seconds.");

		// Update time taken
		res.timeTaken = timer / 1000.0;
//		res.timeProb0 = timerProb0 / 1000.0;
//		res.timePre = (timerProb0 + timerProb1) / 1000.0;

		return res;
	}


	/**
	 * Compute bounded reachability probabilities.
	 * i.e. compute the probability of reaching a state in {@code target} within k steps.
	 * @param idtmc The IDTMC
	 * @param target Target states
	 * @param k Bound
	 * @param minMax Min/max info
	 */
	public ModelCheckerResult computeBoundedReachProbs(IDTMC idtmc, BitSet target, int k, MinMax minMax) throws PrismException
	{
		return computeBoundedUntilProbs(idtmc, null, target, k, minMax);
	}

	/**
	 * Compute bounded until probabilities.
	 * i.e. compute the probability of reaching a state in {@code target},
	 * within k steps, and while remaining in states in {@code remain}.
	 * @param idtmc The IDTMC
	 * @param remain Remain in these states (optional: null means "all")
	 * @param target Target states
	 * @param k Bound
	 * @param minMax Min/max info
	 */
	public ModelCheckerResult computeBoundedUntilProbs(IDTMC idtmc, BitSet remain, BitSet target, int k, MinMax minMax) throws PrismException
	{
		ModelCheckerResult res = null;
		BitSet unknown;
		int i, n, iters;
		double soln[], soln2[], tmpsoln[];
		long timer;

		// Check for any zero lower probability bounds (not supported
		// since this approach assumes the graph structure remains static)
		checkForZeroLowerBoundsInProbabilities(idtmc);
		
		// Start bounded probabilistic reachability
		timer = System.currentTimeMillis();
		mainLog.println("\nStarting bounded probabilistic reachability...");

		// Store num states
		n = idtmc.getNumStates();

		// Create solution vector(s)
		soln = new double[n];
		soln2 = new double[n];

		// Initialise solution vectors.
		for (i = 0; i < n; i++)
			soln[i] = soln2[i] = target.get(i) ? 1.0 : 0.0;

		// Determine set of states actually need to perform computation for
		unknown = new BitSet();
		unknown.set(0, n);
		unknown.andNot(target);
		if (remain != null)
			unknown.and(remain);
		IntSet unknownStates = IntSet.asIntSet(unknown);

		// Start iterations
		iters = 0;
		while (iters < k) {
			iters++;
			// Matrix-vector multiply and min/max ops
			idtmc.mvMult(soln, minMax, soln2, unknownStates.iterator());
			// Swap vectors for next iter
			tmpsoln = soln;
			soln = soln2;
			soln2 = tmpsoln;
		}

		// Finished bounded probabilistic reachability
		timer = System.currentTimeMillis() - timer;
		mainLog.print("Bounded probabilistic reachability");
		mainLog.println(" took " + iters + " iterations and " + timer / 1000.0 + " seconds.");

		// Return results
		res = new ModelCheckerResult();
		res.soln = soln;
		res.lastSoln = soln2;
		res.numIters = iters;
		res.timeTaken = timer / 1000.0;
		res.timePre = 0.0;
		return res;
	}
	
	/**
	 * Utility method to check that an IDTMC has no transitions with lower bound zero.
	 */
	public void checkForZeroLowerBoundsInProbabilities(IDTMC<Double> idtmc) throws PrismException
	{
		int numStates = idtmc.getNumStates();
		for (int s = 0; s < numStates; s++) {
			Iterator<Map.Entry<Integer, Interval<Double>>> iter = idtmc.getTransitionsIterator(s);
			while (iter.hasNext()) {
				Map.Entry<Integer, Interval<Double>> e = iter.next();
				if (e.getValue().getLower() == 0.0) {
					List<State> sl = idtmc.getStatesList();
					String state = sl == null ? "" + s : sl.get(s).toString(modelInfo);
					throw new PrismException("IDTMC transition probability has lower bound of 0 in state " + state);
				}
			}
		}
	}
}
