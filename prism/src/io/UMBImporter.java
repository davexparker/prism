//==============================================================================
//
//	Copyright (c) 2025-
//	Authors:
//	* Dave Parker <david.parker@cs.ox.ac.uk> (University of Oxford)
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

package io;

import common.SafeCast;
import io.umb.UMBException;
import io.umb.UMBIndex;
import io.umb.UMBReader;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import parser.VarList;
import parser.ast.DeclarationBool;
import parser.ast.DeclarationInt;
import parser.ast.DeclarationType;
import parser.ast.Expression;
import prism.BasicModelInfo;
import prism.BasicRewardInfo;
import prism.Evaluator;
import prism.ModelInfo;
import prism.ModelType;
import prism.Prism;
import prism.PrismException;
import prism.RewardInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Class to manage importing models from UMB binary files.
 */
public class UMBImporter extends ExplicitModelImporter
{
	private File umbFile;
	private UMBReader umbReader;
	private UMBIndex umbIndex;

	// Model info extracted from file and then stored in a BasicModelInfo object
	private BasicModelInfo basicModelInfo;

	// Links between model info to UMB file
	private List<String> labelIDs;
	private List<String> rewardIDs;
	private List<String> varIDs;

	// Num states/transitions
	private int numStates = 0;
	private int numChoices = 0;
	private int numTransitions = 0;

	// Reward info extracted from files and then stored in a BasicRewardInfo object
	private BasicRewardInfo basicRewardInfo;

	public UMBImporter(File umbFile) throws PrismException
	{
		this.umbFile = umbFile;

		try {
			umbReader = new UMBReader(umbFile);
			// Extract index and store model stats
			umbIndex = umbReader.getUMBIndex();
			numStates = SafeCast.toIntExact(umbIndex.getNumStates());
			numChoices = SafeCast.toIntExact(umbIndex.getNumChoices());
			numTransitions = SafeCast.toIntExact(umbIndex.getNumBranches());
		} catch (ArithmeticException e) {
			throw new PrismException("UMB model is too large to be imported");
		} catch (UMBException e) {
			throw new PrismException("Error importing from UMB: " + e.getMessage());
		}
	}

	@Override
	public boolean providesStates()
	{
		return umbIndex.hasVariableAnnotations();
	}

	@Override
	public boolean providesLabels()
	{
		return umbIndex.hasAPAnnotations();
	}

	@Override
	public String sourceString()
	{
		return "\"" + umbFile.getName() + "\"";
	}

	@Override
	public ModelInfo getModelInfo() throws PrismException
	{
		// Construct lazily, as needed
		if (basicModelInfo == null) {
			buildModelInfo();
		}
		return basicModelInfo;
	}

	@Override
	public int getNumStates() throws PrismException
	{
		return numStates;
	}

	@Override
	public int getNumChoices() throws PrismException
	{
		return numChoices;
	}

	@Override
	public int getNumTransitions() throws PrismException
	{
		return numTransitions;
	}

	@Override
	public BitSet getDeadlockStates() throws PrismException
	{
		// TODO
		return new BitSet();
	}

	@Override
	public int getNumDeadlockStates() throws PrismException
	{
		// TODO
		return 0;
	}

	@Override
	public RewardInfo getRewardInfo() throws PrismException
	{
		// Construct lazily, as needed
		if (basicRewardInfo == null) {
			buildRewardInfo();
		}
		return basicRewardInfo;
	}

