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

package parser.ast;

import java.util.ArrayList;
import java.util.List;

import parser.type.TypeStruct;
import parser.visitor.ASTVisitor;
import prism.PrismLangException;

public class DeclarationStruct extends DeclarationType
{
	// Field info
	private List<String> fieldNames;
	private List<DeclarationType> fieldTypes;
	
	public DeclarationStruct()
	{
		fieldNames = new ArrayList<>();
		fieldTypes = new ArrayList<>();
		// The type stored for a Declaration/DeclarationType object
		// is static - it is not computed during type checking.
		// (But we re-use the existing "type" field for this purpose)
		// Create a new TypeStruct here and add fields as they appear.
		setType(new TypeStruct());
	}

	/**
	 * Add a field (name and type) to the struct.
	 */
	public void addField(String name, DeclarationType type) 
	{
		fieldNames.add(name);
		fieldTypes.add(type);
		// Update type too
		((TypeStruct) getType()).addField(name, type.getType());
	}
	
	/**
	 * Set the type of the ith field of the struct.
	 */
	public void setFieldType(int i, DeclarationType type) 
	{
		fieldTypes.set(i, type);
		// Update type too
		((TypeStruct) getType()).setFieldType(i, type.getType());
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
	public DeclarationType getFieldType(int i)
	{
		return fieldTypes.get(i);
	}

	@Override
	public Expression getDefaultStart()
	{
		ExpressionStruct start = new ExpressionStruct();
		for (DeclarationType fieldType : fieldTypes) {
			start.addField(fieldType.getDefaultStart());
		}
		return start;
	}
	
	// Methods required for ASTElement:
	
	@Override
	public Object accept(ASTVisitor v) throws PrismLangException
	{
		return v.visit(this);
	}

	@Override
	public String toString()
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
			s += getFieldName(i) + " : " + getFieldType(i);
		}
		s += ")";
		return s;
	}

	@Override
	public ASTElement deepCopy()
	{
		DeclarationStruct ret = new DeclarationStruct();
		int numFields = getNumFields();
		for (int i = 0; i < numFields; i++) {
			ret.addField(getFieldName(i), (DeclarationType) getFieldType(i).deepCopy());
		}
		ret.setPosition(this);
		return ret;
	}
}
