package automata;

import java.util.*;

import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.hoa.HoaWriter;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.translations.LtlTranslationRepository;
import owl.translations.ltl2ldba.AnnotatedLDBA;
import owl.translations.ltl2ldba.AsymmetricLDBAConstruction;
import prism.PrismComponent;
import prism.PrismException;

import static owl.translations.LtlTranslationRepository.applyPreAndPostProcessing;


public class LTL2LDBA extends PrismComponent {
    public LTL2LDBA(PrismComponent parent) throws PrismException {
        super(parent);
    }

    // returns hoa string representation of the LDBA for the given LTL formula
    public String convert(String ltlFormula) throws PrismException {
        LabelledFormula inputFormula = LtlParser.parse(ltlFormula);

        Set<LtlTranslationRepository.Option> translationOptions = new HashSet<>();
        translationOptions.add(LtlTranslationRepository.Option.SIMPLIFY_AUTOMATON);
        translationOptions.add(LtlTranslationRepository.Option.SIMPLIFY_FORMULA);
        translationOptions.add(LtlTranslationRepository.Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS);
        translationOptions.add(LtlTranslationRepository.Option.COMPLETE);

        Automaton<?, ? extends BuchiAcceptance> owl_ldba = applyPreAndPostProcessing(AsymmetricLDBAConstruction.of(BuchiAcceptance.class).andThen(AnnotatedLDBA::copyAsMutable),
                LtlTranslationRepository.BranchingMode.DETERMINISTIC,
                translationOptions,
                BuchiAcceptance.class)
                .apply(inputFormula);

        return HoaWriter.toString(owl_ldba);

        // TODO: continue here with a DA.java wrapper to represent the LDBA as two DAs?
    }
}

