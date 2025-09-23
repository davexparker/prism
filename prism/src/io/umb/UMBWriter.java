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

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.PrimitiveIterator;
import java.util.StringJoiner;

/**
 * Class to handle writing to UMB files.
 */
public class UMBWriter
{
	/**
	 * The (JSON) index to be included in the UMB file.
	 */
	private final UMBIndex umbIndex;

	/**
	 * Representations of the data to be included in the UMB file.
	 */
	private final List<UMBDataFile> umbDataFiles = new ArrayList<>();

	/**
	 * Construct a new {@link UMBWriter} to create a UMB file.
	 */
	public UMBWriter()
	{
		umbIndex = new UMBIndex();
	}

	/**
	 * Get access to the index that will be included in the export file.
	 */
	public UMBIndex getUmbIndex()
	{
		return umbIndex;
	}

	// Methods to add core model info

	/**
	 * Add the state choice offsets, as an iterator of longs
	 */
	public void addStateChoiceOffsets(PrimitiveIterator.OfLong stateChoiceOffsets)
	{
		addLongArray(UMBFormat.STATE_CHOICE_OFFSETS_FILE, stateChoiceOffsets, umbIndex.getNumStates() + 1);
	}

	/**
	 * Add the state choice offsets, as an iterator of ints
	 */
	public void addStateChoiceOffsets(PrimitiveIterator.OfInt stateChoiceOffsets)
	{
		addLongArray(UMBFormat.STATE_CHOICE_OFFSETS_FILE, new UMBUtils.IntToLongIteratorAdapter(stateChoiceOffsets), umbIndex.getNumStates() + 1);
	}

	/**
	 * Add the choice branch offsets, as an iterator of longs
	 */
	public void addChoiceBranchOffsets(PrimitiveIterator.OfLong choiceBranchOffsets)
	{
		addLongArray(UMBFormat.CHOICE_BRANCH_OFFSETS_FILE, choiceBranchOffsets, umbIndex.getNumChoices() + 1);
	}

	/**
	 * Add the choice branch offsets, as an iterator of ints
	 */
	public void addChoiceBranchOffsets(PrimitiveIterator.OfInt choiceBranchOffsets)
	{
		addLongArray(UMBFormat.CHOICE_BRANCH_OFFSETS_FILE, new UMBUtils.IntToLongIteratorAdapter(choiceBranchOffsets), umbIndex.getNumChoices() + 1);
	}

	/**
	 * Add the branch targets, as an iterator of ints
	 */
	public void addBranchTargets(PrimitiveIterator.OfInt branchTargets)
	{
		addLongArray(UMBFormat.BRANCH_TARGETS_FILE, new UMBUtils.IntToLongIteratorAdapter(branchTargets), umbIndex.getNumBranches());
	}

	/**
	 * Add the branch targets, as an iterator of long
	 */
	public void addBranchTargets(PrimitiveIterator.OfLong branchTargets)
	{
		addLongArray(UMBFormat.BRANCH_TARGETS_FILE, branchTargets, umbIndex.getNumBranches());
	}

	/**
	 * Add the branch targets, as an iterator of doubles
	 * For an interval model, two values (lower/upper bound, successively) should be supplied for each branch.
	 */
	public void addBranchProbabilities(PrimitiveIterator.OfDouble branchValues)
	{
		long numProbs = umbIndex.transitionSystem.branchProbabilityType.intervals() ? umbIndex.getNumBranches() * 2 : umbIndex.getNumBranches();
		addDoubleArray(UMBFormat.BRANCH_PROBABILITIES_FILE, branchValues, numProbs);
	}

	/**
	 * Add the exit rates for each state of a CTMC, as an iterator of doubles
	 */
	public void addExitRates(PrimitiveIterator.OfDouble exitRates)
	{
		addDoubleArray(UMBFormat.EXIT_RATES_FILE, exitRates, umbIndex.getNumStates());
	}

	/**
	 * Add the initial states, as a BitSet
	 */
	public void addInitialStates(BitSet initStates) throws UMBException
	{
		addBooleanArray(UMBFormat.INITIAL_STATES_FILE, initStates, umbIndex.getNumStates());
	}

