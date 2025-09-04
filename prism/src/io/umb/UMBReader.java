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

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * Class to handle reading from UMB files.
 */
public class UMBReader
{
	/**
	 * File to be read from.
	 */
	private final File fileIn;

	/**
	 * The (JSON) index extracted from the UMB file.
	 */
	private UMBIndex umbIndex;

	/**
	 * Construct a new {@link UMBReader} reading from the specified file.
	 * @param fileIn The UMB file to read from.
	 */
	public UMBReader(File fileIn) throws UMBException
	{
		this.fileIn = fileIn;
		extractIndex();
	}

	/**
	 * Extract, parse, validate and store the JSON index.
	 */
	private void extractIndex() throws UMBException
	{
		// Extract index JSON as string
		UMBIn umbIn = open();
		umbIn.findArchiveEntry(UMBFormat.INDEX_FILE);
		String json = umbIn.readAsString();
		umbIn.close();

		// Parse/validate JSON
		// Note that we check for required fields, but do not complain about unexpected ones
		// (GSON does not make the latter process very easy)
		umbIndex = UMBIndex.fromJSON(json);
		umbIndex.validate();
	}

	/**
	 * Get the (JSON) index of the UMB file.
	 */
	public UMBIndex getUMBIndex()
	{
		return umbIndex;
	}

	// Methods to extract core model info

	/**
	 * Extract the state choice offsets.
	 */
	public void extractStateChoiceOffsets(LongConsumer longConsumer) throws UMBException
	{
		extractLongArray(UMBFormat.STATE_CHOICE_OFFSETS_FILE, umbIndex.getNumStates() + 1, longConsumer);
	}

	/**
	 * Extract the choice branch offsets.
	 */
	public void extractChoiceBranchOffsets(LongConsumer longConsumer) throws UMBException
	{
		extractLongArray(UMBFormat.CHOICE_BRANCH_OFFSETS_FILE, umbIndex.getNumChoices() + 1, longConsumer);
	}

	/**
	 * Extract the branch targets.
	 */
	public void extractBranchTargets(LongConsumer longConsumer) throws UMBException
	{
		extractLongArray(UMBFormat.BRANCH_TARGETS_FILE, umbIndex.getNumBranches(), longConsumer);
	}

	/**
	 * Extract the branch probabilities.
	 * For an interval model, this will extract two values (lower/upper bound, successively) for each branch.
	 */
	public void extractBranchProbabilities(DoubleConsumer doubleConsumer) throws UMBException
	{
		long numProbs = umbIndex.transitionSystem.branchProbabilityType.intervals() ? umbIndex.getNumBranches() * 2 : umbIndex.getNumBranches();
		extractDoubleArray(UMBFormat.BRANCH_PROBABILITIES_FILE, numProbs, doubleConsumer);
	}

	/**
	 * Extract the exit rates.
	 */
	public void extractExitRates(DoubleConsumer doubleConsumer) throws UMBException
	{
		extractDoubleArray(UMBFormat.EXIT_RATES_FILE, umbIndex.getNumStates(), doubleConsumer);
	}

	/**
	 * Extract the initial states, in sparse form, i.e., a list of (int) state indices
	 */
	public void extractInitialStates(LongConsumer longConsumer) throws UMBException
	{
		extractBooleanArraySparse(UMBFormat.INITIAL_STATES_FILE, longConsumer);
	}

	/**
	 * Does this file store actions for choices?
	 */
	public boolean hasChoiceActionIndices() throws UMBException
	{
		return fileExists(UMBFormat.CHOICE_ACTIONS_FILE);
	}

	/**
	 * Extract the indices for actions of all choices
	 */
	public void extractChoiceActionIndices(IntConsumer intConsumer) throws UMBException
	{
		extractIntArray(UMBFormat.CHOICE_ACTIONS_FILE, umbIndex.getNumChoices(), intConsumer);
	}

	/**
	 * Does this file store actions for branches?
	 */
	public boolean hasBranchActionIndices() throws UMBException
	{
		return fileExists(UMBFormat.BRANCH_ACTIONS_FILE);
	}

