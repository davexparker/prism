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

import static java.lang.Math.exp;

import java.text.DecimalFormat;
import java.util.*;

import static explicit.DistributionalBellmanOperatorProb.toBigRationalPoint;



public class backup extends ProbModelChecker
{
	/**
	 * Create a new MDPModelCheckerDistributional, inherit basic state from parent (unless null).
	 */

public ArrayList<Object> sampleUncertainParameter(DiscreteDistribution param_dist) {
    // Sample a random index from the distribution (based on probabilities)
	ArrayList<Object> sampled = new ArrayList<>();
    int sampledIndex = sampleIndexFromDistribution(param_dist);

    // Get the corresponding support (possible value) for the sampled index
    double fixed_realization = param_dist.getSupport(sampledIndex);
	sampled.add(fixed_realization);
	sampled.add(param_dist.getValue(sampledIndex));

    return sampled;
}

// Helper function to sample an index based on the distribution's probabilities
public int sampleIndexFromDistribution(DiscreteDistribution param_dist) {
    double randomValue = Math.random();  // Random number between 0 and 1
    double cumulativeProbability = 0.0;

    // Iterate through the distribution's atoms and sample based on cumulative probabilities
    for (int i = 0; i < param_dist.getAtoms(); i++) {
        cumulativeProbability += param_dist.getValue(i);  // Add probability of the current atom
        if (randomValue <= cumulativeProbability) {
            return i;  // Return the index of the selected atom
        }
    }
    // In case something goes wrong, return the last index
    return param_dist.getAtoms() - 1;
}

	public backup(PrismComponent parent) throws PrismException
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

		int nactions = mdp.getMaxNumChoices();
		Iterator<Map.Entry<Integer,Double>> iter;
		// Determine set of states actually need to compute values for
		BitSet unknown = new BitSet();
		unknown.set(0, n);
		unknown.andNot(target);
		unknown.andNot(inf);
		IntSet unknownStates = IntSet.asIntSet(unknown);
		//	int numS = unknownStates.cardinality();
		DistributionalBellmanOperatorProb operator, temp_p; DiscreteDistribution save_p; DiscreteDistribution[] distr;
		String distr_type = settings.getString(PrismSettings.PRISM_DISTR_SOLN_METHOD);
		int uncertain_atoms=3;double  u_vmax; double u_vmin;

		int atoms=101;
		double v_max = 100;
		double v_min = 0;
		distr_type = "C51";
		uncertain_atoms = 11;
		u_vmin =0.0; u_vmax = 1.0;
		int iterations = 3000;
		int min_iter = 8;
		double error_thresh = 0.01;
		double gamma =0.1;
		double alpha=0.5;
		Double dtmc_epsilon = null;
		boolean check_dtmc_distr = true;
		mainLog.println("Using default parameters - Distr type: "+ distr_type);
		mainLog.println("----- Parameters:\natoms:"+atoms+" - vmax:"+v_max+" - vmin:"+v_min);
		mainLog.println("alpha:"+alpha+" - discount:"+gamma+" - max iterations:"+iterations+
					" - error thresh:"+error_thresh+ " - epsilon:"+dtmc_epsilon);
		mainLog.println("u_atoms:"+uncertain_atoms+" - u_vmax:"+u_vmax+" - u_vmin:"+u_vmin);
		operator = new DistributionalBellmanOperatorProb(atoms, v_min, v_max, n, "C51", mainLog);

		Evaluator.EvaluatorFunction eval = (Evaluator.EvaluatorFunction) mdp.getEvaluator();
		int numParams = eval.getNumParameters();
		ArrayList<DiscreteDistribution> transition_distr = new ArrayList<>(numParams);
		Double [] empty_eval_array = new Double[numParams];
		ArrayList<Double> trans_distr_file = new ArrayList<>(uncertain_atoms);
		ArrayList<Double> trans_prob = new ArrayList<>(uncertain_atoms);
		int [] index = new int[numParams];
		int counter = 0;
		for(int j=0; j<numParams; j++){
			DiscreteDistribution transition_temp;
			String p_name = eval.getParameterName(j);
			ArrayList<String[]> params = mcMDP.readParams("prism/tests/param_distr/param_"+p_name+".csv", 2);
			int i = 0; empty_eval_array[j] = 0.0;
			ArrayList<Double> con = new ArrayList<>(uncertain_atoms);
			// parse distributional information for
			for (String param : params.get(0)) {
				// distributions over transition probabilities
				trans_distr_file.add(Double.parseDouble(param));
				con.add(Double.parseDouble(param));
				// Probability of having those transition values
				trans_prob.add(Double.parseDouble(params.get(1)[i]));
				i += 1;
			}
			if (counter == 0){
				counter += con.size() - 1;
			} else {
				counter += con.size();
			}
			index[j] = counter;
		}