	/**
	 * Build/store model info from the UMB index file.
	 * Can then be accessed via {@link #getModelInfo()}.
	 */
	private void buildModelInfo() throws PrismException
	{
		// Create BasicModelInfo object
		ModelType modelType = getModelTypeFromIndex(umbIndex);
		basicModelInfo = new BasicModelInfo(modelType);
		// Add variable info
		VarList varList = basicModelInfo.getVarList();
		varIDs = new ArrayList<>();
		if (providesStates()) {
			// We extract all variable annotations from the UMB file
			// IDs are stores in varIDs and (valid) names go in basicModelInfo
			for (UMBIndex.Annotation varAnnotation : umbIndex.getVariableAnnotationsList()) {
				varIDs.add(varAnnotation.id);
				// Get valid, unique variable name (usually just the variable annotation alias)
				String varName = Prism.toIdentifier(varAnnotation.getName());
				while (varList.getIndex(varName) != -1) {
					varName = "_" + varName;
				}
				// Determine type, range, etc. of variable
				try {
					DeclarationType varDecl = null;
					switch (varAnnotation.type) {
						case BOOL:
							varDecl = new DeclarationBool();
							break;
						case INT:
							UMBReader.IntRange varRange = new UMBReader.IntRange();
							umbReader.extractIntAnnotation(varAnnotation, UMBIndex.UMBEntity.STATES, varRange);
							int varMin = varRange.getMin();
							int varMax = varRange.getMax();
							// Note: we do not yet allow 0-range variables
							if (varMin == varMax) {
								varMax++;
							}
							varDecl = new DeclarationInt(Expression.Int(varMin), Expression.Int(varMax));
							break;
						default:
							throw new PrismException("Unknown variable type in UMB index: " + varAnnotation.type);
					}
					varList.addVar(varName, varDecl, -1);
				} catch (UMBException e) {
					throw new PrismException("UMB import problem: " + e.getMessage());
				}
			}
		} else {
			varList.addVar(defaultVariableName(), defaultVariableDeclarationType(), -1);
		}
		// Add label info
		// We extract all labels (AP) annotations from the UMB file, ignoring "deadlock" if present
		// IDs are stores in labelIDs and (valid) names go in basicModelInfo
		labelIDs = new ArrayList<>();
		List<String> labelList = basicModelInfo.getLabelNameList();
		for (UMBIndex.Annotation apAnnotation : umbIndex.getAPAnnotationsList()) {
			String apName = apAnnotation.getName();
			if (!apName.equals("deadlock")) {
				labelIDs.add(apAnnotation.id);
				// Get valid, unique label name (usually just the AP annotation alias)
				String labelName = Prism.toIdentifier(apName);
				while (labelList.contains(labelName)) {
					labelName = "_" + labelName;
				}
				labelList.add(labelName);
			}
		}
	}

	private static ModelType getModelTypeFromIndex(UMBIndex umbIndex) throws PrismException
	{
		if (umbIndex.getNumPlayers() == 0) {
			switch (umbIndex.getBranchProbabilityType()) {
				case NONE:
					throw new PrismException("Unsupported model type in UMB file");
				case DOUBLE:
				case RATIONAL:
					switch (umbIndex.getTime()) {
						case DISCRETE:
							return ModelType.DTMC;
						case STOCHASTIC:
							return ModelType.CTMC;
						case URGENT_STOCHASTIC:
							throw new PrismException("Unsupported model type in UMB file");
					}
				case DOUBLE_INTERVAL:
				case RATIONAL_INTERVAL:
					switch (umbIndex.getTime()) {
						case DISCRETE:
							return ModelType.IDTMC;
						case STOCHASTIC:
						case URGENT_STOCHASTIC:
							throw new PrismException("Unsupported model type in UMB file");
					}
			}
		} else if (umbIndex.getNumPlayers() == 1) {
			switch (umbIndex.getBranchProbabilityType()) {
				case NONE:
					return ModelType.LTS;
				case DOUBLE:
				case RATIONAL:
					switch (umbIndex.getTime()) {
						case DISCRETE:
							return ModelType.MDP;
						case STOCHASTIC:
						case URGENT_STOCHASTIC:
							throw new PrismException("Unsupported model type in UMB file");
					}
				case DOUBLE_INTERVAL:
				case RATIONAL_INTERVAL:
					switch (umbIndex.getTime()) {
						case DISCRETE:
							return ModelType.IMDP;
						case STOCHASTIC:
						case URGENT_STOCHASTIC:
							throw new PrismException("Unsupported model type in UMB file");
					}
			}
		}
		throw new PrismException("Unsupported model type in UMB file");
	}

