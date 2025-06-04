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

import java.util.function.Predicate;

/**
 * Class storing conventions about how/where entities are stored in a UMB file.
 */
public class UMBFormat
{
	/** Filename for (JSON) index */
	public static final String INDEX_FILE = "index.json";

	/** File extension for binary files in the archive */
	public static final String BIN_FILE_EXT = ".bin";

	// Core model information

	public static final String STATE_CHOICE_OFFSETS_FILE = "state-to-choice" + BIN_FILE_EXT;
	public static final String CHOICE_BRANCH_OFFSETS_FILE = "choice-to-branch" + BIN_FILE_EXT;
	public static final String BRANCH_TARGETS_FILE = "branch-to-target" + BIN_FILE_EXT;
	public static final String BRANCH_PROBABILITIES_FILE = "branch-probabilities" + BIN_FILE_EXT;
	public static final String EXIT_RATES_FILE = "exit-rates" + BIN_FILE_EXT;
	public static final String INITIAL_STATES_FILE = "initial-states" + BIN_FILE_EXT;
	public static final String CHOICE_ACTIONS_FILE = "choice-to-action" + BIN_FILE_EXT;
	public static final String BRANCH_ACTIONS_FILE = "branch-to-action" + BIN_FILE_EXT;
	public static final String ACTION_STRING_OFFSETS_FILE = "action-to-action-strings" + BIN_FILE_EXT;
	public static final String ACTION_STRINGS_FILE = "action-strings" + BIN_FILE_EXT;

	// Annotations

	/** Filename for storing an annotation's values */
	public static final String ANNOTATION_VALUES_FILE = "values" + BIN_FILE_EXT;

	/** Location for annotations (directory in zip) */
	public static final String ANNOTATIONS_DIR = "annotations";

	// Subdirectories for built-in annotation groups

	public static final String AP_ANNOTATIONS_GROUP = "aps";
	public static final String REWARD_ANNOTATIONS_GROUP = "rewards";
	public static final String VARIABLE_ANNOTATIONS_GROUP = "variables";

	/**
	 * Get the directory name for an annotation
	 */
	public static String annotationDir(String group, String id, UMBIndex.UMBEntity entity)
	{
		return ANNOTATIONS_DIR + "/" + group + "/" + id + "/" + "for-" + entity;
	}

	/**
	 * Get the filename for an annotation
	 */
	public static String annotationFile(String group, String id, UMBIndex.UMBEntity entity)
	{
		return annotationDir(group, id, entity) + "/" + ANNOTATION_VALUES_FILE;
	}

	// Utility functions

	/**
	 * Check whether a string represents a valid ID.
	 * @param id Proposed ID
	 */
	public static boolean isValidID(String id)
	{
		return id.matches("[a-z0-9_-]+");
	}

	/**
	 * Convert a string to a valid ID.
	 * @param s String to convert
	 */
	public static String toValidID(String s)
	{
		return s.toLowerCase().replaceAll("[^a-z0-9_-]", "_").replace("^[0-9]", "_");
	}

	/**
	 * Convert a string to a unique valid ID.
	 * @param s String to convert
	 * @param idExists Predicate defining what IDs already exist
	 */
	public static String toValidUniqueID(String s, Predicate<String> idExists)
	{
		String id = toValidID(s);
		while (idExists.test(id)) {
			id += "_";
		}
		return id;
	}
}
