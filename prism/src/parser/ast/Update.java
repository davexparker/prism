//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <d.a.parker@cs.bham.ac.uk> (University of Birmingham/Oxford)
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
import java.util.Iterator;
import java.util.stream.Collectors;

import parser.EvaluateContextSubstate;
import parser.State;
import parser.Values;
import parser.VarList;
import parser.type.Type;
import parser.visitor.ASTVisitor;
import prism.PrismLangException;

/**
 * Class to store a single update, i.e. a list of assignments of variables to expressions, e.g. (s'=1)&amp;(x'=x+1)
 */
public class Update extends ASTElement implements Iterable<UpdateElement>
{
	// Individual elements of update
	private ArrayList<UpdateElement> elements;

	// Parent Updates object
	private Updates parent;

	/**
	 * Create an empty update.
	 */
	public Update()
	{
		elements = new ArrayList<>();
	}

	// Set methods

	/**
	 * Add a variable assignment ({@code v}'={@code e}) to this update.
	 * @param v The AST element corresponding to the variable being updated
	 * @param e The expression which will be assigned to the variable
	 */
	public void addElement(Expression v, Expression e)
	{
		elements.add(new UpdateElement(v, e));
	}

	/**
	 * Add a variable assignment (encoded as an UpdateElement) to this update.
	 */
	public void addElement(UpdateElement e)
	{
		elements.add(e);
	}

	/**
	 * Set the ith variable assignment (encoded as an UpdateElement) to this update.
	 */
	public void setElement(int i, UpdateElement e)
	{
		elements.set(i, e);
	}

	/**
	 * Set the variable {@code v} for the {@code i}th variable assignment of this update.
	 * @param i The index of the variable assignment within the update
	 * @param v The AST element corresponding to the variable being updated
	 */
	public void setVarRef(int i, Expression v)
	{
		elements.get(i).setVarRef(v);
	}

	/**
	 * Set the expression {@code e} for the {@code i}th variable assignment of this update.
	 * @param i The index of the variable assignment within the update
	 * @param e The expression which will be assigned to the variable
	 */
	public void setExpression(int i, Expression e)
	{
		elements.get(i).setExpression(e);
	}

	/**
	 * Set the {@link parser.ast.Updates} object containing this update.
	 */
	public void setParent(Updates u)
	{
		parent = u;
	}

	// Get methods

	/**
	 * Get the number of variables assigned values by this update.
	 */
	public int getNumElements()
	{
		return elements.size();
	}

	/** Get the update element (individual assignment) with the given index. */
	public UpdateElement getElement(int index)
	{
		return elements.get(index);
	}
	
	/**
	 * Get the {@code i}th variable reference in this update.
	 * (ExpressionVar for variable, ExpressionArray for array, etc.)
	 */
	public Expression getVarRef(int i)
	{
		return elements.get(i).getVarRef();
	}

	/**
	 * Get the expression used to update the {@code i}th variable in this update.
	 */
	public Expression getExpression(int i)
	{
		return elements.get(i).getExpression();
	}

	/**
	 * Get the type of the {@code i}th variable in this update.
	 */
	public Type getType(int i)
	{
		return elements.get(i).getType();
	}

	/**
	 * Get the {@link parser.ast.Updates} object containing this update.
	 */
	public Updates getParent()
	{
		return parent;
	}

	/**
	 * Execute this update, based on variable values specified as a Values object,
	 * returning the result as a new Values object copied from the existing one.
	 * Values of any constants should also be provided.
	 * @param constantValues Values for constants
	 * @param oldValues Variable values in current state
	 */
	public Values update(Values constantValues, Values oldValues) throws PrismLangException
	{
		Values res = new Values(oldValues);
		update(constantValues, oldValues, res);
		return res;
	}

	/**
	 * Execute this update, based on variable values specified as a Values object,
	 * applying changes in variables to a second Values object. 
	 * Values of any constants should also be provided.
	 * @param constantValues Values for constants
	 * @param oldValues Variable values in current state
	 * @param newValues Values object to apply changes to
	 */
	public void update(Values constantValues, Values oldValues, Values newValues) throws PrismLangException
	{
		for (UpdateElement e : this) {
			e.update(constantValues, oldValues, newValues);
		}
	}

