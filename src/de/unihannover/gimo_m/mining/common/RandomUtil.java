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
import java.util.Random;

public class RandomUtil {

    public static<T> T randomItem(Random random, List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    public static<T> T getItemFromStart(List<T> list, double randomness, Random random) {
    	return list.get(getIndexAtStart(list, randomness, random));
    }

	public static int intBetween(Random random, int lowerInclusive, int upperExclusive) {
		return lowerInclusive + random.nextInt(upperExclusive - lowerInclusive);
	}

	public static int getIndexAtStart(List<?> list, double randomness, Random random) {
        final int max = (int) Math.round(randomness * list.size());
        if (max == 0) {
            return 0;
        } else {
            return random.nextInt(max);
        }
	}

	public static int getIndexAtEnd(List<?> list, double randomness, Random random) {
		return list.size() - 1 - getIndexAtStart(list, randomness, random);
	}

}
