package explicit;

import common.IntSet;
import explicit.rewards.MDPRewards;
import explicit.rewards.StateRewardsArray;
import param.BigRational;
import param.Function;
import prism.Evaluator;
import param.Point;
import prism.PrismComponent;
import prism.PrismException;
import prism.PrismSettings;
import strat.MDStrategy;
import strat.MDStrategyArray;

import java.text.DecimalFormat;
import java.util.*;
import explicit.MDPModelChecker;

import static explicit.DistributionalBellmanOperatorProb.toBigRationalPoint;

public class MDPModelCheckerDistributional extends ProbModelChecker
{
	/**
	 * Create a new MDPModelCheckerDistributional, inherit basic state from parent (unless null).
	 */
	public MDPModelCheckerDistributional(PrismComponent parent) throws PrismException
	{
		super(parent);
	}

	public ModelCheckerResult computeReachRewardsExample(MDP<Function> mdp, MDPRewards<Function> mdpRewards, BitSet target, boolean min) throws PrismException
	{
		Point paramValues =  new Point(new BigRational[]{new BigRational("0.8"), new BigRational("0.6")});

		// Print out MDP (before and after parameter instantiation)
		System.out.println(mdp);
		int numStates = mdp.getNumStates();
		for (int s = 0; s < numStates; s++) {
			int numChoices = mdp.getNumChoices(s);
			for (int i = 0; i < numChoices; i++) {

				Iterator<Map.Entry<Integer, Function>> iter = mdp.getTransitionsIterator(s, i);
//				FunctionalIterator<Map.Entry<Integer, Double>> iter = mdp.getTransitionsMappedIterator(s, i, p -> p.evaluate(paramValues).doubleValue());
				while (iter.hasNext()) {
					Map.Entry<Integer, Function> e = iter.next();
					mainLog.println(s + "," + mdp.getAction(s, i) + ":" + e.getKey() + "=" + e.getValue().evaluate(paramValues).doubleValue());
				}

			}
		}

		// Print out rewards
		for (int s = 0; s < numStates; s++) {
			double rewS = mdpRewards.getStateReward(s).evaluate(paramValues).doubleValue();
			mainLog.println(s + ":" + rewS);
			int numChoices = mdp.getNumChoices(s);
			for (int i = 0; i < numChoices; i++) {
				double rewA = mdpRewards.getTransitionReward(s, i).evaluate(paramValues).doubleValue();
				mainLog.println(s + "," + mdp.getAction(s, i) + ":" + rewA);
			}

		}

		// Dummy result
		ModelCheckerResult res = new ModelCheckerResult();
		res.solnObj = new Object[mdp.getNumStates()];
		res.solnObj[0] = 99.0;
		return res;
	}

	/**
	 * Compute expected reachability rewards for an uncertain MDP with transition probabilities specified as a distribution.
	 * @param mdp The parametric MDP
	 * @param mdpRewards The rewards
	 * @param target Target states
	 * @param min Min or max rewards (true=min, false=max)
	 */
	public ModelCheckerResult computeReachRewards(MDP<Function> mdp, MDPRewards<Function> mdpRewards, BitSet target, boolean min) throws PrismException
	{
		MDPModelChecker mcMDP = new MDPModelChecker(this);
		mcMDP.inheritSettings(this);
		// Start expected reachability
		long timer = System.currentTimeMillis();
		mainLog.println("\nStarting expected reachability (" + (min ? "min" : "max") + ")...");

		// Check for deadlocks in non-target state (because breaks e.g. prob1)
		mdp.checkForDeadlocks(target);

		// Store num states
		int n = mdp.getNumStates();

		// Precomputation (not optional)
		long timerProb1 = System.currentTimeMillis();
		BitSet inf = mcMDP.prob1(mdp, null, target, !min, null);
		inf.flip(0, n);
//		timerProb1 = System.currentTimeMillis() - timerProb1;

		// Print results of precomputation
		int numTarget = target.cardinality();
		int numInf = inf.cardinality();
		mainLog.println("target=" + numTarget + ", inf=" + numInf + ", rest=" + (n - (numTarget + numInf)));

		// Timers
		timer = System.currentTimeMillis();
		long total_timer = System.currentTimeMillis();
		long iteration_timer; long max_iteration_timer=-1;

		// Set up distribution variables
		int atoms;
		int iterations = 5000;
		int min_iter = 8;
		double error_thresh = 0.01;
		double gamma = 1;
		double alpha=0.5;
		Double dtmc_epsilon = null;
		boolean check_dtmc_distr = true;
		boolean gen_trace = true;

		String c51 = "C51";
		String qr = "QR";

		int nactions = mdp.getMaxNumChoices();

		// Determine set of states actually need to compute values for
		BitSet unknown = new BitSet();
		unknown.set(0, n);
		unknown.andNot(target);
		unknown.andNot(inf);
		IntSet unknownStates = IntSet.asIntSet(unknown);
		//	int numS = unknownStates.cardinality();
		DistributionalBellmanOperatorProb operator, temp_p; DiscreteDistribution save_p;
		String distr_type = settings.getString(PrismSettings.PRISM_DISTR_SOLN_METHOD);
		int uncertain_atoms; double  u_vmax; double u_vmin;

		System.out.println(mdp);

		ModelCheckerResult res = new ModelCheckerResult();
		res.solnObj = new Object[mdp.getNumStates()];
		res.solnObj[0] = 99.0;
		return res;

	}

	// create index combination map, index of permutations to point of parameters
	public static int getIndexCombinations(
			int n, int u_atoms, Map<Integer, Point> supp, Map<Integer, BigRational> prob, ArrayList<DiscreteDistribution> distr_list, int [] indexList, int count) {

		if(n == 1) {
			for ( int i=0; i<u_atoms; i++) {
				if (distr_list.get(0).getValue(i)>0){
					BigRational[] big_temp = new BigRational[indexList.length];
					indexList[0] = i;
					BigRational joint_value = new BigRational(1);
					for (int j = 0; j < indexList.length; j++) {
						big_temp[j] = new BigRational(distr_list.get(j).getSupport(indexList[j]));
						joint_value = joint_value.multiply(new BigRational(distr_list.get(j).getValue(indexList[j])));
					}
					supp.put(count, new Point(big_temp));
					prob.put(count, joint_value);
					count++;
				}
			}
			return count;

		} else {
			for (int i=0; i<u_atoms; i++){
				if(distr_list.get(n-1).getValue(i) >0) {
					indexList[n - 1] = i;
					count = getIndexCombinations(n - 1, u_atoms, supp, prob, distr_list, indexList, count);
				}
			}
			return count;
		}
	}

}