		/*Iterator<Map.Entry<Integer,Double>> transit;
		Double [] pointArr = new Double[2];
		pointArr[0] = 0.8;
		pointArr[1] = 0.1;
		transit = mdp.getTransitionsMappedIterator(25, 1, p -> p.evaluate(toBigRationalPoint(result.get(8))).doubleValue());
		while (transit.hasNext()){
			Map.Entry<Integer,Double> e = transit.next();
            double transition_val = e.getValue();
			int next_state = e.getKey();
			System.out.println(e);
		} */

		//System.out.println(trans_distr_file);
		//System.out.println(trans_prob);
		System.out.println(numParams);
		if (numParams == 1){
			DiscreteDistribution m = null;
		double [][][] q_value = new double[trans_distr_file.size()][n][nactions];
		double [][] v = new double[trans_distr_file.size()][n];
		double [][] vPrev = new double[trans_distr_file.size()][n];
		double [] finalV = new double[n];
		double [] action_val = new double[nactions];
		double [] action_exp = new double[n];
		Object [] policy = new Object[n];
		int[] choices = new int[n];
		double max_v; int max_a;
		double max_dist ; int numChoices;
		int iters; boolean isUncertain;

		// Initiate q_value to 0
		PrimitiveIterator.OfInt states = unknownStates.iterator();
		while (states.hasNext()){
			final int s = states.nextInt();
			numChoices = mdp.getNumChoices(s);
			for (int i = 0; i < numChoices; i++)
				for (int j = 0; j < trans_distr_file.size(); j++){
					q_value[j][s][i] = 0.0;
					v[j][s] = 0.0;
				}
			finalV[s] = 0.0;

		}

		for (iters= 0; iters < iterations; iters ++){	
			System.out.println(iters);
			states = unknownStates.iterator();
			while (states.hasNext()){
				final int s = states.nextInt();
				max_v = Float.POSITIVE_INFINITY; max_a = 0;
				numChoices = mdp.getNumChoices(s);
				for (int choice = 0; choice < numChoices; choice ++){
					double reward = mdpRewards.getStateReward(s).evaluate(toBigRationalPoint(empty_eval_array)).doubleValue();
					reward += mdpRewards.getTransitionReward(s, choice).evaluate(toBigRationalPoint(empty_eval_array)).doubleValue();
					Iterator<Map.Entry<Integer, Function>> iter3 = mdp.getTransitionsIterator(s, choice);
						for (int i = 0; i < trans_distr_file.size(); i++){ 
							if (trans_distr_file.get(i)>0){
								double realization = trans_distr_file.get(i);
								Iterator<Map.Entry<Integer,Double>> transit;
        						transit = mdp.getTransitionsMappedIterator(s, choice, p -> p.evaluate(toBigRationalPoint(realization)).doubleValue());
								while (transit.hasNext()){
									Map.Entry<Integer,Double> e = transit.next();
            						double transition_val = e.getValue();
									int next_state = e.getKey();
									q_value[i][s][choice] = reward + transition_val*v[i][next_state];
								}
							}
						}
					//action_val[choice] = q_value;
					//if (action_val[choice] < max_v) {
					//	max_a = choice;
					//	max_v = action_val[choice]; 
					//	action_exp[s] = max_v;
					//}
				}
				
				double prev = 0.0;
				for (int choice = 0; choice < numChoices; choice ++){
					double tmpV = 0.0;
					for (int i=0; i < trans_distr_file.size(); i++){
						if (trans_distr_file.get(i) > 0){
							tmpV += trans_prob.get(i)*q_value[i][s][choice];
						}
					}
					if (tmpV > prev){
						prev = tmpV;
						choices[s] = choice;
						action_exp[s] = prev;
						max_a = choice;
					}
					for (int i=0; i < trans_distr_file.size(); i++){
						v[i][s] = q_value[i][s][max_a];
					}
				}
				policy[s] = mdp.getAction(s, max_a);
			}
			states = unknownStates.iterator();
			double error = 0.0;
			while (states.hasNext()){
				final int s = states.nextInt();
				double preV = finalV[s];
				double curV = 0.0;
				double curE = 0.0;
				for (int j = 0; j < trans_distr_file.size(); j++){
					curV += trans_prob.get(j)*v[j][s];
				}
				finalV[s] = curV;
				if (curV - preV < 0){
					curE = preV - curV;
				} else {
					curE = curV - preV;
				}
				if (curE > error){
					error = curE;
				}
			}
			if (error <= error_thresh){
				break;
			}
		}

		states = unknownStates.iterator();
		while (states.hasNext()){
			final int s = states.nextInt();
			System.out.println(choices[s]);
		}
		operator.writeToFile(mdp.getFirstInitialState(), null);
		ModelCheckerResult res = new ModelCheckerResult();
		res.soln = Arrays.copyOf(action_exp, action_exp.length); // return the expected values for each state
		res.numIters = iterations;
		res.timeTaken = (System.currentTimeMillis() - total_timer) / 1000.0;
		// Store strategy
		if (genStrat) {
			res.strat = new MDStrategyArray<>(mdp, choices);
		}

		return res;
		} else {
			ArrayList<Double[]> jointRealization = createSegmentedCartesianProduct(trans_distr_file, index);
			ArrayList<Double> jointProb = createSegmentedProducts(trans_prob, index);
			DiscreteDistribution m = null;
			double [][][] q_value = new double[jointRealization.size()][n][nactions];
			double [][] v = new double[jointRealization.size()][n];
			double [][] vPrev = new double[jointProb.size()][n];
			double [] finalV = new double[n];
			double [] action_val = new double[nactions];
			double [] action_exp = new double[n];
			Object [] policy = new Object[n];
			int[] choices = new int[n];
			double max_v; int max_a;
			double max_dist ; int numChoices;
			int iters; boolean isUncertain;

		// Initiate q_value to 0
		PrimitiveIterator.OfInt states = unknownStates.iterator();
		while (states.hasNext()){
			final int s = states.nextInt();
			numChoices = mdp.getNumChoices(s);
			for (int i = 0; i < numChoices; i++)
				for (int j = 0; j < trans_distr_file.size(); j++){
					q_value[j][s][i] = 0.0;
					v[j][s] = 0.0;
				}
			finalV[s] = 0.0;

		}

		for (iters= 0; iters < iterations; iters ++){	
			System.out.println(iters);
			states = unknownStates.iterator();
			while (states.hasNext()){
				final int s = states.nextInt();
				max_v = Float.POSITIVE_INFINITY; max_a = 0;
				numChoices = mdp.getNumChoices(s);
				for (int choice = 0; choice < numChoices; choice ++){
					double reward = mdpRewards.getStateReward(s).evaluate(toBigRationalPoint(empty_eval_array)).doubleValue();
					reward += mdpRewards.getTransitionReward(s, choice).evaluate(toBigRationalPoint(empty_eval_array)).doubleValue();
					Iterator<Map.Entry<Integer, Function>> iter3 = mdp.getTransitionsIterator(s, choice);
						for (int i = 0; i < jointRealization.size(); i++){ 
							Double[] realization = jointRealization.get(i);
							Iterator<Map.Entry<Integer,Double>> transit;
        					transit = mdp.getTransitionsMappedIterator(s, choice, p -> p.evaluate(toBigRationalPoint(realization)).doubleValue());
							while (transit.hasNext()){
								Map.Entry<Integer,Double> e = transit.next();
            					double transition_val = e.getValue();
								int next_state = e.getKey();
								
								q_value[i][s][choice] = reward + transition_val*v[i][next_state];
				
							}
						}
					//action_val[choice] = q_value;
					//if (action_val[choice] < max_v) {
					//	max_a = choice;
					//	max_v = action_val[choice]; 
					//	action_exp[s] = max_v;
					//}
				}
				
				double prev = 0.0;
				for (int choice = 0; choice < numChoices; choice ++){
					double tmpV = 0.0;
					for (int i=0; i < jointRealization.size(); i++){
						tmpV += jointProb.get(i)*q_value[i][s][choice];
					}
					if (tmpV > prev){
						prev = tmpV;
						choices[s] = choice;
						action_exp[s] = prev;
						max_a = choice;
					}
					for (int i=0; i < jointRealization.size(); i++){
						v[i][s] = q_value[i][s][max_a];
					}
				}
				policy[s] = mdp.getAction(s, max_a);
			}
			states = unknownStates.iterator();
			double error = 0.0;
			while (states.hasNext()){
				final int s = states.nextInt();
				double preV = finalV[s];
				double curV = 0.0;
				double curE = 0.0;
				for (int j = 0; j < jointRealization.size(); j++){
					curV += jointProb.get(j)*v[j][s];
				}
				finalV[s] = curV;
				if (curV - preV < 0){
					curE = preV - curV;
				} else {
					curE = curV - preV;
				}
				if (curE > error){
					error = curE;
				}
			}
			if (error <= error_thresh){
				break;
			}
		}

		states = unknownStates.iterator();
		while (states.hasNext()){
			final int s = states.nextInt();
			System.out.println(choices[s]);
		}
		operator.writeToFile(mdp.getFirstInitialState(), null);
		ModelCheckerResult res = new ModelCheckerResult();
		res.soln = Arrays.copyOf(action_exp, action_exp.length); // return the expected values for each state
		res.numIters = iterations;
		res.timeTaken = (System.currentTimeMillis() - total_timer) / 1000.0;
		// Store strategy
		if (genStrat) {
			res.strat = new MDStrategyArray<>(mdp, choices);
		}

		return res;
		}
	
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