	/**
	 * Build/store reward info from the UMB index file.
	 * Can then be accessed via {@link #getRewardInfo()}.
	 */
	private void buildRewardInfo() throws PrismException
	{
		ModelType modelType = getModelInfo().getModelType();
		basicRewardInfo = new BasicRewardInfo();
		int numRewards = umbIndex.getNumRewardAnnotations();
		for (int r = 0; r < numRewards; r++) {
			basicRewardInfo.addReward(umbIndex.getRewardAnnotation(r).getName());
			basicRewardInfo.setHasStateRewards(r, umbIndex.hasStateRewards(r));
			if (modelType.nondeterministic()) {
				basicRewardInfo.setHasTransitionRewards(r, umbIndex.hasChoiceRewards(r));
			} else {
				basicRewardInfo.setHasTransitionRewards(r, umbIndex.hasBranchRewards(r));
			}
		}
	}

	@Override
	public void extractStates(IOUtils.StateDefnConsumer storeStateDefn) throws PrismException
	{
		// If there is no info, just assume that states comprise a single integer value
		if (!providesStates()) {
			super.extractStates(storeStateDefn);
			return;
		}
		// Otherwise, extract state variable info
		int numVars = basicModelInfo.getNumVars();
		for (int i = 0; i < numVars; i++) {
			int finalI = i;
			UMBIndex.Annotation varAnnotation = umbIndex.getVariableAnnotation(i);
			try {
				switch (varAnnotation.type) {
					case BOOL:
							// TODO: custom method for var extraction in UMBReader?
							umbReader.extractIndexedBooleanAnnotation(varAnnotation, UMBIndex.UMBEntity.STATES, (s, v) -> {
								try {
									storeStateDefn.accept(SafeCast.toInt(s), finalI, v);
								} catch (PrismException e) {
									throw new RuntimeException(e);
								}
							});
						break;
					case INT:
							umbReader.extractIndexedIntAnnotation(varAnnotation, UMBIndex.UMBEntity.STATES, (s, v) -> {
								try {
									storeStateDefn.accept(SafeCast.toInt(s), finalI, v);
								} catch (PrismException e) {
									throw new RuntimeException(e);
								}
							});
						break;
					default:
						throw new PrismException("Unknown variable type in UMB index: " + varAnnotation.type);
				}
			} catch (UMBException e) {
				throw new PrismException("UMB import problem: " + e.getMessage());
			}
		}
	}

	@Override
	public int computeMaxNumChoices() throws PrismException
	{
		try {
			return SafeCast.toInt(umbReader.extractMaxStateChoiceCount());
		} catch (UMBException e) {
			throw new PrismException("UMB import problem: " + e.getMessage());
		}
	}

	@Override
	public <Value> void extractMCTransitions(IOUtils.MCTransitionConsumer<Value> storeTransition, Evaluator<Value> eval) throws PrismException
	{
		try {
			// Extract transition info
			IntList choiceTransitionOffsets = new IntArrayList(numChoices + 1);
			umbReader.extractChoiceBranchOffsets(l -> choiceTransitionOffsets.add((int) l));
			IntList transitionSuccessors = new IntArrayList(numTransitions);
			umbReader.extractBranchTargets(l -> transitionSuccessors.add((int) l));
			DoubleList transitionProbabilities = new DoubleArrayList(numTransitions);
			umbReader.extractBranchProbabilities(d -> transitionProbabilities.add(d));

			// For CTMCs, extract exit rates
			DoubleList exitRates = null;
			boolean ctmc = getModelInfo().getModelType() == ModelType.CTMC;
			if (ctmc) {
				exitRates = new DoubleArrayList(numStates);
				umbReader.extractExitRates(exitRates::add);
			}

			// Extract action info
			ArrayList<String> actionStrings = null;
			actionStrings = new ArrayList<>();
			if (umbReader.hasActionStrings()) {
				umbReader.extractActionStrings(actionStrings::add);
			} else {
				actionStrings.add(null);
			}
			Object firstAction = actionStrings.get(0);
			IntList transitionActionIndices = null;
			boolean hasActions = umbReader.hasBranchActionIndices();
			if (hasActions) {
				transitionActionIndices = new IntArrayList(numTransitions);
				umbReader.extractBranchActionIndices(transitionActionIndices::add);
			}

			// Convert sparse storage to transitions and store
			// (assume doubles for now)
			IOUtils.MCTransitionConsumer<Double> storeTransitionDoubles = (IOUtils.MCTransitionConsumer<Double>) storeTransition;
			int jLo = 0, jHi = 0;
			for (int s = 0; s < numStates; s++) {
				jLo = jHi;
				jHi = choiceTransitionOffsets.getInt(s + 1);
				for (int j = jLo; j < jHi; j++) {
					double d = transitionProbabilities.getDouble(j);
					if (ctmc) {
						d *= exitRates.getDouble(s);
					}
					//Value v = eval.fromString(Double.toString(d));
					Object action = hasActions ? actionStrings.get(transitionActionIndices.getInt(j)) : firstAction;
					storeTransitionDoubles.accept(s, transitionSuccessors.getInt(j), d, action);
				}
			}

		} catch (UMBException e) {
			throw new PrismException("UMB import problem: " + e.getMessage());
		}
	}

