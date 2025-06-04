//==============================================================================
//
//	Copyright (c) 2024-
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

import explicit.Model;
import explicit.rewards.Rewards;
import io.umb.UMBException;
import io.umb.UMBWriter;
import io.umb.UMBIndex;
import it.unimi.dsi.fastutil.doubles.DoubleIterators;
import it.unimi.dsi.fastutil.ints.IntIterators;
import parser.State;
import parser.type.Type;
import parser.type.TypeBool;
import parser.type.TypeInt;
import prism.Evaluator;
import prism.ModelInfo;
import prism.ModelType;
import prism.Prism;
import prism.PrismException;
import prism.PrismFileLog;
import prism.PrismLog;
import prism.PrismNotSupportedException;

import java.io.File;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

/**
 * Class to manage export of built models to the UMB file format.
 */
public class UMBExporter<Value> extends ModelExporter<Value>
{
	/**
	 * Construct a UMBExporter with default export options.
	 */
	public UMBExporter()
	{
		super();
	}

	/**
	 * Construct a UMBExporter with the specified export options.
	 */
	public UMBExporter(ModelExportOptions modelExportOptions)
	{
		super(modelExportOptions);
	}

	@Override
	public void exportModel(Model<Value> model, PrismLog out) throws PrismException
	{
		// Export to PrismLog only for text mode
		if (!modelExportOptions.getBinaryAsText()) {
			throw new PrismException("Export in UMB binary format must be to a file");
		}
		// Load all model info into a UMBWriter, then export
		UMBWriter umbWriter = createUMBWriter(model);
		try {
			StringBuffer sb = new StringBuffer();
			umbWriter.exportAsText(sb);
			out.print(sb.toString());
		} catch (UMBException e) {
			throw new PrismException(e.getMessage());
		}
	}

	@Override
	public void exportModel(Model<Value> model, File fileOut) throws PrismException
	{
		// Text mode: write to a PrismLog
		if (modelExportOptions.getBinaryAsText()) {
			try (PrismFileLog out = new PrismFileLog(fileOut.getPath())) {
				exportModel(model, out);
			}
		}
		// Otherwise export in binary mode
		// Load all model info into a UMBWriter, then export
		UMBWriter umbWriter = createUMBWriter(model);
		try {
			umbWriter.export(fileOut, modelExportOptions.getZipped());
		} catch (UMBException e) {
			throw new PrismException(e.getMessage());
		}
	}