	/**
	 * Add the initial states, in sparse form, i.e., a list of (int) state indices
	 */
	public void addInitialStates(PrimitiveIterator.OfInt initStates) throws UMBException
	{
		BitSet bsInitStates = new BitSet();
		initStates.forEachRemaining((int s) -> bsInitStates.set(s));
		addBooleanArray(UMBFormat.INITIAL_STATES_FILE, bsInitStates, umbIndex.getNumStates());
	}

	/**
	 * Add the indices for actions of all choices
	 * @param choiceActionIndices Iterator providing the indices for actions of all choices
	 */
	public void addChoiceActionIndices(PrimitiveIterator.OfInt choiceActionIndices) throws UMBException
	{
		addIntArray(UMBFormat.CHOICE_ACTIONS_FILE, choiceActionIndices, umbIndex.getNumChoices());
	}

	/**
	 * Add the indices for actions of all branches
	 * @param branchActionIndices Iterator providing the indices for actions of all branches
	 */
	public void addBranchActionIndices(PrimitiveIterator.OfInt branchActionIndices) throws UMBException
	{
		addIntArray(UMBFormat.BRANCH_ACTIONS_FILE, branchActionIndices, umbIndex.getNumBranches());
	}

	/**
	 * Add the names of all actions as strings (can include "")
	 * @param actionStrings Iterator providing the names for all actions
	 */
	public void addActionStrings(List<String> actionStrings) throws UMBException
	{
		int numStrings = actionStrings.size();
		long[] stringOffsets = new long[numStrings + 1];
		stringOffsets[0] = 0;
		for (int i = 0; i < numStrings; i++) {
			stringOffsets[i + 1] = stringOffsets[i] + actionStrings.get(i).getBytes().length;
		}
		PrimitiveIterator.OfLong it = Arrays.stream(stringOffsets).iterator();
		addLongArray(UMBFormat.ACTION_STRING_OFFSETS_FILE, it, umbIndex.getNumActions() + 1);
		addStringList(UMBFormat.ACTION_STRINGS_FILE, actionStrings);
	}

	// Methods to add standard annotations

	/**
	 * Add a new state AP annotation.
	 * @param apAlias AP annotation alias
	 * @param apStates BitSet providing indices of states satisfying the AP
	 */
	public void addStateAP(String apAlias, BitSet apStates) throws UMBException
	{
		UMBIndex.Annotation annotation = umbIndex.addAnnotation(UMBFormat.AP_ANNOTATIONS_GROUP, apAlias, UMBIndex.UMBType.BOOL);
		addBooleanDataToAnnotation(annotation, UMBIndex.UMBEntity.STATES, apStates);
	}

	/**
	 * Add a new reward annotation, for now without any data attached.
	 * If the alias (name) is non-empty, there should not already exist a reward annotation with the same alias.
	 * @param rewardAlias Optional alias (name) for the rewards (can be omitted: "" or null)
	 */
	public String addRewards(String rewardAlias) throws UMBException
	{
		UMBIndex.Annotation annotation = umbIndex.addAnnotation(UMBFormat.REWARD_ANNOTATIONS_GROUP, rewardAlias, UMBIndex.UMBType.DOUBLE);
		return annotation.id;
	}

	/**
	 * Add state rewards to a previously created reward annotation.
	 * @param rewardID Reward annotation ID
	 * @param stateRewards Iterator providing values defining the reward
	 */
	public void addStateRewardsByID(String rewardID, PrimitiveIterator.OfDouble stateRewards) throws UMBException
	{
		UMBIndex.Annotation annotation = umbIndex.getAnnotation(UMBFormat.REWARD_ANNOTATIONS_GROUP, rewardID);
		addDoubleDataToAnnotation(annotation, UMBIndex.UMBEntity.STATES, stateRewards);
	}

	/**
	 * Add choice rewards to a previously created reward annotation.
	 * @param rewardID Reward annotation ID
	 * @param choiceRewards Iterator providing values defining the reward
	 */
	public void addChoiceRewardsByID(String rewardID, PrimitiveIterator.OfDouble choiceRewards) throws UMBException
	{
		UMBIndex.Annotation annotation = umbIndex.getAnnotation(UMBFormat.REWARD_ANNOTATIONS_GROUP, rewardID);
		addDoubleDataToAnnotation(annotation, UMBIndex.UMBEntity.CHOICES, choiceRewards);
	}

