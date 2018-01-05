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

package prism;

import java.util.List;

import parser.Values;
import parser.VarList;
import parser.type.Type;

/**
 * A copy of a ModelInfo object. Intended as a base for classes that only change some info. 
 */
public abstract class ModelInfoCopy implements ModelInfo
{
	private ModelInfo base;
	
	public ModelInfoCopy(ModelInfo base)
	{
		this.base = base;
	}
	
	@Override
	public ModelType getModelType()
	{
		return base.getModelType();
	}

	@Override
	public void setSomeUndefinedConstants(Values someValues) throws PrismException
	{
		base.setSomeUndefinedConstants(someValues);
	}

	@Override
	public Values getConstantValues()
	{
		return base.getConstantValues();
	}

	@Override
	public boolean containsUnboundedVariables()
	{
		return base.containsUnboundedVariables();
	}

	@Override
	public int getNumVars()
	{
		return base.getNumVars();
	}
	
	@Override
	public List<String> getVarNames()
	{
		return base.getVarNames();
	}

	@Override
	public int getVarIndex(String name)
	{
		return base.getVarIndex(name);
	}

	@Override
	public String getVarName(int i)
	{
		return base.getVarName(i);
	}

	@Override
	public List<Type> getVarTypes()
	{
		return base.getVarTypes();
	}

	@Override
	public Type getVarType(int i) throws PrismException
	{
		return base.getVarType(i);
	}

	@Override
	public int getNumLabels()
	{
		return base.getNumLabels();
	}
	
	@Override
	public List<String> getLabelNames()
	{
		return base.getLabelNames();
	}
	
	@Override
	public String getLabelName(int i) throws PrismException
	{
		return base.getLabelName(i);
	}
	
	@Override
	public int getLabelIndex(String name)
	{
		return base.getLabelIndex(name);
	}
	
	@Override
	public VarList createVarList() throws PrismException
	{
		return base.createVarList();
	}
}
