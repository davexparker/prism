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

package io.umb;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.SerializedName;
import io.UMBBitPacking;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Representation of the (JSON) index for a UMB file.
 */
public class UMBIndex
{
	// Index data, stored in a form that allows JSON import/export via GSON

	/** Major version of the UMB format used for this file */
	public Integer formatVersion = UMBVersion.MAJOR;
	/** Minor version of the UMB format used for this file */
	public Integer formatRevision = UMBVersion.MINOR;
	/** Model metadata */
	public ModelData modelData = new ModelData();
	/** File metadata */
	public FileData fileData = new FileData();
	/** Transition system details */
	public TransitionSystem transitionSystem = new TransitionSystem();
	/** Annotations, arranged by group and then by annotation ID, in ordered maps */
	public LinkedHashMap<String, LinkedHashMap<String, Annotation>> annotations = new LinkedHashMap<>();
	/** State valuations details */
	public StateValuationsDescription stateValuations;

	// Further info about annotations

	/** Names of groups into which annotations are organised */
	public transient List<String> annotationGroups = new ArrayList<>();
	/** Map from annotation aliases to IDs for each annotation group */
	public transient Map<String, Map<String, String>> annotationAliasMaps = new LinkedHashMap<>();

	// Enums

	/** UMB file entities which can be annotated/indexed */
	public enum UMBEntity implements UMBField
	{
		STATES, CHOICES, BRANCHES;
		@Override
		public String toString()
		{
			switch (this) {
				case STATES: return "states";
				case CHOICES: return "choices";
				case BRANCHES: return "branches";
				default: return "?";
			}
		}
	}

	/** UMB data types */
	public enum UMBType implements UMBField
	{
		INT, BOOL, DOUBLE;
		@Override
		public String toString()
		{
			switch (this) {
				case INT: return "int";
				case BOOL: return "bool";
				case DOUBLE: return "double";
				default: return "?";
			}
		}
	}

	/** Types of continuous numerical values */
	public enum ContinuousNumericType implements UMBField
	{
		DOUBLE, RATIONAL, DOUBLE_INTERVAL, RATIONAL_INTERVAL;

		/**
		 * Returns true if values are defined as intervals.
		 */
		public boolean intervals()
		{
			return this == DOUBLE_INTERVAL || this == RATIONAL_INTERVAL;
		}
	}

	/** Notions of time */
	public enum Time implements UMBField
	{
		DISCRETE, STOCHASTIC, URGENT_STOCHASTIC
	}

	// Index contents

	/** Model metadata */
	public static class ModelData
	{
		/** (Short) name of the model */
		public String name;
		/** Version info for the model */
		public String version;
		/** Model author(s) */
		public List<String> authors;
		/** Model description */
		public String description;
		/** Additional comments about the model */
		public String comment;
		/** DOI of the paper where the model was introduced/used/described */
		public String doi;
		/** URL pointing to more information about the model */
		public String url;

		/**
		 * Check this object is valid; throw an exception if not.
		 */
		public void validate() throws UMBException
		{
			// All optional: nothing to do
		}
	}

	/** File metadata */
	public static class FileData
	{
		/** The tool used to create this file */
		public String tool;
		/** Version of the tool used to create this file */
		public String toolVersion;
		/** Date of file creation */
		public Long creationDate = Instant.now().getEpochSecond();
		/** Tool parameters (e.g. string or list of command-line arguments) used */
		public Object parameters;

		/**
		 * Check this object is valid; throw an exception if not.
		 */
		public void validate() throws UMBException
		{
			// All optional: nothing to do
		}
	}

	/** Transition system details */
	public static class TransitionSystem
	{
		/** Notion of time used */
		public Time time;
		/** Number of players */
		@SerializedName("#players")
		public Integer numPlayers;
		/** Number of states */
		@SerializedName("#states")
		public Long numStates;
		/** Number of initial states */
		@SerializedName("#initial-states")
		public Long numInitialStates;
		/** Number of choices */
		@SerializedName("#choices")
		public Long numChoices;
		/** Number of choice actions */
		@SerializedName("#choice-actions")
		public Integer numChoiceActions;
		/** Number of branches */
		@SerializedName("#branches")
		public Long numBranches;
		/** Number of branch actions */
		@SerializedName("#branch-actions")
		public Integer numBranchActions;
		/** Type of branch probabilities */
		public ContinuousNumericType branchProbabilityType;
		/** Type of exit rates */
		public ContinuousNumericType exitRateType;