	/**
	 * Add branch rewards to a previously created reward annotation.
	 * @param rewardID Reward annotation ID
	 * @param branchRewards Iterator providing values defining the reward
	 */
	public void addBranchRewardsByID(String rewardID, PrimitiveIterator.OfDouble branchRewards) throws UMBException
	{
		UMBIndex.Annotation annotation = umbIndex.getAnnotation(UMBFormat.REWARD_ANNOTATIONS_GROUP, rewardID);
		addDoubleDataToAnnotation(annotation, UMBIndex.UMBEntity.BRANCHES, branchRewards);
	}

	/**
	 * Add a new reward annotation, applied to states.
	 * If the alias (name) is non-empty, there should not already exist a reward annotation with the same alias.
	 * If you want to add a reward annotation that applies to multiple entities,
	 * e.g., to both states and branches, use first {@link #addRewards(String)}
	 * and then {@link #addStateRewardsByID} and {@link #addBranchRewardsByID}.
	 * @param rewardAlias Optional alias (name) for the rewards (can be omitted: "" or null)
	 * @param stateRewards Iterator providing values defining the reward
	 */
	public void addStateRewards(String rewardAlias, PrimitiveIterator.OfDouble stateRewards) throws UMBException
	{
		addStateRewardsByID(addRewards(rewardAlias), stateRewards);
	}

	/**
	 * Add a new reward annotation, applied to choices.
	 * If the alias (name) is non-empty, there should not already exist a reward annotation with the same alias.
	 * If you want to add a reward annotation that applies to multiple entities,
	 * e.g., to both states and branches, use first {@link #addRewards(String)}
	 * and then {@link #addStateRewardsByID} and {@link #addBranchRewardsByID}.
	 * @param rewardAlias Optional alias (name) for the rewards (can be omitted: "" or null)
	 * @param choiceRewards Iterator providing values defining the reward
	 */
	public void addChoiceRewards(String rewardAlias, PrimitiveIterator.OfDouble choiceRewards) throws UMBException
	{
		addChoiceRewardsByID(addRewards(rewardAlias), choiceRewards);
	}

	/**
	 * Add a new reward annotation, applied to branches.
	 * If the alias (name) is non-empty, there should not already exist a reward annotation with the same alias.
	 * If you want to add a reward annotation that applies to multiple entities,
	 * e.g., to both states and branches, use first {@link #addRewards(String)}
	 * and then {@link #addStateRewardsByID} and {@link #addBranchRewardsByID}.
	 * @param rewardAlias Optional alias (name) for the rewards (can be omitted: "" or null)
	 * @param branchRewards Iterator providing values defining the reward
	 */
	public void addBranchRewards(String rewardAlias, PrimitiveIterator.OfDouble branchRewards) throws UMBException
	{
		addBranchRewardsByID(addRewards(rewardAlias), branchRewards);
	}

	/**
	 * Add a new boolean-valued variable annotation.
	 * @param variableAlias Variable annotation alias
	 * @param bitset BitSet providing values for the variable
	 */
	public void addBooleanVariableDefinition(String variableAlias, BitSet bitset) throws UMBException
	{
		UMBIndex.Annotation annotation = umbIndex.addAnnotation(UMBFormat.VARIABLE_ANNOTATIONS_GROUP, variableAlias, UMBIndex.UMBType.BOOL);
		addBooleanDataToAnnotation(annotation, UMBIndex.UMBEntity.STATES, bitset);
	}

	/**
	 * Add a new int-valued variable annotation.
	 * @param variableAlias Variable annotation alias
	 * @param varValues Iterator providing values for the variable
	 */
	public void addIntVariableDefinition(String variableAlias, PrimitiveIterator.OfInt varValues) throws UMBException
	{
		UMBIndex.Annotation annotation = umbIndex.addAnnotation(UMBFormat.VARIABLE_ANNOTATIONS_GROUP, variableAlias, UMBIndex.UMBType.INT);
		addIntDataToAnnotation(annotation, UMBIndex.UMBEntity.STATES, varValues);
	}