	/**
	 * Execute this update, based on variable values specified as a State object,
	 * returning the result as a new State object copied from the existing one.
	 * It is assumed that any constants have already been defined.
	 * @param oldState Variable values in current state
	 */
	public State update(State oldState) throws PrismLangException
	{
		State newState = new State(oldState);
		update(oldState, newState, false);
		return newState;
	}

	/**
	 * Execute this update, based on variable values specified as a State object.
	 * Apply changes in variables to a provided copy of the State object.
	 * (i.e. oldState and newState should be equal when passed in) 
	 * It is assumed that any constants have already been defined.
	 * <br>
	 * Arithmetic expressions are evaluated using the default evaluate (i.e., not using exact arithmetic)
	 * @param oldState Variable values in current state
	 * @param newState State object to apply changes to
	 */
	public void update(State oldState, State newState) throws PrismLangException
	{
		update(oldState, newState, false);
	}

	/**
	 * Execute this update, based on variable values specified as a State object.
	 * Apply changes in variables to a provided copy of the State object.
	 * (i.e. oldState and newState should be equal when passed in)
	 * It is assumed that any constants have already been defined.
	 * @param oldState Variable values in current state
	 * @param newState State object to apply changes to
	 * @param exact evaluate arithmetic expressions exactly?
	 */
	public void update(State oldState, State newState, boolean exact) throws PrismLangException
	{
		for (UpdateElement e : this) {
			e.update(oldState, newState, exact);
		}
	}

	/**
	 * Execute this update, based on variable values specified as a State object.
	 * Apply changes in variables to a provided copy of the State object.
	 * (i.e. oldState and newState should be equal when passed in.) 
	 * Both State objects represent only a subset of the total set of variables,
	 * with this subset being defined by the mapping varMap.
	 * Only variables in this subset are updated.
	 * But if doing so requires old values for variables outside the subset, this will cause an exception. 
	 * It is assumed that any constants have already been defined.
	 * @param oldState Variable values in current state
	 * @param newState State object to apply changes to
	 * @param varMap A mapping from indices (over all variables) to the subset (-1 if not in subset). 
	 */
	public void updatePartially(State oldState, State newState, int[] varMap) throws PrismLangException
	{
		int n = elements.size();
		for (int i = 0; i < n; i++) {
			int varIndex = varMap[computeVarIndex(getVarRef(i), oldState)];
			if (varIndex != -1) {
				newState.setValue(varIndex, getExpression(i).evaluate(new EvaluateContextSubstate(oldState, varMap)));
			}
		}
	}

	/**
	 * Check whether this update (from a particular state) would cause any errors, mainly variable overflows.
	 * Variable ranges are specified in the passed in VarList.
	 * Throws an exception if such an error occurs.
	 */
	public State checkUpdate(State oldState, VarList varList) throws PrismLangException
	{
		int n = elements.size();
		State res;
		res = new State(oldState);
		for (int i = 0; i < n; i++) {
			Object newValue = getExpression(i).evaluate(oldState);
			int newValueInt = varList.encodeToInt(i, newValue);
			if (newValueInt < varList.getLow(i) || newValueInt > varList.getHigh(i)) {
				String errMsg = "Overflow when assigning value " + newValue + " to variable " + evaluateVarRef(getExpression(i), oldState, false);
				throw new PrismLangException(errMsg, getElement(i));
			}
		}
		return res;
	}

	// Static utility methods for variable references/indexing 
	
