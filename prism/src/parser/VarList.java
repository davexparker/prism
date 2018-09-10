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

package parser;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import param.BigRational;
import parser.ast.ASTElement;
import parser.ast.Declaration;
import parser.ast.DeclarationArray;
import parser.ast.DeclarationBool;
import parser.ast.DeclarationClock;
import parser.ast.DeclarationEnum;
import parser.ast.DeclarationInt;
import parser.ast.DeclarationIntUnbounded;
import parser.ast.DeclarationStruct;
import parser.ast.DeclarationType;
import parser.ast.Expression;
import parser.ast.ExpressionArrayAccess;
import parser.ast.ExpressionStruct;
import parser.ast.ExpressionStructAccess;
import parser.ast.ExpressionVar;
import parser.ast.Module;
import parser.ast.ModulesFile;
import parser.type.Type;
import parser.type.TypeBool;
import parser.type.TypeEnum;
import parser.type.TypeInt;
import parser.visitor.ASTTraverse;
import prism.EnumConstant;
import prism.PrismLangException;
import prism.PrismUtils;

/**
 * Class to store information about the set of variables in a model.
 * Assumes that any constants in the model have been given fixed values.
 * Thus, initial/min/max values for all variables and array lengths are known.
 * VarList also takes care of how each variable will be encoded to an integer
 * (e.g. for (MT)BDD representation).
 */
public class VarList
{
	// Are we in "exact" mode? (using exact arithmetic to evaluate constants/expressions)
	private boolean exact;
	
	// List of (top-level) variables
	private List<Var> vars;
	// List of all (primitive) variables, expanding arrays etc.
	private List<VarPrimitive> allVars;
	
	// Mapping from (top-level) variable names to index in "vars" list
	private Map<String, Integer> nameMap;
	// Total number of bits needed  to encode
	private int totalNumBits;

	/**
	 * Construct empty variable list.
	 */
	public VarList()
	{
		initialise();
	}

	private void initialise()
	{
		vars = new ArrayList<Var>();
		allVars = new ArrayList<VarPrimitive>();
		nameMap = new HashMap<String, Integer>();
		totalNumBits = 0;
	}
	
	/**
	 * Construct variable list for a ModulesFile.
	 * @param modulesFile The ModulesFile
	 */
	public VarList(ModulesFile modulesFile) throws PrismLangException
	{
		this(modulesFile, false);
	}
	
	/**
	 * Construct variable list for a ModulesFile.
	 * @param modulesFile The ModulesFile
	 * @param exact use exact arithmetic in evaluation of init values?
	 */
	public VarList(ModulesFile modulesFile, boolean exact) throws PrismLangException
	{
		this();
		
		this.exact = exact;

		// First add all globals to the list
		int numGlobals = modulesFile.getNumGlobals();
		for (int i = 0; i < numGlobals; i++) {
			addVar(modulesFile.getGlobal(i), -1, modulesFile.getConstantValues());
		}

		// Then add all module local variables to the list
		int numModules = modulesFile.getNumModules();
		for (int i = 0; i < numModules; i++) {
			Module module = modulesFile.getModule(i);
			int numLocals = module.getNumDeclarations();
			for (int j = 0; j < numLocals; j++) {
				addVar(module.getDeclaration(j), i, modulesFile.getConstantValues());
			}
		}
	}

	/**
	 * Add a new variable to the end of the VarList.
	 * @param decl Declaration defining the variable
	 * @param module Index of module containing variable
	 * @param constantValues Values of constants needed to evaluate low/high/etc.
	 */
	public void addVar(Declaration decl, int module, Values constantValues) throws PrismLangException
	{
		Var var = createVar(decl, module, constantValues);
		vars.add(var);
		totalNumBits += getRangeLogTwo(vars.size() - 1);
		nameMap.put(decl.getName(), vars.size() - 1);
	}