	/**
	 * Crate a {@link UMBWriter} to export the specified model to UMB format.
	 * @param model The model
	 */
	private UMBWriter createUMBWriter(Model<Value> model) throws PrismException
	{
		// Get some model info
		setEvaluator(model.getEvaluator());
		Evaluator<Value> evalRewards = getRewardEvaluator();
		ModelType modelType = model.getModelType();
		int numStates = model.getNumStates();

		// Check for currently unsupported cases
		if (modelType.uncertain() || modelType.partiallyObservable()) {
			throw new PrismNotSupportedException(modelType + "s cannot yet be exported to UMB");
		}
		if (!(model.getEvaluator().one() instanceof Double)) {
			throw new PrismNotSupportedException("UMB export currently only supported for doubles");
		}

		// Create a ModelAccess object to access the model data in a uniform way
		ModelAccess<Value> modelAccess = ModelAccess.wrap(model);

		try {

			// Create the writer and build the index
			UMBWriter umbWriter = new UMBWriter();
			buildIndex(modelAccess, umbWriter.getUmbIndex());

			// Add core transition info
			if (modelType.nondeterministic()) {
				umbWriter.addStateChoiceOffsets(modelAccess.getStateChoiceOffsets());
				if (modelType.isProbabilistic()) {
					umbWriter.addChoiceBranchOffsets(modelAccess.getChoiceTransitionOffsets());
				}
			} else {
				umbWriter.addChoiceBranchOffsets(modelAccess.getStateTransitionOffsets());
			}
			if (model.getModelType().isProbabilistic()) {
				umbWriter.addBranchProbabilities(DoubleIterators.asDoubleIterator(modelAccess.getTransitionProbabilities()));
				if (model.getModelType() == ModelType.CTMC) {
					umbWriter.addExitRates(DoubleIterators.asDoubleIterator(modelAccess.getExitRates()));
				}
			}
			umbWriter.addBranchTargets(modelAccess.getTransitionSuccessors());

			// Add initial states info
			umbWriter.addInitialStates(modelAccess.getInitialStates());

			// Add action labelling info
			umbWriter.addActionStrings(modelAccess.getActionStrings());
			// Only store choice/transition-to-action mapping if there are multiple actions
			if (model.getActions().size() > 1) {
				if (modelType.nondeterministic()) {
					umbWriter.addChoiceActionIndices(modelAccess.getChoiceActionIndices());
				} else {
					umbWriter.addBranchActionIndices(modelAccess.getTransitionActionIndices());
				}
			}

			// Add label info
			int numLabels = getNumLabels();
			for (int i = 0; i < numLabels; i++) {
				umbWriter.addStateAP(getLabelName(i), getLabel(i));
			}

			// Add reward info
			int numRewards = getNumRewards();
			for (int r = 0; r < numRewards; r++) {
				Rewards<Value> reward = getReward(r);
				String id = umbWriter.addRewards(getRewardName(r));
				if (reward.hasStateRewards()) {
					umbWriter.addStateRewardsByID(id, DoubleIterators.asDoubleIterator(modelAccess.getStateRewards(getReward(r))));
				}
				if (reward.hasTransitionRewards()) {
					if (modelType.nondeterministic()) {
						umbWriter.addChoiceRewardsByID(id, DoubleIterators.asDoubleIterator(modelAccess.getTransitionRewards(getReward(r))));
					} else {
						umbWriter.addBranchRewardsByID(id, DoubleIterators.asDoubleIterator(modelAccess.getTransitionRewards(getReward(r))));
					}
				}
				// If there are no rewards, add some dummy zero state rewards
				if (!(reward.hasStateRewards() || reward.hasTransitionRewards())) {
					umbWriter.addStateRewardsByID(id, DoubleIterators.asDoubleIterator(Collections.nCopies(numStates, 0.0).iterator()));
				}
			}

			// Add variable info
			ModelInfo modelInfo = getModelInfo();
			List<State> statesList = model.getStatesList();
			if (modelInfo != null && statesList != null) {
				int numVars = modelInfo.getNumVars();
				for (int i = 0; i < numVars; i++) {
					int finalI = i;
					Type varType = modelInfo.getVarType(i);
					if (varType instanceof TypeBool) {
						BitSet bitset = new BitSet();
						for (int s = 0; s < numStates; s++) {
							if ((boolean) statesList.get(s).varValues[i]) {
								bitset.set(s);
							}
						}
						umbWriter.addBooleanVariableDefinition(modelInfo.getVarName(i), bitset);
					} else if (varType instanceof TypeInt) {
						umbWriter.addIntVariableDefinition(modelInfo.getVarName(i), IntIterators.asIntIterator(statesList.stream().map(s -> (int) s.varValues[finalI]).iterator()));
					} else {
						throw new PrismException("Unsupported variable type in UMB export: " + varType);
					}
				}
			}

			return umbWriter;

		} catch (UMBException e) {
			throw new PrismException("UMB import problem: " + e.getMessage());
		}
	}

	/**
	 * Build the index for the UMB file, storing model type and stats.
	 * @param model The model
	 * @param umbIndex The UMB index to build
	 */
	private void buildIndex(ModelAccess<Value> model, UMBIndex umbIndex)
	{
		umbIndex.fileData.tool = Prism.getToolName();
		umbIndex.fileData.toolVersion = Prism.getVersion();
		storeModelTypeInIndex(model, umbIndex);
		storeModelStatsInIndex(model, umbIndex);
	}

	/**
	 * Store info about the model type in the index for the UMB file.
	 * @param model The model
	 * @param umbIndex The UMB index to build
	 */
	private void storeModelTypeInIndex(ModelAccess<Value> model, UMBIndex umbIndex)
	{
		ModelType modelType = model.getModelType();
		umbIndex.setTime(modelType.continuousTime() ? UMBIndex.Time.STOCHASTIC : UMBIndex.Time.DISCRETE);
		umbIndex.setNumPlayers(model.getNumPlayers());
		if (modelType.uncertain()) {
			umbIndex.setBranchProbabilityType(UMBIndex.BranchProbabilityType.DOUBLE_INTERVAL);
		} else if (modelType.isProbabilistic()) {
			umbIndex.setBranchProbabilityType(UMBIndex.BranchProbabilityType.DOUBLE);
		} else {
			umbIndex.setBranchProbabilityType(UMBIndex.BranchProbabilityType.NONE);
		}
	}

	/**
	 * Store stats about the model in the index for the UMB file.
	 * @param model The model
	 * @param umbIndex The UMB index to build
	 */
	private void storeModelStatsInIndex(ModelAccess<Value> model, UMBIndex umbIndex)
	{
		umbIndex.setNumStates(model.getNumStates());
		umbIndex.setNumInitialStates(model.getNumInitialStates());
		umbIndex.setNumChoices(model.getNumChoices());
		umbIndex.setNumBranches(model.getNumTransitions());
		umbIndex.setNumActions(model.getActions().size());
	}
}
