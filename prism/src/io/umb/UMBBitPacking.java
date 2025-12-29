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

import io.umb.UMBBitString;
import io.umb.UMBException;
import io.umb.UMBIndex;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores information about how a list of typed variables (with optional padding) are packed into a bit string.
 */
public class UMBBitPacking
{
	/** Items stored (variables or padding) */
	private final List<BitPackedItem> items = new ArrayList<>();
	/** Starting bit offsets for each item */
	private final List<Integer> itemOffsets = new ArrayList<>();
	/** Indices (into the item list) of the variables */
	private final List<Integer> varIndices = new ArrayList<>();
	/** Total number of bits used */
	private int totalNumBits = 0;

	/**
	 * Construct a new UMBBitPacking object, based on a UMB valuation description (from JSON).
	 * @param valuationDescr The valuation description
	 */
	public UMBBitPacking(UMBIndex.ValuationDescription valuationDescr)
	{
		for (UMBIndex.ValuationVariable item : valuationDescr.variables) {
			if (item.isVariable()) {
				addVariable(item.name, item.size, item.type);
			}
			else if (item.isPadding()) {
				addPadding(item.padding);
			}
		}
	}

	/**
	 * Construct a new, empty UMBBitPacking object.
	 */
	public UMBBitPacking()
	{
	}

	/**
	 * Add a variable.
	 * @param name Variable name
	 * @param size Variable size (in bits)
	 * @param type Variable type
	 */
	public void addVariable(String name, int size, String type)
	{
		addItem(new BitPackedVariable(name, size, type));
		varIndices.add(items.size() - 1);
	}

	/**
	 * Add padding.
	 * @param size Padding size (in bits)
	 */
	public void addPadding(int size)
	{
		addItem(new BitPackedPadding(size));
	}

	/**
	 * Add an item (variable or padding) and update internal state.
	 * @param item The item to add
	 */
	private void addItem(BitPackedItem item)
	{
		// Compute offset (starting bit) for new item
		if (items.isEmpty()) {
			itemOffsets.add(0);
		} else {
			itemOffsets.add(itemOffsets.get(itemOffsets.size() - 1) + items.get(items.size() - 1).size);
		}
		// Add to list
		items.add(item);
		// Update total size
		totalNumBits += item.size;
	}

	/**
	 * Add padding to align to the next byte boundary, if needed.
	 */
	public void padToByteBoundary()
	{
		if (getTotalNumBits() % 8 != 0) {
			addPadding(8 - (getTotalNumBits() % 8));
		}
	}

	/**
	 * Create a UMB valuation description corresponding to this bit packing.
	 */
	public UMBIndex.ValuationDescription toValuationDescription()
	{
		UMBIndex.ValuationDescription valuationDescr = new UMBIndex.ValuationDescription();
		valuationDescr.alignment = getTotalNumBytes();
		// Create an item for each variable or padding
		int numItems = getNumItems();
		for (int i = 0; i < numItems; i++) {
			BitPackedItem item = getItem(i);
			UMBIndex.ValuationVariable var = new UMBIndex.ValuationVariable();
			if (item instanceof BitPackedVariable) {
				BitPackedVariable varItem = (BitPackedVariable) item;
				var.name = varItem.name;
				var.size = varItem.size;
				var.type = varItem.type;
			} else if (item instanceof BitPackedPadding) {
				var.padding = item.size;
			}
			valuationDescr.variables.add(var);
		}
		// Add final padding to align to byte boundary, if needed
		if (getTotalNumBits() % 8 != 0) {
			UMBIndex.ValuationVariable var = new UMBIndex.ValuationVariable();
			var.padding = 8 - (getTotalNumBits() % 8);
			valuationDescr.variables.add(var);
		}
		return valuationDescr;
	}

	// Getters for information about the packing

	/**
	 * Get the number of items (variables or padding) in the packing.
	 */
	public int getNumItems()
	{
		return items.size();
	}