		/**
		 * Check this object is valid; throw an exception if not.
		 */
		public void validate() throws UMBException
		{
			checkFieldExists(time, "time");
			checkFieldExists(numPlayers, "numPlayers");
			if (numPlayers < 0) {
				throw new UMBException("Number of players must be non-negative");
			}
			checkFieldExists(numStates, "numStates");
			if (numStates < 0) {
				throw new UMBException("Number of states must be at least 1");
			}
			checkFieldExists(numInitialStates, "numInitialStates");
			if (numInitialStates < 0) {
				throw new UMBException("Number of initial states must be non-negative");
			}
			checkFieldExists(numChoices, "numChoices");
			if (numChoices < 0) {
				throw new UMBException("Number of choices must be non-negative");
			}
			checkFieldExists(numChoiceActions, "numChoiceActions");
			if (numChoiceActions < 0) {
				throw new UMBException("Number of choice actions must be non-negative");
			}
			checkFieldExists(numBranches, "numBranches");
			if (numBranches < 0) {
				throw new UMBException("Number of branches must be non-negative");
			}
			checkFieldExists(numBranchActions, "numBranchActions");
			if (numBranchActions < 0) {
				throw new UMBException("Number of branch actions must be non-negative");
			}
			if (time == Time.STOCHASTIC || time == Time.URGENT_STOCHASTIC) {
				checkFieldExists(exitRateType, "exitRateType");
			}
		}
	}

	/** Representation of a model annotation */
	public static class Annotation
	{
		/** Group ID (duplicated for convenience; same as map key) */
		public transient String group;
		/** ID (duplicated for convenience; same as map key) */
		public transient String id;
		/** Alias (name) */
		public String alias;
		/** List of entities to which this annotation applies */
		public List<UMBEntity> appliesTo = new ArrayList<>();
		/** Type of values stored */
		public UMBType type;

		/**
		 * Add an entity to which this annotation applies.
		 * @param entity The entity to add
		 */
		public void addAppliesTo(UMBEntity entity)
		{
			appliesTo.add(entity);
		}

		/**
		 * Check this object is valid; throw an exception if not.
		 */
		public void validate() throws UMBException
		{
			checkFieldExists(appliesTo, "appliesTo");
			if (appliesTo.isEmpty()) {
				throw new UMBException("Annotation \"" + id + "\" in group \"" + group + "\" is empty");
			}
			checkFieldExists(type, "type");
		}

		/**
		 * Get the "name" of this annotation, i.e., the alias if present or the ID if not.
		 */
		public String getName()
		{
			return alias == null ? id : alias;
		}

		/**
		 * Check whether this annotation applies to the specified entity.
		 * @param entity The entity to check
		 */
		public boolean appliesTo(UMBEntity entity)
		{
			return appliesTo.contains(entity);
		}

		/**
		 * Get the name of the file to store this annotation, for the specified entity.
		 * @param entity The entity
		 */
		public String getFilename(UMBEntity entity)
		{
			return UMBFormat.annotationFile(group, id, entity);
		}
	}

	/** Info about state valuations */
	public static class StateValuationsDescription
	{
		/** Alignment (in bytes) of the data */
		public Integer alignment;
		/** List of variables/padding making up each state valuation */
		public List<StateValuationVariable> variables = new ArrayList<>();

		/**
		 * Get the total size of the variables/padding (in bits).
		 */
		public int numBits()
		{
			int numBits = 0;
			for (StateValuationVariable variable : variables) {
				numBits += variable.numBits();
			}
			return numBits;
		}
	}

	/** Info about a state valuation variable/padding */
	public static class StateValuationVariable
	{
		// For variables
		/** Variable name */
		public String name;
		/** Variable size (number of bits) */
		public Integer size;
		/** Variable type */
		public String type;
		// For padding
		/** Amount of padding (number of bits) */
		public Integer padding;

		/**
		 * Is this a variable (as opposed to padding)?
		 */
		public boolean isVariable()
		{
			return padding == null;
		}

		/**
		 * Is this padding (as opposed to a variable)?
		 */
		public boolean isPadding()
		{
			return padding != null;
		}

