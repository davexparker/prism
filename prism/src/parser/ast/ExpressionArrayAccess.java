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

public class ExpressionArrayAccess extends Expression
{
	/** Array being accessed */
	protected Expression array = null;
	/** Index into the array */
	protected Expression index = null;

	/** Size of each element of the array (num. of primitive variables),
	 *  cached for computation of variables indices to look up their values */
	protected int varIndexElementSize;
	
	/** Length of the array, cached to check overflows when evaluating */
	protected int arrayLength;
	
	// Constructors

	public ExpressionArrayAccess(Expression array, Expression index)
	{
		this.array = array;
		this.index = index;
	}

	// Set methods

	public void setArray(Expression array)
	{
		this.array = array;
	}

	public void setIndex(Expression index)
	{
		this.index = index;
	}

	/**
	 * Set the size of each element of the array (num. of primitive variables),
	 * cached for computation of variables indices to look up their values
	 */
	public void setVarIndexElementSize(int varIndexElementSize)
	{
		this.varIndexElementSize = varIndexElementSize;
	}
	
	/**
	 * Set the length of the array, cached to check overflows when evaluating
	 */
	public void setArrayLength(int arrayLength)
	{
		this.arrayLength = arrayLength;
	}
	
	// Get methods

	public Expression getArray()
	{
		return array;
	}

	public Expression getIndex()
	{
		return index;
	}

	/**
	 * Get the size of each element of the array (num. of primitive variables),
	 * cached for computation of variables indices to look up their values
	 */
	public int getVarIndexElementSize()
	{
		return varIndexElementSize;
	}
	
	/**
	 * Get the length of the array, cached to check overflows when evaluating
	 */
	public int getArrayLength()
	{
		return arrayLength;
	}
	
	// Methods required for Expression:

	@Override
	public boolean isConstant()
	{
		return array.isConstant() && index.isConstant();
	}

	@Override
	public boolean isProposition()
	{
		return array.isProposition() && index.isProposition();
	}

	@Override
	public Object evaluate(EvaluateContext ec) throws PrismLangException
	{
		// Evaluate/check index into array and adjust offset for variable lookup
		int evalIndex = index.evaluateInt(ec);
		if (evalIndex < 0 || evalIndex >= arrayLength) {
			throw new PrismLangException("Array index out of bounds (index=" + evalIndex + ", length=" + arrayLength + ")", this);
		}
		ec.addToVarIndexOffset(evalIndex * varIndexElementSize);
		return array.evaluate(ec);
	}
	
	@Override
	public BigRational evaluateExact(EvaluateContext ec) throws PrismLangException
	{
		// Evaluate/check index into array and adjust offset for variable lookup
		int evalIndex = index.evaluateExact(ec).intValue();
		if (evalIndex < 0 || evalIndex >= arrayLength) {
			throw new PrismLangException("Array index out of bounds (index=" + evalIndex + ", length=" + arrayLength + ")", this);
		}
		ec.addToVarIndexOffset(evalIndex * varIndexElementSize);
		return array.evaluateExact(ec);
	}


	@Override
	public boolean returnsSingleValue()
	{
		return array.returnsSingleValue() && index.returnsSingleValue();
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
		ExpressionArrayAccess expr = new ExpressionArrayAccess(array.deepCopy(), index.deepCopy());
		expr.setVarIndexElementSize(varIndexElementSize);
		expr.setArrayLength(arrayLength);
		expr.setType(type);
		expr.setPosition(this);
		return expr;
	}

	// Standard methods
	
	@Override
	public String toString()
	{
		return array + "[" + index + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((array == null) ? 0 : array.hashCode());
		result = prime * result + ((index == null) ? 0 : index.hashCode());
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
		ExpressionArrayAccess other = (ExpressionArrayAccess) obj;
		if (array == null) {
			if (other.array != null)
				return false;
		} else if (!array.equals(other.array))
			return false;
		if (index == null) {
			if (other.index != null)
				return false;
		} else if (!index.equals(other.index))
			return false;
		return true;
	}
}

// ------------------------------------------------------------------------------