	/**
	 * Add a new variable to the start of the VarList.
	 * Warning: this will result in some data being recreated in the list,
	 * so you need to make sure that constantValues is the same as used for the existing variables.
	 * @param decl Declaration defining the variable
	 * @param module Index of module containing variable
	 * @param constantValues Values of constants needed to evaluate low/high/etc.
	 */
	public void addVarAtStart(Declaration decl, int module, Values constantValues) throws PrismLangException
	{
		// Just recreate the data - easier and safer
		int numVarsOld = vars.size();
		List<Var> varsOld = vars;
		initialise();
		addVar(decl, module, constantValues);
		for (int j = 0; j < numVarsOld; j++) {
			Var varOld = varsOld.get(j); 
			addVar(varOld.decl, varOld.module, constantValues);
		}
	}

	/**
	 * Add a new variable at position i in the VarList.
	 * Index i refers to the indexing of primitive variables,
	 * for backwards compatibility, which is a bit odd. Deprecated.
	 * @param decl Declaration defining the variable
	 * @param module Index of module containing variable
	 * @param constantValues Values of constants needed to evaluate low/high/etc.
	 * @deprecated If possible, use {@link #addVar(Declaration, int, Values)} or {@link #addVarAtStart(Declaration, int, Values)}
	 */
	public void addVar(int i, Declaration decl, int module, Values constantValues) throws PrismLangException
	{
		if (i == 0) {
			addVarAtStart(decl, module, constantValues);
		} else if (i == allVars.size()) {
			addVar(decl, module, constantValues);
		} else {
			throw new PrismLangException("Cannot add variable to position " + i + " of VarList");
		}
	}

	/**
	 * Create and return a new variable object to store in the list.
	 * Variable objects for all primitive variables are also created
	 * and stored in the allVars list along the way.
	 * @param decl Declaration defining the variable
	 * @param module Index of module containing variable
	 * @param constantValues Values of constants needed to evaluate low/high/etc.
	 */
	private Var createVar(Declaration decl, int module, Values constantValues) throws PrismLangException
	{
		return createVar(decl, module, "", decl.getDeclType(), decl.getStartOrDefault(), constantValues);
	}