		/**
		 * Get the size of this variable/padding (in bits).
		 */
		public int numBits()
		{
			if (padding != null) {
				return padding;
			} else if (size != null) {
				return size;
			} else {
				return 0;
			}
		}
	}

	/**
	 * Perform validation of this object
	 */
	public void validate() throws UMBException
	{
		checkFieldExists(formatVersion, "formatVersion");
		checkFieldExists(formatRevision, "formatVersion");
		if (modelData != null) {
			modelData.validate();
		}
		if (fileData != null) {
			fileData.validate();
		}
		checkFieldExists(transitionSystem, "transitionSystem");
		transitionSystem.validate();
		if (annotations != null) {
			for (Map.Entry<String, LinkedHashMap<String, Annotation>> entry : annotations.entrySet()) {
				validateAnnotations(entry.getKey(), entry.getValue());
			}
		}
		if (stateValuations != null) {
			validateStateValuations(stateValuations, "stateValuations");
		}
	}

	/**
	 * Perform validation of the annotations
	 */
	public void validateAnnotations(String group, Map<String, Annotation> annotations) throws UMBException
	{
		for (Map.Entry<String, Annotation> entry : annotations.entrySet()) {
			if ("".equals(entry.getKey())) {
				throw new UMBException("Empty annotation ID in group \"" + group + "\"");
			}
			if (!UMBFormat.isValidID(entry.getKey())) {
				throw new UMBException("\"" + entry.getKey() + "\" is not a valid annotation ID in group \"" + group + "\"");
			}
			Annotation a = entry.getValue();
			a.validate();
		}
	}

	/**
	 * Perform validation of a state valuations description
	 */
	public void validateStateValuations(StateValuationsDescription valuations, String fieldName) throws UMBException
	{
		checkFieldExists(valuations.alignment, fieldName + ".alignment");
		checkFieldExists(valuations.variables, fieldName + ".variables");
		for (StateValuationVariable var : stateValuations.variables) {
			validateStateValuationVariable(var);
		}
		// TODO: If strict and (valuations.numBits() % 8 != 0)
	}

	/**
	 * Perform validation of an individual state valuation variable
	 */
	public void validateStateValuationVariable(StateValuationVariable var) throws UMBException
	{
		// Should either be padding or a named variable
		if (var.padding != null) {
			if (var.name != null || var.size != null || var.type != null) {
				throw new UMBException("Malformed variable/padding in state valuation metadata");
			}
		} else {
			if (var.name == null) {
				throw new UMBException("Unnamed variable in state valuation metadata");
			}
			if (var.type == null) {
				throw new UMBException("Untyped variable in state valuation metadata");
			}
			if (var.size == null) {
				throw new UMBException("Only fixed size variables are currently supported for state valuations");
			}
		}
	}

	/**
	 * Create an empty UMBIndex.
	 */
	public UMBIndex()
	{
		// Default no-argument constructor needed for JSON deserialisation
	}

	// Setters

	/**
	 * Set the notion of time used for the model.
	 * @param time Notion of time
	 */
	public void setTime(Time time)
	{
		transitionSystem.time = time;
	}

	/**
	 * Set the number of players in the model.
	 * @param numPlayers The number of players
	 */
	public void setNumPlayers(int numPlayers)
	{
		transitionSystem.numPlayers = numPlayers;
	}

	/**
	 * Set the number of states in the model.
	 * @param numStates The number of states
	 */
	public void setNumStates(long numStates)
	{
		transitionSystem.numStates = numStates;
	}

	/**
	 * Set the number of initial states in the model.
	 * @param numInitialStates The number of initial states
	 */
	public void setNumInitialStates(long numInitialStates)
	{
		transitionSystem.numInitialStates = numInitialStates;
	}

	/**
	 * Set the number of choices in the model.
	 * @param numChoices The number of choices
	 */
	public void setNumChoices(long numChoices)
	{
		transitionSystem.numChoices = numChoices;
	}

	/**
	 * Set the number of branches in the model.
	 * @param numBranches The number of branches
	 */
	public void setNumBranches(long numBranches)
	{
		transitionSystem.numBranches = numBranches;
	}

	/**
	 * Set the number of choice actions in the model.
	 * @param numChoiceActions The number of choice actions
	 */
	public void setNumChoiceActions(int numChoiceActions)
	{
		transitionSystem.numChoiceActions = numChoiceActions;
	}

