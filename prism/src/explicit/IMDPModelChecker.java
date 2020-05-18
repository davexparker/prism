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
import java.util.Map;

import common.IntSet;
import common.Interval;
import prism.AccuracyFactory;
import prism.Evaluator;
import prism.PrismComponent;
import prism.PrismException;
import prism.PrismNotSupportedException;

/**
 * Explicit-state model checker for interval Markov decision prcoesses (IMDPs).
 * This extends MDPModelChecker in order to re-use e.g. precomputation algorithms.
 */
public class IMDPModelChecker extends MDPModelChecker
{
	/**
	 * Create a new IMDPModelChecker, inherit basic state from parent (unless null).
	 */
	public IMDPModelChecker(PrismComponent parent) throws PrismException
	{
		super(parent);
	}

	// Numerical computation functions

	/**
	 * Compute next-state probabilities.
	 * i.e. compute the probability of being in a state in {@code target} in the next step.
	 * @param imdp The IMDP
	 * @param target Target states
	 * @param minMax Min/max info
	 */
	public ModelCheckerResult computeNextProbs(IMDP<Double> imdp, BitSet target, MinMax minMax) throws PrismException
	{
		long timer = System.currentTimeMillis();

		// Check for any zero lower probability bounds (not supported
		// since this approach assumes the graph structure remains static)
		imdp.checkLowerBoundsArePositive();
		
		// Store num states
		int n = imdp.getNumStates();

		// Create/initialise solution vector(s)
		double[] soln = Utils.bitsetToDoubleArray(target, n);
		double[] soln2 = new double[n];

		// Next-step probabilities 
		imdp.mvMult(soln, minMax, soln2, null);

		// Return results
		ModelCheckerResult res = new ModelCheckerResult();
		res.accuracy = AccuracyFactory.boundedNumericalIterations();
		res.soln = soln2;
		res.numIters = 1;
		timer = System.currentTimeMillis() - timer;
		res.timeTaken = timer / 1000.0;
		return res;
	}

	/**
	 * Compute bounded reachability probabilities.
	 * i.e. compute the probability of reaching a state in {@code target} within k steps.
	 * @param imdp The IMDP
	 * @param target Target states
	 * @param k Bound
	 * @param minMax Min/max info
	 */
	public ModelCheckerResult computeBoundedReachProbs(IMDP<Double> imdp, BitSet target, int k, MinMax minMax) throws PrismException
	{
		return computeBoundedUntilProbs(imdp, null, target, k, minMax);
	}

	/**
	 * Compute bounded until probabilities.
	 * i.e. compute the probability of reaching a state in {@code target},
	 * within k steps, and while remaining in states in {@code remain}.
	 * @param imdp The IMDP
	 * @param remain Remain in these states (optional: null means "all")
	 * @param target Target states
	 * @param k Bound
	 * @param minMax Min/max info
	 */
	public ModelCheckerResult computeBoundedUntilProbs(IMDP<Double> imdp, BitSet remain, BitSet target, int k, MinMax minMax) throws PrismException
	{
		ModelCheckerResult res = null;
		BitSet unknown;
		int i, n, iters;
		double soln[], soln2[], tmpsoln[];
		long timer;

		// Start bounded probabilistic reachability
		timer = System.currentTimeMillis();
		mainLog.println("\nStarting bounded probabilistic reachability...");

		// Check for any zero lower probability bounds (not supported
		// since this approach assumes the graph structure remains static)
		imdp.checkLowerBoundsArePositive();
		
		// Store num states
		n = imdp.getNumStates();

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
			imdp.mvMult(soln, minMax, soln2, unknownStates.iterator());
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
		res.accuracy = AccuracyFactory.boundedNumericalIterations();
		res.numIters = iters;
		res.timeTaken = timer / 1000.0;
		res.timePre = 0.0;
		return res;
	}
	
	/**
	 * Compute reachability probabilities.
	 * i.e. compute the probability of reaching a state in {@code target}.
	 * @param imdp The IMDP
	 * @param target Target states
	 * @param minMax Min/max info
	 */
	public ModelCheckerResult computeReachProbs(IMDP<Double> imdp, BitSet target, MinMax minMax) throws PrismException
	{
		return computeReachProbs(imdp, null, target, minMax);
	}

	/**
	 * Compute until probabilities.
	 * i.e. compute the probability of reaching a state in {@code target}.
	 * @param imdp The IMDP
	 * @param remain Remain in these states (optional: null means "all")
	 * @param target Target states
	 * @param minMax Min/max info
	 */
	public ModelCheckerResult computeUntilProbs(IMDP<Double> imdp, BitSet remain, BitSet target, MinMax minMax) throws PrismException
	{
		return computeReachProbs(imdp, remain, target, minMax);
	}