	/**
	 * Extract the indices for actions of all branches
	 */
	public void extractBranchActionIndices(IntConsumer intConsumer) throws UMBException
	{
		extractIntArray(UMBFormat.BRANCH_ACTIONS_FILE, umbIndex.getNumBranches(), intConsumer);
	}

	/**
	 * Does this file store a list of action strings?
	 */
	public boolean hasActionStrings() throws UMBException
	{
		return fileExists(UMBFormat.ACTION_STRING_OFFSETS_FILE) && fileExists(UMBFormat.ACTION_STRINGS_FILE);
	}

	/**
	 * Extract the action strings
	 */
	public void extractActionStrings(Consumer<String> stringConsumer) throws UMBException
	{
		int numActions = umbIndex.getNumActions();
		List<Long> actionStringOffsets = new ArrayList<>(numActions);
		extractLongArray(UMBFormat.ACTION_STRING_OFFSETS_FILE, numActions + 1, actionStringOffsets::add);
		extractStringList(UMBFormat.ACTION_STRINGS_FILE, actionStringOffsets, stringConsumer);
	}

	// Utility methods for extracting date

	public <T extends LongConsumer> T extractStateChoiceCounts(T longConsumer) throws UMBException
	{
		extractStateChoiceOffsets(new OffsetsToCounts(longConsumer));
		return longConsumer;
	}

	public long extractMaxStateChoiceCount() throws UMBException
	{
		return extractStateChoiceCounts(new LongMax()).getMax();
	}

	// Methods to extract standard annotations

	/**
	 * Extract a state AP annotation via its index.
	 * @param i AP annotation index
	 * @param longConsumer Consumer to receive the indices of states satisfying the AP
	 */
	public void extractStateAP(int i, LongConsumer longConsumer) throws UMBException
	{
		UMBIndex.Annotation annotation = getUMBIndex().getAPAnnotation(i);
		extractBooleanAnnotationSparse(annotation, UMBIndex.UMBEntity.STATES, longConsumer);
	}

	/**
	 * Extract a state AP annotation via its ID.
	 * @param apID AP annotation ID
	 * @param longConsumer Consumer to receive the indices of states satisfying the AP
	 */
	public void extractStateAP(String apID, LongConsumer longConsumer) throws UMBException
	{
		UMBIndex.Annotation annotation = getUMBIndex().getAPAnnotationByID(apID);
		extractBooleanAnnotationSparse(annotation, UMBIndex.UMBEntity.STATES, longConsumer);
	}

	/**
	 * Extract a state reward annotation via its index.
	 * @param i Reward annotation index
	 * @param doubleConsumer Consumer to receive the values of the rewards
	 */
	public void extractStateRewards(int i, DoubleConsumer doubleConsumer) throws UMBException
	{
		UMBIndex.Annotation annotation = getUMBIndex().getRewardAnnotation(i);
		extractDoubleAnnotation(annotation, UMBIndex.UMBEntity.STATES, doubleConsumer);
	}

	/**
	 * Extract a state reward annotation from its alias.
	 * @param rewardID Reward annotation ID
	 * @param doubleConsumer Consumer to receive the values of the rewards
	 */
	public void extractStateRewards(String rewardID, DoubleConsumer doubleConsumer) throws UMBException
	{
		UMBIndex.Annotation annotation = getUMBIndex().getRewardAnnotationByID(rewardID);
		extractDoubleAnnotation(annotation, UMBIndex.UMBEntity.STATES, doubleConsumer);
	}

	/**
	 * Extract a choice reward annotation via its index.
	 * @param i Reward annotation index
	 * @param doubleConsumer Consumer to receive the values of the rewards
	 */
	public void extractChoiceRewards(int i, DoubleConsumer doubleConsumer) throws UMBException
	{
		UMBIndex.Annotation annotation = getUMBIndex().getRewardAnnotation(i);
		extractDoubleAnnotation(annotation, UMBIndex.UMBEntity.CHOICES, doubleConsumer);
	}

	/**
	 * Extract a choice reward annotation via its ID.
	 * @param rewardID Reward annotation ID
	 * @param doubleConsumer Consumer to receive the values of the rewards
	 */
	public void extractChoiceRewards(String rewardID, DoubleConsumer doubleConsumer) throws UMBException
	{
		UMBIndex.Annotation annotation = getUMBIndex().getRewardAnnotationByID(rewardID);
		extractDoubleAnnotation(annotation, UMBIndex.UMBEntity.CHOICES, doubleConsumer);
	}

