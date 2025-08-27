//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford)
//	* Joachim Klein <klein@tcs.inf.tu-dresden.de> (TU Dresden)
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

package automata;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.*;

import acceptance.AcceptanceBuchi;
import jhoafparser.consumer.HOAIntermediateStoreAndManipulate;
import jhoafparser.parser.HOAFParser;
import jhoafparser.parser.generated.ParseException;
import jhoafparser.transformations.ToStateAcceptance;
import jltl2ba.APSet;
import jltl2ba.SimpleLTL;
import jltl2ba.LTLFragments;
import jltl2dstar.LTL2Rabin;
import owl.automaton.Automaton;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.degeneralization.RabinDegeneralization;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.hoa.HoaWriter;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.translations.LtlTranslationRepository;
import owl.translations.ltl2ldba.AnnotatedLDBA;
import owl.translations.ltl2ldba.AsymmetricLDBAConstruction;
import owl.translations.rabinizer.RabinizerBuilder;
import owl.translations.rabinizer.RabinizerConfiguration;
import parser.Values;
import parser.ast.Expression;
import prism.*;
import acceptance.AcceptanceOmega;
import acceptance.AcceptanceRabin;
import acceptance.AcceptanceType;

import static owl.translations.LtlTranslationRepository.applyPreAndPostProcessing;

/**
 * Infrastructure for constructing deterministic automata for LTL formulas.
 */
public class LTL2DA extends PrismComponent
{

	public LTL2DA(PrismComponent parent) throws PrismException
	{
		super(parent);
	}

	/**
	 * Convert an LTL formula into a deterministic Rabin automaton.
	 * The LTL formula is represented as a PRISM Expression,
	 * in which atomic propositions are represented by ExpressionLabel objects.
	 * @param ltl the formula
	 * @param constantValues the values of constants, may be {@code null}
	 */
	@SuppressWarnings("unchecked")
	public DA<BitSet, AcceptanceRabin> convertLTLFormulaToDRA(Expression ltl, Values constantValues) throws PrismException
	{
		AcceptanceType[] allowedAcceptance = {
				AcceptanceType.BUCHI,
				AcceptanceType.RABIN,
		};
		DA<BitSet, ? extends AcceptanceOmega> da = convertLTLFormulaToDA(ltl, constantValues, allowedAcceptance);
		if (da.getAcceptance() instanceof AcceptanceBuchi) {
			((DA<BitSet, AcceptanceRabin>) da).setAcceptance(((AcceptanceBuchi) da.getAcceptance()).toRabin());
		}
		return (DA<BitSet, AcceptanceRabin>) da;
	}

	/**
	 * Convert an LTL formula into a deterministic automaton.
	 * The LTL formula is represented as a PRISM Expression,
	 * in which atomic propositions are represented by ExpressionLabel objects.
	 * @param ltl the formula
	 * @param constants the values of constants, may be {@code null}
	 * @param allowedAcceptance the AcceptanceTypes that are allowed to be returned
	 */
	public DA<BitSet, ? extends AcceptanceOmega> convertLTLFormulaToDA(Expression ltl, Values constants, AcceptanceType... allowedAcceptance)
			throws PrismException
	{
		boolean useExternal = useExternal();
		boolean containsTemporalBounds = Expression.containsTemporalTimeBounds(ltl);
		DA<BitSet, ? extends AcceptanceOmega> da;

		// First (unless we are using an external tool), check whether the library can provide a DA
		if (!useExternal || containsTemporalBounds) {
			try {
				return convertLTLFormulaToDAWithLibrary(ltl, constants, allowedAcceptance);
			} catch (PrismException e) {
				// Fail silently, try something else
			}
		}

		// There is (currently) no other way to translate LTL with temporal bounds,
		if (containsTemporalBounds) {
			throw new PrismNotSupportedException("Could not convert LTL formula to deterministic automaton, formula had time-bounds");
		}

		// Use either external tool or built-in conversion
		if (useExternal) {
			da = convertLTLFormulaToDAWithExternalTool(ltl, constants, allowedAcceptance);
		} else {
			//da = convertLTLFormulaToDAWithBuiltIn(ltl, constants, allowedAcceptance);
			da = convertLTLFormulaToDAWithOwl(ltl, constants, allowedAcceptance);
		}
		return da;
	}

