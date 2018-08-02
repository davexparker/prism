//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford)
//	* Mark Kattenbelt <mark.kattenbelt@comlab.ox.ac.uk> (University of Oxford)
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

import parser.type.TypeArray;
import parser.visitor.ASTVisitor;
import prism.PrismLangException;

public class DeclarationArray extends DeclarationType
{
	// Array length
	protected Expression length;
	// Type of object contained in this array
	protected DeclarationType subtype;

	public DeclarationArray(Expression length, DeclarationType subtype)
	{
		this.length = length;
		this.subtype = subtype;
		// The type stored for a Declaration/DeclarationType object
		// is static - it is not computed during type checking.
		// (But we re-use the existing "type" field for this purpose)
		// And we copy the info from DeclarationType across to Declaration for convenience.
		setType(new TypeArray(subtype.getType()));
	}

	/**
	 * Set the length of the array.
	 */
	public void setLength(Expression length)
	{
		this.length = length;
	}

	/**
	 * Set the subtype of the array.
	 */
	public void setSubtype(DeclarationType subtype)
	{
		this.subtype = subtype;
	}

	/**
	 * Get the length of the array.
	 */
	public Expression getLength()
	{
		return length;
	}

	/**
	 * Get the subtype of the array.
	 */
	public DeclarationType getSubtype()
	{
		return subtype;
	}

	@Override
	public Expression getDefaultStart()
	{
		// Currently array elements are all initialised to the same value
		// so we can just use the default value for the (primitive) subtype 
		return subtype.getDefaultStart();
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
		return "array[" + length + "] of " + subtype;
	}

	@Override
	public ASTElement deepCopy()
	{
		Expression lengthCopy = (length == null) ? null : length.deepCopy();
		DeclarationType subtypeCopy = (DeclarationType) subtype.deepCopy();
		DeclarationArray ret = new DeclarationArray(lengthCopy, subtypeCopy);
		ret.setPosition(this);
		return ret;
	}
}
