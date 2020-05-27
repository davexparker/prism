//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford)
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
import java.util.Map;

/**
 * Simple explicit-state representation of a CTMC.
 */
public class CTMCSimple<Value> extends DTMCSimple<Value> implements CTMC<Value>
{
	/**
	 * The cached embedded DTMC.
	 * <p>
	 * Will become invalid if the CTMC is changed. In this case
	 * construct a new one by calling buildImplicitEmbeddedDTMC()
	 * <p>
	 * We cache this so that the PredecessorRelation of the
	 * embedded DTMC is cached.
	 */
	private DTMCEmbeddedSimple cachedEmbeddedDTMC = null;

	// Constructors

	/**
	 * Constructor: empty CTMC.
	 */
	public CTMCSimple()
	{
		super();
	}

	/**
	 * Constructor: new CTMC with fixed number of states.
	 */
	public CTMCSimple(int numStates)
	{
		super(numStates);
	}

	/**
	 * Copy constructor.
	 */
	public CTMCSimple(CTMCSimple<Value> ctmc)
	{
		super(ctmc);
	}
	
	/**
	 * Construct a CTMC from an existing one and a state index permutation,
	 * i.e. in which state index i becomes index permut[i].
	 * Note: have to build new Distributions from scratch anyway to do this,
	 * so may as well provide this functionality as a constructor.
	 */
	public CTMCSimple(CTMCSimple<Value> ctmc, int permut[])
	{
		super(ctmc, permut);
	}

	// Accessors (for CTMC)
	
	@Override
	public double getExitRate(int i)
	{
		return (Double) trans.get(i).sum();
	}
	
	@Override
	public double getMaxExitRate()
	{
		int i;
		double d, max = Double.NEGATIVE_INFINITY;
		for (i = 0; i < numStates; i++) {
			d = (Double) trans.get(i).sum();
			if (d > max)
				max = d;
		}
		return max;
	}
	
	@Override
	public double getMaxExitRate(BitSet subset)
	{
		int i;
		double d, max = Double.NEGATIVE_INFINITY;
		for (i = subset.nextSetBit(0); i >= 0; i = subset.nextSetBit(i + 1)) {
			d = (Double) trans.get(i).sum();
			if (d > max)
				max = d;
		}
		return max;
	}
	
	@Override
	public double getDefaultUniformisationRate()
	{
		return 1.02 * getMaxExitRate(); 
	}
	
	@Override
	public double getDefaultUniformisationRate(BitSet nonAbs)
	{
		return 1.02 * getMaxExitRate(nonAbs); 
	}
	
	// TODO: only works with doubles (all below)
	
	@Override
	public DTMC<Double> buildImplicitEmbeddedDTMC()
	{
		DTMCEmbeddedSimple dtmc = new DTMCEmbeddedSimple((CTMCSimple<Double>) this);
		if (cachedEmbeddedDTMC != null) {
			// replace cached DTMC
			cachedEmbeddedDTMC = dtmc;
		}
		return (DTMC<Double>) dtmc;
	}
	
	@Override
	public DTMC<Double> getImplicitEmbeddedDTMC()
	{
		if (cachedEmbeddedDTMC == null) {
			cachedEmbeddedDTMC = new DTMCEmbeddedSimple((CTMCSimple<Double>) this);
		}
		return (DTMC<Double>) cachedEmbeddedDTMC;
	}

	
	@Override
	public DTMCSimple<Double> buildEmbeddedDTMC()
	{
		DTMCSimple<Double> dtmc;
		Distribution<Double> distr;
		int i;
		double d;
		dtmc = new DTMCSimple<>(numStates);
		for (int in : getInitialStates()) {
			dtmc.addInitialState(in);
		}
		for (i = 0; i < numStates; i++) {
			distr = (Distribution<Double>) trans.get(i);
			d = distr.sum();
			if (d == 0) {
				dtmc.setProbability(i, i, 1.0);
			} else {
				for (Map.Entry<Integer, Double> e : distr) {
					dtmc.setProbability(i, e.getKey(), e.getValue() / d);
				}
			}
		}
		return (DTMCSimple<Double>) dtmc;
	}

	@Override
	public void uniformise(double q)
	{
		Distribution<Double> distr;
		int i;
		for (i = 0; i < numStates; i++) {
			distr = (Distribution<Double>) trans.get(i);
			distr.set(i, q - distr.sumAllBut(i));
		}
	}

	@Override
	public DTMC<Double> buildImplicitUniformisedDTMC(double q)
	{
		return new DTMCUniformisedSimple((CTMCSimple<Double>) this, q);
	}
	
	@Override
	public DTMCSimple buildUniformisedDTMC(double q)
	{
		DTMCSimple dtmc;
		Distribution<Double> distr;
		int i;
		double d;
		dtmc = new DTMCSimple(numStates);
		for (int in : getInitialStates()) {
			dtmc.addInitialState(in);
		}
		for (i = 0; i < numStates; i++) {
			// Add scaled off-diagonal entries
			distr = (Distribution<Double>) trans.get(i);
			for (Map.Entry<Integer, Double> e : distr) {
				dtmc.setProbability(i, e.getKey(), e.getValue() / q);
			}
			// Add diagonal, if needed
			d = distr.sumAllBut(i);
			if (d < q) {
				dtmc.setProbability(i, i, 1 - (d / q));
			}
		}
		return dtmc;
	}
}
