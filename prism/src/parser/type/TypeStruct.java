//==============================================================================
//	
//	Copyright (c) 2018-
//	Authors:
//	* Dave Parker <d.a.parker@cs.bham.ac.uk> (University of Birmingham)
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

package parser.type;

import java.util.*;

import prism.PrismLangException;

public class TypeStruct extends Type 
{
	// Field info
	private List<String> fieldNames;
	private List<Type> fieldTypes;
	
	public TypeStruct()
	{
		fieldNames = new ArrayList<>();
		fieldTypes = new ArrayList<>();
	}

	/**
	 * Add a field (name and type) to the struct.
	 */
	public void addField(String name, Type type) 
	{
		fieldNames.add(name);
		fieldTypes.add(type);
	}
	
	/**
	 * Set the type of the ith field of the struct.
	 */
	public void setFieldType(int i, Type type) 
	{
		fieldTypes.set(i, type);
	}
	
	/**
	 * Get the number of fields in the struct.
	 */
	public int getNumFields() 
	{
		return fieldNames.size();
	}

	/**
	 * Get the name of the ith field of the struct.
	 */
	public String getFieldName(int i)
	{
		return fieldNames.get(i);
	}

	/**
	 * Get the type of the ith field of the struct.
	 */
	public Type getFieldType(int i)
	{
		return fieldTypes.get(i);
	}

	/**
	 * Get the index of the field by its name (-1 if not present).
	 */
	public int getFieldIndex(String name)
	{
		return fieldNames.indexOf(name);
	}

	// Methods required for Type:
	
	@Override
	public String getTypeString()
	{
		String s = "struct(";
		boolean first = true;
		int numFields = getNumFields();
		for (int i = 0; i < numFields; i++) {
			if (first) {
				first = false;
			} else {
				s += ", ";
			}
			if (getFieldName(i) != null ) {
				s += getFieldName(i) + " : ";
			}
			s += getFieldType(i);
		}
		s += ")";
		return s;
	}
	
	@Override
	public boolean isPrimitive()
	{
		return false;
	}
	
	@Override
	public Object defaultValue()
	{
		// No good option for this right now, just return null 
		return null;
	}
	
	@Override
	public boolean canAssign(Type type)
	{
		if (type instanceof TypeStruct) {
			TypeStruct typeStruct = (TypeStruct) type;
			int numFields = getNumFields();
			for (int i = 0; i < numFields; i++) {
				// If field name is present, it needs to match
				// (might be missing for a struct literal though)
				if (typeStruct.getFieldName(i) != null) {
					if (!typeStruct.getFieldName(i).equals(getFieldName(i))) {
						return false;
					}
				}
				// Field must be assignable
				if (!(getFieldType(i).canAssign(typeStruct.getFieldType(i)))) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
	
	@Override
	public List<Object> castValueTo(Object value) throws PrismLangException
	{
		// Must be a list
		if (!(value instanceof List<?>)) {
			throw new PrismLangException("Can't convert " + value.getClass() + " to type " + getTypeString());
		}
		@SuppressWarnings("unchecked")
		List<Object> oldList = (List<Object>) value;
		// Cast each element of list separately
		List<Object> newList = new ArrayList<Object>();
		int numFields = getNumFields();
		for (int i = 0; i < numFields; i++) {
			newList.add(getFieldType(i).castValueTo(oldList.get(i)));
		}
		return newList;
	}

	// Standard methods:
	
	@Override
	public boolean equals(Object o)
	{
		if (o instanceof TypeStruct) {
			TypeStruct os = (TypeStruct) o;
			return os.fieldNames.equals(fieldNames) && os.fieldTypes.equals(fieldTypes); 
		}
		return false;
	}
}