	/**
	 * Get the {@code i}th item (variable or padding).
	 */
	public BitPackedItem getItem(int i)
	{
		return items.get(i);
	}

	/**
	 * Get the starting bit offset of the {@code i}th item (variable or padding).
	 */
	public int getItemOffset(int i)
	{
		return itemOffsets.get(i);
	}

	/**
	 * Get the size (in bits) of the {@code i}th item (variable or padding).
	 */
	public int getItemSize(int i)
	{
		return getItem(i).size;
	}

	/**
	 * Get the total number of bits used in the packing (including padding).
	 */
	public int getTotalNumBits()
	{
		return totalNumBits;
	}

	/**
	 * Get the total number of bytes used in the packing (including padding to byte boundary).
	 */
	public int getTotalNumBytes()
	{
		int totalNumBitsNeeded = totalNumBits;
		if (totalNumBitsNeeded % 8 != 0) {
			totalNumBitsNeeded += (8 - totalNumBitsNeeded % 8);
		}
		return totalNumBitsNeeded / 8;
	}

	/**
	 * Get the number of variables in the packing.
	 */
	public int getNumVariables()
	{
		return varIndices.size();
	}

	/**
	 * Get the {@code i}th variable.
	 */
	public BitPackedVariable getVariable(int i)
	{
		return (BitPackedVariable) items.get(varIndices.get(i));
	}

	/**
	 * Get the starting bit offset of the {@code i}th variable.
	 */
	public int getVariableOffset(int i)
	{
		return itemOffsets.get(varIndices.get(i));
	}

	/**
	 * Get the size (in bits) of the {@code i}th variable.
	 */
	public int getVariableSize(int i)
	{
		return getVariable(i).size;
	}

	// Methods to create and manipulate bit strings according to this packing

	/**
	 * Create a new UMBBitString of the appropriate size for this packing.
	 */
	public UMBBitString newBitString()
	{
		return new UMBBitString(getTotalNumBytes());
	}

	/** Set the value of the {@code i}th variable, which must be a (signed) integer, in a bit string.
	 * @param bitString The bit string to modify
	 * @param i The index of the variable
	 * @param value The value to set
	 */
	public void setIntVariableValue(UMBBitString bitString, int i, int value) throws UMBException
	{
		bitString.setInt(getVariableOffset(i), getVariableSize(i), value);
	}

	/** Set the value of the {@code i}th variable, which must be an (unsigned) integer, in a bit string.
	 * @param bitString The bit string to modify
	 * @param i The index of the variable
	 * @param value The value to set
	 */
	public void setUIntVariableValue(UMBBitString bitString, int i, int value) throws UMBException
	{
		bitString.setUInt(getVariableOffset(i), getVariableSize(i), value);
	}

	/** Set the value of the {@code i}th variable, which must be a double, in a bit string.
	 * @param bitString The bit string to modify
	 * @param i The index of the variable
	 * @param value The value to set
	 */
	public void setDoubleVariableValue(UMBBitString bitString, int i, double value) throws UMBException
	{
		bitString.setDouble(getVariableOffset(i), getVariableSize(i), value);
	}

	/** Set the value of the {@code i}th variable, which must be a boolean, in a bit string.
	 * @param bitString The bit string to modify
	 * @param i The index of the variable
	 * @param value The value to set
	 */
	public void setBooleanVariableValue(UMBBitString bitString, int i, boolean value) throws UMBException
	{
		bitString.setBoolean(getVariableOffset(i), getVariableSize(i), value);
	}

	// Methods to extract values stored in bit strings according to this packing

	/**
	 * Get the value of the {@code i}th variable, from a bit string, as an Object
	 */
	public Object getVariableValue(UMBBitString bitString, int i) throws UMBException
	{
		BitPackedVariable var = getVariable(i);
		switch (var.type) {
			case "bool":
				return getBooleanVariableValue(bitString, i);
			case "int":
				return getIntVariableValue(bitString, i);
			case "uint":
				return getUIntVariableValue(bitString, i);
			case "double":
				return getDoubleVariableValue(bitString, i);
			default:
				throw new UMBException("Unknown variable type: " + var.type);
		}
	}