	/**
	 * Convert an LTL formula into a limit deterministic Buchi automaton.
	 * The LTL formula is represented as a PRISM Expression,
	 * in which atomic propositions are represented by ExpressionLabel objects.
	 * @param ltl the formula
	 * @param constants the values of constants, may be {@code null}
	 */
	public DA<BitSet, ? extends AcceptanceOmega> convertLTLFormulaToLDBA(Expression ltl, Values constants) throws PrismException
	{
		boolean containsTemporalBounds = Expression.containsTemporalTimeBounds(ltl);
		if (containsTemporalBounds) {
			throw new PrismNotSupportedException("Could not convert LTL formula to deterministic automaton, formula had time-bounds");
		}
		DA<BitSet, ? extends AcceptanceOmega> da = convertLTLFormulaToLDBAWithOwl(ltl, constants);
		return da;
	}

	/**
	 * Convert an LTL formula into a DA using a specified conversion process.
	 * Perform any requested simplifications of the acceptance condition
	 * and check that the acceptance condition is suitable.
	 */
	public DA<BitSet, ? extends AcceptanceOmega> convertLTLFormulaToDA(LTL2DAProcess l2ltda, Expression ltl, Values constants, AcceptanceType... allowedAcceptance) throws PrismException
	{
		// Do the LTL-to-DA conversion
		DA<BitSet, ? extends AcceptanceOmega> da = l2ltda.convert(ltl, constants, allowedAcceptance);
		// Simplify the acceptance condition if requested
		if (!getSettings().getBoolean(PrismSettings.PRISM_NO_DA_SIMPLIFY)) {
			da = DASimplifyAcceptance.simplifyAcceptance(this, da, allowedAcceptance);
		}
		// Check the acceptance condition is correct
		AcceptanceOmega acceptance = da.getAcceptance();
		if (AcceptanceType.contains(allowedAcceptance, acceptance.getType())) {
			return da;
		} else if (AcceptanceType.contains(allowedAcceptance, AcceptanceType.GENERIC)) {
			// The specific acceptance type is not allowed, but GENERIC is allowed
			//   -> transform to generic acceptance and switch acceptance condition
			DA.switchAcceptance(da, acceptance.toAcceptanceGeneric());
			return da;
		} else {
			throw new PrismException("Generated DA had " + acceptance.getType() + " acceptance, which is not suitable");
		}
	}

	// Convenience functions for calling specific LTL-to-DA conversion processes

	/**
	 * LTL-to-DA conversion via an external tool.
	 */
	public DA<BitSet, ? extends AcceptanceOmega> convertLTLFormulaToDAWithLibrary(Expression ltl, Values constants, AcceptanceType... allowedAcceptance) throws PrismException
	{
		return convertLTLFormulaToDA(new ConvertLTLFormulaToDAWithLibrary(), ltl, constants, allowedAcceptance);
	}

	/**
	 * LTL-to-DA conversion via built-in.
	 */
	public DA<BitSet, ? extends AcceptanceOmega> convertLTLFormulaToDAWithBuiltIn(Expression ltl, Values constants, AcceptanceType... allowedAcceptance) throws PrismException
	{
		return convertLTLFormulaToDA(new ConvertLTLFormulaToDAWithBuiltIn(), ltl, constants, allowedAcceptance);
	}

	/**
	 * LTL-to-DA conversion via Owl.
	 */
	public DA<BitSet, ? extends AcceptanceOmega> convertLTLFormulaToDAWithOwl(Expression ltl, Values constants, AcceptanceType... allowedAcceptance) throws PrismException
	{
		return convertLTLFormulaToDA(new ConvertLTLFormulaToDAWithOwl(), ltl, constants, allowedAcceptance);
	}

