/**
 * Copyright 2019 Tobias Baum
 *
 * This file is part of GIMO-m.
 *
 * GIMO-m is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GIMO-m is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package de.unihannover.gimo_m.mining.common;

import java.util.List;

public interface ObjectiveStrategy {

	/**
	 * Returns the names of the objectives, in the order that they will occur in the array
	 * returned by {@link #getResult}.
	 */
	public abstract List<String> getObjectiveNames();

	/**
	 * Returns the target functions to use.
	 */
	public abstract List<TargetFunction> getTargetFunctions();

	/**
	 * Creates a new calculator to determine the objective vector for a ruleset.
	 */
	public abstract ObjectiveCalculator createCalculator();

}