	/**
	 * Compute reachability/until probabilities.
	 * i.e. compute the probability of reaching a state in {@code target},
	 * while remaining in those in {@code remain}.
	 * @param imdp The IMDP
	 * @param remain Remain in these states (optional: null means "all")
	 * @param target Target states
	 * @param minMax Min/max info
	 */
	public ModelCheckerResult computeReachProbs(IMDP<Double> imdp, BitSet remain, BitSet target, MinMax minMax) throws PrismException
	{
		// Switch to a supported method, if necessary
		LinEqMethod linEqMethod = this.linEqMethod;
		switch (linEqMethod)
		{
		case POWER:
		case GAUSS_SEIDEL:
		case BACKWARDS_GAUSS_SEIDEL:
		//case JACOBI:
			break; // supported
		default:
			linEqMethod = LinEqMethod.GAUSS_SEIDEL;
			mainLog.printWarning("Switching to linear equation solution method \"" + linEqMethod.fullName() + "\"");
		}

		if (doIntervalIteration && (!precomp || !prob0 || !prob1)) {
			throw new PrismNotSupportedException("Interval iteration requires precomputations to be active");
		}

		// Start probabilistic reachability
		long timer = System.currentTimeMillis();
		mainLog.println("\nStarting probabilistic reachability...");

		// Check for any zero lower probability bounds (not supported
		// since this approach assumes the graph structure remains static)
		imdp.checkLowerBoundsArePositive();
		
		// Check for deadlocks in non-target state (because breaks e.g. prob1)
		imdp.checkForDeadlocks(target);

		// Store num states
		int n = imdp.getNumStates();

		// Precomputation
		BitSet no, yes;
		if (precomp && prob0) {
			no = prob0(imdp, remain, target, minMax.isMin(), null);
		} else {
			no = new BitSet();
		}
		if (precomp && prob1) {
			yes = prob1(imdp, remain, target, minMax.isMin(), null);
		} else {
			yes = (BitSet) target.clone();
		}

		// Print results of precomputation
		int numYes = yes.cardinality();
		int numNo = no.cardinality();
		mainLog.println("target=" + target.cardinality() + ", yes=" + numYes + ", no=" + numNo + ", maybe=" + (n - (numYes + numNo)));

		// Start value iteration
		timer = System.currentTimeMillis();
		String sMinMax = minMax.isMin() ? "min" : "max";
		sMinMax += minMax.isMinUnc() ? "min" : "max";
		mainLog.println("Starting value iteration (" + sMinMax + ")...");

		// Store num states
		n = imdp.getNumStates();

		// Initialise solution vectors
		double[] init = new double[n];
		for (int i = 0; i < n; i++)
			init[i] = yes.get(i) ? 1.0 : no.get(i) ? 0.0 : 0.0;

		// Determine set of states actually need to compute values for
		BitSet unknown = new BitSet();
		unknown.set(0, n);
		unknown.andNot(yes);
		unknown.andNot(no);

		// Compute probabilities (if needed)
		ModelCheckerResult res;
		if (numYes + numNo < n) {
			IterationMethod iterationMethod = null;
			switch (linEqMethod) {
			case POWER:
				iterationMethod = new IterationMethodPower(termCrit == TermCrit.ABSOLUTE, termCritParam);
				break;
			case JACOBI:
				iterationMethod = new IterationMethodJacobi(termCrit == TermCrit.ABSOLUTE, termCritParam);
				break;
			case GAUSS_SEIDEL:
			case BACKWARDS_GAUSS_SEIDEL:
				boolean backwards = linEqMethod == LinEqMethod.BACKWARDS_GAUSS_SEIDEL;
				iterationMethod = new IterationMethodGS(termCrit == TermCrit.ABSOLUTE, termCritParam, backwards);
				break;
			default:
				throw new PrismException("Unknown solution method " + linEqMethod.fullName());
			}
			IterationMethod.IterationValIter iterationReachProbs = iterationMethod.forMvMultMinMaxUnc(imdp, minMax);
			iterationReachProbs.init(init);
			IntSet unknownStates = IntSet.asIntSet(unknown);
			String description = sMinMax + ", with " + iterationMethod.getDescriptionShort();
			res = iterationMethod.doValueIteration(this, description, iterationReachProbs, unknownStates, timer, null);
		} else {
			res = new ModelCheckerResult();
			res.soln = Utils.bitsetToDoubleArray(yes, n);
			res.accuracy = AccuracyFactory.doublesFromQualitative();
		}
		
		// Finished probabilistic reachability
		timer = System.currentTimeMillis() - timer;
		mainLog.println("Probabilistic reachability took " + timer / 1000.0 + " seconds.");

		// Update time taken
		res.timeTaken = timer / 1000.0;

		return res;
	}


	/**
	 * Simple test program.
	 */
	public static void main(String args[])
	{
		try {
			IMDPModelChecker mc = new IMDPModelChecker(null);
			Evaluator<Interval<Double>> eval = Evaluator.createForDoubleIntervals();
			IMDPSimple<Double> imdp = new IMDPSimple<>();
			imdp.setEvaluator(eval);
			imdp.addState();
			imdp.addState();
			imdp.addState();
			imdp.addInitialState(0);
			Distribution<Interval<Double>> distr;
			distr = new Distribution<>(eval);
			distr.add(1, new Interval<Double>(0.2, 0.4));
			distr.add(2, new Interval<Double>(0.6, 0.8));
			imdp.addActionLabelledChoice(0, distr, "a");
			distr = new Distribution<>(eval);
			distr.add(1, new Interval<Double>(0.1, 0.3));
			distr.add(2, new Interval<Double>(0.7, 0.9));
			imdp.addActionLabelledChoice(0, distr, "b");
			imdp.findDeadlocks(true);
			imdp.exportToDotFile("imdp.dot");
			BitSet target = new BitSet();
			target.set(2);
			ModelCheckerResult res;
			res = mc.computeReachProbs(imdp, target, MinMax.min().setMinUnc(true));
			System.out.println("minmin: " + res.soln[0]);
			res = mc.computeReachProbs(imdp, target, MinMax.min().setMinUnc(false));
			System.out.println("minmax: " + res.soln[0]);
			res = mc.computeReachProbs(imdp, target, MinMax.max().setMinUnc(true));
			System.out.println("maxmin: " + res.soln[0]);
			res = mc.computeReachProbs(imdp, target, MinMax.max().setMinUnc(false));
			System.out.println("maxmax: " + res.soln[0]);
		} catch (PrismException e) {
			System.out.println(e);
		}
	}
}
