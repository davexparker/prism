package explicit;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import common.Interval;
import parser.ast.PropertiesFile;
import prism.*;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

public class Abstractions
{
    Prism prism = new Prism(new PrismDevNullLog());

    /**
     * @param args: [0] filename of UMB file for concrete model, [1] expression for target of reachability, [2] abstract states filename
     */
    public void run(String[] args)
    {
        try {
            if (args.length != 3) {
                throw new RuntimeException("Usage: [0] filename of UMB file for concrete model, [1] expression for target of reachability, [2] abstract states filename");
            }

            // Initialise PRISM
            prism.initialise();
            prism.setEngine(Prism.EXPLICIT);

            // Concrete model details
            String modelFilename = args[0];
            String targetExpression = args[1];
            boolean min = false; // Pmin

            // Build, model check concrete model
            prism.loadModelFromUMBFile(new File(modelFilename));
            prism.buildModel();
            Result result = prism.modelCheck("P" + (min?"min":"max") + "=? [ F " + targetExpression + " ];");
            MDP<Double> model = (MDP<Double>) prism.getBuiltModelExplicit();
            System.out.println("Concrete model: " + model.infoString());
            System.out.println("Concrete model value: " + result.getResult());

            // Get set of target states
            StateModelChecker mc = new StateModelChecker(prism);
            PropertiesFile pf = prism.parsePropertiesString(targetExpression);
            mc.setModelCheckingInfo(prism.getModelInfo(), null, null);
            StateValues sv = mc.checkExpression(model, pf.getProperty(0), null);
            BitSet target = sv.getBitSet();

            // Load abstraction info
            String c2aJsonFilename = args[2];
            Map<String, Integer> c2aJsonMap;
            Type type = new TypeToken<Map<String, Integer>>(){}.getType();
            try (FileReader reader = new FileReader(c2aJsonFilename)) {
                c2aJsonMap = new Gson().fromJson(reader, type);
            } catch (IOException e) {
                throw new PrismException("File error: " + e.getMessage());
            }
            int[] concrete2abstract = new int[c2aJsonMap.size()];
            c2aJsonMap.forEach((key, value) -> concrete2abstract[Integer.parseInt(key)] = value);
            int nAbstract = c2aJsonMap.values().stream().max(Integer::compareTo).orElseThrow(() -> new PrismException("Empty map")) + 1;
            System.out.println(nAbstract + " abstract states");

            // Build/solve abstractions
            buildGameAbstraction(model, target, false, concrete2abstract, nAbstract);
            buildIMDPAbstraction(model, target, false, concrete2abstract, nAbstract);
            buildGameAbstractionViaRefinement(model, target, false, concrete2abstract, nAbstract);

        } catch (PrismException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }

    }

    /**
     * Build/solve a game-based abstraction
     * @param modelConcrete The concrete model
     * @param targetConcrete target states in concrete model (for probabilistic reachability)
     * @param min Min/max for probabilistic reachability?
     * @param concreteToAbstract Mapping from concrete to abstract states
     * @param nAbstract Number of abstract states
     */
    public void buildGameAbstraction(MDP<Double> modelConcrete, BitSet targetConcrete, boolean min, int[] concreteToAbstract, int nAbstract) throws PrismException
    {
        int numConcreteStates = modelConcrete.getNumStates();
        if (numConcreteStates != concreteToAbstract.length) {
            throw new PrismException("Length of concreteToAbstract array does not match number of concrete states");
        }
        if (modelConcrete.getNumInitialStates() > 1) {
            throw new PrismException("Concrete model should have a single initial state");
        }
        int initConcrete = modelConcrete.getFirstInitialState();

        // Create empty abstraction
        STPGAbstrSimple<Double> abstraction = new STPGAbstrSimple<>(nAbstract);
        List<List<Set<Integer>>> abstractToConcrete = new ArrayList<>();
        for (int a = 0; a < nAbstract; a++) {
            abstractToConcrete.add(new ArrayList<Set<Integer>>());
        }
        BitSet targetAbstract = new BitSet();

        // Process each concrete state
        for (int c = 0; c < numConcreteStates; c++) {
            int a = concreteToAbstract[c];

            // Lift distributions to abstract states, add to abstraction
            DistributionSet<Double> distrsAbstract = abstraction.newDistributionSet(null);
            int numChoices = modelConcrete.getNumChoices(c);
            for (int i = 0; i < numChoices; i++) {
                Distribution<Double> distrAbstract = Distribution.ofDouble();
                modelConcrete.forEachTransition(c, i, (s, t, d) -> {
                    distrAbstract.add(concreteToAbstract[t], d);
                });
                distrsAbstract.add(distrAbstract);
            }
            int j = abstraction.addDistributionSet(a, distrsAbstract);

            // Store abstract->concrete info
            // (not really needed at the moment, more for refinement)
            List<Set<Integer>> list = abstractToConcrete.get(a);
            if (j >= list.size()) {
                list.add(new HashSet<>(1));
            }
            list.get(j).add(c);

            // Update initial/target abstract states
            if (modelConcrete.isInitialState(c)) {
                abstraction.addInitialState(a);
            }
            if (targetConcrete.get(c)) {
                targetAbstract.set(a);
            }
        }
        System.out.println("Game-based abstraction: " + abstraction.infoString());

        // Solve abstraction to get bounds
        STPGModelChecker mc =  new STPGModelChecker(prism);
        double lb = mc.computeReachProbs(abstraction, targetAbstract, true, min).soln[initConcrete];
        double ub = mc.computeReachProbs(abstraction, targetAbstract, false, min).soln[initConcrete];
        System.out.println("Bounds from game-based abstraction: [" + lb + ", " + ub + "]");
    }