	/**
	 * Set the number of branch actions in the model.
	 * @param numBranchActions The number of choice actions
	 */
	public void setNumBranchActions(int numBranchActions)
	{
		transitionSystem.numBranchActions = numBranchActions;
	}

	/**
	 * Set the type of branch probabilities used in the model.
	 * @param branchProbabilityType The type of branch probabilities
	 */
	public void setBranchProbabilityType(ContinuousNumericType branchProbabilityType)
	{
		transitionSystem.branchProbabilityType = branchProbabilityType;
	}

	/**
	 * Set the type of exit rates used in the model.
	 * @param exitRateType The type of exit rates
	 */
	public void setExitRateType(ContinuousNumericType exitRateType)
	{
		transitionSystem.exitRateType = exitRateType;
	}

	/**
	 * Add a new annotation, for now without any data attached.
	 * If an alias is provided, there should not already exist
	 * an annotation in the same group with the same alias.
	 * @param group The ID of the group
	 * @param alias Optional alias (name) for the annotation ("" or null if not needed)
	 * @param type The type of the values to be stored in the annotation
	 */
	public Annotation addAnnotation(String group, String alias, UMBType type) throws UMBException
	{
		// Check if group exists; create if not
		if (!annotationGroups.contains(group)) {
			if (!UMBFormat.isValidID(group)) {
				throw new UMBException("Invalid group ID \"" + group + "\"");
			}
			annotationGroups.add(group);
			annotationAliasMaps.put(group, new LinkedHashMap<>());
			annotations.put(group, new LinkedHashMap<>());
		}
		// If alias is present, check it does not already exist
		if (alias != null && !alias.isEmpty()) {
			Map<String, String> nameMap = annotationAliasMaps.get(group);
			if (nameMap.containsKey(alias)) {
				throw new UMBException("Duplicate alias \"" + alias + "\" in group \"" + group + "\"");
			}
		}
		// Create/store annotation
		// (ID is based on alias if present; if not, just use integer, 1-indexed indices)
		LinkedHashMap<String, Annotation> grpAnnotations = annotations.get(group);
		Annotation annotation = new Annotation();
		annotation.group = group;
		String id = Integer.toString(grpAnnotations.size() + 1);
		if (alias != null && !alias.isEmpty()) {
			id = UMBFormat.toValidUniqueID(alias, grpAnnotations::containsKey);
			annotation.alias = alias;
			annotationAliasMaps.get(group).put(alias, id);
		}
		annotation.id = id;
		annotation.type = type;
		grpAnnotations.put(id, annotation);
		return annotation;
	}

	/**
	 * Set the state valuations metadata, extracted from a {@link UMBBitPacking} object.
	 */
	public void setStateValuationsFromBitPacking(UMBBitPacking bitPacking)
	{
		stateValuations = bitPacking.toStateValuationsDescription();
	}

	// Getters

	/**
	 * Get the notion of time used for the model.
	 */
	public Time getTime()
	{
		return transitionSystem.time;
	}

	/**
	 * Get the number of players in the model.
	 */
	public int getNumPlayers()
	{
		return transitionSystem.numPlayers;
	}

	/**
	 * Get the number of states in the model.
	 */
	public long getNumStates()
	{
		return transitionSystem.numStates;
	}

	/**
	 * Get the number of initial states in the model.
	 */
	public long getNumInitialStates()
	{
		return transitionSystem.numInitialStates;
	}

	/**
	 * Get the number of choices in the model.
	 */
	public long getNumChoices()
	{
		return transitionSystem.numChoices;
	}

	/**
	 * Get the number of branches in the model.
	 */
	public long getNumBranches()
	{
		return transitionSystem.numBranches;
	}

	/**
	 * Get the number of choice actions in the model.
	 */
	public int getNumChoiceActions()
	{
		return transitionSystem.numChoiceActions;
	}

	/**
	 * Get the number of branch actions in the model.
	 */
	public int getNumBranchActions()
	{
		return transitionSystem.numBranchActions;
	}

	/**
	 * Get the type of branch probabilities used in the model.
	 */
	public ContinuousNumericType getBranchProbabilityType()
	{
		return transitionSystem.branchProbabilityType;
	}

	/**
	 * Get the type of exit rates used in the model.
	 */
	public ContinuousNumericType getExitRateType()
	{
		return transitionSystem.exitRateType;
	}