	/**
	 * Recursive helper function for {@link #createVar(Declaration, int, Values)}
	 * @param decl Declaration defining the variable (or its parent)
	 * @param nameSuffix Suffix to be added to the name of new variables created
	 * @param declType DeclarationType defining the variable's type
	 * @param exprInit Expression defining the initial state of the variable 
	 * @param constantValues Values of constants needed to evaluate low/high/etc.
	 */
	private Var createVar(Declaration decl, int module, String nameSuffix, DeclarationType declType, Expression exprInit, Values constantValues) throws PrismLangException
	{
		int startIndex = allVars.size();
		Var var = null;
		
		// Primitive variables
		if (declType.getType().isPrimitive()) {

			// Evaluate initial value
			Object initialValue;
			if (exact) {
				BigRational r = exprInit.evaluateExact(constantValues);
				initialValue = declType.getType().castFromBigRational(r);
			} else {
				initialValue = exprInit.evaluate(constantValues);
				initialValue = declType.getType().castValueTo(initialValue);
			}

			// Variable is a bounded integer
			if (declType instanceof DeclarationInt) {

				DeclarationInt intdecl = (DeclarationInt) declType;
				int low = intdecl.getLow().evaluateInt(constantValues);
				int high = intdecl.getHigh().evaluateInt(constantValues);
				int start = exprInit.evaluateInt(constantValues);
				// Check range is valid
				if (high - low <= 0) {
					String s = "Invalid range (" + low + "-" + high + ") for variable \"" + decl.getName() + "\"";
					throw new PrismLangException(s, decl);
				}
				if ((long) high - (long) low >= Integer.MAX_VALUE) {
					String s = "Range for variable \"" + decl.getName() + "\" (" + low + "-" + high + ") is too big";
					throw new PrismLangException(s, decl);
				}
				// Check start is valid
				if (start < low || start > high) {
					String s = "Invalid initial value (" + start + ") for variable \"" + decl.getName() + "\"";
					throw new PrismLangException(s, decl);
				}
				var = new VarPrimitive(decl.getName() + nameSuffix, declType.getType(), initialValue, low, high, start);
				allVars.add((VarPrimitive) var);
			}

			// Variable is a Boolean
			else if (declType instanceof DeclarationBool) {
				int start = exprInit.evaluateBoolean(constantValues) ? 1 : 0;
				var = new VarPrimitive(decl.getName() + nameSuffix, declType.getType(), initialValue, 0, 1, start);
				allVars.add((VarPrimitive) var);
			}

			// Variable is an enum
			else if (declType instanceof DeclarationEnum) {
				if (!(initialValue instanceof EnumConstant)) {
					throw new PrismLangException("Enum variables can only be initialised to an appropriate constant", decl);
				}
				int start = ((EnumConstant) initialValue).getIndex();
				int size = ((DeclarationEnum) declType).getNumConstants();
				var = new VarPrimitive(decl.getName() + nameSuffix, declType.getType(), initialValue, 0, size - 1, start);
				allVars.add((VarPrimitive) var);
			}

			// Variable is a clock
			else if (declType instanceof DeclarationClock) {
				// Just use dummy info
				var = new VarPrimitive(decl.getName() + nameSuffix, declType.getType(), 0, 0, 1, 0);
				allVars.add((VarPrimitive) var);
			}

			// Variable is an (unbounded) integer
			else if (declType instanceof DeclarationIntUnbounded) {
				// Just use dummy range info
				int start = exprInit.evaluateInt(constantValues);
				var = new VarPrimitive(decl.getName() + nameSuffix, declType.getType(), initialValue, 0, 1, start);
				allVars.add((VarPrimitive) var);
			}
		}
		// Variable is an array
		else if (declType instanceof DeclarationArray) {
			int length = ((DeclarationArray) declType).getLength().evaluateInt(constantValues);
			if (length < 0) {
				String s = "Invalid size (" + length + ") for array \"" + decl.getName() + "\"";
				throw new PrismLangException(s, decl);
			}
			VarArray varArray = new VarArray(decl.getName() + nameSuffix, declType.getType());
			for (int i = 0; i < length; i++) {
				Var varElement = createVar(decl, module, nameSuffix + "[" + i + "]", ((DeclarationArray) declType).getSubtype(), exprInit, constantValues);
				varArray.elements.add(varElement);
			}
			varArray.elementSize = varArray.elements.get(0).numPrimitives;
			var = varArray;
		}
		// Variable is a struct
		else if (declType instanceof DeclarationStruct) {
			DeclarationStruct declStruct = (DeclarationStruct) declType;
			int numFields = declStruct.getNumFields();
			VarStruct varStruct = new VarStruct(decl.getName() + nameSuffix, declType.getType());
			if (!(exprInit instanceof ExpressionStruct)) {
				throw new PrismLangException("Struct variable can only be initialised to a struct", decl);
			}
			int fieldOffset = 0;
			for (int i = 0; i < numFields; i++) {
				String fieldName = declStruct.getFieldName(i);
				Var varField = createVar(decl, module, nameSuffix + "." + fieldName, declStruct.getFieldType(i), ((ExpressionStruct) exprInit).getField(i), constantValues);
				varStruct.fields.add(varField);
				varStruct.fieldNames.add(fieldName);
				varStruct.fieldOffsets.add(fieldOffset);
				fieldOffset += varField.numPrimitives;
			}
			var = varStruct;
		}
		// Unknown variable type
		else {
			throw new PrismLangException("Unknown variable type \"" + declType + "\" in declaration", decl);

		}
		
		// Store declaration/module
		var.decl = decl;
		var.module = module;
		// Store indexing info
		var.startIndex = startIndex;
		var.endIndex = allVars.size() - 1;
		var.numPrimitives = var.endIndex - var.startIndex + 1; 
		
		return var;
	}
	
	/**
	 * Get the number of (top-level) variables stored in this list.
	 * E.g. this returns 3 for variable list { a, b[2], c[2][2] } 
	 */
	public int getNumTopLevelVars()
	{
		return vars.size();
	}

