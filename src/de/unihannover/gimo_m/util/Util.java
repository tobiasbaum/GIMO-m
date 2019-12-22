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
package de.unihannover.gimo_m.util;

import java.util.List;
import java.util.Random;

public class Util {

    public static<T> T randomItem(Random random, List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

	public static double determineSplitPointWithFewDigits(double lower, double upper) {
		final double middle = (lower + upper) / 2.0;
		final double bl = (2.0 * lower + upper) / 3.0;
		final double bu = (lower + 2.0 * upper) / 3.0;

		for (int exp = -10; exp < 10; exp++) {
			final double r = roundToExp(middle, exp);
			if (r > bl && r < bu) {
				return r;
			}
		}
		return middle;
	}

	private static double roundToExp(double middle, int exp) {
		final double factor = Math.pow(10, exp);
		return Math.round(middle * factor) / factor;
	}

}
