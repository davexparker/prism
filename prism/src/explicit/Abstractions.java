package explicit;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import common.Interval;
import parser.ast.Expression;
import parser.ast.ExpressionProb;
import parser.ast.ExpressionTemporal;
import parser.ast.PropertiesFile;
import prism.*;
import strat.MDStrategy;
import strat.MDStrategyArray;
import strat.Strategy;
import strat.StrategyExportOptions;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Predicate;

public class Abstractions
{
    Prism prism = new Prism(new PrismDevNullLog());

    public static class Property
    {
        public String op; // "P" or "R"
        public MinMax minMax; // min or max?
        public BitSet target; // target states for reachability
        public BitSet remain; // constrain states for until (null if not needed)
        @Override
        public String toString()
        {
            String s = op + (minMax.isMin() ? "min" : "max");
            if (target != null) {
                s += ", " + target.cardinality() + " target states";
            }
            if (remain != null) {
                s += ", " + remain.cardinality() + " remain states";
            }
            return s;
        }
    }

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
            String propString = args[1];
            System.out.println("Model file: " + modelFilename);
            System.out.println("Property string: " + propString);

            // Build concrete MDP, parse/extract property, model check
            prism.loadModelFromUMBFile(new File(modelFilename));
            prism.buildModel();
            if (prism.getModelType() != ModelType.MDP) {
                throw new RuntimeException("Concrete model is not an MDP");
            }
            MDP<Double> modelConcrete = (MDP<Double>) prism.getBuiltModelExplicit();
            PropertiesFile propPF = prism.parsePropertiesString(propString);
            Expression propExpr = propPF.getProperty(0);
            Property propConcrete = extractProperty(propExpr, modelConcrete, prism.getModelInfo());
            Result result = prism.modelCheck(propPF, propExpr);
            double valConcrete = (Double) result.getResult();
            System.out.println("\nConcrete MDP: " + modelConcrete.infoString());
            System.out.println("Concrete property: " + propConcrete);
            System.out.println("Concrete result: " + valConcrete);

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
            //System.out.println(nAbstract + " abstract states");