	/**
	 * Get the number of (primitive) variables stored in this list.  
	 * E.g. this returns 1+2+4=7 for variable list { a, b[2], c[2][2] } 
	 */
	public int getNumVars()
	{
		return allVars.size();
	}

	/**
	 * Look up the index of a (top-level) variable, as stored in this list, by name.
	 * Returns -1 if there is no such variable. 
	 */
	public int getIndex(String name)
	{
		Integer i = nameMap.get(name);
		return (i == null) ? -1 : i;
	}

	/**
	 * Check if there is a (top-level) variable of a given name in this list.
	 */
	public boolean exists(String name)
	{
		return getIndex(name) != -1;
	}

	/**
	 * Get the declaration of the ith (top-level) variable in this list.
	 */
	public Declaration getDeclaration(int i)
	{
		return vars.get(i).decl;
	}

	/**
	 * Get the name of the ith (primitive) variable in this list.
	 */
	public String getName(int i)
	{
		return allVars.get(i).name;
	}

	/**
	 * Get the type of the ith variable in this list.
	 */
	public Type getType(int i)
	{
		return allVars.get(i).type;
	}

	/**
	 * Get the initial value of the ith variable in this list.
	 */
	public Object getInitialValue(int i)
	{
		return allVars.get(i).init;
	}

	/**
	 * Get the index of the module of the ith variable in this list (-1 denotes global variable).
	 */
	public int getModule(int i)
	{
		return allVars.get(i).module;
	}

	/**
	 * Get the low value of the ith variable in this list (when encoded as an integer).
	 */
	public int getLow(int i)
	{
		return allVars.get(i).low;
	}

	/**
	 * Get the high value of the ith variable in this list (when encoded as an integer).
	 */
	public int getHigh(int i)
	{
		return allVars.get(i).high;
	}

	/**
	 * Get the range of the ith variable in this list (when encoded as an integer).
	 */
	public int getRange(int i)
	{
		return getHigh(i) - getLow(i) + 1;
	}

	/**
	 * Get the number of bits required to store the ith variable in this list (when encoded as an integer).
	 */
	public int getRangeLogTwo(int i)
	{
		return (int) Math.ceil(PrismUtils.log2(getRange(i)));
	}

	/**
	 * Get the total number of bits required to store all variables in this list (when encoded as integers).
	 */
	public int getTotalNumBits()
	{
		return totalNumBits;
	}

	/**
	 * Get the initial value of the ith variable in this list (when encoded as an integer).
	 */
	public int getStart(int i)
	{
		return allVars.get(i).start;
	}

	/**
	 * Get the value (as an Object) for the ith variable, from its encoding as an integer. 
	 */
	public Object decodeFromInt(int i, int val)
	{
		Type type = getType(i);
		// Integer type
		if (type instanceof TypeInt) {
			return val + getLow(i);
		}
		// Boolean type
		else if (type instanceof TypeBool) {
			return val != 0;
		}
		// Enum type
		else if (type instanceof TypeEnum) {
			return ((TypeEnum) type).getConstant(val);
		}
		// Anything else
		return null;
	}

	/**
	 * Get the integer encoding of a value for the ith variable, specified as an Object.
	 * The Object is assumed to be of correct type (e.g. Integer, Boolean).
	 * Throws an exception if Object is of the wrong type.
	 */
	public int encodeToInt(int i, Object val) throws PrismLangException
	{
		Type type = getType(i);
		try {
			// Integer type
			if (type instanceof TypeInt) {
				return ((TypeInt) type).castValueTo(val).intValue() - getLow(i);
			}
			// Boolean type
			else if (type instanceof TypeBool) {
				return ((TypeBool) type).castValueTo(val).booleanValue() ? 1 : 0;
			}
			// Enum type
			else if (type instanceof TypeEnum) {
				return ((TypeEnum) type).castValueTo(val).getIndex();
			}
			// Anything else
			else {
				throw new PrismLangException("Unknown type " + type + " for variable " + getName(i));
			}
		} catch (ClassCastException e) {
			throw new PrismLangException("Value " + val + " is wrong type for variable " + getName(i));
		}
	}

