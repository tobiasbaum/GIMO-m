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

public interface ObjectiveCalculator {

	/**
	 * Adds the results for another instance for the current ruleset.
	 */
	public abstract void handleInstance(String correctClass, String predictedClass);

	/**
	 * Is called after all instances have been processed to determine the final objective vector.
	 */
	public abstract double[] getResult(double rulesetComplexity, double rulesetFeatureCount);

}