            // Build/solve abstractions
            buildGameAbstraction(modelConcrete, propConcrete, concrete2abstract, nAbstract);
            buildIMDPAbstraction(modelConcrete, propConcrete, concrete2abstract, nAbstract);
            buildGameAbstractionViaRefinement(modelConcrete, propConcrete.target, propConcrete.minMax.isMin(), concrete2abstract, nAbstract);

        } catch (PrismException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }

    }

    /**
     * Extract info from a property, including check which states satisfy target etc.
     */
    private Property extractProperty(Expression propExpr, MDP<Double> model, ModelInfo modelInfo) throws PrismException
    {
        Property prop = new Property();
        StateModelChecker mc = new StateModelChecker(prism);
        if (propExpr instanceof ExpressionProb) {
            prop.op = "P";
            prop.minMax = ((ExpressionProb) propExpr).getRelopBoundInfo(null).getMinMax(model.getModelType());
            Expression pathExpr = ((ExpressionProb) propExpr).getExpression();
            if (pathExpr instanceof ExpressionTemporal && ((ExpressionTemporal) pathExpr).getOperator() == ExpressionTemporal.P_F) {
                Expression targetExpr = ((ExpressionTemporal) ((ExpressionProb) propExpr).getExpression()).getOperand2();
                mc.setModelCheckingInfo(modelInfo, null, null);
                StateValues sv = mc.checkExpression(model, targetExpr, null);
                prop.target = sv.getBitSet();
            } else if (pathExpr instanceof ExpressionTemporal && ((ExpressionTemporal) pathExpr).getOperator() == ExpressionTemporal.P_U) {
                Expression remainExpr = ((ExpressionTemporal) ((ExpressionProb) propExpr).getExpression()).getOperand1();
                Expression targetExpr = ((ExpressionTemporal) ((ExpressionProb) propExpr).getExpression()).getOperand2();
                mc.setModelCheckingInfo(modelInfo, null, null);
                StateValues sv = mc.checkExpression(model, remainExpr, null);
                prop.remain = sv.getBitSet();
                sv = mc.checkExpression(model, targetExpr, null);
                prop.target = sv.getBitSet();
            } else {
                throw new PrismException("Unknown property type: " + pathExpr);
            }
        } else {
            throw new PrismException("Unknown property type: " + propExpr);
        }
        return prop;
    }

    /**
     * Build/solve a game-based abstraction
     * @param modelConcrete The concrete model
     * @param propConcrete The property for the concrete model
     * @param concreteToAbstract Mapping from concrete to abstract states
     * @param nAbstract Number of abstract states
     */
    public void buildGameAbstraction(MDP<Double> modelConcrete, Property propConcrete, int[] concreteToAbstract, int nAbstract) throws PrismException
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
        // Create property to check on abstraction
        Property propAbstract = new Property();
        propAbstract.op = propConcrete.op;
        propAbstract.minMax = new MinMax(propConcrete.minMax);
        propAbstract.target = new BitSet();
        propAbstract.remain = null;
        if (propConcrete.remain != null) {
            propAbstract.remain = new BitSet();
        }

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
        }
        propAbstract.target = overapproximateSet(propConcrete.target::get, concreteToAbstract, nAbstract);
        if (propAbstract.remain != null) {
            propAbstract.remain = underapproximateSet(propConcrete.remain::get, concreteToAbstract, nAbstract);
        }
        System.out.println("\nGame-based abstraction: " + abstraction.infoString());
        System.out.println("Game-based abstraction property: " + propAbstract);

        // Solve abstraction to get bounds
        STPGModelChecker mcStpg =  new STPGModelChecker(prism);
        mcStpg.setGenStrat(true);
        ModelCheckerResult res = mcStpg.computeUntilProbs(abstraction, propAbstract.remain, propAbstract.target, true, propAbstract.minMax.isMin());
        double lb = res.soln[initConcrete];
        MDStrategyArray<Double> lbStrat = (MDStrategyArray<Double>) res.strat;
        res = mcStpg.computeUntilProbs(abstraction, propAbstract.remain, propAbstract.target, false, propAbstract.minMax.isMin());
        double ub = res.soln[initConcrete];
        MDStrategyArray<Double> ubStrat = (MDStrategyArray<Double>) res.strat;
        System.out.println("Bounds from game-based abstraction: [" + lb + ", " + ub + "]");

        // Concretise strategy and solve induced DTMC to get performance of strategy on concrete model
        MDStrategyArray<Double> stratConcrete = concretiseStrategy(lbStrat, concreteToAbstract, numConcreteStates, modelConcrete);
        DTMC<Double> dtmcInduced = (DTMC<Double>) stratConcrete.constructInducedModel(new StrategyExportOptions().setReachOnly(false));
        DTMCModelChecker mcDtmc =  new DTMCModelChecker(prism);
        res = mcDtmc.computeUntilProbs(dtmcInduced, propConcrete.remain, propConcrete.target);
        double strat1perf = res.soln[initConcrete];
        System.out.println("Performance of strategy on concrete model: " + strat1perf);
    }

    /**
     * Build/solve an IMDP-based abstraction
     * @param modelConcrete The concrete model
     * @param propConcrete The property for the concrete model
     * @param concreteToAbstract Mapping from concrete to abstract states
     * @param nAbstract Number of abstract states
     */
    public void buildIMDPAbstraction(MDP<Double> modelConcrete, Property propConcrete, int[] concreteToAbstract, int nAbstract) throws PrismException
    {
        int numConcreteStates = modelConcrete.getNumStates();
        if (numConcreteStates != concreteToAbstract.length) {
            throw new PrismException("Length of concreteToAbstract array does not match number of concrete states");
        }
        if (modelConcrete.getNumInitialStates() > 1) {
            throw new PrismException("Concrete model should have a single initial state");
        }
        int initConcrete = modelConcrete.getFirstInitialState();
        if (!modelConcrete.areAllChoiceActionsUnique()) {
            throw new PrismException("Concrete model should have distinct choice actions in all states");
        }

        // Create empty abstraction
        IMDPSimple<Double> abstraction = new IMDPSimple<>(nAbstract);
        // Temporary storage for IMDP transitions
        List<Map<Object, Distribution<Interval<Double>>>> abstractionData = new ArrayList<>(nAbstract);
        for (int a = 0; a < nAbstract; a++) {
            abstractionData.add(new HashMap<>());
        }
        // Create property to check on abstraction
        Property propAbstract = new Property();
        propAbstract.op = propConcrete.op;
        propAbstract.minMax = new MinMax(propConcrete.minMax);
        propAbstract.target = new BitSet();
        propAbstract.remain = null;
        if (propConcrete.remain != null) {
            propAbstract.remain = new BitSet();
        }

        // Process each concrete state
        for (int c = 0; c < numConcreteStates; c++) {
            int a = concreteToAbstract[c];
            // Check that available actions match
            if (!abstractionData.get(a).isEmpty()) {
                int numChoices = abstractionData.get(a).size();
                if (numChoices != modelConcrete.getNumChoices(c)) {
                    throw new PrismException("Concrete state " + c + " does not match number of choices");
                }
                HashSet<Object> actionsAbstract = new HashSet<>(abstractionData.get(a).keySet());
                HashSet<Object> actionsConcrete = new HashSet<>();
                for (int i = 0; i < numChoices; i++) {
                    actionsConcrete.add(modelConcrete.getAction(c, i));
                }
                if (!actionsAbstract.equals(actionsConcrete)) {
                    throw new PrismException("Concrete state " + c + " does not match actions");
                }
            }
            // Lift distributions to abstract states, add to abstraction
            int numChoices = modelConcrete.getNumChoices(c);
            for (int i = 0; i < numChoices; i++) {
                Object action = modelConcrete.getAction(c, i);
                // Get storage for IMDP transitions for this state-action (create if missing)
                Distribution<Interval<Double>> idistrAbstract = abstractionData.get(a).get(action);
                if (idistrAbstract == null) {
                    // Lift distribution to abstract states, make new point interval distribution
                    Distribution<Interval<Double>> idistrAbstractNew = new Distribution<>(Evaluator.forDoubleInterval());
                    modelConcrete.forEachTransition(c, i, (s, t, d) -> {
                        int aSucc = concreteToAbstract[t];
                        idistrAbstractNew.add(aSucc, new Interval<>(d, d));
                    });
                    abstractionData.get(a).put(action, idistrAbstractNew);
                } else {
                    // Lift distribution to abstract states
                    Distribution<Double> distrAbstractNew = Distribution.ofDouble();
                    modelConcrete.forEachTransition(c, i, (s, t, d) -> {
                        int aSucc = concreteToAbstract[t];
                        distrAbstractNew.add(aSucc, d);
                    });
                    // Merge distributions (take min/max of probabilities for each abstract successor)
                    Set<Integer> combinedSupport = new HashSet<>(idistrAbstract.getSupport());
                    combinedSupport.addAll(distrAbstractNew.getSupport());
                    Distribution<Interval<Double>> idistrAbstractNew = new Distribution<>(Evaluator.forDoubleInterval());
                    for (int aSucc : combinedSupport) {
                        Interval<Double> ival = idistrAbstract.get(aSucc);
                        double pNew = distrAbstractNew.get(aSucc);
                        idistrAbstractNew.set(aSucc, new Interval<>(Double.min(ival.getLower(), pNew), Double.max(ival.getUpper(), pNew)));
                    }
                    abstractionData.get(a).put(action, idistrAbstractNew);
                }
            }

            // Update initial/target abstract states
            if (modelConcrete.isInitialState(c)) {
                abstraction.addInitialState(a);
            }
        }
        propAbstract.target = overapproximateSet(propConcrete.target::get, concreteToAbstract, nAbstract);
        if (propAbstract.remain != null) {
            propAbstract.remain = underapproximateSet(propConcrete.remain::get, concreteToAbstract, nAbstract);
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
        System.out.println("\nIMDP-based abstraction: " + abstraction.infoString());
        System.out.println("IMDP-based abstraction property: " + propAbstract);

        // Print abstraction
       /* for (int a = 0; a < nAbstract; a++) {
            System.out.print(a);
            int numChoices = abstraction.getNumChoices(a);
            for (int i = 0; i < numChoices; i++) {
                System.out.print(" " + abstraction.getAction(a, i) + ":");
                System.out.print(((IMDPSimple<Double>) abstraction).mdp.trans.get(a).get(i));
            }
            System.out.println();
            for (int c = 0; c < numConcreteStates; c++) {
                if (concreteToAbstract[c] == a) {
                    System.out.print("  " + c);
                    for (int i = 0; i < numChoices; i++) {
                        System.out.print(" " + modelConcrete.getAction(c, i) + ":{");
                        modelConcrete.getTransitionsIterator(c, i).forEachRemaining(e -> {
                           System.out.print(" " + e.getKey() + "=" + e.getValue());
                        });
                        System.out.print(" }");
                    }
                    System.out.println();
                }
            }
        }*/

        // Solve abstraction to get bounds
        IMDPModelChecker mcImdp =  new IMDPModelChecker(prism);
        mcImdp.setGenStrat(true);
        mcImdp.setProb1(false);
        ModelCheckerResult res = mcImdp.computeUntilProbs(abstraction, propAbstract.remain, propAbstract.target, new MinMax(propAbstract.minMax).setMinUnc(true));
        double lb = res.soln[initConcrete];
        MDStrategyArray<Double> lbStrat = (MDStrategyArray<Double>) res.strat;
        res = mcImdp.computeUntilProbs(abstraction, propAbstract.remain, propAbstract.target, new MinMax(propAbstract.minMax).setMinUnc(false));
        double ub = res.soln[initConcrete];
        MDStrategyArray<Double> ubStrat = (MDStrategyArray<Double>) res.strat;
        System.out.println("Bounds from IMDP-based abstraction: [" + lb + ", " + ub + "]");

        // Analyse accuracy of policy in induced IDTMC
        IDTMC<Double> idtmcInduced = (IDTMC<Double>) lbStrat.constructInducedModel(new StrategyExportOptions().setReachOnly(false));
        IDTMCModelChecker mcIdtmc =  new IDTMCModelChecker(prism);
        mcIdtmc.setProb1(false);
        res = mcIdtmc.computeUntilProbs(idtmcInduced, propAbstract.remain, propAbstract.target, new MinMax().setMinUnc(true));
        double strat1lb = res.soln[initConcrete];
        res = mcIdtmc.computeReachProbs(idtmcInduced, propAbstract.remain, propAbstract.target, new MinMax().setMinUnc(false));
        double strat1ub = res.soln[initConcrete];
        System.out.println("Bounds from induced IDTMC abstraction: [" + strat1lb + ", " + strat1ub + "]");

        // Concretise strategy and solve induced DTMC to get performance of strategy on concrete model
        MDStrategyArray<Double> stratConcrete = concretiseStrategy(lbStrat, concreteToAbstract, numConcreteStates, modelConcrete);
        DTMC<Double> dtmcInduced = (DTMC<Double>) stratConcrete.constructInducedModel(new StrategyExportOptions().setReachOnly(false));
        DTMCModelChecker mcDtmc =  new DTMCModelChecker(prism);
        res = mcDtmc.computeUntilProbs(dtmcInduced, propConcrete.remain, propConcrete.target);
        double strat1perf = res.soln[initConcrete];
        System.out.println("Performance of strategy on concrete model: " + strat1perf);
    }

    public BitSet overapproximateSet(Predicate<Integer> setConcrete, int[] concreteToAbstract, int nAbstract)
    {
        BitSet setAbstract = new BitSet(nAbstract);
        for (int c = 0; c < concreteToAbstract.length; c++) {
            if (setConcrete.test(c)) {
                setAbstract.set(concreteToAbstract[c]);
            }
        }
        return setAbstract;
    }

    public BitSet underapproximateSet(Predicate<Integer> setConcrete, int[] concreteToAbstract, int nAbstract)
    {
        BitSet setAbstract = new BitSet(nAbstract);
        for (int a = 0; a < nAbstract; a++) {
            setAbstract.set(a);
        }
        for (int c = 0; c < concreteToAbstract.length; c++) {
            if (!setConcrete.test(c)) {
                setAbstract.set(concreteToAbstract[c], false);
            }
        }
        return setAbstract;
    }

    public MDStrategyArray<Double> concretiseStrategy(MDStrategy<Double> stratAbstract, int[] concreteToAbstract, int numConcreteStates, NondetModel<Double> modelConcrete)
    {
        // Concretise strategy and solve induced DTMC to get performance of strategy on concrete model
        int[] stratArrayConcrete = new int[numConcreteStates];
        for (int c = 0; c < numConcreteStates; c++) {
            stratArrayConcrete[c] = modelConcrete.getChoiceByAction(c, stratAbstract.getChoiceAction(concreteToAbstract[c]));
        }
        return new MDStrategyArray<>(modelConcrete, stratArrayConcrete);
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
