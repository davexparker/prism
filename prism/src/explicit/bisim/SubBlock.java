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
 * A sub-block of a block. During the refinement step, blocks are split into
 * sub-blocks. A sub-block has an ID, which will be used in the next refinement
 * step when the sub-block plays the role of a block.
 *
 * @author Zainab Fatmi
 * @author Franck van Breugel
 */
public abstract class SubBlock<Value>
{

    /**
     * The ID of this sub-block. It will be used in the next refinement step when
     * the sub-block plays the role of a block
     */
    private int ID;

    /**
     * Returns the ID of this sub-block.
     *
     * @return the ID of this sub-block
     */
    public int getID()
    {
        return this.ID;
    }

    /**
     * Sets the ID of this sub-block to the given ID.
     *
     * @param ID the new ID of this sub-block
     */
    public void setID(int ID)
    {
        this.ID = ID;
    }

    /**
     * Returns the probability of transitioning to states in the given block.
     *
     * @param block the ID of a block
     * @return the probability of transitioning to states in the given block
     */
    public abstract Value get(Integer block);

    /**
     * Sets the probability of transitioning to states in the given block to the
     * given probability.
     *
     * @param block       the ID of a block
     * @param probability the probability of transitioning to states in the block
     */
    public abstract void put(Integer block, Value probability);

    /**
     * Adds the given block to the action label.
     *
     * @param action the action label on a transition to a state in the block
     * @param block  the ID of a block
     */
    public abstract void add(Object action, Integer block);
}
