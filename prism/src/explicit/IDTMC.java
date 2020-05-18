//==============================================================================
//	
//	Copyright (c) 2020-
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

package explicit;

import java.util.BitSet;
import java.util.PrimitiveIterator;

import common.Interval;
import common.IterableStateSet;

/**
 * Interface for classes that provide (read) access to an explicit-state MDP,
 * where the transition probabilities are stored as double floating point values.
 * <br>
 * For the generic methods, e.g., the prob0 / prob1 precomputations that do not
 * care about the concrete values, see {@link explicit.MDPGeneric}.
 */
public interface IDTMC<Value> extends DTMC<Interval<Value>>
{
	/**
	 * Do a matrix-vector multiplication for
	 * the DTMC's transition probability matrix P and the vector {@code vect} passed in.
	 * i.e. for all s: result[s] = sum_j P(s,j)*vect[j]
	 * @param vect Vector to multiply by
	 * @param result Vector to store result in
	 * @param subset Only do multiplication for these rows (ignored if null)
	 * @param complement If true, {@code subset} is taken to be its complement (ignored if {@code subset} is null)
	 */
	public default void mvMult(double vect[], MinMax minMax, double result[], BitSet subset, boolean complement)
	{
		mvMult(vect, minMax, result, new IterableStateSet(subset, getNumStates(), complement).iterator());
	}

	/**
	 * Do a matrix-vector multiplication for the DTMC's transition probability matrix P
	 * and the vector {@code vect} passed in, for the state indices provided by the iterator,
	 * i.e., for all s of {@code states}: result[s] = sum_j P(s,j)*vect[j]
	 * <p>
	 * If the state indices in the iterator are not distinct, the result will still be valid,
	 * but this situation should be avoided for performance reasons.
	 * @param vect Vector to multiply by
	 * @param result Vector to store result in
	 * @param states Perform multiplication for these rows, in the iteration order
	 */
	public default void mvMult(double vect[], MinMax minMax, double result[], PrimitiveIterator.OfInt states)
	{
		while (states.hasNext()) {
			int s = states.nextInt();
			result[s] = mvMultSingle(s, vect, minMax);
		}
	}

	/**
	 * Do a single row of matrix-vector multiplication for
	 * the DTMC's transition probability matrix P and the vector {@code vect} passed in.
	 * i.e. return sum_j P(s,j)*vect[j]
	 * @param s Row index
	 * @param vect Vector to multiply by
	 */
	public double mvMultSingle(int s, double vect[], MinMax minMax);
	
	/**
	 * Do a Gauss-Seidel-style matrix-vector multiplication followed by min/max.
	 * i.e. for all s: vect[s] = min/max_k { (sum_{j!=s} P_k(s,j)*vect[j]) / 1-P_k(s,s) }
	 * and store new values directly in {@code vect} as computed.
	 * The maximum (absolute/relative) difference between old/new
	 * elements of {@code vect} is also returned.
	 * Optionally, store optimal (memoryless) strategy info.
	 * @param vect Vector to multiply by (and store the result in)
	 * @param min Min or max for (true=min, false=max)
	 * @param subset Only do multiplication for these rows (ignored if null)
	 * @param complement If true, {@code subset} is taken to be its complement (ignored if {@code subset} is null)
	 * @param absolute If true, compute absolute, rather than relative, difference
	 * @param strat Storage for (memoryless) strategy choice indices (ignored if null)
	 * @return The maximum difference between old/new elements of {@code vect}
	 */
	public default double mvMultGS(double vect[], MinMax minMax, BitSet subset, boolean complement, boolean absolute)
	{
		return mvMultGS(vect, minMax, new IterableStateSet(subset, getNumStates(), complement).iterator(), absolute);
	}

	/**
	 * Do a Gauss-Seidel-style matrix-vector multiplication followed by min/max.
	 * i.e. for all s: vect[s] = min/max_k { (sum_{j!=s} P_k(s,j)*vect[j]) / 1-P_k(s,s) }
	 * and store new values directly in {@code vect} as computed.
	 * The maximum (absolute/relative) difference between old/new
	 * elements of {@code vect} is also returned.
	 * Optionally, store optimal (memoryless) strategy info.
	 * @param vect Vector to multiply by (and store the result in)
	 * @param min Min or max for (true=min, false=max)
	 * @param states Perform computation for these rows, in the iteration order
	 * @param absolute If true, compute absolute, rather than relative, difference
	 * @param strat Storage for (memoryless) strategy choice indices (ignored if null)
	 * @return The maximum difference between old/new elements of {@code vect}
	 */
	public default double mvMultGS(double vect[], MinMax minMax, PrimitiveIterator.OfInt states, boolean absolute)
	{
		double d, diff, maxDiff = 0.0;
		while (states.hasNext()) {
			final int s = states.nextInt();
			d = mvMultJacMinMaxSingle(s, vect, minMax);
			diff = absolute ? (Math.abs(d - vect[s])) : (Math.abs(d - vect[s]) / d);
			maxDiff = diff > maxDiff ? diff : maxDiff;
			vect[s] = d;
		}
		return maxDiff;
	}

	/**
	 * Do a single row of Jacobi-style matrix-vector multiplication followed by min/max.
	 * i.e. return min/max_k { (sum_{j!=s} P_k(s,j)*vect[j]) / 1-P_k(s,s) }
	 * Optionally, store optimal (memoryless) strategy info.
	 * @param s Row index
	 * @param vect Vector to multiply by
	 * @param min Min or max for (true=min, false=max)
	 * @param strat Storage for (memoryless) strategy choice indices (ignored if null)
	 */
	public double mvMultJacMinMaxSingle(int s, double vect[], MinMax minMax);
}
