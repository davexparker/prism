package explicit;

import common.IntSet;
import common.Interval;
import explicit.rewards.MDPRewards;
import explicit.rewards.MDPRewardsSimple;
import explicit.rewards.StateRewardsArray;
import explicit.*;
import param.BigRational;
import param.Function;
import prism.AccuracyFactory;
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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

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
		double gamma =1;
		double alpha=0.5;
		Double dtmc_epsilon = null;
		boolean check_dtmc_distr = true;
		mainLog.println("Using default parameters - Distr type: "+ distr_type);
		mainLog.println("----- Parameters:\natoms:"+atoms+" - vmax:"+v_max+" - vmin:"+v_min);
		mainLog.println("alpha:"+alpha+" - discount:"+gamma+" - max iterations:"+iterations+
					" - error thresh:"+error_thresh+ " - epsilon:"+dtmc_epsilon);
		mainLog.println("u_atoms:"+uncertain_atoms+" - u_vmax:"+u_vmax+" - u_vmin:"+u_vmin);

		Evaluator.EvaluatorFunction eval = (Evaluator.EvaluatorFunction) mdp.getEvaluator();
		int numParams = eval.getNumParameters();
		Double [] empty_eval_array = new Double[numParams];
		ArrayList<Double> trans_distr_file = new ArrayList<>(uncertain_atoms);
		ArrayList<Double> trans_prob = new ArrayList<>(uncertain_atoms);
		int [] index = new int[numParams];
		int counter = 0;
		for(int j=0; j<numParams; j++){
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
		int numChoices;
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
								q_value[i][s][choice] = reward;
								while (transit.hasNext()){
									Map.Entry<Integer,Double> e = transit.next();
            						double transition_val = e.getValue();
									int next_state = e.getKey();
									q_value[i][s][choice] += gamma*transition_val*v[i][next_state];
								}
							}
						}
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
		double expected_dtmc = 0;
		double [] exp_dtmc_atom = new double[trans_distr_file.size()];
		double [] exp_weighted = new double[trans_distr_file.size()];
		int total_atoms = trans_distr_file.size();
		long dtmc_timer = System.currentTimeMillis();
		for (int i = 0; i < total_atoms; i ++){
			double realization = trans_distr_file.get(i);
			MDP<Double> atom_mdp;
			atom_mdp = new MDPSimple<>(mdp,p -> p.evaluate(toBigRationalPoint(realization)).doubleValue(),Evaluator.forDouble());
			MDStrategy strat = new MDStrategyArray(atom_mdp, choices);
			DTMC dtmc = new DTMCFromMDPAndMDStrategy(atom_mdp, strat);
			StateRewardsArray mcRewards = new StateRewardsArray(n);

			for (int s = 0; s < n; s++) {
				double reward = mdpRewards.getStateReward(s).evaluate(toBigRationalPoint(empty_eval_array)).doubleValue() ;
				reward +=  mdpRewards.getTransitionReward(s, choices[s]).evaluate(toBigRationalPoint(empty_eval_array)).doubleValue();
				mcRewards.setStateReward(s, reward);
			}
			DTMCModelChecker mcDTMC = new DTMCModelChecker(this);
			timer = System.currentTimeMillis();
			ModelCheckerResult dtmc_result = mcDTMC.computeReachRewardsDistr(dtmc, mcRewards, target, "prism/umdp_out/distr_dtmc_prob_exp_"+i+".csv", dtmc_epsilon);
			timer = System.currentTimeMillis() - timer;
			if (verbosity >= 1) {
				mainLog.print("\nDTMC computation (" + (min ? "min" : "max") + ")");
				mainLog.println(" : " + timer / 1000.0 + " seconds.");
			}
			TreeMap<Integer,Double> result_i = (TreeMap<Integer, Double>) dtmc_result.solnObj[dtmc.getFirstInitialState()];
			for(Map.Entry<Integer, Double> entry : result_i.entrySet())
				{
					exp_dtmc_atom[i]+= entry.getKey() *entry.getValue();
				}

				// Use the joint distribution if there are multiple parameters
			expected_dtmc += trans_prob.get(i) * exp_dtmc_atom[i];
			exp_weighted[i] = trans_prob.get(i)*exp_dtmc_atom[i];
				
				
			}

			dtmc_timer = System.currentTimeMillis() - dtmc_timer;
			if (verbosity >= 1) {
				mainLog.print("\nTotal DTMC computation for total atoms - "+total_atoms);
				mainLog.println(" : " + dtmc_timer / 1000.0 + " seconds.");
			}
			
			writeCSV(exp_dtmc_atom, trans_prob, trans_distr_file, "/Users/khangvhuynh/prism-new/prism/tests/result/result.csv");
			// print info for DTMC results
			mainLog.println("Exp values: " + Arrays.toString(exp_dtmc_atom));
			mainLog.println("After weighted: " + Arrays.toString(exp_weighted));
			mainLog.println("DTMC weighted expected value :" + expected_dtmc);

			int numGridPoints = 100;
			double worstPerformance = Double.POSITIVE_INFINITY;
    		double worstX = 0;
			double X_max = 1.0;
			double X_min = 0.0;
    		double step = (X_max - X_min) / (numGridPoints - 1);
			double performance = 0.0;
    		for (int i = 0; i < numGridPoints; i++) {
        		double realization = X_min + i * step;
				MDP<Double> atom_mdp;
				atom_mdp = new MDPSimple<>(mdp,p -> p.evaluate(toBigRationalPoint(realization)).doubleValue(),Evaluator.forDouble());
				MDStrategy strat = new MDStrategyArray(atom_mdp, choices);
				DTMC dtmc = new DTMCFromMDPAndMDStrategy(atom_mdp, strat);
				StateRewardsArray mcRewards = new StateRewardsArray(n);

				for (int s = 0; s < n; s++) {
					double reward = mdpRewards.getStateReward(s).evaluate(toBigRationalPoint(empty_eval_array)).doubleValue() ;
					reward +=  mdpRewards.getTransitionReward(s, choices[s]).evaluate(toBigRationalPoint(empty_eval_array)).doubleValue();
					mcRewards.setStateReward(s, reward);
				}
				DTMCModelChecker mcDTMC = new DTMCModelChecker(this);
				ModelCheckerResult dtmc_result = mcDTMC.computeReachRewardsDistr(dtmc, mcRewards, target, "prism/umdp_out/distr_dtmc_prob_exp_"+i+".csv", dtmc_epsilon);
    
    	// Extract the performance (for example, by summing over the DTMC distribution).
    			TreeMap<Integer,Double> result = (TreeMap<Integer, Double>) dtmc_result.solnObj[dtmc.getFirstInitialState()];
    			for (Map.Entry<Integer, Double> entry : result.entrySet()) {
        			performance += entry.getKey() * entry.getValue();
    			}
        		if (performance < worstPerformance) { // assuming lower reward is worse
            		worstPerformance = performance;
            		worstX = realization;
        		}
    		}
    
    		mainLog.println("Worst-case parameter realization: " + worstX);
    		mainLog.println("Worst-case performance: " + worstPerformance);
			Double[] e_max = new Double[numParams];
			Double[] e_min = new Double[numParams];
			e_max[0] = 0.8;
			e_min[0] = 0.4;
			double[][] gl = new double[2][numParams];
			gl = computeGlobalSensitivity(mdp, mdpRewards, choices, target, min, e_min, e_max, 1000);
			try {
   				writeCsvSobol(gl, 10, "sobol_single.csv");
			} catch (IOException e) {
    			e.printStackTrace();
    		// Handle the exception (e.g., log it, rethrow, or notify the user)
			}
			IMDP<Double> imdp = makeIMDP(mdp, e_max, e_min, 5);
			IMDPModelChecker immc = new IMDPModelChecker(null);
			ModelCheckerResult resim;
			MDPRewards<Double> mdpRewards2 = new MDPRewardsSimple<Double>(mdpRewards, mdp, f -> f.evaluate(toBigRationalPoint(empty_eval_array)).doubleValue(), Evaluator.forDouble());
			resim = immc.computeReachRewards(imdp, mdpRewards2, target, MinMax.min().setMinUnc(true));
			System.out.println("minmin: " + resim.soln[0]);
			resim = immc.computeReachRewards(imdp, mdpRewards2, target, MinMax.min().setMinUnc(false));
			System.out.println("minmax: " + resim.soln[0]);
			resim = immc.computeReachRewards(imdp, mdpRewards2,target, MinMax.max().setMinUnc(true));
			System.out.println("maxmin: " + resim.soln[0]);
			resim = immc.computeReachRewards(imdp, mdpRewards2, target, MinMax.max().setMinUnc(false));
			System.out.println("maxmax: " + resim.soln[0]);

		states = unknownStates.iterator();
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
				states = unknownStates.iterator();
				while (states.hasNext()){
					final int s = states.nextInt();
					max_v = Float.POSITIVE_INFINITY; max_a = 0;
					numChoices = mdp.getNumChoices(s);
					for (int choice = 0; choice < numChoices; choice ++){
						
							for (int i = 0; i < jointRealization.size(); i++){ 
								Double[] realization = jointRealization.get(i);
								double reward = mdpRewards.getStateReward(s).evaluate(toBigRationalPoint(realization)).doubleValue();
								reward += mdpRewards.getTransitionReward(s, choice).evaluate(toBigRationalPoint(realization)).doubleValue();
								Iterator<Map.Entry<Integer,Double>> transit;
        						transit = mdp.getTransitionsMappedIterator(s, choice, p -> p.evaluate(toBigRationalPoint(realization)).doubleValue());
								q_value[i][s][choice] = reward;
								while (transit.hasNext()){
									Map.Entry<Integer,Double> e = transit.next();
            						double transition_val = e.getValue();
									int next_state = e.getKey();
								
									q_value[i][s][choice] += gamma*transition_val*v[i][next_state];
				
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
							action_exp[s] = tmpV;
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
		
		double expected_dtmc = 0;
		double [] exp_dtmc_atom = new double[jointRealization.size()];
		int total_atoms = jointRealization.size();
		long dtmc_timer = System.currentTimeMillis();
		for (int i = 0; i < total_atoms; i ++){
			int finalI = i;
			Double[] realization = jointRealization.get(i);
			MDP<Double> atom_mdp;
			atom_mdp = new MDPSimple<>(mdp,p -> p.evaluate(toBigRationalPoint(realization)).doubleValue(),Evaluator.forDouble());
			MDStrategy strat = new MDStrategyArray(atom_mdp, choices);
			DTMC dtmc = new DTMCFromMDPAndMDStrategy(atom_mdp, strat);
			StateRewardsArray mcRewards = new StateRewardsArray(n);

			for (int s = 0; s < n; s++) {
				double reward = mdpRewards.getStateReward(s).evaluate(toBigRationalPoint(realization)).doubleValue() ;
				reward +=  mdpRewards.getTransitionReward(s, choices[s]).evaluate(toBigRationalPoint(realization)).doubleValue();
				mcRewards.setStateReward(s, reward);
			}
			DTMCModelChecker mcDTMC = new DTMCModelChecker(this);
			timer = System.currentTimeMillis();
			ModelCheckerResult dtmc_result = mcDTMC.computeReachRewardsDistr(dtmc, mcRewards, target, "prism/umdp_out/distr_dtmc_prob_exp_"+i+".csv", dtmc_epsilon);
			timer = System.currentTimeMillis() - timer;
			if (verbosity >= 1) {
				mainLog.print("\nDTMC computation (" + (min ? "min" : "max") + ")");
				mainLog.println(" : " + timer / 1000.0 + " seconds.");
			}
			TreeMap<Integer,Double> result_i = (TreeMap<Integer, Double>) dtmc_result.solnObj[dtmc.getFirstInitialState()];
			for(Map.Entry<Integer, Double> entry : result_i.entrySet())
				{
					exp_dtmc_atom[i]+= entry.getKey() *entry.getValue();
				}

				// Use the joint distribution if there are multiple parameters
			expected_dtmc += jointProb.get(i) * exp_dtmc_atom[i];
				
				
			}

			dtmc_timer = System.currentTimeMillis() - dtmc_timer;
			if (verbosity >= 1) {
				mainLog.print("\nTotal DTMC computation for total atoms - "+total_atoms);
				mainLog.println(" : " + dtmc_timer / 1000.0 + " seconds.");
			}
			

			// print info for DTMC results
			mainLog.println("Exp values: " + Arrays.toString(exp_dtmc_atom));
			mainLog.println("DTMC weighted expected value :" + expected_dtmc);

			int numGridPoints = 10;
			double worstPerformance = Double.POSITIVE_INFINITY;
    		Double[] worstX = new Double[numParams];
			Double[] X_max = new Double[numParams];
			Double[] X_min = new Double[numParams];
			Double[] step = new Double[numParams];
			X_max[0] = 0.6;
			X_max[1] = 0.2;
			X_min[0] = 0.4;
			X_min[1] = 0.05;
    		step[0] = (X_max[0] - X_min[0]) / (numGridPoints - 1);
			step[1] = (X_max[1] - X_min[1]) / (numGridPoints - 1);
			int[] indices = new int[numParams];
			double performance = 0.0;
			int cur = 0;
			int numPa = (int) Math.pow(numGridPoints, numParams) - 1;
			ArrayList<Double> longPa = new ArrayList<>(numPa);
			while (cur < numParams){
				int i = 0;
				while (i < numGridPoints){
					Double x;
					x = X_min[cur] + i * step[cur];
					longPa.add(x);
					i = i + 1; 
				}
				indices[cur] = numGridPoints*(cur+1) - 1;
				cur = cur + 1;
			}
			ArrayList<Double[]> gridRealization = createSegmentedCartesianProduct(longPa, indices);
    		for (int i = 0; i < gridRealization.size(); i++) {
				
        		Double[] realization = gridRealization.get(i);
				MDP<Double> atom_mdp;
				atom_mdp = new MDPSimple<>(mdp,p -> p.evaluate(toBigRationalPoint(realization)).doubleValue(),Evaluator.forDouble());
				MDStrategy strat = new MDStrategyArray(atom_mdp, choices);
				DTMC dtmc = new DTMCFromMDPAndMDStrategy(atom_mdp, strat);
				StateRewardsArray mcRewards = new StateRewardsArray(n);

				for (int s = 0; s < n; s++) {
					double reward = mdpRewards.getStateReward(s).evaluate(toBigRationalPoint(realization)).doubleValue() ;
					reward +=  mdpRewards.getTransitionReward(s, choices[s]).evaluate(toBigRationalPoint(realization)).doubleValue();
					mcRewards.setStateReward(s, reward);
				}
				DTMCModelChecker mcDTMC = new DTMCModelChecker(this);
				ModelCheckerResult dtmc_result = mcDTMC.computeReachRewardsDistr(dtmc, mcRewards, target, "prism/umdp_out/distr_dtmc_prob_exp_"+i+".csv", dtmc_epsilon);
    
    	// Extract the performance (for example, by summing over the DTMC distribution).
    			TreeMap<Integer,Double> result = (TreeMap<Integer, Double>) dtmc_result.solnObj[dtmc.getFirstInitialState()];
    			for (Map.Entry<Integer, Double> entry : result.entrySet()) {
        			performance += entry.getKey() * entry.getValue();
    			}
        		if (performance < worstPerformance) { // assuming lower reward is worse
            		worstPerformance = performance;
            		worstX = realization;
        		}
    		}
    
    		mainLog.println("Worst-case parameter realization: " + Arrays.toString(worstX));
    		mainLog.println("Worst-case performance: " + worstPerformance);

			//mainLog.println("Computer Next State Probability:");
			//computeNextProbs(mdp, target, true, gridRealization);
			//mainLog.println("Compute Global Sensitivity (Sobol Index): ");
			//computeGlobalSensitivity(mdp, mdpRewards, choices, target, min, X_min, X_max, 1000);




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

	/**
	 * Compute next-step reachability probabilities for an uncertain parametric MDP.
	 * For each candidate parameter realization (drawn from the grid between X_min and X_max)
	 * the MDP is instantiated and the next-step probability vector is computed.
	 * Then, for each state, we take the worst-case probability (lowest if min==true, highest otherwise)
	 * over all parameter realizations.
	 *
	 * @param mdp      An MDP with transition probabilities given as functions (i.e. MDP<Function>).
	 * @param target   The set of target states.
	 * @param min      If true, compute the worst-case (minimum) probability; otherwise the maximum.
	 * @param paramGrid A grid of parameter where next-step reachability probabilities got evaluated
	 * @param steps    The number of discretization steps per parameter.
	 * @return A ModelCheckerResult containing the worst-case next–step probability vector.
	 * @throws PrismException if an error occurs.
	 */
	public ModelCheckerResult computeNextProbs(MDP<Function> mdp, BitSet target, boolean min,
														ArrayList<Double[]> paramGrid)
			throws PrismException {

		// Number of states in the MDP.
		int n = mdp.getNumStates();

		// Create a grid over the parameter space.
		mainLog.println("Parameter grid obtained. Total grid points: " + paramGrid.size());

		// We'll accumulate, for each state, the worst-case probability over the grid.
		// For a min problem, initialize with POSITIVE_INFINITY; for a max problem, with NEGATIVE_INFINITY.
		double worstCaseProbs[] = new double[n];
		if (min) {
			Arrays.fill(worstCaseProbs, Double.POSITIVE_INFINITY);
		} else {
			Arrays.fill(worstCaseProbs, Double.NEGATIVE_INFINITY);
		}

		// Temporary arrays for computation.
		double soln[], soln2[];
		soln = Utils.bitsetToDoubleArray(target, n);  // Initial vector: 1 for target states, 0 elsewhere.
		soln2 = new double[n];

		// Optionally, create a dummy strategy array if needed.
		int strat[] = null;
		if (genStrat) {
			strat = new int[n];
			for (int i = 0; i < n; i++) {
				strat[i] = target.get(i) ? -2 : -1;
			}
		}

		// Process each parameter realization from the grid.
		int gridIndex = 0;
		for (Double[] params : paramGrid) {
			gridIndex++;
			// Build a string to display the current parameter realization.
			StringBuilder sbParams = new StringBuilder();
			sbParams.append("Processing grid point ").append(gridIndex).append(" of ").append(paramGrid.size()).append(": ");
			for (Double param : params) {
				sbParams.append(param).append(" ");
			}
			mainLog.println(sbParams.toString());

			// Convert the Double[] to a Point with BigRational values.
			BigRational[] brParams = new BigRational[params.length];
			for (int i = 0; i < params.length; i++) {
				brParams[i] = new BigRational(Double.toString(params[i]));
			}
			Point paramPoint = new Point(brParams);
			// Create a concrete MDP by evaluating the transition functions at this parameter value.
			MDP<Double> concreteMdp = new MDPSimple<>(mdp,p -> p.evaluate(paramPoint).doubleValue(),Evaluator.forDouble());

			// Compute the next-step probability vector for the instantiated MDP.
			concreteMdp.mvMultMinMax(soln, min, soln2, null, false, strat);

			// Log the computed next-step probabilities for the current grid point.
			StringBuilder sbProbs = new StringBuilder("Next-step probabilities: ");
			for (int i = 0; i < n; i++) {
				sbProbs.append(soln2[i]).append(" ");
			}
			mainLog.println(sbProbs.toString());

			// Update the worst-case probabilities for each state.
			for (int i = 0; i < n; i++) {
				if (min) {
					worstCaseProbs[i] = Math.min(worstCaseProbs[i], soln2[i]);
				} else {
					worstCaseProbs[i] = Math.max(worstCaseProbs[i], soln2[i]);
				}
			}
		}

		// Log the final worst-case probabilities.
		StringBuilder sbFinal = new StringBuilder("Final worst-case probabilities: ");
		for (int i = 0; i < n; i++) {
			sbFinal.append(worstCaseProbs[i]).append(" ");
		}
		mainLog.println(sbFinal.toString());

		// Build the result.
		ModelCheckerResult res = new ModelCheckerResult();
		res.soln = worstCaseProbs;
		res.numIters = paramGrid.size();  // Number of parameter instantiations examined.
		if (genStrat) {
			// Note: This strategy corresponds to the last computed one.
			res.strat = new MDStrategyArray<>(mdp, strat);
		}
		return res;
	}

	/**
	 * Computes the aggregated performance for a concrete MDP instantiated at a given parameter vector.
	 * For example, this may be the average reachability probability.
	 *
	 * @param uncertainMDP The uncertain (parametric) MDP of type MDP<Function>.
	 * @param target       The set of target states.
	 * @param min          If true, we compute worst-case (minimum) probabilities; else, best-case (maximum).
	 * @param params       The parameter vector at which to instantiate the MDP.
	 * @return The aggregated performance metric.
	 * @throws PrismException if an error occurs.
	 */
	public double computeAggregatedPerformance(MDP<Function> uncertainMDP, MDPRewards<Function> mdpRewards, int[] choices, BitSet target, boolean min, Double[] params)
			throws PrismException {

		int n = uncertainMDP.getNumStates();
		Double dtmc_epsilon = null;
		double performance;
		performance = 0.0;

		// Instantiate a concrete MDP by evaluating the functions at the given parameter vector.
		MDP<Double> atom_mdp;

		// Compute reachability probabilities for the concrete MDP using your fixed-parameter method.
		// Here, computeReachProbs is assumed to return a ModelCheckerResult whose 'soln' field is a double[].
		// Convert the Double[] to a Point with BigRational values.
		BigRational[] brParams = new BigRational[params.length];
		for (int i = 0; i < params.length; i++) {
			brParams[i] = new BigRational(Double.toString(params[i]));
		}
		Point paramPoint = new Point(brParams);
		atom_mdp = new MDPSimple<>(uncertainMDP,p -> p.evaluate(paramPoint).doubleValue(),Evaluator.forDouble());
		MDStrategy strat = new MDStrategyArray(atom_mdp, choices);
		DTMC dtmc = new DTMCFromMDPAndMDStrategy(atom_mdp, strat);
		StateRewardsArray mcRewards = new StateRewardsArray(n);
		Double [] empty_eval_array = new Double[params.length];
		for (int j = 0; j < params.length; j++){
			empty_eval_array[j] = 0.0;
		}
		for (int s = 0; s < n; s++) {
			double reward = mdpRewards.getStateReward(s).evaluate(paramPoint).doubleValue() ;
			reward +=  mdpRewards.getTransitionReward(s, choices[s]).evaluate(paramPoint).doubleValue();
			mcRewards.setStateReward(s, reward);
		}
		DTMCModelChecker mcDTMC = new DTMCModelChecker(this);
		ModelCheckerResult dtmc_result = mcDTMC.computeReachRewardsDistr(dtmc, mcRewards, target, "prism/umdp_out/distr_dtmc_prob_exp_agg.csv", dtmc_epsilon);
    
    	// Extract the performance (for example, by summing over the DTMC distribution).
    	TreeMap<Integer,Double> result = (TreeMap<Integer, Double>) dtmc_result.solnObj[dtmc.getFirstInitialState()];
    	for (Map.Entry<Integer, Double> entry : result.entrySet()) {
        	performance += entry.getKey() * entry.getValue();
    	}
		return performance;
	}

	/**
	 * Computes the global sensitivity indices (first-order and total) for the uncertain parametric MDP.
	 * Uses a variance-based (Sobol) method.
	 *
	 * @param uncertainMDP The uncertain (parametric) MDP.
	 * @param target       The set of target states.
	 * @param min          If true, we consider worst-case (min) performance; else, best-case (max).
	 * @param X_min        An array of minimum values for each parameter.
	 * @param X_max        An array of maximum values for each parameter.
	 * @param N            The number of random samples for each sample matrix.
	 * @return A two-dimensional array of sensitivity indices:
	 *         indices[0] contains the first-order (main effect) Sobol indices,
	 *         indices[1] contains the total effect Sobol indices.
	 * @throws PrismException if an error occurs.
	 */
	public double[][] computeGlobalSensitivity(MDP<Function> uncertainMDP, MDPRewards<Function> mdpRewards,int[] choices, BitSet target, boolean min,
											Double[] X_min, Double[] X_max, int N) throws PrismException {
		int k = X_min.length;  // number of parameters
		// Generate two independent sample matrices A and B (size: N x k).
		Double[][] A = generateRandomSamples(X_min, X_max, N, k);
		Double[][] B = generateRandomSamples(X_min, X_max, N, k);
		Evaluator.EvaluatorFunction eval = (Evaluator.EvaluatorFunction) uncertainMDP.getEvaluator();
		mainLog.println("Generated sample matrices A and B with N = " + N);

		Double[] Y_A = new Double[N];
		Double[] Y_B = new Double[N];
		// Compute performance for each sample in A and B.
		for (int j = 0; j < N; j++) {
			Y_A[j] = computeAggregatedPerformance(uncertainMDP, mdpRewards, choices, target, min, A[j]);
			Y_B[j] = computeAggregatedPerformance(uncertainMDP, mdpRewards, choices, target, min, B[j]);
			mainLog.println("Sample " + j + ": Y_A = " + Y_A[j] + ", Y_B = " + Y_B[j]);
		}
		double V_Y = variance(Y_A);
		mainLog.println("Total variance V_Y = " + V_Y);

		double[] S = new double[k];       // First-order (main effect) indices.
		double[] S_total = new double[k];   // Total effect indices.

		// For each parameter i, form matrix A_B(i) and compute corresponding performance.
		for (int i = 0; i < k; i++) {
			Double[][] A_B = formMatrix_AB(A, B, i);
			Double[] Y_A_B = new Double[N];
			for (int j = 0; j < N; j++) {
				Y_A_B[j] = computeAggregatedPerformance(uncertainMDP, mdpRewards, choices, target, min, A_B[j]);
			}
			Double sumFirst = 0.0;
			Double sumTotal = 0.0;
			for (int j = 0; j < N; j++) {
				sumFirst += Y_A[j] * (Y_A_B[j] - Y_B[j]);
				double diff = Y_A[j] - Y_A_B[j];
				sumTotal += diff * diff;
			}
			S[i] = sumFirst / (N * V_Y);
			S_total[i] = sumTotal / (2 * N * V_Y);
			mainLog.println("Parameter " + eval.getParameterName(i) + ": First-order index S[" + i + "] = " + S[i]
					+ ", Total index S_total[" + i + "] = " + S_total[i]);
		}

		double[][] globalSensitivityIndices = new double[2][k];
		globalSensitivityIndices[0] = S;
		globalSensitivityIndices[1] = S_total;
		return globalSensitivityIndices;
	}

	/**
	 * Generates an array of N random samples, each being a k-dimensional vector,
	 * with each parameter sampled uniformly between X_min[i] and X_max[i].
	 *
	 * @param X_min Array of minimum bounds for each parameter.
	 * @param X_max Array of maximum bounds for each parameter.
	 * @param N     Number of samples.
	 * @param k     Number of parameters.
	 * @return A 2D array of samples (size: N x k).
	 */
	private Double[][] generateRandomSamples(Double[] X_min, Double[] X_max, int N, int k) {
		Double[][] samples = new Double[N][k];
		Random rand = new Random();
		for (int j = 0; j < N; j++) {
			for (int i = 0; i < k; i++) {
				double r = rand.nextDouble(); // Uniform in [0,1)
				samples[j][i] = X_min[i] + r * (X_max[i] - X_min[i]);
			}
		}
		return samples;
	}

	/**
	 * Computes the sample variance of the values in the array.
	 *
	 * @param values Array of sample values.
	 * @return The sample variance.
	 */
	private Double variance(Double[] values) {
		int N = values.length;
		double sum = 0.0;
		for (double v : values) {
			sum += v;
		}
		double mean = sum / N;
		double varSum = 0.0;
		for (double v : values) {
			varSum += (v - mean) * (v - mean);
		}
		return varSum / (N - 1);  // Using sample variance (N-1 in denominator)
	}

	/**
	 * Forms a new sample matrix A_B for a given parameter index i.
	 * For each sample j, A_B[j] is the same as A[j] except that its i-th element is replaced by B[j][i].
	 *
	 * @param A The first sample matrix (size: N x k).
	 * @param B The second sample matrix (size: N x k).
	 * @param i The parameter index to replace.
	 * @return A new sample matrix A_B (size: N x k).
	 */
	private Double[][] formMatrix_AB(Double[][] A, Double[][] B, int i) {
		int N = A.length;
		int k = A[0].length;
		Double[][] A_B = new Double[N][k];
		for (int j = 0; j < N; j++) {
			// Copy entire sample from A.
			A_B[j] = A[j].clone();
			// Replace the i-th parameter with that from B.
			A_B[j][i] = B[j][i];
		}
		return A_B;
	}
	/**
	 * Compute reachability/until probabilities.
	 * i.e. compute the min/max probability of reaching a state in {@code target},
	 * while remaining in those in {@code remain}.
	 * @param mdp The MDP
	 * @param remain Remain in these states (optional: null means "all")
	 * @param target Target states
	 * @param min Min or max probabilities (true=min, false=max)
	 * @param init Optionally, an initial solution vector (may be overwritten) 
	 * @param known Optionally, a set of states for which the exact answer is known
	 * Note: if 'known' is specified (i.e. is non-null, 'init' must also be given and is used for the exact values).
	 * Also, 'known' values cannot be passed for some solution methods, e.g. policy iteration.  
	 */
	public ModelCheckerResult computeReachProbs(MDP<Double> mdp, BitSet remain, BitSet target, boolean min, double init[], BitSet known) throws PrismException
	{
		MDPModelChecker mc = new MDPModelChecker(null);
		return mc.computeReachProbs(mdp, remain, target, min, init, known);
	}

	/**
	 * Compute reachability probabilities.
	 * i.e. compute the min/max probability of reaching a state in {@code target}.
	 * @param mdp The MDP
	 * @param target Target states
	 * @param min Min or max probabilities (true=min, false=max)
	 */
	public ModelCheckerResult computeReachProbs(MDP<Double> mdp, BitSet target, boolean min) throws PrismException
	{
		return computeReachProbs(mdp, null, target, min, null, null);
	}

	/**
	 * Make IMDP from upMPD with a range
	**/
	public IMDP<Double> makeIMDP(MDP<Function> mdp, Double[] X_max, Double[] X_min, int numGridPoints){
		IMDPSimple<Double> imdp = new IMDPSimple<>();
		// Store num states
		int n = mdp.getNumStates();		
		Double[] step = new Double[X_max.length];
		for (int i = 0; i < X_max.length; i++){
			step[i] = (X_max[i] - X_min[i]) / (numGridPoints - 1);
		}
		int[] indices = new int[X_max.length];
		int cur = 0;
		int numPa = (int) Math.pow(numGridPoints, X_max.length) - 1;
		ArrayList<Double> longPa = new ArrayList<>(numPa);
		while (cur < X_max.length){
			int i = 0;
			while (i < numGridPoints){
				Double x;
				x = X_min[cur] + i * step[cur];
				longPa.add(x);
				i = i + 1; 
			}
			indices[cur] = numGridPoints*(cur+1) - 1;
			cur = cur + 1;
		}
		ArrayList<Double[]> gridRealization = createSegmentedCartesianProduct(longPa, indices);
		for (int s = 0; s < n; s ++){
			imdp.addState();
		}
		for (int s = 0; s < n; s ++){
			int numChoices = mdp.getNumChoices(s);
			for (int choice = 0; choice < numChoices; choice ++){
				Map<Integer, Double[]> transition_interval = new HashMap<>();
				Map<Integer, Double> max = new HashMap<>();
				Map<Integer, Double> min = new HashMap<>();
				int point = 0;
				while(point < gridRealization.size()){
					Double[] realization;
					realization = gridRealization.get(point);
					Iterator<Map.Entry<Integer,Double>> transit;
        			transit = mdp.getTransitionsMappedIterator(s, choice, p -> p.evaluate(toBigRationalPoint(realization)).doubleValue());
					while(transit.hasNext()){
						Map.Entry<Integer,Double> e = transit.next();
						int nextState = e.getKey();
						if (max.containsKey(nextState)){
							continue;
						} else {
							Double[] pair = new Double[2];
							pair[0] = Double.POSITIVE_INFINITY; // min
							pair[1] = Double.NEGATIVE_INFINITY; // max
							max.put(nextState, pair[1]);
							min.put(nextState, pair[0]);
							transition_interval.put(nextState, pair);
						}
						
					}
					transit = mdp.getTransitionsMappedIterator(s, choice, p -> p.evaluate(toBigRationalPoint(realization)).doubleValue());
					while (transit.hasNext()){
						Map.Entry<Integer,Double> e = transit.next();
						Double transition_val = e.getValue();
						int nextState = e.getKey();
						Double upper = max.get(nextState);
						Double lower = min.get(nextState);
						if (transition_val > max.get(nextState)){
							max.replace(nextState, transition_val);
						} 
						if (transition_val < min.get(nextState)){
							min.replace(nextState, transition_val);
						}

						Double[] pair = new Double[2];
						pair[0] = min.get(nextState);
						pair[1] = max.get(nextState);

						transition_interval.put(nextState, pair);
						
						
					}
					point = point + 1;
				}
				Distribution<Interval<Double>> distr;
				Evaluator<Interval<Double>> eval = Evaluator.forDoubleInterval();
				distr = new Distribution<>(eval);
				for (Map.Entry<Integer, Double[]> entry : transition_interval.entrySet()) {
           			int next_state = entry.getKey();
            		Double[] pair = entry.getValue();
            		distr.add(next_state, new Interval<Double>(pair[0], pair[1]));
        		}
				imdp.addActionLabelledChoice(s, distr, choice);
			}
		}
		
		return imdp;
	}

	public static void writeCSV(double[] values, ArrayList<Double> probability,ArrayList<Double> prob_val, String fileName) {
    // Separate the base name and extension.
    int dotIndex = fileName.lastIndexOf(".");
    String baseName = (dotIndex != -1) ? fileName.substring(0, dotIndex) : fileName;
    String extension = (dotIndex != -1) ? fileName.substring(dotIndex) : "";
    
    File file = new File(fileName);
    int counter = 1;
    
    // Loop until a non-existent file is found.
    while (file.exists()) {
        String newFileName = baseName + "_" + counter + extension;
        file = new File(newFileName);
        counter++;
    }

	System.out.println("File will be created as: " + file.getName());

    
    try (PrintWriter writer = new PrintWriter(file)) {
        // Write the CSV header.
        writer.println("id,exp_value,probability,realization,weighted_exp_value");

        // Write each row: index and corresponding value.
        for (int i = 0; i < values.length; i++) {
            writer.println(i + "," + values[i] + "," + probability.get(i) + "," +prob_val.get(i)+","+ (values[i] * probability.get(i)));
        }
        
        System.out.println("CSV file created successfully: " + file.getName());
    } catch (FileNotFoundException e) {
        System.err.println("Error creating file: " + e.getMessage());
    }
}
	/**
     * Writes a double[][] to a CSV file. Assumes data has two rows:
     * - data[0] for first_sobol values.
     * - data[1] for total_sobol values.
     * 
     * If the CSV file exists, the new rows are appended. If not, a new file is created with a header.
     *
     * @param data the double array where data[0] and data[1] must be of equal length
     * @param filePath the file path to write the CSV output
     * @throws IOException if an I/O error occurs
     */
    public static void writeCsvSobol(double[][] data, int num, String filePath) throws IOException {
        // Validate the input
        if (data == null || data.length < 2) {
            throw new IllegalArgumentException("Data must contain at least 2 rows.");
        }
        int numElements = data[0].length;
        if (data[1].length != numElements) {
            throw new IllegalArgumentException("Both rows must have the same number of elements.");
        }

        File file = new File(filePath);
        boolean fileExists = file.exists();

        // Open file in append mode
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
            // If file does not exist, write the header
            if (!fileExists) {
                writer.write("num_disc,first_sobol,total_sobol");
                writer.newLine();
            }
            
            // Write each row of data
            for (int i = 0; i < numElements; i++) {
                writer.write(num + "," +data[0][i] + "," + data[1][i]);
                writer.newLine();
            }
        }
    }

}