	/**
	 * Extract a branch reward annotation via its index.
	 * @param i Reward annotation index
	 * @param doubleConsumer Consumer to receive the values of the rewards
	 */
	public void extractBranchRewards(int i, DoubleConsumer doubleConsumer) throws UMBException
	{
		UMBIndex.Annotation annotation = getUMBIndex().getRewardAnnotation(i);
		extractDoubleAnnotation(annotation, UMBIndex.UMBEntity.BRANCHES, doubleConsumer);
	}

	/**
	 * Extract a branch reward annotation via its ID.
	 * @param rewardID Reward annotation ID
	 * @param doubleConsumer Consumer to receive the values of the rewards
	 */
	public void extractBranchRewards(String rewardID, DoubleConsumer doubleConsumer) throws UMBException
	{
		UMBIndex.Annotation annotation = getUMBIndex().getRewardAnnotationByID(rewardID);
		extractDoubleAnnotation(annotation, UMBIndex.UMBEntity.BRANCHES, doubleConsumer);
	}

	// Methods to extract annotations

	public void extractBooleanAnnotationSparse(String group, String id, UMBIndex.UMBEntity appliesTo, LongConsumer longConsumer) throws UMBException
	{
		extractBooleanAnnotationSparse(umbIndex.getAnnotation(group, id), appliesTo, longConsumer);
	}

	public void extractBooleanAnnotationSparse(UMBIndex.Annotation annotation, UMBIndex.UMBEntity appliesTo, LongConsumer longConsumer) throws UMBException
	{
		String filename = annotation.getFilename(appliesTo);
		extractBooleanArraySparse(filename, longConsumer);
	}

	public void extractIndexedBooleanAnnotation(String group, String id, UMBIndex.UMBEntity appliesTo, LongBooleanConsumer longBooleanConsumer) throws UMBException
	{
		extractIndexedBooleanAnnotation(umbIndex.getAnnotation(group, id), appliesTo, longBooleanConsumer);
	}

	public void extractIndexedBooleanAnnotation(UMBIndex.Annotation annotation, UMBIndex.UMBEntity appliesTo, LongBooleanConsumer longBooleanConsumer) throws UMBException
	{
		String filename = annotation.getFilename(appliesTo);
		extractBooleanArray(filename, getUMBIndex().getAnnotationDataSize(appliesTo), new IndexedBooleanConsumer(longBooleanConsumer));
	}

	public void extractIntAnnotation(String group, String id, UMBIndex.UMBEntity appliesTo, IntConsumer intConsumer) throws UMBException
	{
		extractIntAnnotation(umbIndex.getAnnotation(group, id), appliesTo, intConsumer);
	}

	public void extractIntAnnotation(UMBIndex.Annotation annotation, UMBIndex.UMBEntity appliesTo, IntConsumer intConsumer) throws UMBException
	{
		String filename = annotation.getFilename(appliesTo);
		extractIntArray(filename, getUMBIndex().getAnnotationDataSize(appliesTo), intConsumer);
	}

	public void extractIndexedIntAnnotation(String group, String id, UMBIndex.UMBEntity appliesTo, LongIntConsumer longIntConsumer) throws UMBException
	{
		extractIndexedIntAnnotation(umbIndex.getAnnotation(group, id), appliesTo, longIntConsumer);
	}

	public void extractIndexedIntAnnotation(UMBIndex.Annotation annotation, UMBIndex.UMBEntity appliesTo, LongIntConsumer longIntConsumer) throws UMBException
	{
		String filename = annotation.getFilename(appliesTo);
		extractIntArray(filename, getUMBIndex().getAnnotationDataSize(appliesTo), new IndexedIntConsumer(longIntConsumer));
	}

	public void extractDoubleAnnotation(String group, String id, UMBIndex.UMBEntity appliesTo, DoubleConsumer doubleConsumer) throws UMBException
	{
		extractDoubleAnnotation(umbIndex.getAnnotation(group, id), appliesTo, doubleConsumer);
	}

