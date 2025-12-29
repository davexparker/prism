/*
 * Copyright 2025 Dave Parker (University of Oxford)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.pmctools.umbj;

import java.util.List;
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
	public static final String CHOICE_ACTIONS_FILE = "choice-to-choice-action" + BIN_FILE_EXT;
	public static final String BRANCH_ACTIONS_FILE = "branch-to-branch-action" + BIN_FILE_EXT;
	public static final String CHOICE_ACTION_STRING_OFFSETS_FILE = "choice-action-to-string" + BIN_FILE_EXT;
	public static final String CHOICE_ACTION_STRINGS_FILE = "choice-action-strings" + BIN_FILE_EXT;
	public static final String BRANCH_ACTION_STRING_OFFSETS_FILE = "branch-action-to-string" + BIN_FILE_EXT;
	public static final String BRANCH_ACTION_STRINGS_FILE = "branch-action-strings" + BIN_FILE_EXT;
	public static final String OBSERVATIONS_DIR = "observations";
	public static final String OBSERVATIONS_FILE = "values" + BIN_FILE_EXT;

	// Annotations

	/** Filename for storing an annotation's values */
	public static final String ANNOTATION_VALUES_FILE = "values" + BIN_FILE_EXT;

	/** Location for annotations (directory in zip) */
	public static final String ANNOTATIONS_DIR = "annotations";

	// Subdirectories for built-in annotation groups

	public static final String AP_ANNOTATIONS_GROUP = "aps";
	public static final String REWARD_ANNOTATIONS_GROUP = "rewards";

	// Valuations

	/** Location for valuations (directory in zip) */
	public static final String VALUATIONS_DIR = "valuations";

	/** Filename for storing valuation variable values */
	public static final String VALUATIONS_FILE = "valuations" + BIN_FILE_EXT;

	// Allowable compression formats

	public enum CompressionFormat {
		GZIP,
		XZ;
		public String extension() {
			switch (this) {
				case GZIP: return "gz";
				case XZ: return "xz";
				default: throw new IllegalStateException("Unknown compression format: " + this);
			}
		}
	}

	/** Allowable compression formats (strict) */
	public static final List<CompressionFormat> ALLOWED_COMPRESSION_FORMATS = List.of(CompressionFormat.XZ);

	/** Default compression format */
	public static final CompressionFormat DEFAULT_COMPRESSION_FORMAT = CompressionFormat.XZ;

	/**
	 * Get the directory name for observations
	 */
	public static String observationsDir(UMBIndex.UMBEntity entity)
	{
		return OBSERVATIONS_DIR + "/" + "for-" + entity;
	}

	/**
	 * Get the filename for observations
	 */
	public static String observationsFile(UMBIndex.UMBEntity entity)
	{
		return observationsDir(entity) + "/" + OBSERVATIONS_FILE;
	}

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

	/**
	 * Get the directory name for a set of valuations
	 */
	public static String valuationsDir(UMBIndex.UMBEntity entity)
	{
		return VALUATIONS_DIR + "/" + "for-" + entity;
	}

	/**
	 * Get the filename for a set of valuations
	 */
	public static String valuationsFile(UMBIndex.UMBEntity entity)
	{
		return valuationsDir(entity) + "/" + VALUATIONS_FILE;
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
