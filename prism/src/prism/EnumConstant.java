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

/**
 * Representation of an enum constant, e.g. in a State object.
 */
public class EnumConstant implements Comparable<EnumConstant>
{
	/** Name of the enum constant (just for display mostly) */
	private final String name;
	/** Index of the enum constant in its containing type; used as the value in practice */
	private final int index;
	
	/**
	 * Constructor
	 * @param name Name of the enum constant
	 * @param index Index of the enum constant in its containing type
	 */
	public EnumConstant(String name, int index)
	{
		this.name = name;
		this.index = index;
	}

	public String getName()
	{
		return name;
	}

	public int getIndex()
	{
		return index;
	}

	@Override
	public int hashCode()
	{
		// Only use index to for hashing/equality
		// (we assume that type checking takes care of the rest)
		final int prime = 31;
		int result = 1;
		result = prime * result + index;
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		// Only use index to determine equality
		// (we assume that type checking takes care of the rest)
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		EnumConstant other = (EnumConstant) obj;
		if (index != other.index)
			return false;
		return true;
	}

	@Override
	public int compareTo(EnumConstant o)
	{
		// Just need index to determine ordering
		return Integer.compare(index, o.index);
	}

	@Override
	public String toString()
	{
		// Just display name (not actual index)
		return name;
	}
}
