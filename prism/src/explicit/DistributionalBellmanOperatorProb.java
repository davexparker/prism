package explicit;

// TODO: this class is for when the transitions are uncertain (only the transitions are the source of distribution)
// This class does not do distribution over the returns, only over the values of the uncertain parameter
// the main difference is the step + updates, since it will generate a distribution

// Still saves a distribution for each state. the number of atoms 
// is based on the number of atoms in the parameter distribution.

import param.BigRational;
import param.Function;
import param.Point;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;

class DistributionalBellmanOperatorProb extends DistributionalBellmanOperator {

    public DistributionalBellmanOperatorProb(int atoms, double vmin, double vmax, int numStates, String distr_type, prism.PrismLog log) {
        super(atoms, vmin, vmax, numStates, distr_type, log);

    }

    // Step for when the support represents the possible expected values and the transition is uncertain
    // Assumption : only one uncertain parameter is associated with a state-action pair.
    public double step(MDP<Function> mdp, double realization, int s, int choice, double gamma, double reward) {
        TreeMap<Double, Double> sum_p = new TreeMap<>();
        Iterator<Map.Entry<Integer,Double>> iter;
        double exp_value = 0.0;
        int temp_atoms;
        DiscreteDistribution res;
		System.out.println("realization: " + realization);
        Iterator<Map.Entry<Integer,Double>> transit;
        transit = mdp.getTransitionsMappedIterator(s, choice, p -> p.evaluate(toBigRationalPoint(realization)).doubleValue());
		while (transit.hasNext()){
			Map.Entry<Integer,Double> e = transit.next();
            double transition_val = e.getValue();
			int numTrans = distr[e.getKey()].getAtoms();
            System.out.println(transition_val);
			for (int j =0; j< numTrans; j++){
                exp_value += (distr[e.getKey()].getValue(j) * distr[e.getKey()].getSupport(j) * transition_val);
            }
		}
        exp_value += reward;
        return exp_value;
    }
    // Log distribution for a state to a file <filename> as a csv with columns : support index, probability, support value
    // Categorical : support index, probability, support value
    // Quantile : support value, probability, cumulative probability
    @Override
    public void writeToFile(int state, String filename){
        if (filename == null) {filename="distr_exp_prob_"+distr_type.toLowerCase()+".csv";}
        try (PrintWriter pw = new PrintWriter("prism/"+filename)) {
            pw.println("r,p,z");
            pw.println(distr[state].toFile());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static Point toBigRationalPoint(Double input)
    {
        return new Point(new BigRational[]{new BigRational(input)});
    }

    public static Point toBigRationalPoint(Double [] input)
    {
        BigRational [] temp = new BigRational[input.length];
        for (int i=0; i<input.length; i++){
            temp[i] = new BigRational(input[i]);
        }
        return new Point(temp);
    }

}