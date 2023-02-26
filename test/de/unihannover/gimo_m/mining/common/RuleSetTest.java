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

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import de.unihannover.gimo_m.mining.common.And;
import de.unihannover.gimo_m.mining.common.Equals;
import de.unihannover.gimo_m.mining.common.Leq;
import de.unihannover.gimo_m.mining.common.NotEquals;
import de.unihannover.gimo_m.mining.common.Record;
import de.unihannover.gimo_m.mining.common.RecordScheme;
import de.unihannover.gimo_m.mining.common.RecordSet;
import de.unihannover.gimo_m.mining.common.RuleSet;

public class RuleSetTest {

    private RecordScheme scheme;

    @Before
    public void setUp() {
        this.scheme = new RecordScheme(Arrays.asList("nA", "nB", "nC"), Arrays.asList("sA", "sB", "sC"));
    }

    private RecordSet data() {
    	return new RecordSet(this.scheme, new Record[] {
    			new Record(1, Arrays.asList(0.1, 0.1, 0.1), Arrays.asList("a", "a", "a"), "cl"),
    			new Record(2, Arrays.asList(0.2, 0.2, 0.2), Arrays.asList("b", "b", "b"), "cl"),
    			new Record(3, Arrays.asList(0.3, 0.3, 0.3), Arrays.asList("c", "c", "c"), "cl")
    	});
    }

    private RecordSet dataWithSaBinary() {
    	return new RecordSet(this.scheme, new Record[] {
    			new Record(4, Arrays.asList(0.1, 0.1, 0.1), Arrays.asList("X", "a", "a"), "cl"),
    			new Record(5, Arrays.asList(0.2, 0.2, 0.2), Arrays.asList("X", "b", "b"), "cl"),
    			new Record(6, Arrays.asList(0.3, 0.3, 0.3), Arrays.asList("Y", "c", "c"), "cl")
    	});
    }

    private Leq leq(String column, double val) {
        return new Leq(this.scheme, this.scheme.getAbsIndex(column), val);
    }

    private Equals eq(String column, String val) {
        return new Equals(this.scheme, this.scheme.getAbsIndex(column), val);
    }

    private NotEquals neq(String column, String val) {
        return new NotEquals(this.scheme, this.scheme.getAbsIndex(column), val);
    }

    @Test
    public void testSimplifyOnlyExclusions() {
        final RuleSet rs = RuleSet.create("test").addRule("test", new And(this.leq("nA", 5)));
        assertEquals(RuleSet.create("test"), rs.simplify(this.data()));
    }

    @Test
    public void testSimplifyDuplicateInclusion() {
        final RuleSet rs = RuleSet.create("test")
                        .addRule("g1", new And(this.leq("nA", 5)))
                        .addRule("g1", new And(this.leq("nA", 5)));
        assertEquals(RuleSet.create("test").addRule("g1", new And(this.leq("nA", 5))), rs.simplify(this.data()));
    }

    @Test
    public void testSimplifyDuplicateExclusion() {
        final RuleSet rs = RuleSet.create("test")
                        .addRule("g1", new And(this.leq("nA", 5)))
                        .addRule("g2", new And(this.leq("nB", 5)))
                        .addRule("g2", new And(this.leq("nB", 5)));
        assertEquals(
                RuleSet.create("test")
                    .addRule("g1", new And(this.leq("nA", 5)))
                    .addRule("g2", new And(this.leq("nB", 5))),
                rs.simplify(this.data()));
    }

    @Test
    public void testAddExceptionDuplicate() {
        final RuleSet rs = RuleSet.create("test")
                .addException("g1", new Or(new And(this.leq("nA", 5))))
                .addException("g2", new Or(new And(this.leq("nB", 5))))
                .addException("g2", new Or(new And(this.leq("nC", 5))));
        assertEquals(
                RuleSet.create("test")
                        .addException("g1", new Or(new And(this.leq("nA", 5))))
                        .addException("g2", new Or(new And(this.leq("nB", 5)), new And(this.leq("nC", 5)))),
                rs.simplify(this.data()));
    }

    @Test
    public void testSimplifyBinaryFeatures1() {
        final RuleSet rs = RuleSet.create("test")
                        .addRule("g1", new And(this.neq("sA", "X")));
        assertEquals(
                RuleSet.create("test")
                    .addRule("g1", new And(this.eq("sA", "Y"))),
                rs.simplify(this.dataWithSaBinary()));
    }

    @Test
    public void testSimplifyBinaryFeatures2() {
        final RuleSet rs = RuleSet.create("test")
                        .addRule("g1", new And(this.neq("sA", "Y")));
        assertEquals(
                RuleSet.create("test")
                    .addRule("g1", new And(this.eq("sA", "X"))),
                rs.simplify(this.dataWithSaBinary()));
    }

    @Test
    public void testSimplifyBinaryFeatures3() {
        final RuleSet rs = RuleSet.create("test")
                        .addRule("g1", new And(this.eq("sA", "Y"), this.neq("sA", "X"), this.neq("sB", "a")));
        assertEquals(
                RuleSet.create("test")
                    .addRule("g1", new And(this.eq("sA", "Y"), this.neq("sB", "a"))),
                rs.simplify(this.dataWithSaBinary()));
    }

    @Test
    public void testToStringSimple() {
        final RuleSet rs = RuleSet.create("test")
                .addRule("g1", new And(this.eq("sA", "Y"), this.neq("sB", "a")))
                .addRule("g2", new And(this.leq("nC", 1.2)));

        assertEquals(
                "normally use test\n" +
        		"but use g1 when\n" +
        		"  (sA == 'Y' and sB != 'a')\n" +
        		"but use g2 when\n" +
        		"  (nC<=1.2)\n",
        		rs.toString());
    }

    @Test
    public void testToStringWithGrouping() {
        final RuleSet rs = RuleSet.create("test")
                .addRule("g1", new And(this.eq("sB", "b"), this.neq("sA", "a"), this.eq("sC", "y")))
                .addRule("g1", new And(this.eq("sC", "x"), this.neq("sA", "c")))
                .addRule("g1", new And(this.eq("sA", "b"), this.neq("sB", "a")))
                .addRule("g1", new And(this.eq("sB", "c")))
                .addRule("g1", new And(this.eq("sA", "z")));

        assertEquals(
                "normally use test\n" +
        		"but use g1 when\n" +
        		"  (sA == 'z')\n" +
        		"  or (sA != 'c' and sC == 'x')\n" +
                "  or (sA != 'a' and sB == 'b' and sC == 'y')\n" +
				"  or (sA == 'b' and sB != 'a')\n" +
        		"  or (sB == 'c')\n",
        		rs.toString());
    }

}
