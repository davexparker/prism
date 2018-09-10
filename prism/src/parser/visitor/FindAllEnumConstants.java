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

package parser.visitor;

import java.util.Map;

import parser.ast.ExpressionIdent;
import parser.ast.ExpressionLiteral;
import parser.type.TypeEnum;
import prism.PrismLangException;

/**
 * Find all references to enum constants, replace any identifier objects with literal objects
 * (i.e. convert ExpressionIdent -> ExpressionLiteral).
 */
public class FindAllEnumConstants extends ASTTraverseModify
{
	private Map<String, TypeEnum> enumConstTypes;
	
	public FindAllEnumConstants(Map<String, TypeEnum> enumConstTypes)
	{
		this.enumConstTypes = enumConstTypes;
	}
	
	public Object visit(ExpressionIdent e) throws PrismLangException
	{
		// See if identifier corresponds to an enum constant
		TypeEnum t = enumConstTypes.get(e.getName());
		if (t != null) {
			// If so, replace it with an ExpressionLiteral object
			ExpressionLiteral expr = new ExpressionLiteral(t, t.getConstantByName(e.getName()));
			expr.setPosition(e);
			return expr;
		}
		// Otherwise, leave it unchanged
		return e;
	}
}

