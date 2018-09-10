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

import java.util.ArrayList;
import java.util.List;

import prism.EnumConstant;
import prism.PrismLangException;

public class TypeEnum extends Type 
{
	// Constants
	private List<EnumConstant> constants;
	
	public TypeEnum()
	{
		constants = new ArrayList<>();
	}

	/**
	 * Add a constant to the enum.
	 */
	public void addConstant(String name) 
	{
		constants.add(new EnumConstant(name, getNumConstants()));
	}
	
	/**
	 * Get the number of constants in the enum.
	 */
	public int getNumConstants() 
	{
		return constants.size();
	}

	/**
	 * Get the ith constant of the enum.
	 */
	public EnumConstant getConstant(int i)
	{
		return constants.get(i);
	}

	/**
	 * Get the name of the ith constant of the enum.
	 */
	public String getConstantName(int i)
	{
		return constants.get(i).getName();
	}

	/**
	 * Get the constant with the specified name (null if not present).
	 */
	public EnumConstant getConstantByName(String name)
	{
		int n = getNumConstants();
		for (int i = 0; i < n; i++) {
			if (getConstant(i).getName().equals(name)) {
				return getConstant(i);
			}
		}
		return null;
	}

	// Methods required for Type:
	
	@Override
	public String getTypeString()
	{
		String s = "enum(";
		boolean first = true;
		int numConstants = getNumConstants();
		for (int i = 0; i < numConstants; i++) {
			if (first) {
				first = false;
			} else {
				s += ", ";
			}
			s += getConstantName(i);
		}
		s += ")";
		return s;
	}
	
	@Override
	public boolean isPrimitive()
	{
		return true;
	}
	
	@Override
	public Object defaultValue()
	{
		// First constant in the list 
		return constants.get(0);
	}
	
	@Override
	public boolean canAssign(Type type)
	{
		return equals(type);
	}
	
	@Override
	public EnumConstant castValueTo(Object value) throws PrismLangException
	{
		if (value instanceof EnumConstant) {
			return (EnumConstant) value;
		} else {
			throw new PrismLangException("Can't convert " + value.getClass() + " to type " + getTypeString());
		}
	}

	// Standard methods:
	
	@Override
	public boolean equals(Object o)
	{
		if (o instanceof TypeEnum) {
			TypeEnum oe = (TypeEnum) o;
			return oe.constants.equals(constants); 
		}
		return false;
	}
}
