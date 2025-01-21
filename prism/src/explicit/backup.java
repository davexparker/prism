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
		for(int j=0; j<numParams; j++){
			DiscreteDistribution transition_temp;
			String p_name = eval.getParameterName(j);
			ArrayList<String[]> params = mcMDP.readParams("prism/tests/param_distr/param_"+p_name+".csv", 2);
			int i = 0; empty_eval_array[j] = 0.0;
			// parse distributional information for
			for (String param : params.get(0)) {
				// distributions over transition probabilities
				trans_distr_file.add(Double.parseDouble(param));
				;
				// Probability of having those transition values
				trans_prob.add(Double.parseDouble(params.get(1)[i]));
				i += 1;
			}
		}

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
	}


 /* public ModelCheckerResult computeReachRewards(MDP<Function> mdp, MDPRewards<Function> mdpRewards, BitSet target, boolean min) throws PrismException
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
		int iterations = 3000;
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

		if (distr_type.equals(c51) || distr_type.equals(qr)) {
			ArrayList<String []> params = mcMDP.readParams(null, -1);
			atoms = Integer.parseInt(params.get(0)[0]);
			double v_min = Double.parseDouble(params.get(0)[1]);
			double v_max = Double.parseDouble(params.get(0)[2]);
			error_thresh = Double.parseDouble(params.get(0)[3]); // 0.7 for uav
			dtmc_epsilon = Double.parseDouble(params.get(0)[4]);
			alpha = Double.parseDouble(params.get(0)[5]);

			// Uncertain parameter distribution info
			uncertain_atoms = Integer.parseInt(params.get(0)[6]);
			u_vmin = Double.parseDouble(params.get(0)[7]);
			u_vmax = Double.parseDouble(params.get(0)[8]);

			operator = new DistributionalBellmanOperatorProb(atoms, v_min, v_max, n, distr_type, mainLog);
			temp_p = new DistributionalBellmanOperatorProb(atoms, v_min, v_max, n, distr_type, mainLog);

			if(distr_type.equals(c51)){
				save_p = new DistributionCategorical(atoms, v_min, v_max, mainLog);
			} else{
				save_p = new DistributionQuantile(atoms, mainLog);
			}

			mainLog.println("Distr type: "+ distr_type);
			mainLog.println("----- Parameters:\natoms:"+atoms+" - vmax:"+v_max+" - vmin:"+v_min);
			mainLog.println("alpha:"+alpha+" - discount:"+gamma+" - max iterations:"+iterations+
					" - error thresh:"+error_thresh+ " - epsilon:"+dtmc_epsilon);
			mainLog.println("u_atoms:"+uncertain_atoms+" - u_vmax:"+u_vmax+" - u_vmin:"+u_vmin);
		} else{
			atoms=101;
			double v_max = 100;
			double v_min = 0;
			distr_type = "C51";
			uncertain_atoms = 11;
			u_vmin =0.0; u_vmax = 1.0;
			mainLog.println("Using default parameters - Distr type: "+ distr_type);
			mainLog.println("----- Parameters:\natoms:"+atoms+" - vmax:"+v_max+" - vmin:"+v_min);
			mainLog.println("alpha:"+alpha+" - discount:"+gamma+" - max iterations:"+iterations+
					" - error thresh:"+error_thresh+ " - epsilon:"+dtmc_epsilon);
			mainLog.println("u_atoms:"+uncertain_atoms+" - u_vmax:"+u_vmax+" - u_vmin:"+u_vmin);
			operator = new DistributionalBellmanOperatorProb(atoms, v_min, v_max, n, "C51", mainLog);
			temp_p = new DistributionalBellmanOperatorProb(atoms, v_min, v_max, n, "C51", mainLog);
			save_p = new DistributionCategorical(atoms, v_min, v_max, mainLog);
		}

		//  Parse uncertain parameter distribution info here
		Evaluator.EvaluatorFunction eval = (Evaluator.EvaluatorFunction) mdp.getEvaluator();
		int numParams = eval.getNumParameters();
		ArrayList<DiscreteDistribution> transition_distr = new ArrayList<>(numParams);
		Double [] empty_eval_array = new Double[numParams];
//		ArrayList<Double> joint_distr_supports = new ArrayList<>(numParams*numParams);
//		ArrayList<Double> joint_distr_probs = new ArrayList<>(numParams*numParams);

		for(int j=0; j<numParams; j++){
			DiscreteDistribution transition_temp;
			String p_name = eval.getParameterName(j);
			ArrayList<String[]> params = mcMDP.readParams("prism/tests/param_distr/param_"+p_name+".csv", 2);
			ArrayList<Double> trans_distr_file = new ArrayList<>(uncertain_atoms);
			ArrayList<Double> trans_prob = new ArrayList<>(uncertain_atoms);

			int i = 0; empty_eval_array[j] = 0.0;
			// parse distributional information for
			for (String param : params.get(0)) {
				// distributions over transition probabilities
				trans_distr_file.add(Double.parseDouble(param));
				;
				// Probability of having those transition values
				trans_prob.add(Double.parseDouble(params.get(1)[i]));
				i += 1;
			}

			if (distr_type.equals(c51)) {
				transition_temp = new DistributionCategorical(uncertain_atoms,
						u_vmin, u_vmax, mainLog);
			} else {
				transition_temp = new DistributionQuantile(uncertain_atoms, mainLog);
			}
			transition_temp.project(trans_prob, trans_distr_file);

			mainLog.println("\n-----------\nParam "+p_name+" distr:");
			mainLog.println(transition_temp.getValues());
			mainLog.println(transition_temp.getSupports());

			transition_distr.add(transition_temp);
		}

		Map<Integer, Point> jointSupp=new HashMap<>(uncertain_atoms); Map<Integer, BigRational> jointProb;
		DiscreteDistribution joint_distr; int joint_atoms = 0;
		if(numParams >1) {
			jointProb = new HashMap<>(uncertain_atoms);
			joint_atoms = getIndexCombinations(numParams,uncertain_atoms, jointSupp, jointProb, transition_distr, new int[numParams], 0);

			mainLog.println("\nJoint distr with joint atoms:"+joint_atoms);
			Iterator<Integer> iter_prob = jointProb.keySet().iterator();
			DecimalFormat df = new DecimalFormat("0.000");
			while (iter_prob.hasNext()) {
				int key = iter_prob.next();
				mainLog.print(key+"="+df.format(jointProb.get(key).doubleValue())+", ");
			}
			mainLog.println();
			mainLog.println(jointSupp);

			if (distr_type.equals(c51)) {
				joint_distr = new DistributionCategorical(joint_atoms,
						0, joint_atoms-1, mainLog);
			} else {
				joint_distr = new DistributionQuantile(joint_atoms, mainLog);
			}

			joint_distr.project(jointProb.values(), jointSupp.keySet().toArray());

		} else {
            joint_distr = null;
        }


        // Create/initialise solution vector(s)
		DiscreteDistribution m = null;
		double [] action_val = new double[nactions];
		double [] action_exp = new double[n];
		Object [] policy = new Object[n];
		int[] choices = new int[n];
		double min_v; int min_a;
		double max_dist ; int numChoices;
		int iters; boolean isUncertain;

		// Start iterations - number of episodes
		for (iters = 0; (iters < iterations) ; iters++)
		{
			iteration_timer = System.currentTimeMillis();

			// copy to temp value from operator
			if (iters> 0) {
				temp_p.clone(operator);
			}
			// mainLog.println("iteration : " + iters);

			PrimitiveIterator.OfInt states = unknownStates.iterator();
			for (int i = 0; i < 10; i++){
			while (states.hasNext()) {
				final int s = states.nextInt();
				//System.out.println(s);
				numChoices = mdp.getNumChoices(s);
				min_v = Float.POSITIVE_INFINITY; min_a = 0;
				save_p.clear();
				Arrays.fill(action_val, Float.POSITIVE_INFINITY);
				// Track the path of transitions
        		List<Integer> path = new ArrayList<>();
        		path.add(s);  // Add current state to the path
				// mainLog.println("state : " + s);
				for (int choice = 0; choice < numChoices; choice++){ // aka action

					// INFO: the value passed to evaluate doesn't matter here because the rewards don't depend on the
					// 		  transition probabilities
					double reward = mdpRewards.getStateReward(s).evaluate(toBigRationalPoint(empty_eval_array)).doubleValue() ;
					reward +=  mdpRewards.getTransitionReward(s, choice).evaluate(toBigRationalPoint(empty_eval_array)).doubleValue();

					// check if the transition is uncertain
					isUncertain = true;
					Iterator<Map.Entry<Integer, Function>> iter3 = mdp.getTransitionsIterator(s, choice);
					while (iter3.hasNext()) {
						Map.Entry<Integer, Function> e = iter3.next();
						isUncertain &= e.getValue().isConstant();
						// Get the next state and transition probability
                		int nextState = e.getKey();
                		double transitionProb = e.getValue().evaluate(toBigRationalPoint(empty_eval_array)).doubleValue();
						//DistributionalBellmanOperatorProb tmp = operator;
                		// Add next state to the path
                		path.add(nextState);

					}
					//System.out.println("---------------------------");
					//System.out.println("transition_distr" + transition_distr.get(0));

					if(!isUncertain)
					{
						// Uncertain transition step
						// if multiple parameters exist, send the joint distribution
						if(numParams>1){
							m = operator.step(mdp, joint_distr, joint_distr.getValue(i), 0.0, jointSupp, s, choice, gamma, reward);
							System.out.println("Hi 1");
						} else{
							//System.out.println(choice);
							System.out.println("Hi 2");
							m = operator.step(mdp, transition_distr.get(0), transition_distr.get(0).getValue(i),0.0, jointSupp, s, choice, gamma, reward);
						}

					}
					else {
						Iterator<Map.Entry<Integer, Double>> iter2;
						iter2 = mdp.getTransitionsMappedIterator(s, choice, p -> p.evaluate(toBigRationalPoint(empty_eval_array)).doubleValue());
						System.out.println("Hi 3");
						m = operator.step(iter2, gamma, reward, s);
					}
					// mainLog.println("state : "+s+"- choice: "+ mdp.getAction(s, choice)+" -- [" + m.toString(operator.getFormat()) +"]");
					action_val[choice] = m.getExpValue();

					if (action_val[choice] < min_v) {
						min_a = choice;
						min_v = action_val[choice]; action_exp[s] = min_v;
						save_p.clone(m);
					}
				//System.out.println("m: " + m);
				//System.out.println("save-p " + save_p);
				}
				policy[s] = mdp.getAction(s, min_a);
				choices[s] = min_a;
				temp_p.update(save_p, s);
				}
			}
			//System.out.println("temp: "+temp_p);


			states = unknownStates.iterator();
			max_dist = 0.0;

			mainLog.print("initial s: "+ mdp.getFirstInitialState()+" -- [");
			mainLog.print(operator.toString(mdp.getFirstInitialState()));
			mainLog.print("]\n");

			while (states.hasNext()) {
				final int s = states.nextInt();
				double tempo = operator.getW(temp_p.getDist(s), s);
				//if(tempo > max_dist){bad.add(s);}
				max_dist = Math.max(max_dist, tempo);
				operator.update(temp_p.getDist(s), s);
			}
			mainLog.println("Max Wp dist :"+(max_dist) + " error Wp:" + (error_thresh) +" at iter:"+iters);
			if ((max_dist <error_thresh)&(iters>min_iter)) {
				break;
			}

			iteration_timer = System.currentTimeMillis() - iteration_timer;
			max_iteration_timer = Math.max(iteration_timer, max_iteration_timer);
		}

		mainLog.println("\nV[start] at " + (iters + 1) + " with method "+distr_type);
		DecimalFormat df = new DecimalFormat("0.000");
		mainLog.print("[");
		mainLog.print(operator.toString(mdp.getFirstInitialState()));
		mainLog.print("]\n");
		mainLog.println("E at initial state:" + operator.getExpValue(mdp.getFirstInitialState()));

		timer = System.currentTimeMillis() - timer;
		if (verbosity >= 1) {
			mainLog.print("\nValue iteration computation (" + (min ? "min" : "max") + ")");
			mainLog.println(" : " + timer / 1000.0 + " seconds.");
			mainLog.println("Max time for 1 iteration :"+max_iteration_timer/1000.0+"s");
		}

		// Compute distribution on induced DTMC for each param
		// TODO create a function that takes mdp<mdp> + param index
		mainLog.println("Computing distribution on induced DTMC...");
		if (check_dtmc_distr) {
			double expected_dtmc = 0;
			int total_atoms = (!jointSupp.isEmpty()? joint_atoms : uncertain_atoms );
			double [] exp_dtmc_atom = new double[total_atoms];
			long dtmc_timer = System.currentTimeMillis();
			for(int i=0; i<total_atoms; i++) {
				int finalI=i;

				// if multiple parameters, use jointSupp which maps an index to a point
				MDP<Double> atom_mdp;
				if(!jointSupp.isEmpty()) {
					atom_mdp = new MDPSimple<>(mdp,
							p -> p.evaluate(jointSupp.get(finalI)).doubleValue(),
							Evaluator.forDouble());
				} else {
					atom_mdp = new MDPSimple<>(mdp,
							p -> p.evaluate(toBigRationalPoint(transition_distr.get(0).getSupport(finalI))).doubleValue(),
							Evaluator.forDouble());
				}

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
				if(!jointSupp.isEmpty()) {
					expected_dtmc += joint_distr.getValue(i) * exp_dtmc_atom[i];
				} else{
					expected_dtmc += transition_distr.get(0).getValue(i) * exp_dtmc_atom[i];
				}
				
				
			}

			dtmc_timer = System.currentTimeMillis() - dtmc_timer;
			if (verbosity >= 1) {
				mainLog.print("\nTotal DTMC computation for total atoms - "+total_atoms);
				mainLog.println(" : " + dtmc_timer / 1000.0 + " seconds.");
			}
			

			// print info for DTMC results
			if(!jointSupp.isEmpty()) {
				mainLog.println("-----Atoms: " + Arrays.toString(joint_distr.getSupports()));
				mainLog.println("---Weights: " + Arrays.toString(joint_distr.getValues()));
			} else {
				mainLog.println("-----Atoms: " + Arrays.toString(transition_distr.get(0).getSupports()));
				mainLog.println("---Weights: " + Arrays.toString(transition_distr.get(0).getValues()));
			}
			mainLog.println("Exp values: " + Arrays.toString(exp_dtmc_atom));
			mainLog.println("DTMC weighted expected value :" + expected_dtmc);
		}


		// TODO create a function that takes mdp<mdp> + param index
//		MDPSimple mdpToSimulate = new MDPSimple(mdp);
//		if(gen_trace) {
//			mcMDP.exportTrace(mdpToSimulate, target, strat, "exp");
//		}

		// Store results
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
	}*/

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
