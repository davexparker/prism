//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford, formerly University of Birmingham)
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

package parser.visitor;

import java.util.List;

import parser.ast.ExpressionIdent;
import parser.ast.ExpressionVar;
import parser.type.Type;
import prism.PrismLangException;

/**
 * Find all references to variables, replace any identifier objects with variable objects
 * (i.e. convert ExpressionIdent -> ExpressionVar) and check variables exist.
 */
public class FindAllVars extends ASTTraverseModify
{
	private List<String> varIdents;
	private List<Type> varTypes;
	
	public FindAllVars(List<String> varIdents, List<Type> varTypes)
	{
		this.varIdents = varIdents;
		this.varTypes = varTypes;
	}
	
	public Object visit(ExpressionIdent e) throws PrismLangException
	{
		// See if identifier corresponds to a variable
		int i = varIdents.indexOf(e.getName());
		if (i != -1) {
			// If so, replace it with an ExpressionVar object
			ExpressionVar expr = new ExpressionVar(e.getName(), varTypes.get(i));
			expr.setPosition(e);
			return expr;
		}
		// Otherwise, leave it unchanged
		return e;
	}
}