	/**
	 * Get the value of the {@code i}th variable, which must be a (signed) integer, from a bit string.
	 */
	public int getIntVariableValue(UMBBitString bitString, int i) throws UMBException
	{
		return bitString.getInt(getVariableOffset(i), getVariableSize(i));
	}

	/**
	 * Get the value of the {@code i}th variable, which must be an (unsigned) integer, from a bit string.
	 */
	public int getUIntVariableValue(UMBBitString bitString, int i) throws UMBException
	{
		return bitString.getUInt(getVariableOffset(i), getVariableSize(i));
	}

	/**
	 * Get the value of the {@code i}th variable, which must be a double, from a bit string.
	 */
	public double getDoubleVariableValue(UMBBitString bitString, int i) throws UMBException
	{
		return bitString.getDouble(getVariableOffset(i), getVariableSize(i));
	}

	/**
	 * Get the value of the {@code i}th variable, which must be a boolean, from a bit string.
	 */
	public boolean getBooleanVariableValue(UMBBitString bitString, int i) throws UMBException
	{
		return bitString.getBoolean(getVariableOffset(i), getVariableSize(i));
	}

	/**
	 * Create a string representation of values stored in a bit string according to the packing,
	 * showing divisions into variables and padding.
	 * @param bitString The bit string to format
	 */
	public String decodeBitString(UMBBitString bitString) throws UMBException
	{
		StringBuilder sb = new StringBuilder();
		int numVars = getNumVariables();
		for (int i = 0; i < numVars; i++) {
			BitPackedVariable var = getVariable(i);
			int offset = getVariableOffset(i);
			int size = getVariableSize(i);
			switch (var.type) {
				case "bool":
					sb.append(bitString.getBoolean(offset, size));
					break;
				case "int":
					sb.append(bitString.getInt(offset, size));
					break;
				case "uint":
					sb.append(bitString.getUInt(offset, size));
					break;
				case "double":
					sb.append(bitString.getDouble(offset, size));
					break;
				default:
					throw new UMBException("Unknown variable type: " + var.type);
			}
			if (i < numVars - 1) {
				sb.append(",");
			}
		}
		return sb.toString();
	}

	/**
	 * Create a string representation of a bit string according to the packing,
	 * showing divisions into variables and padding.
	 * @param bitString The bit string to format
	 */
	public String formatBitString(UMBBitString bitString)
	{
		StringBuilder sb = new StringBuilder();
		int numItems = getNumItems();
		// Go through items in reverse order (most significant first)
		for (int i = numItems - 1; i >= 0; i--) {
			BitPackedItem item = getItem(i);
			int offset = getItemOffset(i);
			int size = getItemSize(i);
			if (item instanceof BitPackedVariable) {
				sb.append(bitString.toString(offset, size));
			} else if (item instanceof BitPackedPadding) {
				for (int j = 0; j < size; j++) {
					sb.append(".");
				}
			}
			if (i > 0) {
				sb.append("|");
			}
		}
		return sb.toString();
	}

	// Classes for items in the packing

	/**
	 * An item in the packing (either a variable or padding).
	 */
	public static class BitPackedItem
	{
		/** Size (in bits) of this item */
		public int size;
	}

	/**
	 * A variable in the packing.
	 */
	public static class BitPackedVariable extends BitPackedItem
	{
		/** Name of the variable */
		public String name;
		/** Type of the variable */
		public String type;

		public BitPackedVariable(String name, int size, String type)
		{
			this.size = size;
			this.name = name;
			this.type = type;
		}
	}

	/**
	 * Padding in the packing.
	 */
	public static class BitPackedPadding extends BitPackedItem
	{
		public BitPackedPadding(int size)
		{
			this.size = size;
		}
	}
}