	/**
	 * LTL-to-LDBA conversion via Owl.
	 */
	public DA<BitSet, ? extends AcceptanceOmega> convertLTLFormulaToLDBAWithOwl(Expression ltl, Values constants) throws PrismException
	{
		return convertLTLFormulaToDA(new ConvertLTLFormulaToLDBAWithOwl(), ltl, constants, AcceptanceType.BUCHI);
	}

	/**
	 * LTL-to-DA conversion via an external tool.
	 */
	public DA<BitSet, ? extends AcceptanceOmega> convertLTLFormulaToDAWithExternalTool(Expression ltl, Values constants, AcceptanceType... allowedAcceptance) throws PrismException
	{
		return convertLTLFormulaToDA(new ConvertLTLFormulaToDAWithExternalTool(), ltl, constants, allowedAcceptance);
	}

	// Various LTL-to-DA conversion processes

	/**
	 * Interface for LTL-to-DA conversion processes.
	 */
	public interface LTL2DAProcess
	{
		/**
		 * Convert an LTL formula into a DA of one of the specified acceptance types.
		 * Throws an exception if the conversion fails.
		 */
		DA<BitSet, ? extends AcceptanceOmega> convert(Expression ltl, Values constants, AcceptanceType... allowedAcceptance) throws PrismException;
	}

	/**
	 * LTL-to-DA conversion using the built-in library
	 */
	private class ConvertLTLFormulaToDAWithLibrary implements LTL2DAProcess
	{
		public DA<BitSet, ? extends AcceptanceOmega> convert(Expression ltl, Values constants, AcceptanceType... allowedAcceptance) throws PrismException
		{
			DA<BitSet, ? extends AcceptanceOmega> da = LTL2RabinLibrary.getDAforLTL(ltl, constants, allowedAcceptance);
			if (da != null) {
				mainLog.println("Taking " + da.getAutomataType()+" from library...");
				return da;
			} else {
				throw new PrismException("DA not found in library");
			}
		}
	}

	/**
	 * LTL-to-DA conversion using the built-in converters
	 */
	private class ConvertLTLFormulaToDAWithBuiltIn implements LTL2DAProcess
	{
		public DA<BitSet, ? extends AcceptanceOmega> convert(Expression ltl, Values constants, AcceptanceType... allowedAcceptance) throws PrismException
		{
			SimpleLTL simpleLTL = ltl.convertForJltl2ba();
			DA<BitSet, ? extends AcceptanceOmega> da = null;
			// Don't use LTL2WDBA translation yet
			boolean allowLTL2WDBA = false;
			if (allowLTL2WDBA) {
				LTLFragments fragments = LTLFragments.analyse(simpleLTL);
				mainLog.println(fragments);
				if (fragments.isSyntacticGuarantee() && AcceptanceType.contains(allowedAcceptance, AcceptanceType.REACH)) {
					// A co-safety property
					mainLog.println("Generating DFA for co-safety property...");
					LTL2WDBA ltl2wdba = new LTL2WDBA(LTL2DA.this);
					da = ltl2wdba.cosafeltl2dfa(simpleLTL);
				} else if (allowLTL2WDBA && fragments.isSyntacticObligation() && AcceptanceType.contains(allowedAcceptance, AcceptanceType.BUCHI)) {
					// An obligation property
					mainLog.println("Generating DBA for obligation property...");
					LTL2WDBA ltl2wdba = new LTL2WDBA(LTL2DA.this);
					da = ltl2wdba.obligation2wdba(simpleLTL);
				}
			}
			if (da == null) {
				// Use jltl2dstar LTL2DA
				da = LTL2Rabin.ltl2da(simpleLTL, allowedAcceptance);
			}
			if (da != null) {
				return da;
			} else {
				throw new PrismException("Built-in converters failed to build DA");
			}
		}
	}

