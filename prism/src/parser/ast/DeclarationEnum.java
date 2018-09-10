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

import parser.type.TypeEnum;
import parser.visitor.ASTVisitor;
import prism.PrismLangException;

public class DeclarationEnum extends DeclarationType
{
	// Constants
	private List<String> constants;
	// AST elements for constant
	private List<ExpressionIdent> constantIdents;
	
	public DeclarationEnum()
	{
		constants = new ArrayList<>();
		constantIdents = new ArrayList<>();
		// The type stored for a Declaration/DeclarationType object
		// is static - it is not computed during type checking.
		// (But we re-use the existing "type" field for this purpose)
		// Create a new TypeEnum here and add constants as they appear.
		setType(new TypeEnum());
	}

	/**
	 * Add a constant to the enum.
	 */
	public void addConstant(ExpressionIdent ident) 
	{
		constants.add(ident.getName());
		constantIdents.add(ident);
		// Update type too
		((TypeEnum) getType()).addConstant(ident.getName());
	}
	
	/**
	 * Get the number of constants in the enum.
	 */
	public int getNumConstants() 
	{
		return constants.size();
	}

	/**
	 * Get the name of the ith constant of the enum.
	 */
	public String getConstant(int i)
	{
		return constants.get(i);
	}

	/**
	 * Get the ExpressionIdent for the ith constant of the enum declaration.
	 */
	public ExpressionIdent getConstantIdent(int i)
	{
		return constantIdents.get(i);
	}

	/**
	 * Get the index of the constant by its name (-1 if not present).
	 */
	public int getConstantIndex(String name)
	{
		return constants.indexOf(name);
	}
	
	@Override
	public Expression getDefaultStart()
	{
		// First constant in the list 
		return new ExpressionLiteral(getType(), ((TypeEnum) getType()).getConstant(0));
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
		return type.getTypeString();
	}

	@Override
	public ASTElement deepCopy()
	{
		DeclarationEnum ret = new DeclarationEnum();
		int numConstants = getNumConstants();
		for (int i = 0; i < numConstants; i++) {
			ret.addConstant((ExpressionIdent) getConstantIdent(i).deepCopy());
		}
		ret.setPosition(this);
		return ret;
	}

	// Standard methods:
	
	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((constants == null) ? 0 : constants.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DeclarationEnum other = (DeclarationEnum) obj;
		if (constants == null) {
			if (other.constants != null)
				return false;
		} else if (!constants.equals(other.constants))
			return false;
		return true;
	}
}
