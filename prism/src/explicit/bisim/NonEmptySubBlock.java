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

import java.util.HashMap;
import java.util.Map;

import prism.Evaluator;

/**
 * A non-empty sub-block. Each state of a sub-block has the same lifting. This
 * lifting of a state s maps each block b to the probability of the state s
 * transitioning to states in that block b. Only blocks that have been split in
 * the previous refinement step are considered.
 *
 * @author Zainab Fatmi
 * @author Franck van Breugel
 */
public final class NonEmptySubBlock<Value> extends SubBlock<Value>
{

    /**
     * Evaluator for manipulating probability values in the distribution (of type
     * {@code Value})
     */
    private Evaluator<Value> eval;

    /**
     * The lifting of this non-empty sub-block. It maps block IDs to probabilities.
     */
    private final Map<Integer, Value> lifting;

    /**
     * Initializes this non-empty sub-block.
     */
    public NonEmptySubBlock(Evaluator<Value> eval)
    {
        super();
        this.lifting = new HashMap<Integer, Value>();
        this.eval = eval;
    }

    @Override
    public Value get(Integer block)
    {
        return this.lifting.get(block);
    }

    @Override
    public void put(Integer block, Value probability)
    {
        this.lifting.put(block, probability);
    }

    /**
     * Throws an exception since the states in this sub-block are probabilistic.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void add(Object action, Integer block)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Tests whether the given object is equal to this non-empty sub-block. Two
     * non-empty sub-blocks are considered equal if their liftings maps blocks to
     * probabilities that are very close.
     *
     * @param object an object
     * @return true if the given object is equal to this non-empty sub-block,
     * false otherwise
     */
    @Override
    public boolean equals(Object object)
    {
        if (object instanceof NonEmptySubBlock) {
            @SuppressWarnings("unchecked")
            NonEmptySubBlock<Value> other = (NonEmptySubBlock<Value>) object;
            if (this.lifting.size() != other.lifting.size()) {
                return false;
            } else {
                for (Map.Entry<Integer, Value> entry : this.lifting.entrySet()) {
                    Value probability = other.lifting.get(entry.getKey());
                    if (probability == null || !eval.equals(entry.getValue(), probability)) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            return false;
        }
    }
}