	public void extractDoubleAnnotation(UMBIndex.Annotation annotation, UMBIndex.UMBEntity appliesTo, DoubleConsumer doubleConsumer) throws UMBException
	{
		String filename = annotation.getFilename(appliesTo);
		extractDoubleArray(filename, getUMBIndex().getAnnotationDataSize(appliesTo), doubleConsumer);
	}

	// Local methods for extracting data

	private boolean fileExists(String filename) throws UMBException
	{
		UMBIn umbIn = open();
		try {
			umbIn.findArchiveEntry(filename);
		} catch (UMBException e) {
			return false;
		}
		umbIn.close();
		return true;
	}

	private void extractBooleanArraySparse(String filename, LongConsumer longConsumer) throws UMBException
	{
		UMBIn umbIn = open();
		umbIn.findArchiveEntry(filename);
		ByteBuffer bytes;
		// Extract index of each 1 bit
		long index = 0;
		int blockSize = Long.BYTES * 8;
		while ((bytes = umbIn.readBytes(Long.BYTES)) != null) {
			long l = bytes.getLong();
			// Find local index i of each 1 bit within 64-bit block
			for (int i = 0; i < blockSize; i++) {
				if ((l & (1L << i)) != 0) {
					longConsumer.accept(index + i);
				}
			}
			index += Long.BYTES * 8;
		}
		umbIn.close();
	}

	private void extractBooleanArray(String filename, long size, BooleanConsumer booleanConsumer) throws UMBException
	{
		UMBIn umbIn = open();
		long entrySize = umbIn.findArchiveEntry(filename);
		long expectedSize = ((size + 63) / 64) * 8;
		if (entrySize != expectedSize) {
			throw new UMBException("File " + filename + " has unexpected size (" + entrySize + " bytes, not " + expectedSize + ")");
		}
		ByteBuffer bytes;
		// Extract index of each 1 bit
		long index = 0;
		while ((bytes = umbIn.readBytes(Long.BYTES)) != null) {
			long l = bytes.getLong();
			// Find local index i of each 1 bit within 64-bit block
			int blockSize = index + Long.BYTES * 8 <= size ? Long.BYTES * 8 : (int) (size - index);
			for (int i = 0; i < blockSize; i++) {
				booleanConsumer.accept((l & (1L << i)) != 0);
			}
			index += Long.BYTES * 8;
		}
		umbIn.close();
	}

	private void extractIntArray(String filename, long size, IntConsumer intConsumer) throws UMBException
	{
		UMBIn umbIn = open();
		long entrySize = umbIn.findArchiveEntry(filename);
		if (entrySize != size * Integer.BYTES) {
			throw new UMBException("File " + filename + " has unexpected size (" + entrySize + " bytes, not " + (size * Integer.BYTES) + ")");
		}
		ByteBuffer bytes;
		while ((bytes = umbIn.readBytes(Integer.BYTES)) != null){
			intConsumer.accept(bytes.getInt());
		}
		umbIn.close();
	}

	private void extractLongArray(String filename, long size, LongConsumer longConsumer) throws UMBException
	{
		UMBIn umbIn = open();
		long entrySize = umbIn.findArchiveEntry(filename);
		if (entrySize != size * Long.BYTES) {
			throw new UMBException("File " + filename + " has unexpected size (" + entrySize + " bytes, not " + (size * Long.BYTES) + ")");
		}
		ByteBuffer bytes;
		while ((bytes = umbIn.readBytes(Long.BYTES)) != null) {
			longConsumer.accept(bytes.getLong());
		}
		umbIn.close();
	}

	private void extractDoubleArray(String filename, long size, DoubleConsumer doubleConsumer) throws UMBException
	{
		UMBIn umbIn = open();
		long entrySize = umbIn.findArchiveEntry(filename);
		if (entrySize != size * Double.BYTES) {
			throw new UMBException("File " + filename + " has unexpected size (" + entrySize + " bytes, not " + (size * Double.BYTES) + ")");
		}
		ByteBuffer bytes;
		while ((bytes = umbIn.readBytes(Double.BYTES)) != null) {
			doubleConsumer.accept(bytes.getDouble());
		}
		umbIn.close();
	}

