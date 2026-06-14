//==============================================================================
//	
//	Copyright (c) 2020-
//	Authors:
//	* Zainab Fatmi
//	* Franck van Breugel
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

package explicit.bisim;

/**
 * An empty sub-block.
 *
 * @author Zainab Fatmi
 * @author Franck van Breugel
 */
public final class EmptySubBlock<Value> extends SubBlock<Value>
{

    /**
     * Tests whether the given object is an empty sub-block.
     *
     * @param object an object
     * @return true if the given object is an empty sub-block, false otherwise
     */
    @Override
    public boolean equals(Object object)
    {
        return object instanceof EmptySubBlock;
    }

    /**
     * Throws an exception since the states in this empty sub-block do not
     * transition to any of the blocks that have been split in the previous
     * refinement step.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public Value get(Integer block)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws an exception since the states in this empty sub-block do not
     * transition to any of the blocks that have been split in the previous
     * refinement step.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void put(Integer block, Value probability)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws an exception since the states in this empty sub-block do not
     * transition to any of the blocks that have been split in the previous
     * refinement step.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void add(Object action, Integer block)
    {
        throw new UnsupportedOperationException();
    }
}
