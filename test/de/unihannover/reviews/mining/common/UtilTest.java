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
package de.unihannover.reviews.mining.common;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import de.unihannover.gimo_m.mining.common.Util;

public class UtilTest {

	private static final double EPSILON = 0.000000000001;

	@Test
	public void testSimpleSplitPoint() {
		assertEquals(1.0, Util.determineSplitPointWithFewDigits(0.0, 2.0), EPSILON);
		assertEquals(3.0, Util.determineSplitPointWithFewDigits(0.0, 6.0), EPSILON);
		assertEquals(3.0, Util.determineSplitPointWithFewDigits(2.0, 4.0), EPSILON);
		assertEquals(50.0, Util.determineSplitPointWithFewDigits(0.0, 100.0), EPSILON);
		assertEquals(0.5, Util.determineSplitPointWithFewDigits(0.0, 1.0), EPSILON);
		assertEquals(-0.5, Util.determineSplitPointWithFewDigits(-1.0, 0.0), EPSILON);
		assertEquals(-15.0, Util.determineSplitPointWithFewDigits(-20.0, -10.0), EPSILON);
		assertEquals(0.12345, Util.determineSplitPointWithFewDigits(0.1234, 0.1235), EPSILON);
	}

	@Test
	public void testRoundingIsNeeded() {
		assertEquals(1.0, Util.determineSplitPointWithFewDigits(0.0, 2.1), EPSILON);
		assertEquals(1.0, Util.determineSplitPointWithFewDigits(0.1, 2.1), EPSILON);
		assertEquals(3.0, Util.determineSplitPointWithFewDigits(2.0, 4.1), EPSILON);
		assertEquals(3.0, Util.determineSplitPointWithFewDigits(2.0, 3.9), EPSILON);
		assertEquals(50.0, Util.determineSplitPointWithFewDigits(0.0, 100.01), EPSILON);
		assertEquals(50.0, Util.determineSplitPointWithFewDigits(0.09, 100.01), EPSILON);
		assertEquals(-0.5, Util.determineSplitPointWithFewDigits(-1.0, 0.01), EPSILON);
		assertEquals(-0.5, Util.determineSplitPointWithFewDigits(-1.01, -0.09), EPSILON);
	}
}