	// Methods to add annotations

	/**
	 * Add a new annotation, for now without any data attached.
	 * If an alias is provided, there should not already exist
	 * an annotation in the same group with the same alias.
	 * @param group The ID of the group
	 * @param alias Optional alias (name) for the annotation ("" or null if not needed)
	 * @param type The type of the values to be stored in the annotation
	 */
	public UMBIndex.Annotation addAnnotation(String group, String alias, UMBIndex.UMBType type) throws UMBException
	{
		return umbIndex.addAnnotation(group, alias, type);
	}

	/**
	 * Add a new Boolean-valued annotation.
	 * If an alias is provided, there should not already exist
	 * an annotation in the same group with the same alias.
	 * @param group The ID of the group
	 * @param alias Optional alias (name) for the annotation ("" or null if not needed)
	 * @param appliesTo The entity to which the annotation applies
	 * @param bitset BitSet providing data
	 */
	public void addBooleanAnnotation(String group, String alias, UMBIndex.UMBEntity appliesTo, BitSet bitset) throws UMBException
	{
		UMBIndex.Annotation annotation = addAnnotation(group, alias, UMBIndex.UMBType.BOOL);
		addBooleanDataToAnnotation(annotation, appliesTo, bitset);
	}

	/**
	 * Add a new double-valued annotation.
	 * If an alias is provided, there should not already exist
	 * an annotation in the same group with the same alias.
	 * @param group The ID of the group
	 * @param alias Optional alias (name) for the annotation ("" or null if not needed)
	 * @param appliesTo The entity to which the annotation applies
	 * @param doubleValues Iterator providing data
	 */
	public void addDoubleAnnotation(String group, String alias, UMBIndex.UMBEntity appliesTo, PrimitiveIterator.OfDouble doubleValues) throws UMBException
	{
		UMBIndex.Annotation annotation = addAnnotation(group, alias, UMBIndex.UMBType.DOUBLE);
		addDoubleDataToAnnotation(annotation, appliesTo, doubleValues);
	}

	/**
	 * Add new boolean-valued data to an existing annotation.
	 * @param annotation The annotation
	 * @param appliesTo The entity to which the annotation applies
	 * @param bitset BitSet providing data
	 */
	public void addBooleanDataToAnnotation(UMBIndex.Annotation annotation, UMBIndex.UMBEntity appliesTo, BitSet bitset) throws UMBException
	{
		if (annotation.appliesTo(appliesTo)) {
			throw new UMBException("Duplicate data for " + appliesTo + "s in annotation \"" + annotation.id + "\" in group \"" + annotation.group + "\"");
		}
		annotation.addAppliesTo(appliesTo);
		long annotationSize = umbIndex.getAnnotationDataSize(appliesTo);
		addBooleanArray(annotation.getFilename(appliesTo), bitset, annotationSize);
	}

	/**
	 * Add new int-valued data to an existing annotation.
	 * @param annotation The annotation
	 * @param appliesTo The entity to which the annotation applies
	 * @param intValues Iterator providing data
	 */
	public void addIntDataToAnnotation(UMBIndex.Annotation annotation, UMBIndex.UMBEntity appliesTo, PrimitiveIterator.OfInt intValues) throws UMBException
	{
		if (annotation.appliesTo(appliesTo)) {
			throw new UMBException("Duplicate data for " + appliesTo + "s in annotation \"" + annotation.id + "\" in group \"" + annotation.group + "\"");
		}
		annotation.addAppliesTo(appliesTo);
		long annotationSize = umbIndex.getAnnotationDataSize(appliesTo);
		addIntArray(annotation.getFilename(appliesTo), intValues, annotationSize);
	}

