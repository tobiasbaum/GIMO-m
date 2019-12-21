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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import de.unihannover.gimo_m.mining.common.And;
import de.unihannover.gimo_m.mining.common.Equals;
import de.unihannover.gimo_m.mining.common.Geq;
import de.unihannover.gimo_m.mining.common.Leq;
import de.unihannover.gimo_m.mining.common.NotEquals;
import de.unihannover.gimo_m.mining.common.RecordScheme;
import de.unihannover.gimo_m.mining.common.RulePattern;

public class RulePatternTest {

	@Test
	public void testExactMatch() {
		final RecordScheme scheme = new RecordScheme(Arrays.asList("n1", "n2"), Arrays.asList("s1", "s2"));

		final RulePattern p = RulePattern.parse(scheme, "n1<=3.0");

		assertTrue(p.matches(new And(new Leq(scheme, scheme.getAbsIndex("n1"), 3.0))));
		assertFalse(p.matches(new And(new Leq(scheme, scheme.getAbsIndex("n1"), 2.0))));
		assertFalse(p.matches(new And(new Leq(scheme, scheme.getAbsIndex("n2"), 3.0))));
		assertFalse(p.matches(new And(new Leq(scheme, scheme.getAbsIndex("n1"), 3.0), new Geq(scheme, scheme.getAbsIndex("n1"), 1.0))));
	}

	@Test
	public void testMultipleConditions() {
		final RecordScheme scheme = new RecordScheme(Arrays.asList("n1", "n2"), Arrays.asList("s1", "s2"));

		final RulePattern p = RulePattern.parse(scheme, "(n2 >= 4.0 and s1 == 'abc')");

		assertTrue(p.matches(new And(new Geq(scheme, scheme.getAbsIndex("n2"), 4.0), new Equals(scheme, scheme.getAbsIndex("s1"), "abc"))));
		assertFalse(p.matches(new And(new Geq(scheme, scheme.getAbsIndex("n2"), 4.0))));
		assertFalse(p.matches(new And(new Equals(scheme, scheme.getAbsIndex("s1"), "abc"))));
		assertFalse(p.matches(new And(new Geq(scheme, scheme.getAbsIndex("n2"), 4.1), new Equals(scheme, scheme.getAbsIndex("s1"), "abc"))));
		assertFalse(p.matches(new And(new Geq(scheme, scheme.getAbsIndex("n2"), 4.0), new Equals(scheme, scheme.getAbsIndex("s1"), "ab"))));
	}

	@Test
	public void testMatchWithAdditionalConditionWild() {
		final RecordScheme scheme = new RecordScheme(Arrays.asList("n1", "n2"), Arrays.asList("s1", "s2"));

		final RulePattern p = RulePattern.parse(scheme, "n1<=3.0 and *");

		assertTrue(p.matches(new And(new Leq(scheme, scheme.getAbsIndex("n1"), 3.0))));
		assertFalse(p.matches(new And(new Leq(scheme, scheme.getAbsIndex("n1"), 2.0))));
		assertFalse(p.matches(new And(new Leq(scheme, scheme.getAbsIndex("n2"), 3.0))));
		assertTrue(p.matches(new And(new Leq(scheme, scheme.getAbsIndex("n1"), 3.0), new Geq(scheme, scheme.getAbsIndex("n1"), 1.0))));
	}

	@Test
	public void testWildcardForNumericValue() {
		final RecordScheme scheme = new RecordScheme(Arrays.asList("n1", "n2"), Arrays.asList("s1", "s2"));

		final RulePattern p = RulePattern.parse(scheme, "n1 >= * and n1 <= *");

		assertTrue(p.matches(new And(new Geq(scheme, scheme.getAbsIndex("n1"), 1.0), new Leq(scheme, scheme.getAbsIndex("n1"), 3.0))));
		assertTrue(p.matches(new And(new Geq(scheme, scheme.getAbsIndex("n1"), 2.0), new Leq(scheme, scheme.getAbsIndex("n1"), 234.0))));
		assertFalse(p.matches(new And(new Leq(scheme, scheme.getAbsIndex("n1"), 3.0))));
		assertFalse(p.matches(new And(new Geq(scheme, scheme.getAbsIndex("n1"), 1.0))));
		assertFalse(p.matches(new And(new Geq(scheme, scheme.getAbsIndex("n1"), 1.0), new Leq(scheme, scheme.getAbsIndex("n2"), 3.0))));
	}

