//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <d.a.parker@cs.bham.ac.uk> (University of Birmingham/Oxford)
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

import java.util.Iterator;
import java.util.Map;

import explicit.CTMC;
import explicit.CTMCSimple;
import explicit.Distribution;
import explicit.DTMC;
import explicit.DTMCSimple;
import explicit.Model;
import explicit.MDPSimple;
import prism.PrismComponent;
import prism.PrismException;

/**
 * Method to perform bisimulation minimisation for explicit-state models.
 */
public class OldBisimulationMethod<Value> extends Bisimulation<Value>
{
    // Local storage of partition info
    protected MDPSimple<Value> mdp;

    /**
     * Construct a new OldBisimulationMethod object.
     */
    public OldBisimulationMethod(PrismComponent parent) throws PrismException
    {
        super(parent);
    }

    @Override
    protected boolean minimiseDTMC(DTMC<Value> dtmc)
    {
        // Iterative splitting
        boolean changed = true;
        while (changed)
            changed = splitDTMC(dtmc);
        // printPartition(dtmc);
        return (numStates != numBlocks);
    }

    @Override
    protected boolean minimiseCTMC(CTMC<Value> ctmc)
    {
        return minimiseDTMC(ctmc);
    }

    @Override
    protected DTMCSimple<Value> buildReducedDTMC(DTMC<Value> dtmc)
    {
        // Build reduced model
        DTMCSimple<Value> dtmcNew = new DTMCSimple<>(numBlocks);
        for (int i = 0; i < numBlocks; i++) {
            for (Map.Entry<Integer, Value> e : mdp.getChoice(i, 0)) {
                dtmcNew.setProbability(i, e.getKey(), e.getValue());
            }
        }
        return dtmcNew;
    }

    @Override
    protected CTMCSimple<Value> buildReducedCTMC(CTMC<Value> ctmc)
    {
        // Build reduced model
        CTMCSimple<Value> ctmcNew = new CTMCSimple<>(numBlocks);
        for (int i = 0; i < numBlocks; i++) {
            for (Map.Entry<Integer, Value> e : mdp.getChoice(i, 0)) {
                ctmcNew.setProbability(i, e.getKey(), e.getValue());
            }
        }
        return ctmcNew;
    }

    /**
     * Perform a split of the current partition, if possible, updating
     * {@code numBlocks} and {@code partition}.
     *
     * @return whether or not the partition was split
     */
    private boolean splitDTMC(DTMC<Value> dtmc)
    {
        int s, a, i, numBlocksNew, numChoicesOld;
        Distribution<Value> distrNew;
        int[] partitionNew = new int[numStates];
        numBlocksNew = 0;
        // Compute the signature for each state (i.e. the distribution for outgoing
        // transitions, lifted to the current partition)
        // For convenience, we just store them as an MDP, with action label equal to
        // the index of the block
        mdp = new MDPSimple<>(numBlocks);
        for (s = 0; s < numStates; s++) {
            // Build lifted distribution
            Iterator<Map.Entry<Integer, Value>> iter = dtmc.getTransitionsIterator(s);
            distrNew = new Distribution<>(dtmc.getEvaluator());
            while (iter.hasNext()) {
                Map.Entry<Integer, Value> e = iter.next();
                distrNew.add(partition[e.getKey()], e.getValue());
            }
            // Store in MDP, update new partition
            a = partition[s];
            numChoicesOld = mdp.getNumChoices(a);
            i = mdp.addChoice(a, distrNew);
            if (i == numChoicesOld)
                mdp.setAction(a, i, numBlocksNew++);
            partitionNew[s] = (Integer) mdp.getAction(a, i);
        }
        // Debug info
        // mainLog.println("New partition: " + java.util.Arrays.toString(partitionNew));
        // mainLog.println("Signatures MDP: " + mdp.infoString());
        // mainLog.println("Signatures MDP: " + mdp);
        // try { mdp.exportToDotFile("mdp.dot"); } catch (PrismException e) {}
        // Update info
        boolean changed = numBlocks != numBlocksNew;
        if (changed) {
            // Note, once converged, we keep the partition from the previous iter
            // because the transition info in the MDP is in terms of this
            partition = partitionNew;
            numBlocks = numBlocksNew;
        }

        return changed;
    }
}
