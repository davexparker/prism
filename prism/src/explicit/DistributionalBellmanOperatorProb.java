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
    public TreeMap<Integer, DiscreteDistribution> stepSep(MDP<Function> mdp, DiscreteDistribution param, Map<Integer, Point> jointMap, int s, int choice, double gamma, double state_reward) {
        TreeMap<Double, Double> sum_p = new TreeMap<>();
        Iterator<Map.Entry<Integer,Double>> iter;
        double exp_value;
        int temp_atoms;
        DiscreteDistribution res;

        if (isCategorical) {
            res = new DistributionCategorical(atoms, v_min, v_max, mainLog);
        } else {
            res = new DistributionQuantile(atoms, mainLog);
        }

        // tree map construction

        TreeMap<Integer, DiscreteDistribution> valuesForRealizations = new TreeMap<>();

    for (int i = 0; i < param.getAtoms(); i++) {
        double expValue = 0.0;

        // Check if the parameter realization has a non-zero probability
        if (param.getValue(i) > 0) {
            int finalI = i;
            DiscreteDistribution tempDist;
            if (isCategorical) {
                tempDist = new DistributionCategorical(atoms, v_min, v_max, mainLog);
            } else {
                tempDist = new DistributionQuantile(atoms, mainLog);
            }

            Iterator<Map.Entry<Integer, Double>> iter2;
            if (!jointMap.isEmpty()) {
                iter2 = mdp.getTransitionsMappedIterator(s, choice,
                        p -> p.evaluate(jointMap.get(finalI)).doubleValue());
            } else {
                iter2 = mdp.getTransitionsMappedIterator(s, choice,
                        p -> p.evaluate(toBigRationalPoint(param.getSupport(finalI))).doubleValue());
            }

            // Compute the value for this parameter realization
            while (iter2.hasNext()) {
                Map.Entry<Integer, Double> e = iter2.next();
                double transitionVal = e.getValue();
                int tempAtoms = distr[e.getKey()].getAtoms();

                for (int j = 0; j < tempAtoms; j++) {
                    expValue += (distr[e.getKey()].getValue(j) * 
                                 distr[e.getKey()].getSupport(j) * 
                                 transitionVal);
                }
            }
            expValue = expValue * gamma + state_reward;

            // Store the value in the distribution for this realization
            if (sum_p.containsKey(expValue)) {
                sum_p.put(expValue, sum_p.get(expValue) + param.getValue(i));
            } else {
                sum_p.put(expValue,  param.getValue(i));
            }
            tempDist.project(sum_p);
            valuesForRealizations.put(i, tempDist);
        }
    }

        return valuesForRealizations;
    }

    public DiscreteDistribution step(MDP<Function> mdp, DiscreteDistribution param, Map<Integer, Point> jointMap, int s, int choice, double gamma, double state_reward) {
        TreeMap<Integer, DiscreteDistribution> values = stepSep(mdp, param, jointMap, s, choice, gamma, state_reward);
        TreeMap<Double, Double> aggregatedValues = new TreeMap<>();

        for (DiscreteDistribution dist : values.values()) {
            for (int i = 0; i < dist.getAtoms(); i++) {
                double value = dist.getSupport(i);
                double probability = dist.getValue(i);
                aggregatedValues.put(value, aggregatedValues.getOrDefault(value, 0.0) + probability);
            }
        }

        DiscreteDistribution result;
        if (isCategorical) {
            result = new DistributionCategorical(atoms, v_min, v_max, mainLog);
        } else {
            result = new DistributionQuantile(atoms, mainLog);
        }

        result.project(aggregatedValues);

        return result;
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