	/**
	 * Add new double-valued data to an existing annotation.
	 * @param annotation The annotation
	 * @param appliesTo The entity to which the annotation applies
	 * @param doubleValues Iterator providing data
	 */
	public void addDoubleDataToAnnotation(UMBIndex.Annotation annotation, UMBIndex.UMBEntity appliesTo, PrimitiveIterator.OfDouble doubleValues) throws UMBException
	{
		if (annotation.appliesTo(appliesTo)) {
			throw new UMBException("Duplicate data for " + appliesTo + "s in annotation \"" + annotation.id + "\" in group \"" + annotation.group + "\"");
		}
		annotation.addAppliesTo(appliesTo);
		long annotationSize = umbIndex.getAnnotationDataSize(appliesTo);
		addDoubleArray(annotation.getFilename(appliesTo), doubleValues, annotationSize);
	}

	// Methods to add binary files

	public void addBooleanArray(String name, BitSet booleanValues, long size)
	{
		umbDataFiles.add(new BooleanArray(booleanValues, size, name));
	}

	public void addCharArray(String name, PrimitiveIterator.OfLong longValues, long size)
	{
		umbDataFiles.add(new LongArray(longValues, size, name));
	}

	public void addIntArray(String name, PrimitiveIterator.OfInt intValues, long size)
	{
		umbDataFiles.add(new IntArray(intValues, size, name));
	}

	public void addLongArray(String name, PrimitiveIterator.OfLong longValues, long size)
	{
		umbDataFiles.add(new LongArray(longValues, size, name));
	}

	public void addDoubleArray(String name, PrimitiveIterator.OfDouble doubleValues, long size)
	{
		umbDataFiles.add(new DoubleArray(doubleValues, size, name));
	}

	public void addStringList(String name, List<String> strings)
	{
		umbDataFiles.add(new StringListFile(strings, name));
	}

	/**
	 * Export content to a UMB file.
	 * @param fileOut The file to export to.
	 */
	public void export(File fileOut) throws UMBException
	{
		export(fileOut, true);
	}

	/**
	 * Export content to a UMB file.
	 * @param fileOut The file to export to.
	 * @param zipped Whether to zip the file
	 */
	public void export(File fileOut, boolean zipped) throws UMBException
	{
		UMBOut umbOut = new UMBOut(fileOut, zipped);
		exportIndex(umbOut);
		for (UMBDataFile umbDataFile : umbDataFiles) {
			exportUMBFile(umbDataFile, umbOut);
		}
		umbOut.close();
	}

	/**
	 * Export content to a textual representation of a UMB file.
	 * @param sb Where to write the text to.
	 */
	public void exportAsText(StringBuffer sb) throws UMBException
	{
		exportIndexToText(sb);
		for (UMBDataFile umbDataFile : umbDataFiles) {
			exportUMBFileToText(umbDataFile, sb);
		}
	}

	/**
	 * Export the index to a UMB file.
	 */
	private void exportIndex(UMBOut umbOut) throws UMBException
	{
		exportTextToTar(umbIndex.toJSON(), UMBFormat.INDEX_FILE, umbOut);
	}

	/**
	 * Export the index to a StringBuffer
	 */
	private void exportIndexToText(StringBuffer sb)
	{
		exportTextToText(umbIndex.toJSON(), new File(UMBFormat.INDEX_FILE), sb);
	}

	private void exportTextToTar(String text, String filename, UMBOut umbOut) throws UMBException
	{
		byte[] bytes = text.getBytes();
		umbOut.createArchiveEntry(filename, bytes.length);
		umbOut.write(bytes, 0, bytes.length);
		umbOut.closeArchiveEntry();
	}

	private void exportUMBFile(UMBDataFile umbDataFile, UMBOut umbOut) throws UMBException
	{
		umbOut.createArchiveEntry(umbDataFile.name, umbDataFile.totalBytes());
		Iterator<ByteBuffer> byteIter = umbDataFile.byteIterator();
		ByteBuffer buffer;
		while (byteIter.hasNext()) {
			buffer = byteIter.next();
			umbOut.write(buffer.array(), 0, buffer.position());
		}
		umbOut.closeArchiveEntry();
	}

