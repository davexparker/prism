//==============================================================================
//	
//	Copyright (c) 2018
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
import parser.State;
import parser.Values;
import parser.VarList;
import parser.type.Type;
import parser.visitor.ASTVisitor;
import prism.PrismLangException;

/**
 * Class to store a single element of an Update, i.e. a single assignment (e.g. s'=1)
 */
public class UpdateElement extends ASTElement
{
	/** Reference to the variable to be updated
	 * (ExpressionVar for variable, ExpressionArray for array, etc.) */
	private Expression varRef;
	/** Name of the variable */
	private String varName;
	/** The expression for the assignment value, used to up */
	private Expression expr;

	/** Constructor */
	public UpdateElement(Expression varRef, Expression expr)
	{
		this.varRef = varRef;
		this.expr = expr;
	}

	/** Shallow copy constructor */
	public UpdateElement(UpdateElement other)
	{
		varRef = other.varRef;
		varName = other.varName;
		expr = other.expr;
	}

	// Setters
	
	/**
	 * Set the reference to the variable to be updated
	 * (ExpressionVar for variable, ExpressionArray for array, etc.)
	 * */
	public void setVarRef(Expression varRef)
	{
		this.varRef = varRef;
	}

	/**
	 * Set the expression that will be assigned to the variable.
	 */
	public void setExpression(Expression expr)
	{
		this.expr = expr;
	}

	// Getters

	/**
	 * Get the reference to the variable to be updated
	 * (ExpressionVar for variable, ExpressionArray for array, etc.)
	 */
	public Expression getVarRef()
	{
		return varRef;
	}

	/**
	 * Get the expression that will be assigned to the variable.
	 */
	public Expression getExpression()
	{
		return expr;
	}

	/**
	 * Get the name of the variable that is the assignment target.
	 * This is the "parent" variable, e.g. "a" for a[i][j].
	 */
	public String getVarName()
	{
		// Do this on demand, because need some info about variables
		// that is not available when this is first constructed by the parser 
		if (varName == null) {
			varName = Update.extractVarNameFromVarRef(varRef);
		}
		return varName;
	}

	/**
	 * Get the type of the update element (i.e. the type of the variable being updated). 
	 */
	public Type getType()
	{
		return varRef.getType();
	}

	/**
	 * Execute this update element, based on variable values specified as a Values object,
	 * applying changes in variables to a second Values object. 
	 * Values of any constants should also be provided.
	 * @param constantValues Values for constants
	 * @param oldValues Variable values in current state
	 * @param newValues Values object to apply changes to
	 */
	public void update(Values constantValues, Values oldValues, Values newValues) throws PrismLangException
	{
		newValues.setValue(varName, expr.evaluate(constantValues, oldValues));
	}

	/**
	 * Execute this update element, based on variable values specified as a State object.
	 * It is assumed that any constants have already been defined.
	 * @param oldState Variable values in current state
	 * @param newState State object to apply changes to
	 */
	public void update(State oldState, State newState) throws PrismLangException
	{
		update(oldState, newState, false);
	}

	/**
	 * Execute this update element, based on variable values specified as a State object.
	 * It is assumed that any constants have already been defined.
	 * @param oldState Variable values in current state
	 * @param newState State object to apply changes to
	 * @param exact evaluate arithmetic expressions exactly?
	 */
	public void update(State oldState, State newState, boolean exact) throws PrismLangException
	{
		// Get value that variable will be updated to (as Object) 
		Object newValue;
		if (exact) {
			BigRational r = expr.evaluateExact(oldState);
			newValue = expr.getType().castFromBigRational(r);
		} else {
			newValue = expr.evaluate(oldState);
		}
		// Compute index of variable reference in state and update
		int varIndex = Update.computeVarIndex(varRef, oldState);
		newState.setValue(varIndex, newValue);
	}
	
	/**
	 * Evaluate this update element in the context of a state,
	 * i.e. with assignment values and array indices/etc. evaluated, and return as a string.
	 * e.g. (a[i+1][0]'=i), in state i=1, returns "a[2][0]'=1".
	 * Note that this is *not* parenthesised, as in the PRISM language, or as from toString(). 
	 */
	public String evaluate(State state, boolean exact) throws PrismLangException
	{
		String s = Update.evaluateVarRef(getVarRef(), state, exact).toString();
		return s + "'=" + (exact ? getExpression().evaluateExact(state) : getExpression().evaluate(state));
	}
	
	// Methods required for ASTElement:

	@Override
	public Object accept(ASTVisitor v) throws PrismLangException
	{
		return v.visit(this);
	}

	@Override
	public UpdateElement deepCopy()
	{
		UpdateElement result = new UpdateElement(varRef.deepCopy(), expr.deepCopy());
		result.type = type;
		result.setPosition(this);
		return result;
	}
	
	// Other methods:
	
	@Override
	public String toString()
	{
		return "(" + getVarRef() + "'=" + getExpression() + ")";
	}
}
