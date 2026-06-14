//==============================================================================
//
//	Copyright (c) 2025-
//	Authors:
//	* Zainab Fatmi
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A non-empty sub-block. Each state of a sub-block has the same actions. Each
 * action label of a state s is mapped to the set of blocks that s transitions
 * to with that action. Only blocks that have been split in the previous
 * refinement step are considered.
 *
 * @author Zainab Fatmi
 */
public final class NonEmptyActionSubBlock<Value> extends SubBlock<Value>
{

	/**
	 * The actions of this non-empty sub-block. It maps actions to a set of block IDs.
	 */
	private final Map<Object, Set<Integer>> actions;

	/**
	 * Initializes this non-empty sub-block.
	 */
	public NonEmptyActionSubBlock()
	{
		super();
		this.actions = new HashMap<Object, Set<Integer>>();
	}

	/**
	 * Throws an exception since the states in this sub-block are not probabilistic.
	 *
	 * @throws UnsupportedOperationException always
	 */
	@Override
	public Value get(Integer block)
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * Throws an exception since the states in this sub-block are not probabilistic.
	 *
	 * @throws UnsupportedOperationException always
	 */
	@Override
	public void put(Integer block, Value probability)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(Object action, Integer block)
	{
		if (!actions.containsKey(action)) {
			actions.put(action, new HashSet<Integer>());
		}
		actions.get(action).add(block);
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
		if (object instanceof NonEmptyActionSubBlock) {
			@SuppressWarnings("unchecked")
			NonEmptyActionSubBlock<Value> other = (NonEmptyActionSubBlock<Value>) object;
			if (this.actions.size() != other.actions.size()) {
				return false;
			} else {
				for (Map.Entry<Object, Set<Integer>> entry : this.actions.entrySet()) {
					if (!entry.getValue().equals(other.actions.get(entry.getKey())))
						return false;
				}
			}
			return true;
		} else {
			return false;
		}
	}
}