	/**
	 * Get the integer encoding of a value for the ith variable, specified as a string.
	 */
	public int encodeToIntFromString(int i, String s) throws PrismLangException
	{
		Type type = getType(i);
		// Integer type
		if (type instanceof TypeInt) {
			try {
				int iVal = Integer.parseInt(s);
				return iVal - getLow(i);
			} catch (NumberFormatException e) {
				throw new PrismLangException("\"" + s + "\" is not a valid integer value");
			}
		}
		// Boolean type
		else if (type instanceof TypeBool) {
			if (s.equals("true"))
				return 1;
			else if (s.equals("false"))
				return 0;
			else
				throw new PrismLangException("\"" + s + "\" is not a valid Boolean value");

		}
		// Enum type
		else if (type instanceof TypeEnum) {
			return ((TypeEnum) type).getConstantByName(s).getIndex();
		}
		// Anything else
		else {
			throw new PrismLangException("Unknown type " + type + " for variable " + getName(i));
		}
	}

	/**
	 * Add variable indexing info recursively to an ASTElement.
	 * In particular, set the variable index on ExpressionVar objects,
	 * the array length and element size for ExpressionArrayAccess objects,
	 * and the field info for ExpressionStructAccess objects.
	 */
	public void addVarIndexing(ASTElement e) throws PrismLangException
	{
		// Do this is using a visitor template since we want to traverse the whole AST element.
		// Can just use ASTTraverse, not ASTTraverseModify, even though we do make changes to
		// the ASTElements, because we just call set methods, never actually replace whole elements.
		
		// For ASTElements making up variable references, e.g. an ExpressionVar nested inside
		// one or more ExpressionArrayAccess objects, we override visit to return a Var object,
		// as needed to compute the indexing info.
		
		e.accept(new ASTTraverse()
		{
			public Object visit(ExpressionArrayAccess varRef) throws PrismLangException
			{
				// Recurse on the index - there could be variable/array references in there
				varRef.getIndex().accept(this);
				// And recurse on the array, to get get the Var object for the child
				VarArray varArray = (VarArray) varRef.getArray().accept(this); 
				// Set the indexing info and return
				varRef.setVarIndexElementSize(varArray.elementSize);
				varRef.setArrayLength(varArray.elements.size());
				// Var elements are all the same so just return the first one
				return varArray.elements.get(0);
			}
			
			public Object visit(ExpressionStructAccess varRef) throws PrismLangException
			{
				// Recurse on the struct, to get get the Var object for the child
				VarStruct varStruct = (VarStruct) varRef.getStruct().accept(this); 
				// Set the indexing info and return
				int fieldNum = varStruct.fieldNames.indexOf(varRef.getField());
				if (fieldNum == -1) {
					throw new PrismLangException("Unknown field " + varRef.getField(), varRef);
				}
				varRef.setVarIndexOffset(varStruct.fieldOffsets.get(fieldNum));
				return varStruct.fields.get(fieldNum);
			}
			
			public Object visit(ExpressionVar varRef) throws PrismLangException
			{
				// Look up the variable name and corresponding Var object 
				int i = nameMap.get(varRef.getName());
				if (i == -1) {
					throw new PrismLangException("Unknown variable " + ((ExpressionVar) varRef).getName(), varRef);
				}
				Var var = vars.get(i);
				// Set the indexing info and return
				varRef.setVarIndex(var.startIndex);
				return var;
			}
		});
	}
	
	/**
	 * Create a State object representing the default initial state using these variables.
	 */
	public State getDefaultInitialState() throws PrismLangException
	{
		int numVars = getNumVars();
		int count = 0;
		State initialState = new State(numVars);
		for (int i = 0; i < numVars; i++) {
			initialState.setValue(count++, getInitialValue(i));
		}
		return initialState;
	}