	/*private void exportIntArrayToTarBuffered(PrimitiveIterator.OfInt intValues, int size, File file) throws IOException
	{
		TarArchiveEntry entry = new TarArchiveEntry(file);
		entry.setSize((long) size * Integer.BYTES);
		tarOut.putArchiveEntry(entry);
		int bufferSize = 100;
		ByteBuffer buffer = ByteBuffer.allocate(bufferSize * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
		while (intValues.hasNext()) {
			buffer.putInt(intValues.nextInt());
			if (!buffer.hasRemaining()) {
				tarOut.write(buffer.array(), 0, buffer.position());
				buffer.clear();
			}
		}
		if (buffer.position() > 0) {
			tarOut.write(buffer.array(), 0, buffer.position());
			buffer.clear();
		}
		tarOut.closeArchiveEntry();
	}*/

	private void exportTextToText(String text, File file, StringBuffer sb)
	{
		sb.append("/" + file.getName() + ":\n");
		sb.append(text);
		sb.append("\n");
	}

	private void exportUMBFileToText(UMBDataFile umbDataFile, StringBuffer sb)
	{
		sb.append("/" + umbDataFile.name + ":\n");
		sb.append(umbDataFile.toText());
		sb.append("\n");
	}

	private static String toArrayString(Iterator<?> iter)
	{
		StringJoiner sj = new StringJoiner(",", "[", "]");
		iter.forEachRemaining(o -> sj.add(o.toString()));
		return sj.toString();
	}

	/**
	 * Class to manage writing to the zipped archive for a UMB file
	 */
	private static class UMBOut
	{
		/** Output stream for zip file */
		private final OutputStream fsOut;
		/** Output stream for zipping */
		private final CompressorOutputStream zipOut;
		/** Output stream for tar file */
		private final ArchiveOutputStream tarOut;

		/**
		 * Open a new UMB file for writing
		 * @param fileOut The file to write to
		 */
		public UMBOut(File fileOut) throws UMBException
		{
			this(fileOut, true);
		}

		/**
		 * Open a new UMB file for writing
		 * @param fileOut The file to write to
		 * @param zipped Whether to zip the file
		 */
		public UMBOut(File fileOut, boolean zipped) throws UMBException
		{
			this(fileOut, zipped ? UMBFormat.DEFAULT_COMPRESSION_FORMAT : null);
		}

		/**
		 * Open a new UMB file for writing
		 * @param fileOut The file to write to
		 * @param compressionFormat How to zip the file (null means no zipping)
		 */
		public UMBOut(File fileOut, UMBFormat.CompressionFormat compressionFormat) throws UMBException
		{
			try {
				// Open file/zip/tar
				fsOut = new BufferedOutputStream(Files.newOutputStream(fileOut.toPath()));
				if (compressionFormat != null) {
					zipOut = new CompressorStreamFactory().createCompressorOutputStream(compressionFormat.extension(), fsOut);
					tarOut = new TarArchiveOutputStream(zipOut);
				} else {
					zipOut = null;
					tarOut = new TarArchiveOutputStream(fsOut);
				}
			} catch (IOException e) {
				throw new UMBException("Could not create UMB file: " + e.getMessage());
			} catch (CompressorException e) {
				throw new UMBException("Could not create zip for UMB file: " + e.getMessage());
			}
		}

		/**
		 * Create an entry (a file) within the archive for subsequent writing.
		 * @param name Name of the file
		 * @param size Size of the file (number of bytes)
		 */
		public void createArchiveEntry(String name, long size) throws UMBException
		{
			try {
				File file = new File(name);
				TarArchiveEntry entry = new TarArchiveEntry(file);
				entry.setSize(size);
				tarOut.putArchiveEntry(entry);
			} catch (IOException e) {
				throw new UMBException("I/O error writing \"" + name + "\" to UMB file");
			}
		}

		/**
		 * Close the current entry (file) of the archive.
		 */
		public void closeArchiveEntry() throws UMBException
		{
			try {
				tarOut.closeArchiveEntry();
			} catch (IOException e) {
				throw new UMBException("I/O error writing to UMB file");
			}
		}

		/**
		 * Write some data (bytes) to the current entry.
		 * @param bytes The data, as an array of bytes
		 * @param off The offset into the array to start from
		 * @param len The number of bytes to write
		 */
		public void write(byte[] bytes, int off, int len) throws UMBException
		{
			try {
				tarOut.write(bytes, off, len);
			} catch (IOException e) {
				throw new UMBException("I/O error writing to UMB file");
			}
		}

