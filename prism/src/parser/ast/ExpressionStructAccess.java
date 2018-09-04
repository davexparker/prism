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

import param.BigRational;
import parser.EvaluateContext;
import parser.visitor.ASTVisitor;
import prism.PrismLangException;

public class ExpressionStructAccess extends Expression
{
	/** Array being accessed */
	protected Expression struct = null;
	/** name of struct field being accessed */
	protected String field = null;

	/** Offset of the field of the struct being accessed (in terms of num. of primitive variables) */
	protected int varIndexOffset;
	
	// Constructors

	public ExpressionStructAccess(Expression struct, String field)
	{
		this.struct = struct;
		this.field = field;
	}

	// Set methods

	public void setStruct(Expression struct)
	{
		this.struct = struct;
	}

	public void setField(String field)
	{
		this.field = field;
	}

	/**
	 * Set the offset of the field of the struct being accessed (in terms of num. of primitive variables). 
	 */
	public void setVarIndexOffset(int varIndexOffset)
	{
		this.varIndexOffset = varIndexOffset;
	}
	
	// Get methods

	public Expression getStruct()
	{
		return struct;
	}

	public String getField()
	{
		return field;
	}

	/**
	 * Get the offset of the field of the struct being accessed (in terms of num. of primitive variables). 
	 */
	public int getVarIndexOffset()
	{
		return varIndexOffset;
	}
	
	// Methods required for Expression:

	@Override
	public boolean isConstant()
	{
		return struct.isConstant();
	}

	@Override
	public boolean isProposition()
	{
		return struct.isProposition();
	}

	@Override
	public Object evaluate(EvaluateContext ec) throws PrismLangException
	{
		ec.addToVarIndexOffset(varIndexOffset);
		return struct.evaluate(ec);
	}
	
	@Override
	public BigRational evaluateExact(EvaluateContext ec) throws PrismLangException
	{
		return BigRational.from(evaluate(ec));
	}

	@Override
	public boolean returnsSingleValue()
	{
		return struct.returnsSingleValue();
	}

	// Methods required for ASTElement:

	@Override
	public Object accept(ASTVisitor v) throws PrismLangException
	{
		return v.visit(this);
	}

	@Override
	public Expression deepCopy()
	{
		ExpressionStructAccess expr = new ExpressionStructAccess(struct.deepCopy(), field);
		expr.setVarIndexOffset(varIndexOffset);
		expr.setType(type);
		expr.setPosition(this);
		return expr;
	}

	// Standard methods
	
	@Override
	public String toString()
	{
		return struct + "." + field;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((struct == null) ? 0 : struct.hashCode());
		result = prime * result + ((field == null) ? 0 : field.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ExpressionStructAccess other = (ExpressionStructAccess) obj;
		if (struct == null) {
			if (other.struct != null)
				return false;
		} else if (!struct.equals(other.struct))
			return false;
		if (field == null) {
			if (other.field != null)
				return false;
		} else if (!field.equals(other.field))
			return false;
		return true;
	}
}

// ------------------------------------------------------------------------------
