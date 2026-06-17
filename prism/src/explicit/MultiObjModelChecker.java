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

package explicit;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import parser.Values;
import parser.ast.Expression;
import parser.ast.ExpressionFunc;
import parser.ast.ExpressionQuant;
import prism.ModelType;
import prism.MultiObjModelCheckerUtils;
import prism.Operator;
import prism.MultiObjQuery;
import prism.PrismComponent;
import prism.PrismException;
import prism.PrismNotSupportedException;

/**
 * Multi-objective model checking for the explicit engine.
 *
 * <p>This class extends the engine-agnostic base class {@link prism.MultiObjModelChecker}
 * and is the intended home for the explicit-engine implementation of:
 * <ul>
 *   <li>Product MDP construction (using {@link explicit.LTLModelChecker#constructProductMDP})</li>
 *   <li>End component computation</li>
 *   <li>Weighted single-objective MDP solving (via {@link explicit.MDPModelChecker})</li>
 * </ul>
 *
 * <p><b>Status:</b> Scaffold only — not yet implemented.
 * Calling {@link #checkMultiObjective} will throw {@link PrismNotSupportedException}.
 * See TODO comments for the implementation plan.
 */
public class MultiObjModelChecker extends prism.MultiObjModelChecker
{
	/** The parent model checker providing access to settings, log, and single-objective solvers. */
	private final ProbModelChecker mc;

	public MultiObjModelChecker(PrismComponent parent, ProbModelChecker mc) throws PrismException
	{
		super(parent);
		this.mc = mc;
	}

	/**
	 * Top-level entry point for multi-objective model checking.
	 * Parses the query, validates it, and dispatches to Pareto or achievability computation.
	 *
	 * @param model          The explicit-state MDP
	 * @param expr           The multi(...) expression
	 * @param statesOfInterest States of interest (must be a singleton for multi-objective)
	 * @return The result (TileList for Pareto, Double for achievability/numerical)
	 */
	public Object checkMultiObjective(explicit.MDP<?> model, ExpressionFunc expr, BitSet statesOfInterest) throws PrismException
	{
		// TODO: implement explicit engine multi-objective model checking.
		//
		// Planned implementation outline:
		//
		// 1. Parse query expressions using MultiObjModelCheckerUtils.extractOperatorAndStepBound()
		//    Retrieve reward structures from the explicit model (RewardGenerator).
		//
		// 2. Validate using MultiObjModelCheckerUtils.validateQueryStructure().
		//
		// 3. For each probability objective, build product MDP:
		//    explicit.LTLModelChecker.constructProductMDP(mc, model, expr, statesOfInterest)
		//
		// 4. Compute end components and accepting EC states.
		//
		// 5. Create a WeightedObjectiveSolver lambda that:
		//    - Constructs a weighted-sum reward structure (MDPRewardsSimple)
		//    - Calls MDPModelChecker.computeReachRewards() (or computeReachProbs for P objectives)
		//    - Returns the value at the initial state
		//
		// 6. For Pareto queries (numNumerical >= 2): call runParetoCurveIteration()
		//    For achievability/numerical: call runAchievabilityIteration()

		throw new PrismNotSupportedException("Multi-objective model checking is not yet implemented for the explicit engine");
	}
}