	private void extractStringList(String filename, List<Long> stringOffsets, Consumer<String> stringConsumer) throws UMBException
	{
		int numStrings = stringOffsets.size() - 1;
		UMBIn umbIn = open();
		long entrySize = umbIn.findArchiveEntry(filename);
		if (entrySize != stringOffsets.get(numStrings)) {
			throw new UMBException("File " + filename + " has unexpected size (" + entrySize + " bytes, not " + stringOffsets.get(numStrings) + ")");
		}
		for (int i = 0; i < numStrings; i++) {
			long sLen = stringOffsets.get(i + 1) - stringOffsets.get(i);
			if (sLen > Integer.MAX_VALUE) {
				throw new UMBException("Could not read overlength string (" + sLen + "bytes) from file " + filename);
			}
			String s = umbIn.readString((int) sLen);
			if (s == null) {
				throw new UMBException("Could not read string of length " + sLen + " from file " + filename);
			}
			stringConsumer.accept(s);
		}
		umbIn.close();
	}

	private UMBIn open() throws UMBException
	{
		return new UMBIn(fileIn);
	}

	//

	/**
	 * Class to manage reading from the zipped archive for a UMB file
	 */
	private static class UMBIn
	{
		/** Input stream from zip file */
		private final InputStream fsIn;
		/** Input stream after unzipping */
		private CompressorInputStream zipIn;
		/** Input stream from tar file */
		private TarArchiveInputStream tarIn;

		/** Byte buffer used to return file contents */
		private ByteBuffer byteBuffer;
		/** Initial size of byte buffer */
		private static final int DEFAULT_BUFFER_SIZE = 1024;

		/**
		 * Open a new UMB file for reading
		 */
		public UMBIn(File fileIn) throws UMBException
		{
			try {
				// Open file/zip/tar and create buffer
				fsIn = new BufferedInputStream(Files.newInputStream(fileIn.toPath()));
				try {
					// Any supported zip format is fine
					zipIn = new CompressorStreamFactory().createCompressorInputStream(fsIn);
					tarIn = new TarArchiveInputStream(zipIn);
				} catch (CompressorException e) {
					// No zipping also fine
					zipIn = null;
					tarIn = new TarArchiveInputStream(fsIn);
				}
				byteBuffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
			} catch (IOException e) {
				throw new UMBException("Could not open UMB file: " + e.getMessage());
			}
		}

		/**
		 * Find an entry (file) within the archive for subsequent reading.
		 * Returns the size (number of bytes) of the entry if it is found,
		 * or throws an exception if not.
		 * @param name Name of the file
		 */
		public long findArchiveEntry(String name) throws UMBException
		{
			try {
				TarArchiveEntry entry;
				while ((entry = tarIn.getNextTarEntry()) != null) {
					if (!tarIn.canReadEntryData(entry)) {
						continue;
					}
					if (entry.getName().equals(name)) {
						return entry.getSize();
					}
				}
			} catch (IOException e) {
				throw new UMBException("I/O error extracting from UMB file");
			}
			throw new UMBException("UMB archive entry \"" + name + "\" not found");
		}

		public TarArchiveInputStream getInputStream()
		{
			return tarIn;
		}

		/**
		 * Read the specified number of bytes from the current entry (file) of the archive.
		 * Returns the bytes in a {@link ByteBuffer}, or returns null if no or too few bytes are available.
		 */
		public ByteBuffer readBytes(int numBytes) throws UMBException
		{
			// Ensure buffer is big enough
			if (numBytes > byteBuffer.capacity()) {
				byteBuffer = ByteBuffer.allocate(numBytes).order(ByteOrder.LITTLE_ENDIAN);
			}
			try {
				byte[] bytes = byteBuffer.array();
				int bytesRead = tarIn.read(bytes, 0, numBytes);
				byteBuffer.position(numBytes);
				if (bytesRead < numBytes) {
					return null;
				}
				// Prepare buffer for reading and return
				byteBuffer.flip();
				return byteBuffer;
			} catch (IOException e) {
				throw new UMBException("I/O error extracting " + numBytes + " bytes from UMB entry \"" + tarIn.getCurrentEntry().getName() + "\"");
			}
		}