    /**
     * Build/solve an IMDP-based abstraction
     * @param modelConcrete The concrete model
     * @param targetConcrete target states in concrete model (for probabilistic reachability)
     * @param min Min/max for probabilistic reachability?
     * @param concreteToAbstract Mapping from concrete to abstract states
     * @param nAbstract Number of abstract states
     */
    public void buildIMDPAbstraction(MDP<Double> modelConcrete, BitSet targetConcrete, boolean min, int[] concreteToAbstract, int nAbstract) throws PrismException
    {
        int numConcreteStates = modelConcrete.getNumStates();
        if (numConcreteStates != concreteToAbstract.length) {
            throw new PrismException("Length of concreteToAbstract array does not match number of concrete states");
        }
        if (modelConcrete.getNumInitialStates() > 1) {
            throw new PrismException("Concrete model should have a single initial state");
        }
        int initConcrete = modelConcrete.getFirstInitialState();

        // Create empty abstraction
        IMDPSimple<Double> abstraction = new IMDPSimple<>(nAbstract);
        BitSet targetAbstract = new BitSet();
        // Temporary storage for IMDP transitions
        List<Map<Object, Distribution<Interval<Double>>>> abstractionData = new ArrayList<>(nAbstract);
        for (int a = 0; a < nAbstract; a++) {
            abstractionData.add(new HashMap<>());
        }

        // Process each concrete state
        for (int c = 0; c < numConcreteStates; c++) {
            int a = concreteToAbstract[c];

            // Lift distributions to abstract states, add to abstraction
            int numChoices = modelConcrete.getNumChoices(c);
            for (int i = 0; i < numChoices; i++) {
                Object action = modelConcrete.getAction(a, i);
                // Get storage for IMDP transitions for this state-action (create if missing)
                Distribution<Interval<Double>> idistrAbstract = abstractionData.get(a).computeIfAbsent(action, k -> new Distribution<>(Evaluator.forDoubleInterval()));
                modelConcrete.forEachTransition(c, i, (s, t, d) -> {
                    int aSucc = concreteToAbstract[t];
                    Interval<Double> ival = idistrAbstract.get(aSucc);
                    idistrAbstract.set(aSucc, new Interval<>(Double.min(ival.getLower(), d),  Double.max(ival.getUpper(), d)));
                });
            }

            // Update initial/target abstract states
            if (modelConcrete.isInitialState(c)) {
                abstraction.addInitialState(a);
            }
            if (targetConcrete.get(c)) {
                targetAbstract.set(a);
            }
        }
        // Add transitions to IMDP
        for (int a = 0; a < nAbstract; a++) {
            int finalA = a;
            abstractionData.get(a).entrySet().forEach(e -> {
                Object action = e.getKey();
                Distribution<Interval<Double>> idistr = e.getValue();
                abstraction.addActionLabelledChoice(finalA, idistr, action);
            });
        }
        System.out.println("IMDP-based abstraction: " + abstraction.infoString());

        // Solve abstraction to get bounds
        IMDPModelChecker mc =  new IMDPModelChecker(prism);
        MinMax minMax = new MinMax();
        minMax.setMin(min);
        minMax.setMinUnc(true);
        double lb = mc.computeReachProbs(abstraction, targetAbstract, minMax).soln[initConcrete];
        minMax.setMinUnc(false);
        double ub = mc.computeReachProbs(abstraction, targetAbstract, minMax).soln[initConcrete];
        System.out.println("Bounds from IMDP-based abstraction: [" + lb + ", " + ub + "]");
    }

    /**
     * Build/solve a game-based abstraction
     * @param modelConcrete The concrete model
     * @param targetConcrete target states in concrete model (for probabilistic reachability)
     * @param min Min/max for probabilistic reachability?
     * @param concreteToAbstract Mapping from concrete to abstract states
     * @param nAbstract Number of abstract states
     */
    public void buildGameAbstractionViaRefinement(MDP<Double> modelConcrete, BitSet targetConcrete, boolean min, int[] concreteToAbstract, int nAbstract) throws PrismException
    {
        /*
        PrismSTPGAbstractRefine abstractRefine = new PrismSTPGAbstractRefine(prism);
        abstractRefine.sanityChecks = true;
        abstractRefine.exact = true;
        abstractRefine.exactCheck = true;
        abstractRefine.setModelType(ModelType.MDP);
        abstractRefine.setPropertyType(QuantAbstractRefine.PropertyType.PROB_REACH);
        // abstractRefine.targetLabel = "finished";
        abstractRefine.targetConcrete = targetConcrete;
        String filenameBase = "prism/abstr";
        abstractRefine.traFile = filenameBase + ".tra";
        abstractRefine.labFile = filenameBase + ".lab";
        // abstractRefine.rewsFile = filenameBase + ".rews";
        // abstractRefine.rewtFile = filenameBase + ".rewt";
        abstractRefine.abstractRefine(min);
        */
    }

    /**
     * Run me
     */
    public static void main(String[] args)
    {
        new Abstractions().run(args);
    }
}
