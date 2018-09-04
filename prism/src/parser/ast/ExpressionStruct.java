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

import param.BigRational;
import parser.*;
import parser.visitor.*;
import prism.PrismLangException;

public class ExpressionStruct extends Expression
{
	// Fields
	private ArrayList<Expression> fields;

	// Constructors

	public ExpressionStruct()
	{
		fields = new ArrayList<Expression>();
	}

	// Set methods

	public void addField(Expression e)
	{
		fields.add(e);
	}

	public void setField(int i, Expression e)
	{
		fields.set(i, e);
	}

	// Get methods

	public int getNumFields()
	{
		return fields.size();
	}

	public Expression getField(int i)
	{
		return fields.get(i);
	}

	// Methods required for Expression:

	@Override
	public boolean isConstant()
	{
		int n = getNumFields();
		for (int i = 0; i < n; i++) {
			if (!getField(i).isConstant())
				return false;
		}
		return true;
	}

	@Override
	public boolean isProposition()
	{
		int n = getNumFields();
		for (int i = 0; i < n; i++) {
			if (!getField(i).isProposition())
				return false;
		}
		return true;
	}
	
	@Override
	public Object evaluate(EvaluateContext ec) throws PrismLangException
	{
		int n = getNumFields();
		List<Object> res = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			res.add(getField(i).evaluate(ec));
		}
		return res;
	}

	@Override
	public BigRational evaluateExact(EvaluateContext ec) throws PrismLangException
	{
		throw new PrismLangException("Exact evaluation of structs not yet supported");
	}
	
	@Override
	public boolean returnsSingleValue()
	{
		int n = getNumFields();
		for (int i = 0; i < n; i++) {
			if (!getField(i).returnsSingleValue())
				return false;
		}
		return true;
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
		ExpressionStruct e = new ExpressionStruct();
		int n = getNumFields();
		for (int i = 0; i < n; i++) {
			e.addField((Expression) getField(i).deepCopy());
		}
		e.setType(type);
		e.setPosition(this);
		return e;
	}
	
	// Standard methods
	
	@Override
	public String toString()
	{
		String s = "struct(";
		int n = getNumFields();
		boolean first = true;
		for (int i = 0; i < n; i++) {
			if (!first)
				s += ", ";
			else
				first = false;
			s = s + getField(i);
		}
		s += ")";
		return s;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((fields == null) ? 0 : fields.hashCode());
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
		ExpressionStruct other = (ExpressionStruct) obj;
		if (fields == null) {
			if (other.fields != null)
				return false;
		} else if (!fields.equals(other.fields))
			return false;
		return true;
	}
}

// ------------------------------------------------------------------------------