	@Override
	public <Value> void extractMDPTransitions(IOUtils.MDPTransitionConsumer<Value> storeTransition, Evaluator<Value> eval) throws PrismException
	{
		try {
			// Extract transition info
			IntList stateChoiceOffsets = new IntArrayList(numStates + 1);
			umbReader.extractStateChoiceOffsets(l -> stateChoiceOffsets.add((int) l));
			IntList choiceTransitionOffsets = new IntArrayList(numChoices + 1);
			umbReader.extractChoiceBranchOffsets(l -> choiceTransitionOffsets.add((int) l));
			IntList transitionSuccessors = new IntArrayList(numTransitions);
			umbReader.extractBranchTargets(l -> transitionSuccessors.add((int) l));
			DoubleList transitionProbabilities = new DoubleArrayList(numTransitions);
			umbReader.extractBranchProbabilities(d -> transitionProbabilities.add(d));

			// Extract action info
			ArrayList<String> actionStrings = null;
			actionStrings = new ArrayList<>();
			if (umbReader.hasActionStrings()) {
				umbReader.extractActionStrings(actionStrings::add);
			} else {
				actionStrings.add(null);
			}
			Object firstAction = actionStrings.get(0);
			IntList choiceActionIndices = null;
			boolean hasActions = umbReader.hasChoiceActionIndices();
			if (hasActions) {
				choiceActionIndices = new IntArrayList(numChoices);
				umbReader.extractChoiceActionIndices(choiceActionIndices::add);
			}

			// Convert sparse storage to transitions and store
			// (assume doubles for now)
			IOUtils.MDPTransitionConsumer<Double> storeTransitionDoubles = (IOUtils.MDPTransitionConsumer<Double>) storeTransition;
			int iLo = 0, iHi = 0;
			int jLo = 0, jHi = 0;
			for (int s = 0; s < numStates; s++) {
				iLo = iHi;
				iHi = stateChoiceOffsets.getInt(s + 1);
				int iCount = 0;
				for (int i = iLo; i < iHi; i++) {
					jLo = jHi;
					jHi = choiceTransitionOffsets.getInt(i + 1);
					for (int j = jLo; j < jHi; j++) {
						double d = transitionProbabilities.getDouble(j);
						Object action = hasActions ? actionStrings.get(choiceActionIndices.getInt(i)) : firstAction;
						storeTransitionDoubles.accept(s, iCount, transitionSuccessors.getInt(j), d, action);
					}
					iCount++;
				}
			}

		} catch (UMBException e) {
			throw new PrismException("UMB import problem: " + e.getMessage());
		}
	}

	@Override
	public void extractLTSTransitions(IOUtils.LTSTransitionConsumer storeTransition) throws PrismException
	{
		try {
			// Extract transition info
			IntList stateChoiceOffsets = new IntArrayList(numStates + 1);
			umbReader.extractStateChoiceOffsets(l -> stateChoiceOffsets.add((int) l));
			IntList transitionSuccessors = new IntArrayList(numTransitions);
			umbReader.extractBranchTargets(l -> transitionSuccessors.add((int) l));

			// Extract action info
			ArrayList<String> actionStrings = null;
			actionStrings = new ArrayList<>();
			if (umbReader.hasActionStrings()) {
				umbReader.extractActionStrings(actionStrings::add);
			} else {
				actionStrings.add(null);
			}
			Object firstAction = actionStrings.get(0);
			IntList choiceActionIndices = null;
			boolean hasActions = umbReader.hasChoiceActionIndices();
			if (hasActions) {
				choiceActionIndices = new IntArrayList(numChoices);
				umbReader.extractChoiceActionIndices(choiceActionIndices::add);
			}

			// Convert sparse storage to transitions and store
			int iLo = 0, iHi = 0;
			for (int s = 0; s < numStates; s++) {
				iLo = iHi;
				iHi = stateChoiceOffsets.getInt(s + 1);
				int iCount = 0;
				for (int i = iLo; i < iHi; i++) {
					Object action = hasActions ? actionStrings.get(choiceActionIndices.getInt(i)) : firstAction;
					storeTransition.accept(s, iCount, transitionSuccessors.getInt(i), action);
					iCount++;
				}
			}

		} catch (UMBException e) {
			throw new PrismException("UMB import problem: " + e.getMessage());
		}
	}