	/**
	 * Compute the index (within a State object) of the primitive variable to be updated,
	 * using indexing info already attached to Expression objects in the variable reference
	 */
	public static int computeVarIndex(Expression varRef, State state) throws PrismLangException
	{
		if (varRef instanceof ExpressionVar) {
			return ((ExpressionVar) varRef).getVarIndex();
		} else if (varRef instanceof ExpressionArrayAccess) {
			int evalIndex = ((ExpressionArrayAccess) varRef).getIndex().evaluateInt(state);
			int arrayLength = ((ExpressionArrayAccess) varRef).getArrayLength();
			if (evalIndex < 0 || evalIndex >= arrayLength) {
				throw new PrismLangException("Array index out of bounds (index=" + evalIndex + ", length=" + arrayLength + ")", varRef);
			}
			int elementSize = ((ExpressionArrayAccess) varRef).getVarIndexElementSize();
			return evalIndex * elementSize + computeVarIndex(((ExpressionArrayAccess) varRef).getArray(), state);
		} else if (varRef instanceof ExpressionStructAccess) {
			return ((ExpressionStructAccess) varRef).getVarIndexOffset() + computeVarIndex(((ExpressionStructAccess) varRef).getStruct(), state);
		}
		throw new PrismLangException("Invalid variable reference in update", varRef);
	}
	
	/**
	 * Extract the name of the top-level variable for a variable reference,
	 * e.g. a[i][0], encoded as an Expression, returns "a".
	 */
	public static String extractVarNameFromVarRef(Expression varRef)
	{
		if (varRef instanceof ExpressionVar) {
			return ((ExpressionVar) varRef).getName();
		} else if (varRef instanceof ExpressionArrayAccess) {
			return extractVarNameFromVarRef(((ExpressionArrayAccess) varRef).getArray());
		} else if (varRef instanceof ExpressionStructAccess) {
			return extractVarNameFromVarRef(((ExpressionStructAccess) varRef).getStruct());
		}
		return null; 
	}
	
	/**
	 * Evaluate a variable reference in the context of a state.
	 * The "evaluated" reference is returned as an Expression, but with indices/etc. evaluated.  
	 * e.g. a[i+1][0], in state i=1, returns a variable reference for a[2][0].
	 */
	public static Expression evaluateVarRef(Expression varRef, State state, boolean exact) throws PrismLangException
	{
		if (varRef instanceof ExpressionVar) {
			return varRef;
		} else if (varRef instanceof ExpressionArrayAccess) {
			Expression array = evaluateVarRef(((ExpressionArrayAccess) varRef).getArray(), state, exact);
			Expression index = ((ExpressionArrayAccess) varRef).getIndex().deepCopy();
			Object indexObj = exact ? index.getType().castFromBigRational(index.evaluateExact(state)) : index.evaluate(state);
			return new ExpressionArrayAccess(array, Expression.Literal(indexObj));
		} else if (varRef instanceof ExpressionStructAccess) {
			Expression struct = evaluateVarRef(((ExpressionStructAccess) varRef).getStruct(), state, exact);
			return new ExpressionStructAccess(struct, ((ExpressionStructAccess) varRef).getField());
		}
		return null; 
	}
	
	// Methods required for ASTElement:

	@Override
	public Object accept(ASTVisitor v) throws PrismLangException
	{
		return v.visit(this);
	}

	@Override
	public ASTElement deepCopy()
	{
		Update ret = new Update();
		for (UpdateElement e : this) {
			ret.addElement(e.deepCopy());
		}
		ret.setPosition(this);
		return ret;
	}
	
	// Other methods:
	
	@Override
	public Iterator<UpdateElement> iterator()
	{
		return elements.iterator();
	}
	
	@Override
	public String toString()
	{
		// Normal case
		if (elements.size() > 0) {
			return elements.stream().map(UpdateElement::toString).collect(Collectors.joining(" & "));
		}
		// Special (empty) case
		else {
			return "true";
		}
	}
	
	/**
	 * String representation of the update, with assignment values and array indices/etc. evaluated.
	 * @param state The state in which to evaluate
	 * @param sep String used to separate elements, e.g. " & " or ", "
	 * @param emptyIsTrue If true, format an empty list as "true"; otherwise use ""
	 */
	public String toString(State state, String sep, boolean emptyIsTrue) throws PrismLangException
	{
		// Normal case
		if (elements.size() > 0) {
			boolean first = true;
			String s = "";
			for (UpdateElement ue : elements) {
				if (first)
					first = false;
				else
					s += sep;
				s += ue.evaluate(state, false);
			}
			return s;
		}
		// Special (empty) case
		else {
			return emptyIsTrue ? "true" : "";
		}
	}
}

//------------------------------------------------------------------------------