	/**
	 * Get a list of all possible values for a subset of the variables in this list.
	 * @param vars The subset of variables
	 */
	public List<Values> getAllValues(List<String> vars) throws PrismLangException
	{
		int i, j, k, n, lo, hi;
		Vector<Values> allValues;
		Values vals, valsNew;

		allValues = new Vector<Values>();
		allValues.add(new Values());
		for (String var : vars) {
			i = getIndex(var);
			if (getType(i) instanceof TypeBool) {
				n = allValues.size();
				for (j = 0; j < n; j++) {
					vals = allValues.get(j);
					valsNew = new Values(vals);
					valsNew.setValue(var, true);
					allValues.add(valsNew);
					vals.addValue(var, false);
				}
			} else if (getType(i) instanceof TypeInt) {
				lo = getLow(i);
				hi = getHigh(i);
				n = allValues.size();
				for (j = 0; j < n; j++) {
					vals = allValues.get(j);
					for (k = lo + 1; k < hi + 1; k++) {
						valsNew = new Values(vals);
						valsNew.setValue(var, k);
						allValues.add(valsNew);
					}
					vals.addValue(var, lo);
				}
			} else if (getType(i) instanceof TypeEnum) {
				TypeEnum te = (TypeEnum) getType(i);
				n = te.getNumConstants();
				for (j = 0; j < n; j++) {
					vals = allValues.get(j);
					for (k = 1; k < n; k++) {
						valsNew = new Values(vals);
						valsNew.setValue(var, te.getConstant(k));
						allValues.add(valsNew);
					}
					vals.addValue(var, te.getConstant(0));
				}
			} else {
				throw new PrismLangException("Cannot determine all values for a variable of type " + getType(i));
			}
		}

		return allValues;
	}

	/**
	 * Get a list of all possible states over the variables in this list. Use with care!
	 */
	public List<State> getAllStates() throws PrismLangException
	{
		List<State> allStates;
		State state, stateNew;

		int numVars = getNumVars();
		allStates = new ArrayList<State>();
		allStates.add(new State(numVars));
		for (int i = 0; i < numVars; i++) {
			if (getType(i) instanceof TypeBool) {
				int n = allStates.size();
				for (int j = 0; j < n; j++) {
					state = allStates.get(j);
					stateNew = new State(state);
					stateNew.setValue(i, true);
					state.setValue(i, false);
					allStates.add(stateNew);
				}
			} else if (getType(i) instanceof TypeInt) {
				int lo = getLow(i);
				int hi = getHigh(i);
				int n = allStates.size();
				for (int j = 0; j < n; j++) {
					state = allStates.get(j);
					for (int k = lo + 1; k < hi + 1; k++) {
						stateNew = new State(state);
						stateNew.setValue(i, k);
						allStates.add(stateNew);
					}
					state.setValue(i, lo);
				}
			} else if (getType(i) instanceof TypeEnum) {
				TypeEnum te = (TypeEnum) getType(i);
				int n = te.getNumConstants();
				for (int j = 0; j < n; j++) {
					state = allStates.get(j);
					for (int k = 1; k < n; k++) {
						stateNew = new State(state);
						stateNew.setValue(i, te.getConstant(k));
						allStates.add(stateNew);
					}
					state.setValue(i, te.getConstant(0));
				}
			} else {
				throw new PrismLangException("Cannot determine all values for a variable of type " + getType(i));
			}
		}

		return allStates;
	}

	/**
	 * Convert a bit vector representing a single state to a State object. 
	 */
	public State convertBitSetToState(BitSet bits)
	{
		int i, n, j, var, val;
		State state;
		state = new State(getNumVars());
		var = val = j = 0;
		n = totalNumBits;
		for (i = 0; i < n; i++) {
			if (bits.get(i))
				val += (1 << (getRangeLogTwo(var) - j - 1));
			if (j >= getRangeLogTwo(var) - 1) {
				state.setValue(var, decodeFromInt(var, val));
				var++;
				val = 0;
				j = 0;
			} else {
				j++;
			}
		}
		return state;
	}