	@Override
	public void extractLabelsAndInitialStates(BiConsumer<Integer, Integer> storeLabel, Consumer<Integer> storeInit, Consumer<Integer> storeDeadlock) throws PrismException
	{
		try {
			// Extract initial states
			umbReader.extractInitialStates(s -> storeInit.accept(SafeCast.toIntExact(s)));
			// Extract labels
			int numLabels = labelIDs.size();
			for (int i = 0; i < numLabels; i++) {
				int finalI = i;
				umbReader.extractStateAP(labelIDs.get(i), s -> storeLabel.accept(SafeCast.toIntExact(s), finalI));
			}
			// If a "deadlock" AP is stored, use it to store deadlock state info
			if (storeDeadlock != null) {
				String deadlockId = null;
				if (umbIndex.hasAPAnnotationWithAlias("deadlock")) {
					deadlockId = umbIndex.getAPAnnotationByAlias("deadlock").id;
				} else if (umbIndex.getAPAnnotations().get("deadlock") != null) {
					deadlockId = "deadlock";
				}
				if (deadlockId != null) {
					umbReader.extractStateAP(deadlockId, s -> storeDeadlock.accept(SafeCast.toIntExact(s)));
				}
			}
		} catch (UMBException e) {
			throw new PrismException("UMB import problem: " + e.getMessage());
		}
	}

	@Override
	public <Value> void extractStateRewards(int rewardIndex, BiConsumer<Integer, Value> storeReward, Evaluator<Value> eval) throws PrismException
	{
		try {
			if (!basicRewardInfo.rewardStructHasStateRewards(rewardIndex)) {
				return;
			}
			umbReader.extractStateRewards(rewardIndex, new IndexedDoubleConsumer((BiConsumer<Integer, Double>) storeReward));
		} catch (UMBException e) {
			throw new PrismException("UMB import problem: " + e.getMessage());
		}
	}

	@Override
	public <Value> void extractMCTransitionRewards(int rewardIndex, IOUtils.TransitionRewardConsumer<Value> storeReward, Evaluator<Value> eval) throws PrismException
	{
		if (!basicRewardInfo.rewardStructHasTransitionRewards(rewardIndex)) {
			return;
		}

		try {
			// Extract transition rewards from UMB
			DoubleList transRewards = new DoubleArrayList(numTransitions);
			umbReader.extractBranchRewards(rewardIndex, transRewards::add);

			// Convert sparse storage to reward list and store
			IOUtils.TransitionRewardConsumer<Double> storeRewardDoubles = (IOUtils.TransitionRewardConsumer<Double>) storeReward;

			// If the model has already been built and provided, use this to look for transition indexing
			if (modelLookup != null) {
				ModelAccess<Value> modelAccess = ModelAccess.wrap(modelLookup);
				int iLo = 0, iHi = 0;
				for (int s = 0; s < numStates; s++) {
					iLo = iHi;
					iHi = iLo + modelAccess.getNumTransitions(s, 0);
					for (int i = iLo; i < iHi; i++) {
						double d = transRewards.getDouble(i);
						if (d > 0) {
							switch (transitionRewardIndexing) {
								case STATE:
									storeRewardDoubles.accept(s, modelAccess.getTransitionSuccessor(s, 0, i - iLo), d);
									break;
								case OFFSET:
									storeRewardDoubles.accept(s, i - iLo, d);
									break;
								default:
									throw new PrismException("Unknown transition reward indexing " + transitionRewardIndexing);
							}
						}
					}
				}
			}
			// If there is no model available, we extract transition info from UMB first
			else {
				IntList stateTransitionOffsets = new IntArrayList(numStates + 1);
				umbReader.extractChoiceBranchOffsets(l -> stateTransitionOffsets.add((int) l));
				IntList transitionSuccessors;
				if (transitionRewardIndexing == TransitionRewardIndexing.STATE) {
					transitionSuccessors = new IntArrayList(numTransitions);
					umbReader.extractBranchTargets(l -> transitionSuccessors.add((int) l));
				} else {
					transitionSuccessors = null;
				}

				int iLo = 0, iHi = 0;
				for (int s = 0; s < numStates; s++) {
					iLo = iHi;
					iHi = stateTransitionOffsets.getInt(s + 1);
					for (int i = iLo; i < iHi; i++) {
						double d = transRewards.getDouble(i);
						if (d > 0) {
							switch (transitionRewardIndexing) {
								case STATE:
									storeRewardDoubles.accept(s, transitionSuccessors.getInt(i), d);
									break;
								case OFFSET:
									storeRewardDoubles.accept(s, i - iLo, d);
									break;
								default:
									throw new PrismException("Unknown transition reward indexing " + transitionRewardIndexing);
							}
						}
					}
				}
			}
		} catch (UMBException e) {
			throw new PrismException("UMB import problem: " + e.getMessage());
		}
	}