	/**
	 * LTL-to-DA conversion via Owl
	 */
	private class ConvertLTLFormulaToDAWithOwl implements LTL2DAProcess
	{
		public DA<BitSet, ? extends AcceptanceOmega> convert(Expression ltl, Values constants, AcceptanceType... allowedAcceptance) throws PrismException
		{
			// Convert LTL formula to required format
			SimpleLTL ltlFormula = prepareLTLFormulaForExternalTool(ltl);
			String ltlString = convertLTLToExternalSyntax(ltlFormula, "Spot");

			// Parse and convert with Owl
			LabelledFormula formula = LtlParser.parse(ltlString);
			RabinizerConfiguration config = RabinizerConfiguration.of(true, true, true);
			Automaton<?, ? extends GeneralizedRabinAcceptance> dgra = RabinizerBuilder.build(formula, config);
			dgra = OmegaAcceptanceCast.cast(AcceptanceOptimizations.transform(dgra), GeneralizedRabinAcceptance.class);
			Automaton<?, ? extends RabinAcceptance> dra = RabinDegeneralization.degeneralize(AcceptanceOptimizations.transform(dgra));
			dra = OmegaAcceptanceCast.cast(AcceptanceOptimizations.transform(dra), RabinAcceptance.class);
			dra = OmegaAcceptanceCast.cast(Views.complete(dra), RabinAcceptance.class);
			String hoaString = HoaWriter.toString(dra);

			// Extract result and convert HOA
			DA<BitSet, ? extends AcceptanceOmega> da = constructDAFromHOA(hoaString);
			checkAPs(ltlFormula, da.getAPList());
			revertDAForExternalTool(da);

			return da;
		}
	}

	/**
	 * LTL-to-LDBA conversion via Owl
	 */
	private class ConvertLTLFormulaToLDBAWithOwl implements LTL2DAProcess
	{
		public DA<BitSet, ? extends AcceptanceOmega> convert(Expression ltl, Values constants, AcceptanceType... allowedAcceptance) throws PrismException
		{
			// Convert LTL formula to required format
			SimpleLTL ltlFormula = prepareLTLFormulaForExternalTool(ltl);
			String ltlString = convertLTLToExternalSyntax(ltlFormula, "Spot");

			// Parse and convert with Owl
			LabelledFormula formula = LtlParser.parse(ltlString);
			Set<LtlTranslationRepository.Option> translationOptions = new HashSet<>();
			translationOptions.add(LtlTranslationRepository.Option.SIMPLIFY_AUTOMATON);
			translationOptions.add(LtlTranslationRepository.Option.SIMPLIFY_FORMULA);
			translationOptions.add(LtlTranslationRepository.Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS);
			translationOptions.add(LtlTranslationRepository.Option.COMPLETE);
			Automaton<?, ? extends BuchiAcceptance> ldba = applyPreAndPostProcessing(AsymmetricLDBAConstruction.of(BuchiAcceptance.class).andThen(AnnotatedLDBA::copyAsMutable),
					LtlTranslationRepository.BranchingMode.DETERMINISTIC,
					translationOptions,
					BuchiAcceptance.class)
					.apply(formula);
			String hoaString = HoaWriter.toString(ldba);

			// Extract result and convert HOA
			DA<BitSet, ? extends AcceptanceOmega> da = constructDAFromHOA(hoaString);
			checkAPs(ltlFormula, da.getAPList());
			revertDAForExternalTool(da);

			return da;
		}
	}