	// Methods to get info about annotations

	/**
	 * Get a stored model annotation
	 * @param group The ID of the group containing the annotation
	 * @param id The ID of the annotation within the group
	 */
	public Annotation getAnnotation(String group, String id) throws UMBException
	{
		LinkedHashMap<String, Annotation> grpAnnotations = annotations.get(group);
		if (grpAnnotations == null) {
			throw new UMBException("Unknown annotation group ID \"" + group + "\"");
		}
		Annotation annotation = grpAnnotations.get(id);
		if (annotation == null) {
			throw new UMBException("Unknown annotation ID \"" + id + "\" in group \"" + group + "\"");
		}
		return annotation;
	}

	/**
	 * See if a stored model annotation with a given alias exists.
	 * @param group The ID of the group containing the annotation
	 * @param alias The alias of the annotation
	 */
	public boolean annotationWithAliasExists(String group, String alias)
	{
		Map<String, String> aliasMap = annotationAliasMaps.get(group);
		if (aliasMap == null) {
			return false;
		}
		return aliasMap.containsKey(alias);
	}

	/**
	 * Get a stored model annotation by its alias.
	 * Throws an exception if no such annotation exists.
	 * @param group The ID of the group containing the annotation
	 * @param alias The alias of the annotation
	 */
	public String getAnnotationIdForAlias(String group, String alias) throws UMBException
	{
		Map<String, String> aliasMap = annotationAliasMaps.get(group);
		if (aliasMap == null) {
			throw new UMBException("Annotation group \"" + group + "\" does not exist");
		}
		String id = aliasMap.get(alias);
		if (id == null) {
			throw new UMBException("No annotation with alias \"" + alias + "\" in group \"" + group + "\" exists");
		}
		return id;
	}

	/**
	 * Get the size of the annotation data for the specified entity.
	 * @param appliesTo The entity to which the annotation applies
	 */
	public long getAnnotationDataSize(UMBEntity appliesTo) throws UMBException
	{
		switch (appliesTo) {
			case STATES:
				return getNumStates();
			case CHOICES:
				return getNumChoices();
			case BRANCHES:
				return getNumBranches();
			default:
				throw new UMBException("Unsupported annotation application \"" + appliesTo + "\"");
		}
	}

	// Methods to get info about AP annotations

	/**
	 * Get all AP annotations, as an ordered map from ID to {@link Annotation}.
	 * This is guaranteed to return a non-null map, but it may be empty.
	 */
	public LinkedHashMap<String, Annotation> getAPAnnotations()
	{
		return annotations.getOrDefault(UMBFormat.AP_ANNOTATIONS_GROUP, new LinkedHashMap<>());
	}

	/**
	 * Get all AP annotations as a list.
	 */
	public List<Annotation> getAPAnnotationsList()
	{
		return new ArrayList<>(getAPAnnotations().values());
	}

	/**
	 * Does this UMB file have any AP annotations?
	 */
	public boolean hasAPAnnotations()
	{
		return !getAPAnnotations().isEmpty();
	}

	/**
	 * Get the number of AP annotations.
	 */
	public int getNumAPAnnotations()
	{
		return getAPAnnotations().size();
	}

	/**
	 * Get the {@code i}th AP annotation.
	 */
	public Annotation getAPAnnotation(int i)
	{
		return getAPAnnotationsList().get(i);
	}

	/**
	 * Get an AP annotation by its ID
	 * Throws an exception if no such annotation exists.
	 */
	public Annotation getAPAnnotationByID(String apID) throws UMBException
	{
		Annotation annotation = getAPAnnotations().get(apID);
		if (annotation == null) {
			throw new UMBException("Unknown AP annotation ID \"" + apID + "\"");
		}
		return annotation;
	}

	/**
	 * Does this UMB file have an AP annotation with the specified alias?
	 */
	public boolean hasAPAnnotationWithAlias(String apAlias)
	{
		return annotationWithAliasExists(UMBFormat.AP_ANNOTATIONS_GROUP, apAlias);
	}

	/**
	 * Get an AP annotation by its alias.
	 * Throws an exception if no such annotation exists.
	 */
	public Annotation getAPAnnotationByAlias(String apAlias) throws UMBException
	{
		String apID = getAnnotationIdForAlias(UMBFormat.AP_ANNOTATIONS_GROUP, apAlias);
		return getAPAnnotationByID(apID);
	}

