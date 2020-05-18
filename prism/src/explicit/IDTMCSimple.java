//==============================================================================
//	
//	Copyright (c) 2020-
//	Authors:
//	* Dave Parker <d.a.parker@cs.bham.ac.uk> (University of Birmingham)
//	* Alberto Puggelli <alberto.puggelli@gmail.com>
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import common.Interval;
import prism.ModelType;

/**
 * Simple explicit-state representation of a DTMC.
 */
public class IDTMCSimple<Value> extends DTMCSimple<Interval<Value>> implements IDTMC<Value>
{
	// Constructors

	/**
	 * Constructor: empty DTMC.
	 */
	public IDTMCSimple()
	{
		initialise(0);
	}

	/**
	 * Constructor: new DTMC with fixed number of states.
	 */
	public IDTMCSimple(int numStates)
	{
		initialise(numStates);
	}

	/**
	 * Copy constructor.
	 */
	public IDTMCSimple(IDTMCSimple<Value> dtmc)
	{
		this(dtmc.numStates);
		copyFrom(dtmc);
		for (int i = 0; i < numStates; i++) {
			trans.set(i, new Distribution<>(dtmc.trans.get(i)));
		}
		numTransitions = dtmc.numTransitions;
	}

	/**
	 * Construct a DTMC from an existing one and a state index permutation,
	 * i.e. in which state index i becomes index permut[i].
	 * Pointer to states list is NOT copied (since now wrong).
	 * Note: have to build new Distributions from scratch anyway to do this,
	 * so may as well provide this functionality as a constructor.
	 */
	public IDTMCSimple(IDTMCSimple<Value> dtmc, int permut[])
	{
		this(dtmc.numStates);
		copyFrom(dtmc, permut);
		for (int i = 0; i < numStates; i++) {
			trans.set(permut[i], new Distribution<>(dtmc.trans.get(i), permut));
		}
		numTransitions = dtmc.numTransitions;
	}

	// Accessors (for Model)

	@Override
	public ModelType getModelType()
	{
		return ModelType.IDTMC;
	}

	// Accessors (for IDTMC?)

//	@Override
	public double mvMultSingle(int s, double vect[], MinMax minMax)
	{
		// One step of value iteration for IDTMCs
		// Avoid enumeration of all extreme distributions using optimisation from:
		// Three-valued abstraction for probabilistic systems,
		// Joost-Pieter Katoen, Daniel Klink, Martin Leucker and Verena Wolf
		// (Defn 17, p.372, and p.380)
		
		// Extract, for each transition, the probability interval (lo/hi)
		// and the value from vector vect for the successor state
		int numTransitions = getNumTransitions(s);
		double[] probsLo = new double[numTransitions];
		double[] probsHi = new double[numTransitions];
		double[] succVals = new double[numTransitions];
		List<Integer> indices = new ArrayList<>();
		int i = 0;
		Iterator<Map.Entry<Integer, Interval<Value>>> iter = getTransitionsIterator(s);
		while (iter.hasNext()) {
			Map.Entry<Integer, Interval<Value>> e = iter.next();
			Interval<Double> intv = (Interval<Double>) e.getValue();
			probsLo[i] = intv.getLower();
			probsHi[i] = intv.getUpper();
			succVals[i] = vect[e.getKey()];
			indices.add(i);
			i++;
		}
		// Get a list of indices for the transitions,
		// sorted according to the successor values
		if (minMax.isMax()) {
			Collections.sort(indices, (o1, o2) -> -Double.compare(succVals[o1], succVals[o2]));
		} else {
			Collections.sort(indices, (o1, o2) -> Double.compare(succVals[o1], succVals[o2]));
		}
		// First add products of probability lower bounds and successor values
		double res = 0.0;
		double totP = 1.0;
		for (i = 0; i < numTransitions; i++) {
			res += succVals[i] * probsLo[i];
			totP -= probsLo[i];
		}
		// Then add remaining ones in descending order
		for (i = 0; i < numTransitions; i++) {
			int j = indices.get(i);
			double delta = probsHi[j] - probsLo[j];
			if (delta < totP) {
				res += delta * succVals[j];
				totP -= delta;
			} else {
				res += totP * succVals[j];
				break;
			}
		}
		return res;
	}

	//@Override
	public double mvMultSingleOld(int s, double vect[], MinMax minMax)
	{
		List<Double> probsLo = new ArrayList<>();
		List<Double> probsHi = new ArrayList<>();
		List<Double> succVals = new ArrayList<>();
		Iterator<Map.Entry<Integer, Interval<Value>>> iter = getTransitionsIterator(s);
		while (iter.hasNext()) {
			Map.Entry<Integer, Interval<Value>> e = iter.next();
			Interval<Double> intv = (Interval<Double>) e.getValue();
			probsLo.add(intv.getLower());
			probsHi.add(intv.getUpper());
			succVals.add(vect[e.getKey()]);
		}
		ArrayList<Double> copy = new ArrayList<Double>(succVals);
		Collections.sort(copy);
		ArrayList<Integer> idx = new ArrayList<Integer>();
		for (int i = 0; i < copy.size(); i++) {
			idx.add(succVals.indexOf(copy.get(i)));
		}

		if (minMax.isMax())
			Collections.reverse(idx);
		int n = succVals.size();
		double res = 0.0;
		double totP = 1.0;
		for (int i = 0; i < n; i++) {
			res += succVals.get(i) * probsLo.get(i);
			totP -= probsLo.get(i);
		}

		int id;
		for (int i = 0; i < idx.size(); i++) {
			id = idx.get(i);
			double delta = probsHi.get(id) - probsLo.get(id);
			if (delta < totP) {
				res += delta * succVals.get(id);
				totP -= delta;
			} else {
				res += totP * succVals.get(id);
				break;
			}
		}
		for (int i = 0; i < succVals.size(); i++) {
			//			System.out.println(s + "->" + succVals.get(i) + " = " +  probsLo.get(i) + "," + probsHi.get(i));
		}
		return res;
	}

	@Override
	public double mvMultJacMinMaxSingle(int s, double[] vect, MinMax minMax)
	{
		// TODO Auto-generated method stub
		return 0;
	}

}