	public static ArrayList<Double[]> createSegmentedCartesianProduct(ArrayList<Double> list, int[] indices) {
        List<List<Double>> segments = new ArrayList<>();
        int start = 0;

        // Split the list into segments based on the indices
        for (int end : indices) {
            List<Double> segment = new ArrayList<>();
            for (int i = start; i <= end && i < list.size(); i++) {
                segment.add(list.get(i));
            }
            segments.add(segment);
            start = end + 1;
        }

        ArrayList<Double[]> allCombinations = new ArrayList<>();
        // Generate Cartesian product across these segments
        generateCartesianProduct(segments, 0, new ArrayList<Double>(), allCombinations);

        return allCombinations;
    }

    // Recursive function to generate cartesian product of segments
    private static void generateCartesianProduct(List<List<Double>> segments, int index, List<Double> current, ArrayList<Double[]> allCombinations) {
        if (index == segments.size()) {
            Double[] combinationArray = current.toArray(new Double[0]);
            allCombinations.add(combinationArray);
            return;
        }

        List<Double> segment = segments.get(index);
        for (Double item : segment) {
            current.add(item);
            generateCartesianProduct(segments, index + 1, current, allCombinations);
            current.remove(current.size() - 1);
        }
    }

	public static ArrayList<Double> createSegmentedProducts(ArrayList<Double> list, int[] indices) {
        List<List<Double>> segments = new ArrayList<>();
        int start = 0;

        for (int end : indices) {
            List<Double> segment = new ArrayList<>();
            for (int i = start; i <= end && i < list.size(); i++) {
                segment.add(list.get(i));
            }
            segments.add(segment);
            start = end + 1;
        }

        ArrayList<Double> allProducts = new ArrayList<>();
        generateProducts(segments, 0, 1.0, allProducts);

        return allProducts;
    }

    private static void generateProducts(List<List<Double>> segments, int index, double currentProduct, ArrayList<Double> allProducts) {
        if (index == segments.size()) {
            allProducts.add(currentProduct);
            return;
        }

        List<Double> segment = segments.get(index);
        for (Double item : segment) {
            generateProducts(segments, index + 1, currentProduct * item, allProducts);
        }
    }

}