	/**
	 * Get the names of all AP annotations (name is alias if present or ID if not)
	 */
	public List<String> getAPNames()
	{
		return getAPAnnotationsList().stream()
				.map(Annotation::getName)
				.collect(Collectors.toCollection(ArrayList::new));
	}

	// Methods to get info about reward annotations

	/**
	 * Get all reward annotations, as an ordered map from ID to {@link Annotation}.
	 * This is guaranteed to return a non-null map, but it may be empty.
	 */
	public LinkedHashMap<String, Annotation> getRewardAnnotations()
	{
		return annotations.getOrDefault(UMBFormat.REWARD_ANNOTATIONS_GROUP, new LinkedHashMap<>());
	}

	/**
	 * Get all reward annotations as a list.
	 */
	public List<Annotation> getRewardAnnotationsList()
	{
		return new ArrayList<>(getRewardAnnotations().values());
	}

	/**
	 * Does this UMB file have any reward annotations?
	 */
	public boolean hasRewardAnnotations()
	{
		return !getRewardAnnotations().isEmpty();
	}

	/**
	 * Get the number of reward annotations.
	 */
	public int getNumRewardAnnotations()
	{
		return getRewardAnnotations().size();
	}

	/**
	 * Get the {@code i}th reward annotation.
	 */
	public Annotation getRewardAnnotation(int i)
	{
		return getRewardAnnotationsList().get(i);
	}

	/**
	 * Get a reward annotation by its ID
	 * Throws an exception if no such annotation exists.
	 */
	public Annotation getRewardAnnotationByID(String rewardID) throws UMBException
	{
		Annotation annotation = getRewardAnnotations().get(rewardID);
		if (annotation == null) {
			throw new UMBException("Unknown reward annotation ID \"" + rewardID + "\"");
		}
		return annotation;
	}

	/**
	 * Does this UMB file have a reward annotation with the specified alias?
	 */
	public boolean hasRewardAnnotationWithAlias(String rewardAlias)
	{
		return annotationWithAliasExists(UMBFormat.REWARD_ANNOTATIONS_GROUP, rewardAlias);
	}

	/**
	 * Get a reward annotation by its alias.
	 * Throws an exception if no such annotation exists.
	 */
	public Annotation getRewardAnnotationByAlias(String rewardAlias) throws UMBException
	{
		String rewardID = getAnnotationIdForAlias(UMBFormat.REWARD_ANNOTATIONS_GROUP, rewardAlias);
		return getRewardAnnotationByID(rewardID);
	}

	/**
	 * Get the names of all reward annotations (name is alias if present or ID if not)
	 */
	public List<String> getRewardNames()
	{
		return getRewardAnnotationsList().stream()
				.map(Annotation::getName)
				.collect(Collectors.toCollection(ArrayList::new));
	}

	public boolean hasStateRewards(int i)
	{
		return getRewardAnnotation(i).appliesTo(UMBEntity.STATES);
	}

	public boolean hasChoiceRewards(int i)
	{
		return getRewardAnnotation(i).appliesTo(UMBEntity.CHOICES);
	}

	public boolean hasBranchRewards(int i)
	{
		return getRewardAnnotation(i).appliesTo(UMBEntity.BRANCHES);
	}

	/**
	 * Does this UMB file have any state valuations?
	 */
	public boolean hasStateValuations()
	{
		return stateValuations != null;
	}

	/**
	 * Get a {@link UMBBitPacking} object representing the state valuations metadata.
	 * Throws an exception if no such metadata is present.
	 */
	public UMBBitPacking getStateValuationBitPacking() throws UMBException
	{
		if (stateValuations == null) {
			throw new UMBException("No state valuation metadata present");
		}
		return new UMBBitPacking(stateValuations);
	}

	// Validation methods

	/**
	 * Check whether a field exists (is non-null) and throw an exception if not.
	 * @param field The field, as stored in {@link UMBIndex}
	 * @param fieldName The name of the field, as stored in {@link UMBIndex} (not JSON/UMB)
	 */
	private static void checkFieldExists(Object field, String fieldName) throws UMBException
	{
		if (field == null) {
			throw new UMBException("Required field \"" + fieldNameToUMB(fieldName) + "\" is missing");
		}
	}