	/**
	 * LTL-to-DA conversion via an external tool.
	 */
	private class ConvertLTLFormulaToDAWithExternalTool implements LTL2DAProcess
	{
		public DA<BitSet, ? extends AcceptanceOmega> convert(Expression ltl, Values constants, AcceptanceType... allowedAcceptance) throws PrismException
		{
			File ltl_file = null;
			File da_file = null;
			File tool_output = null;
			try {
				// Convert LTL formula to required format
				SimpleLTL ltlFormula = prepareLTLFormulaForExternalTool(ltl);
				String syntax = getSettings().getString(PrismSettings.PRISM_LTL2DA_SYNTAX);
				String ltlString = convertLTLToExternalSyntax(ltlFormula, syntax);

				// Create temporary files for communicating with the external tool; write input
				ltl_file = File.createTempFile("prism-ltl-external-", ".ltl", null);
				da_file = File.createTempFile("prism-ltl-external-", ".hoa", null);
				tool_output = File.createTempFile("prism-ltl-external-", ".output", null);
				FileWriter ltlWriter = new FileWriter(ltl_file);
				ltlWriter.write(ltlString);
				ltlWriter.close();

				// Set up call to external tool
				String ltl2daTool = getSettings().getString(PrismSettings.PRISM_LTL2DA_TOOL);
				mainLog.println("Calling external LTL->DA tool: " + ltl2daTool);
				List<String> arguments = new ArrayList<>();
				arguments.add(ltl2daTool);
				//mainLog.print("LTL formula (in " + syntax + " syntax): " + ltlString);
				arguments.add(ltl_file.getAbsolutePath());
				arguments.add(da_file.getAbsolutePath());

				// Execute call to external tool
				// If we are running under the Nailgun environment, setup the
				// environment to include the environment variables of the Nailgun client
				ProcessBuilder builder = new ProcessBuilder(arguments);
				builder.redirectOutput(tool_output);
				builder.redirectErrorStream(true);
				PrismNG.setupChildProcessEnvironment(builder);
				Process p = builder.start();
				p.getInputStream().close();
				int rv;
				while (true) {
					try {
						rv = p.waitFor();
						break;
					} catch (InterruptedException e) {
					}
				}
				if (rv != 0) {
					throw new PrismException("Tool return value=" + rv);
				}

				// Extract result and convert HOA
				DA<BitSet, ? extends AcceptanceOmega> da = constructDAFromHOA(da_file);
				checkAPs(ltlFormula, da.getAPList());
				revertDAForExternalTool(da);

				// Tidy up
				tool_output.delete();
				da_file.delete();
				ltl_file.delete();

				return da;

			} catch (IOException | PrismException e) {
				// In case of error, print temporary file info for debugging
				mainLog.println("LTL formula: " + (ltl_file == null ? "?" : ltl_file.getAbsolutePath()));
				mainLog.println("Automaton output: " + (da_file == null ? "?" : da_file.getAbsolutePath()));
				mainLog.println("Tool output (stdout and stderr): " + (tool_output == null ? "?" : tool_output.getAbsolutePath()));
				throw new PrismException("External LTL->DA tool failed: " + e.getMessage());
			}
		}
	}

	// Helper functions for LTL-to-DA conversion processes

	/**
	 * Prepare an LTL formula for use in an external tool/library,
	 * by switching APs L0, L1, etc. t the safer p0, p1, etc.
	 */
	private SimpleLTL prepareLTLFormulaForExternalTool(Expression ltl) throws PrismException
	{
		SimpleLTL ltlFormula = ltl.convertForJltl2ba();
		SimpleLTL ltlFormulaSafeAP = ltlFormula.clone();
		ltlFormulaSafeAP.renameAP("L", "p");
		return ltlFormulaSafeAP;
	}

	/**
	 * Convert an LTL formula into the syntax of an external tool/library.
	 * {@code syntax} is one of: "LBT", "Spin", "Spot", "Rabinizer".
	 */
	private String convertLTLToExternalSyntax(SimpleLTL ltlFormula, String syntax) throws PrismException
	{
		if (syntax == null) {
			syntax = "";
		}
		String ltlString;
		switch (syntax) {
			case "LBT":
				ltlString = ltlFormula.toStringLBT();
				break;
			case "Spin":
				ltlString = ltlFormula.toStringSpin();
				break;
			case "Spot":
				ltlString = ltlFormula.toStringSpot();
				break;
			case "Rabinizer":
				ltlFormula = ltlFormula.toBasicOperators();
				ltlString = ltlFormula.toStringSpot();
				break;
			default:
				throw new PrismException("Unknown LTL syntax option \"" + syntax + "\"");
		}
		return ltlString;
	}

