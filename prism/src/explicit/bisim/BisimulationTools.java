//==============================================================================
//
//	Copyright (c) 2025-
//	Authors:
//	* Zainab Fatmi
//	* Dave Parker <david.parker@cs.ox.ac.uk> (University of Oxford)
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

import explicit.Model;
import prism.PrismComponent;
import prism.PrismException;

/**
 * Handles bisimulation minimisation for explicit-state models. Instantiates the
 * bisimulation minimisation method specified by the {@code bisimmethod} option.
 * If the class has a registered alias, that can be used. Otherwise, reflection
 * is used to instantiate the class. The default is DefaultBisimulation.
 */
public class BisimulationTools<Value>
{

	protected Bisimulation<Value> bisim;

	// aliases
	protected final String PACKAGE = "explicit.bisim.";
	protected final String DEFAULT = "new";
	protected final String OLD = "old";
	protected final String ROBUST = "robust";
	protected final String SROBUST = "structurally-robust";
	protected final String ULE = "ule";

	/**
	 * Perform bisimulation minimisation on a model.
	 * Labels and rewards to be respected are those attached to the model.
	 *
	 * @param parent      The parent component
	 * @param bisimMethod The bisimulation minimization method
	 * @param model       The model
	 */
	@SuppressWarnings("unchecked")
	public Model<Value> minimise(PrismComponent parent, String bisimMethod, Model<Value> model) throws PrismException
	{
		if (bisimMethod == null) {
			/* use default */
			bisim = new DefaultBisimulation<Value>(parent);
		} else {
			/* use alias */
			switch (bisimMethod) {
				case (DEFAULT):
					bisim = new DefaultBisimulation<Value>(parent);
					break;
				case (ROBUST):
					bisim = new RobustBisimulation<Value>(parent);
					break;
				case (SROBUST):
					bisim = new StructurallyRobustBisimulation<Value>(parent);
					break;
				case (OLD):
					bisim = new OldBisimulationMethod<Value>(parent);
					break;
				case (ULE):
					bisim = new ULE<Value>(parent);
					break;
				default:
					/* use reflection */
					try {
						/* class is in the bisimulation package */
						Class<?> bisimClass = Class.forName(PACKAGE + bisimMethod);
						bisim = (Bisimulation<Value>) bisimClass.getConstructor(PrismComponent.class).newInstance(parent);
					} catch (ClassNotFoundException e) {
						/* class may be elsewhere */
						try {
							Class<?> bisimClass = Class.forName(bisimMethod);
							bisim = (Bisimulation<Value>) bisimClass.getConstructor(PrismComponent.class).newInstance(parent);
						} catch (ClassNotFoundException exception) {
							throw new PrismException("Unknown bisimulation minimisation method");
						} catch (ReflectiveOperationException exception) {
							throw new PrismException("Could not instantiate " + bisimMethod + " using the required constructor");
						}
					} catch (ReflectiveOperationException e) {
						throw new PrismException("Could not instantiate " + bisimMethod + " using the required constructor");
					}
			}
		}
		return bisim.minimise(model);
	}

	/**
	 * Did bisimulation minimisation reduce the state space of the model?
	 */
	public boolean minimised()
	{
		if (bisim == null) {
			return false;
		}
		return bisim.minimised();
	}
}