	/**
	 * Perform a deep copy.
	 */
	public VarList deepCopy()
	{
		VarList ret = new VarList();
		ret.exact = exact;
		int n = vars.size();
		ret.vars = new ArrayList<Var>(n);
		ret.nameMap = new HashMap<String, Integer>(n);
		for (int i = 0; i < n; i++) {
			ret.vars.add(vars.get(i).deepCopy());
			ret.nameMap.put(getName(i), i);
		}
		n = allVars.size();
		ret.allVars = new ArrayList<VarPrimitive>(n);
		for (int i = 0; i < n; i++) {
			ret.allVars.add(allVars.get(i).deepCopy());
		}
		return ret;
	}
	
	@Override
	public Object clone()
	{
		return deepCopy();
	}

	/**
	 * Class to store information about a single variable, or subvariable (e.g. array element)
	 */
	class Var
	{
		// Name
		public String name;
		// Type
		public Type type;
		// Basic info (name/type/etc.) stored as Declaration
		public Declaration decl;
		// Index of containing module (-1 for a global)
		public int module;
		// Start and end index for this in the list of all (primitive) vars
		public int startIndex;
		public int endIndex;
		// Num vars this corresponds to when arrays etc. are expanded
		public int numPrimitives;

		/** Default constructor */
		public Var(String name, Type type)
		{
			this.name = name;
			this.type = type;
		}

		/** Copy constructor */
		public Var(Var var)
		{
			name = var.name;
			type = var.type;
			decl = (Declaration) var.decl.deepCopy();
			module = var.module;
			startIndex = var.startIndex;
			endIndex = var.endIndex;
			numPrimitives = var.numPrimitives;
		}
		
		@Override
		public String toString()
		{
			return name + ":" + startIndex + "-" + endIndex;
		}
		
		/**
		 * Perform a deep copy.
		 */
		public Var deepCopy()
		{
			return new Var(this);
		}
	}
	
	class VarPrimitive extends Var
	{
		// Initial value
		public Object init;
		// Info about how variable is encoded as an integer
		public int low;
		public int high;
		public int start;
		
		/** Default constructor */
		public VarPrimitive(String name, Type type, Object init, int low, int high, int start)
		{
			super(name, type);
			this.init = init;
			this.low = low;
			this.high = high;
			this.start = start;
		}

		/** Copy constructor */
		public VarPrimitive(VarPrimitive var)
		{
			super(var);
			this.init = var.init;
			this.low = var.low;
			this.high = var.high;
			this.start = var.start;
		}

		@Override
		public VarPrimitive deepCopy()
		{
			return new VarPrimitive(this);
		}
	}
	
	class VarArray extends Var
	{
		// Elements of the array
		public List<Var> elements = new ArrayList<>();
		
		// Size of each array element (in terms of number of primitive vars)
		public int elementSize;
		
		/** Default constructor */
		public VarArray(String name, Type type)
		{
			super(name, type);
		}

		/** Copy constructor */
		public VarArray(VarArray var)
		{
			super(var);
			for (Var element : var.elements) {
				elements.add(element.deepCopy());
			}
			elementSize = var.elementSize;
		}
		
		@Override
		public VarArray deepCopy()
		{
			return new VarArray(this);
		}
	}
	
	class VarStruct extends Var
	{
		// Fields of the struct
		public List<Var> fields = new ArrayList<>();
		
		// Names of each struct field
		public List<String> fieldNames = new ArrayList<>();
		
		// Offsets of each struct field (in terms of number of primitive vars)
		public List<Integer> fieldOffsets = new ArrayList<>();
		
		/** Default constructor */
		public VarStruct(String name, Type type)
		{
			super(name, type);
		}

		/** Copy constructor */
		public VarStruct(VarStruct var)
		{
			super(var);
			for (Var field : var.fields) {
				fields.add(field.deepCopy());
			}
			for (String name : var.fieldNames) {
				fieldNames.add(name);
			}
			for (int offset : var.fieldOffsets) {
				fieldOffsets.add(offset);
			}
		}
		
		@Override
		public VarStruct deepCopy()
		{
			return new VarStruct(this);
		}
	}
}