	// Import/export from/to JSON

	/**
	 * Convert this index to JSON format.
	 */
	public String toJSON()
	{
		return gsonBuilder().toJson(this);
	}

	/**
	 * Parse an index from JSON format.
	 */
	public static UMBIndex fromJSON(String json) throws UMBException
	{
		try {
			UMBIndex umbIndex = gsonBuilder().fromJson(json, UMBIndex.class);
			umbIndex.buildAnnotationInfo();
			return umbIndex;
		} catch (JsonParseException e) {
			throw new UMBException(e.getMessage());
		}
	}

	/**
	 * Build derived info {@code annotationGroups} and {@code annotationAliasMaps}
	 * from the list of annotations in {@code annotations}.
	 */
	private void buildAnnotationInfo()
	{
		annotationGroups = new ArrayList<>();
		annotationAliasMaps = new LinkedHashMap<>();
		annotationGroups.addAll(annotations.keySet());
		for (Map.Entry<String, LinkedHashMap<String, Annotation>> entry2 : annotations.entrySet()) {
			String group = entry2.getKey();
			Map<String, String> aliasMap = new LinkedHashMap<>();
			annotationAliasMaps.put(group, aliasMap);
			for (Map.Entry<String, Annotation> entry : entry2.getValue().entrySet()) {
				entry.getValue().group = group;
				entry.getValue().id = entry.getKey();
				if (entry.getValue().alias != null) {
					aliasMap.put(entry.getValue().alias, entry.getKey());
				}
			}
		}
	}

	/**
	 * Configure a Gson object for UMB import/export.
	 */
	private static Gson gsonBuilder()
	{
		return new GsonBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
			.registerTypeHierarchyAdapter(UMBField.class, new EnumSerializer<>())
			.registerTypeHierarchyAdapter(UMBField.class, new EnumDeserializer<>())
			.setPrettyPrinting()
			.create();
	}

	/**
	 * Customised enum behaviour for UMB field (de)serialisation.
	 */
	interface UMBField
	{
		default String description()
		{
			return toUMB(((Enum) this).name());
		}

		/**
		 * Convert to UMB-style field values (lower case, hyphenated)
		 */
		static String toUMB(String value)
		{
			return value.toLowerCase().replace("_", "-");
		}
	}

	/**
	 * Convert the name of a field, as stored in {@link UMBIndex} to its name in UMB JSON,
	 * i.e., converting (possibly capitalised) camel case to lower case hyphenated.
	 * Actual serialisation is done with {@link FieldNamingPolicy#LOWER_CASE_WITH_DASHES}
	 * but this should match closely enough for error reporting etc.
	 */
	public static String fieldNameToUMB(String field)
	{
		return field.replaceAll("([a-z])([A-Z])", "$1-$2").replaceAll("^([A-Z])", "$1").toLowerCase();
	}

	/**
	 * Custom JSON serializer for enums, following style of UMB specification
	 */
	static class EnumSerializer<T extends Enum<T>> implements JsonSerializer<T>
	{
		@Override
		public JsonElement serialize(T t, Type type, JsonSerializationContext jsonSerializationContext)
		{
			// Format for UMB (lower case, hyphenated)
			return new JsonPrimitive(UMBField.toUMB(t.name()));
		}
	}

	/**
	 * Custom JSON deserializer for enums, following style of UMB specification
	 */
	static class EnumDeserializer<T extends Enum<T>> implements JsonDeserializer<T>
	{
		@Override
		public T deserialize(JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context)
		{
			String fieldValue = json.getAsString();
			try {
				// Look up enum, ignoring UMB field style conversion (lower case, hyphenated))
				// And throw an exception if lookup fails (GSON default is to just store null)
				if (!json.isJsonPrimitive()) {
					throw new IllegalArgumentException();
				}
				for (T enumConstant : ((Class<T>) typeOfT).getEnumConstants()) {
					if (UMBField.toUMB(enumConstant.name()).equals(UMBField.toUMB(fieldValue))) {
						return enumConstant;
					}
				}
				throw new IllegalArgumentException();
			} catch (IllegalArgumentException e) {
				String fieldName = fieldNameToUMB(((Class<T>) typeOfT).getSimpleName());
				throw new JsonParseException("Invalid value \"" + fieldValue + "\" for " + fieldName, e);
			}
		}
	}
}