		/**
		 * Read a string of the specified length from the current entry (file) of the archive.
		 */
		public String readString(int length) throws UMBException
		{
			// Ensure buffer is big enough
			if (length > byteBuffer.capacity()) {
				byteBuffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
			}
			try {
				byte[] bytes = byteBuffer.array();
				int bytesRead = tarIn.read(bytes, 0, length);
				byteBuffer.position(length);
				if (bytesRead < length) {
					return null;
				}
				return new String(bytes, 0, bytesRead, StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new UMBException("I/O error extracting string from UMB entry \"" + tarIn.getCurrentEntry().getName() + "\"");
			}
		}

		/**
		 * Read the whole of the current entry (file) of the archive as a string.
		 */
		public String readAsString() throws UMBException
		{
			StringBuilder sb = new StringBuilder();
			try {
				byte[] bytes = byteBuffer.array();
				int bytesRead;
				while ((bytesRead = tarIn.read(bytes)) != -1) {
					sb.append(new String(bytes, 0, bytesRead, StandardCharsets.UTF_8));
				}
				return sb.toString();
			} catch (IOException e) {
				throw new UMBException("I/O error extracting string from UMB entry \"" + tarIn.getCurrentEntry().getName() + "\"");
			}
		}

		/**
		 * Close the UMB file.
		 */
		public void close() throws UMBException
		{
			try {
				if (tarIn != null) {
					tarIn.close();
				}
				if (zipIn != null) {
					zipIn.close();
				}
				if (fsIn != null) {
					fsIn.close();
				}
			} catch (IOException e) {
				throw new UMBException("I/O error closing UMB file");
			}
		}
	}

	// Utility classes

	@FunctionalInterface
	public interface LongBooleanConsumer
	{
		void accept(long index, boolean value);
	}

	@FunctionalInterface
	public interface LongIntConsumer
	{
		void accept(long index, int value);
	}

	@FunctionalInterface
	public interface LongLongConsumer
	{
		void accept(long index, long value);
	}

	/**
	 * Class to convert a sequence of ints to a sequence of (long) indexed ints.
	 */
	public static class IndexedIntConsumer implements IntConsumer
	{
		private final LongIntConsumer longIntConsumer;
		private long index = 0;

		public IndexedIntConsumer(LongIntConsumer longIntConsumer)
		{
			this.longIntConsumer = longIntConsumer;
		}

		@Override
		public void accept(int intValue)
		{
			longIntConsumer.accept(index++, intValue);
		}
	}

	/**
	 * Class to convert a sequence of booleans to a sequence of (long) indexed booleans.
	 */
	public static class IndexedBooleanConsumer implements BooleanConsumer
	{
		private final LongBooleanConsumer longBooleanConsumer;
		private long index = 0;

		public IndexedBooleanConsumer(LongBooleanConsumer longBooleanConsumer)
		{
			this.longBooleanConsumer = longBooleanConsumer;
		}

		@Override
		public void accept(boolean booleanValue)
		{
			longBooleanConsumer.accept(index++, booleanValue);
		}
	}

	/**
	 * Class to convert a non-decreasing sequence of n+1 non-negative (long) offsets
	 * to a corresponding sequence of n (long) counts. Both via consumers of longs.
	 */
	public static class OffsetsToCounts implements LongConsumer
	{
		LongConsumer out;
		long offsetLast;

		OffsetsToCounts(LongConsumer out)
		{
			this.out = out;
		}

		@Override
		public void accept(long offset)
		{
			if (offsetLast != -1) {
				out.accept(offset - offsetLast);
			}
			offsetLast = offset;
		}
	}

	/**
	 * Class to compute the minimum/maximum value of a sequence of ints, provided via a consumer.
	 */
	public static class IntRange implements IntConsumer
	{
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;

		public int getMin()
		{
			return min;
		}

		public int getMax()
		{
			return max;
		}

		@Override
		public void accept(int i)
		{
			min = Integer.min(min, i); max = Integer.max(max, i);
		}
	}

	/**
	 * Class to compute the maximum value of a sequence of longs, provided via a consumer.
	 */
	public static class LongMax implements LongConsumer
	{
		long max = Long.MIN_VALUE;

		public long getMax()
		{
			return max;
		}

		@Override
		public void accept(long l)
		{
			max = Long.max(max, l);
		}
	}
}