		/**
		 * Close the UMB file.
		 */
		public void close() throws UMBException
		{
			try {
				if (tarOut != null) {
					tarOut.finish();
				}
				if (zipOut != null) {
					zipOut.close();
				}
				if (fsOut != null) {
					fsOut.close();
				}
			} catch (IOException e) {
				throw new UMBException("I/O error closing UMB file");
			}
		}
	}

	/**
	 * Classes representing the various data files stored in a UMB file.
	 */
	abstract static class UMBDataFile
	{
		protected String name;
		protected ByteBuffer buffer;

		public abstract long totalBytes();
		public abstract Iterator<ByteBuffer> byteIterator();
		public abstract String toText();
	}

	/**
	 * A UMB data file to be stored containing an array of values.
	 */
	abstract static class Array extends UMBDataFile
	{
		protected long size;
		protected int bufferSize;

		public abstract int numBytes();
		public abstract boolean hasNextBytes();
		public abstract void encodeNextBytes();

		public long totalBytes()
		{
			return size * numBytes();
		}

		public abstract Iterator<? extends Object> iterator();

		@Override
		public Iterator<ByteBuffer> byteIterator()
		{
			return new Iterator<ByteBuffer>()
			{
				{
					buffer = ByteBuffer.allocate(numBytes()).order(ByteOrder.LITTLE_ENDIAN);
				}

				@Override
				public boolean hasNext()
				{
					return hasNextBytes();
				}

				@Override
				public ByteBuffer next()
				{
					buffer.clear();
					encodeNextBytes();
					return buffer;
				}

			};
		}

		public String nextAsText()
		{
			return iterator().next().toString();
		}

		public String toText()
		{
			StringJoiner sj = new StringJoiner(",", "[", "]");
			while (hasNextBytes()) {
				sj.add(nextAsText());
			}
			return sj.toString();
		}
	}

	/**
	 * A UMB data file to be stored containing an array of booleans.
	 */
	static class BooleanArray extends Array
	{
		protected BitSet booleanValues;
		protected long[] booleanLongs;
		protected int posn;

		public BooleanArray(BitSet booleanValues, long size, String name)
		{
			this.booleanValues = booleanValues;
			booleanLongs = booleanValues.toLongArray();
			this.size = size;
			this.name = name;
			posn = 0;
		}

		@Override
		public long totalBytes()
		{
			return 8 * (((size - 1)/ 64) + 1);
		}

		@Override
		public Iterator<Boolean> iterator()
		{
			return new Iterator<>()
			{
				@Override
				public boolean hasNext()
				{
					return posn < size;
				}

				@Override
				public Boolean next()
				{
					return booleanValues.get(posn++);
				}
			};
		}

		@Override
		public int numBytes()
		{
			return Long.BYTES; // 8
		}

		@Override
		public boolean hasNextBytes()
		{
			return posn < size;
		}

		@Override
		public void encodeNextBytes()
		{
			int posn2 = posn / 64;
			buffer.putLong(booleanLongs.length > posn2 ? booleanLongs[posn2] : 0);
			posn += 64;
		}

		@Override
		public String toText()
		{
			StringJoiner sj = new StringJoiner("", "[", "]");
			iterator().forEachRemaining(b -> sj.add(b ? "1" : "0"));
			return sj.toString();
		}
	}

	/**
	 * A UMB data file to be stored containing an array of ints.
	 */
	static class IntArray extends Array
	{
		protected PrimitiveIterator.OfInt intValues;

		public IntArray(PrimitiveIterator.OfInt intValues, long size, String name)
		{
			this.intValues = intValues;
			this.size = size;
			this.name = name;
		}

		@Override
		public PrimitiveIterator.OfInt iterator()
		{
			return intValues;
		}

		@Override
		public int numBytes()
		{
			return Integer.BYTES; // 4
		}

		@Override
		public boolean hasNextBytes()
		{
			return intValues.hasNext();
		}