	/**
	 * Construct a DA by parsing an HOA file, represented as a string.
	 */
	private DA<BitSet, ? extends AcceptanceOmega> constructDAFromHOA(String hoaString) throws PrismException
	{
		return constructDAFromHOA(() -> new ByteArrayInputStream(hoaString.getBytes()), "HOA string");
	}

	/**
	 * Construct a DA by parsing an HOA file.
	 */
	private DA<BitSet, ? extends AcceptanceOmega> constructDAFromHOA(File hoaFile) throws PrismException
	{
		return constructDAFromHOA(() -> Files.newInputStream(hoaFile.toPath()), "HOA file " + hoaFile.getAbsoluteFile());
	}

	interface HOAStreamSupplier { InputStream get() throws IOException; }


    /**
     * Precheck that works on raw HOA and scans states for nondeterminism.
     */
    private static boolean isHOADeterministic(String hoaText) {
        Map<Integer, Map<String, Integer>> seenPerState = new HashMap<>();
        int curState = -1;
		boolean trueEdge = false;

        try (BufferedReader br = new BufferedReader(new StringReader(hoaText))) {
            for (String line; (line = br.readLine()) != null; ) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("--")) continue;

                if (line.startsWith("properties:") && line.contains("deterministic")) {
                    return true; // fast path from header
                }

                if (line.startsWith("State:")) {
                    String[] parts = line.split("\\s+");
                    curState = Integer.parseInt(parts[1]);
                    seenPerState.putIfAbsent(curState, new HashMap<>());
					trueEdge = false;
                    continue;
                }

                if (!line.startsWith("[")) continue;

                int rb = line.indexOf(']');
                if (rb < 0) continue;

				// Assuming DNF here...
                String[] label = line.substring(0, rb + 1).replaceAll("\\s+", "").replaceAll("\\[", "").replaceAll("]", "").replaceAll("\\)", "").replaceAll("\\(", "").split("\\|");
                String rest = line.substring(rb + 1).trim();
                String[] parts = rest.split("\\s+");
                if (parts.length == 0) continue;

                int dest;
                try { dest = Integer.parseInt(parts[0]); }
                catch (NumberFormatException ignore) { continue; }

				// label --> state mapping
                Map<String, Integer> map = seenPerState.get(curState);

				// early exits involving [t]
				if (Arrays.asList(label).contains("t")) {
					trueEdge = true;
				}

				for (String l : label) {
					Integer prev = map.putIfAbsent(l, dest);

					if (prev != null && prev != dest) {
						return false; // same label, different destination
					}
				}

				if (trueEdge && map.size() > 1) {
					return false; // true edge and something else
				}
            }
        } catch (IOException ignore) { }

        return true;
    }

    /**
	 * Construct a DA by parsing an HOA file, supplied as an InputStream.
	 * The InputStream may need to be recreated multiple times in case of failure.
	 */
	private DA<BitSet, ? extends AcceptanceOmega> constructDAFromHOA(HOAStreamSupplier hoaStreamSupplier, String hoaSourceDescription) throws PrismException
	{
		DA<BitSet, ? extends AcceptanceOmega> da;
        final String hoa;

		try {
            // Parse the HOA and convert to Automaton!
            InputStream in = hoaStreamSupplier.get();
            hoa = new String(in.readAllBytes());

            boolean det = isHOADeterministic(hoa);

            if (!det) {
                mainLog.println("HOA automaton is nondeterministic.");
            } else {
                mainLog.println("HOA automaton is deterministic.");
            }
            try {
                HOAF2DA consumerDA = new HOAF2DA(det);
                HOAFParser.parseHOA(hoaStreamSupplier.get(), consumerDA);
                da = consumerDA.getDA();
                da.setDeterminism(det);
            } catch (HOAF2DA.TransitionBasedAcceptanceException e) {
                // Try again, this time transforming to state acceptance
                mainLog.println("Automaton with transition-based acceptance, automatically converting to state-based acceptance...");
                HOAF2DA consumerDA = new HOAF2DA(det);
                HOAIntermediateStoreAndManipulate consumerTransform = new HOAIntermediateStoreAndManipulate(consumerDA, new ToStateAcceptance());
                HOAFParser.parseHOA(hoaStreamSupplier.get(), consumerTransform);
                da = consumerDA.getDA();
                da.setDeterminism(det);
            }
        } catch (IOException e) {
            throw new PrismException("Unable to read " + hoaSourceDescription);
        } catch (ParseException e) {
            throw new PrismException("Parse error: " + e.getMessage() + " reading " + hoaSourceDescription);
        }

		return da;
	}

	/**
	 * Revert the APs in an externally generated DA, i.e., the reverse of
	 * what is done in {@link #prepareLTLFormulaForExternalTool(Expression)}.
	 */
	private void revertDAForExternalTool(DA<BitSet, ? extends AcceptanceOmega> da)
	{
		List<String> automatonAPList = da.getAPList();
		for (int i = 0; i < automatonAPList.size(); i++) {
			if (automatonAPList.get(i).startsWith("p")) {
				String renamed = "L" + automatonAPList.get(i).substring("p".length());
				automatonAPList.set(i, renamed);
			}
		}
	}

	/** Check whether we should use an external LTL->DA tool */
	private boolean useExternal()
	{
		String ltl2da_tool = getSettings().getString(PrismSettings.PRISM_LTL2DA_TOOL);
		if (ltl2da_tool != null && !ltl2da_tool.isEmpty()) {
			return true;
		}
		return false;
	}

	/** Check the atomic propositions of the (externally generated) automaton */
	private void checkAPs(SimpleLTL ltl, List<String> automatonAPs) throws PrismException
	{
		APSet ltlAPs = ltl.getAPs();
		for (String ap : automatonAPs) {
			if (!ltlAPs.hasAP(ap)) {
				throw new PrismException("Generated automaton has extra atomic proposition \"" + ap + "\"");
			}
		}
		// It's fine for the automaton to not have APs that occur in the formula, e.g., for
		// p0 | !p0, the external tool could simplify to 'true' and omit all APs
	}

	/**
	 * Simple test method: convert LTL formula (in LBT format) to HOA/Dot/txt
	 */
	public static void main(String args[])
	{
		try {
			// Usage:
			// * ... 'X p1'
			// * ... 'X p1' da.hoa
			// * ... 'X p1' da.hoa hoa
			// * ... 'X p1' da.dot dot
			// * ... 'X p1' - hoa
			// * ... 'X p1' - txt

			// Convert to Expression (from PRISM format)
			/*String pltl = "P=?[" + ltl + "]";
			PropertiesFile pf = Prism.getPrismParser().parsePropertiesFile(new ModulesFile(), new ByteArrayInputStream(pltl.getBytes()));
			Prism.releasePrismParser();
			Expression expr = pf.getProperty(0);
			expr = ((ExpressionProb) expr).getExpression();
			System.out.println("LTL: " + expr);*/

			// Convert to Expression (from LBT format)
			// String ltl = args[0];
			SimpleLTL sltl = SimpleLTL.parseFormulaLBT(args[0]);
			Expression expr = Expression.createFromJltl2ba(sltl);
			// System.out.println("LBT: " + ltl);
			// System.out.println("LTL: " + expr);

			// Build/export DA
			LTL2DA ltl2da = new LTL2DA(new PrismComponent());
			DA<BitSet, ? extends AcceptanceOmega> da = ltl2da.convertLTLFormulaToDA(expr, null, AcceptanceType.RABIN, AcceptanceType.REACH);
			PrintStream out = (args.length < 2 || "-".equals(args[1])) ? System.out : new PrintStream(args[1]);
			String format = (args.length < 3) ? "hoa" : args[2];
			da.print(out, format);

		} catch (Exception e) {
			e.printStackTrace();
			System.err.print("Error: " + e);
		}
	}
}