	@Test
	public void testWildcardForStringValue() {
		final RecordScheme scheme = new RecordScheme(Arrays.asList("n1", "n2"), Arrays.asList("s1", "s2"));

		final RulePattern p = RulePattern.parse(scheme, "s1 != * and *");

		assertTrue(p.matches(new And(new NotEquals(scheme, scheme.getAbsIndex("s1"), "a"), new Leq(scheme, scheme.getAbsIndex("n1"), 3.0))));
		assertTrue(p.matches(new And(new NotEquals(scheme, scheme.getAbsIndex("s1"), "b"), new Leq(scheme, scheme.getAbsIndex("n1"), 234.0))));
		assertTrue(p.matches(new And(new NotEquals(scheme, scheme.getAbsIndex("s1"), "x"))));
		assertFalse(p.matches(new And(new NotEquals(scheme, scheme.getAbsIndex("s2"), "a"))));
		assertFalse(p.matches(new And(new Geq(scheme, scheme.getAbsIndex("n1"), 1.0))));
	}

	@Test
	public void testCreateFromAnd() {
		final RecordScheme scheme = new RecordScheme(Arrays.asList("n1", "n2"), Arrays.asList("s1", "s2"));

		final RulePattern p = RulePattern.createExact(
				new And(new Equals(scheme, scheme.getAbsIndex("s1"), "a"), new NotEquals(scheme, scheme.getAbsIndex("s2"), "b")));

		assertTrue(p.matches(new And(new Equals(scheme, scheme.getAbsIndex("s1"), "a"), new NotEquals(scheme, scheme.getAbsIndex("s2"), "b"))));
		assertFalse(p.matches(new And(new Equals(scheme, scheme.getAbsIndex("s1"), "a"), new Equals(scheme, scheme.getAbsIndex("s2"), "b"))));
		assertFalse(p.matches(new And(new NotEquals(scheme, scheme.getAbsIndex("s1"), "a"), new NotEquals(scheme, scheme.getAbsIndex("s2"), "b"))));
		assertFalse(p.matches(new And(new Equals(scheme, scheme.getAbsIndex("s2"), "a"), new NotEquals(scheme, scheme.getAbsIndex("s1"), "b"))));
	}

	@Test
	public void testEquals() {
		final RecordScheme scheme = new RecordScheme(Arrays.asList("n1", "n2"), Arrays.asList("s1", "s2"));

		final RulePattern p1 = RulePattern.parse(scheme, "s1 == 'a' and s2 == * and *");
		final RulePattern p2 = RulePattern.parse(scheme, "s1 == 'a' and s2 == * and *");
		final RulePattern p3 = RulePattern.parse(scheme, "s1 == 'a' and s2 == *");
		final RulePattern p4 = RulePattern.parse(scheme, "s1 == 'a' and *");
		final RulePattern p5 = RulePattern.parse(scheme, "s2 == * and *");

		assertEquals(p1, p2);
		assertNotEquals(p1, p3);
		assertNotEquals(p1, p4);
		assertNotEquals(p1, p5);
		assertNotEquals(p3, p4);
		assertNotEquals(p3, p5);
		assertNotEquals(p4, p5);
	}

	@Test
	public void testToString() {
		final RecordScheme scheme = new RecordScheme(Arrays.asList("n1", "n2"), Arrays.asList("s1", "s2"));

		checkParseAndToString(scheme, "s1 == 'a' and s2 == * and *");
		checkParseAndToString(scheme, "s1 == 'a' and s2 == * and *");
		checkParseAndToString(scheme, "s1 == 'a' and s2 == *");
		checkParseAndToString(scheme, "s1 == 'a' and *");
		checkParseAndToString(scheme, "s2 == * and *");
		checkParseAndToString(scheme, "n1 <= *");
		checkParseAndToString(scheme, "n1 >= *");
		checkParseAndToString(scheme, "s1 != *");
	}

	private static void checkParseAndToString(RecordScheme scheme, String s) {
		assertEquals(s, RulePattern.parse(scheme, s).toString());
	}
}