		@Override
		public void encodeNextBytes()
		{
			buffer.putInt(intValues.nextInt());
		}
	}

	/**
	 * A UMB data file to be stored containing an array of doubles.
	 */
	static class LongArray extends Array
	{
		protected PrimitiveIterator.OfLong longValues;

		public LongArray(PrimitiveIterator.OfLong longValues, long size, String name)
		{
			this.longValues = longValues;
			this.size = size;
			this.name = name;
		}

		@Override
		public PrimitiveIterator.OfLong iterator()
		{
			return longValues;
		}

		@Override
		public int numBytes()
		{
			return Long.BYTES; // 8
		}

		@Override
		public boolean hasNextBytes()
		{
			return longValues.hasNext();
		}

		@Override
		public void encodeNextBytes()
		{
			buffer.putLong(longValues.nextLong());
		}
	}

	/**
	 * A UMB data file to be stored containing an array of doubles.
	 */
	class DoubleArray extends Array
	{
		protected PrimitiveIterator.OfDouble doubleValues;

		public DoubleArray(PrimitiveIterator.OfDouble doubleValues, long size, String name)
		{
			this.doubleValues = doubleValues;
			this.size = size;
			this.name = name;
		}

		@Override
		public PrimitiveIterator.OfDouble iterator()
		{
			return doubleValues;
		}

		@Override
		public int numBytes()
		{
			return Double.BYTES; // 8
		}

		@Override
		public boolean hasNextBytes()
		{
			return doubleValues.hasNext();
		}

		public String nextAsText()
		{
			return formatDouble(doubleValues.nextDouble());
		}

		@Override
		public void encodeNextBytes()
		{
			buffer.putDouble(doubleValues.nextDouble());
		}
	}

	/**
	 * A UMB data file containing a list of strings
	 */
	static class StringListFile extends UMBDataFile
	{
		protected List<String> strings;
		protected int totalBytes;
		protected byte[] stringBytes;

		public StringListFile(List<String> strings, String name)
		{
			this.name = name;
			this.strings = strings;
			int numStrings = strings.size();
			byte[][] stringsAsBytes = new byte[numStrings][];
			totalBytes = 0;
			for (int i = 0; i < numStrings; i++) {
				stringsAsBytes[i] = strings.get(i).getBytes(StandardCharsets.UTF_8);
				totalBytes += stringsAsBytes[i].length;
			}
			stringBytes = new byte[totalBytes];
			int count = 0;
			for (int i = 0; i < numStrings; i++) {
				System.arraycopy(stringsAsBytes[i], 0, stringBytes, count, stringsAsBytes[i].length);
				count += stringsAsBytes[i].length;
			}
		}

		@Override
		public long totalBytes()
		{
			return totalBytes;
		}

		@Override
		public Iterator<ByteBuffer> byteIterator()
		{
			return new Iterator<>()
			{
				boolean hasNext = true;
				{
					buffer = ByteBuffer.wrap(stringBytes);
					buffer.position(totalBytes);
				}

				@Override
				public boolean hasNext()
				{
					return hasNext;
				}

				@Override
				public ByteBuffer next()
				{
					hasNext = false;
					return buffer;
				}
			};
		}

		@Override
		public String toText()
		{
			return "[" + String.join(",", strings) + "]";
		}
	}

	// Utility methods

	public final static int FORMAT_DOUBLE_PRECISION = 14;

	/**
	 * Format a double as a string for use in the textual version of UMB
	 * @param d Double to format
	 */
	public String formatDouble(double d)
	{
		return formatDouble(d, FORMAT_DOUBLE_PRECISION);
	}

	/**
	 * Format a double as a string for use in the textual version of UMB
	 * @param d Double to format
	 * @param precision Precision (nymber of significant figures)
	 */
	public String formatDouble(double d, int precision)
	{
		// Format as either decimal or scientific notation, depending on precision
		String result = String.format((Locale) null, "%." + precision + "g", d);
		// Remove trailing zeros (keep one if of form x.000...)
		result = result.replaceFirst("(\\.[0-9]*?)0+(e|$)", "$1$2");
		return result.replaceFirst("\\.(e|$)", ".0$1");
	}
}