	@Override
	public <Value> void extractMDPTransitionRewards(int rewardIndex, IOUtils.TransitionRewardConsumer<Value> storeReward, Evaluator<Value> eval) throws PrismException
	{
		if (!basicRewardInfo.rewardStructHasTransitionRewards(rewardIndex)) {
			return;
		}

		try {
			// Extract transition rewards from UMB
			DoubleList transRewards = new DoubleArrayList(numChoices);
			umbReader.extractChoiceRewards(rewardIndex, transRewards::add);

			// Convert sparse storage to reward list and store
			IOUtils.TransitionRewardConsumer<Double> storeRewardDoubles = (IOUtils.TransitionRewardConsumer<Double>) storeReward;

			// If the model has already been built and provided, use this to look for transition indexing
			ModelAccess<Value> modelAccess = null;
			IntList stateChoiceOffsets;
			if (modelLookup != null) {
				stateChoiceOffsets = null;
				modelAccess = ModelAccess.wrap(modelLookup);
			}
			// If there is no model available, we extract transition info from UMB first
			else {
				stateChoiceOffsets = new IntArrayList(numStates + 1);
				umbReader.extractStateChoiceOffsets(l -> stateChoiceOffsets.add((int) l));
			}

			// Convert sparse storage to reward list and store
			int iLo = 0, iHi = 0;
			for (int s = 0; s < numStates; s++) {
				iLo = iHi;
				if (modelLookup != null) {
					iHi = iLo + modelAccess.getNumChoices(s);
				} else {
					iHi = stateChoiceOffsets.getInt(s + 1);
				}
				for (int i = iLo; i < iHi; i++) {
					double d = transRewards.getDouble(i);
					if (d > 0) {
						storeRewardDoubles.accept(s, i - iLo, d);
					}
				}
			}
		} catch (UMBException e) {
			throw new PrismException("UMB import problem: " + e.getMessage());
		}
	}

	// Utility classes

	/**
	 * Class to add an increasing index to values from a consumer.
	 */
	private static class IndexedConsumer<Value> implements Consumer<Value>
	{
		BiConsumer<Integer, Value> out;
		int index;

		IndexedConsumer(BiConsumer<Integer, Value> out)
		{
			this.out = out;
		}

		@Override
		public void accept(Value v)
		{
			out.accept(index++, v);
		}
	}

	/**
	 * Class to add an increasing index to values from a double consumer.
	 */
	private static class IndexedDoubleConsumer implements DoubleConsumer
	{
		BiConsumer<Integer, Double> out;
		int index;

		IndexedDoubleConsumer(BiConsumer<Integer, Double> out)
		{
			this.out = out;
		}

		@Override
		public void accept(double d)
		{
			out.accept(index++, d);
		}
	}
}
