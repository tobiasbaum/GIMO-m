package de.unihannover.reviews.mining.common;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import de.unihannover.reviews.miningInputCreation.TriggerClassification;

public class RuleSetTest {

    private RecordScheme scheme;

    @Before
    public void setUp() {
        this.scheme = new RecordScheme(Arrays.asList("nA", "nB", "nC"), Arrays.asList("sA", "sB", "sC"));
    }

    private RecordSet data() {
    	return new RecordSet(this.scheme, new Record[] {
    			new Record(new ChangePartId("TIC-123", "File", "fefe"), Arrays.asList(0.1, 0.1, 0.1), Arrays.asList("a", "a", "a"), TriggerClassification.CAN_BE),
    			new Record(new ChangePartId("TIC-123", "File", "fefe"), Arrays.asList(0.2, 0.2, 0.2), Arrays.asList("b", "b", "b"), TriggerClassification.CAN_BE),
    			new Record(new ChangePartId("TIC-123", "File", "fefe"), Arrays.asList(0.3, 0.3, 0.3), Arrays.asList("c", "c", "c"), TriggerClassification.CAN_BE)
    	});
    }

    private RecordSet dataWithSaBinary() {
    	return new RecordSet(this.scheme, new Record[] {
    			new Record(new ChangePartId("TIC-123", "File", "fefe"), Arrays.asList(0.1, 0.1, 0.1), Arrays.asList("X", "a", "a"), TriggerClassification.CAN_BE),
    			new Record(new ChangePartId("TIC-123", "File", "fefe"), Arrays.asList(0.2, 0.2, 0.2), Arrays.asList("X", "b", "b"), TriggerClassification.CAN_BE),
    			new Record(new ChangePartId("TIC-123", "File", "fefe"), Arrays.asList(0.3, 0.3, 0.3), Arrays.asList("Y", "c", "c"), TriggerClassification.CAN_BE)
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
        final RuleSet rs = RuleSet.create("test").exclude(new And(this.leq("nA", 5)));
        assertEquals(RuleSet.create("test"), rs.simplify(this.data()));
    }

    @Test
    public void testSimplifyDuplicateInclusion() {
        final RuleSet rs = RuleSet.create("test")
                        .include(new And(this.leq("nA", 5)))
                        .include(new And(this.leq("nA", 5)));
        assertEquals(RuleSet.create("test").include(new And(this.leq("nA", 5))), rs.simplify(this.data()));
    }

    @Test
    public void testSimplifyDuplicateExclusion() {
        final RuleSet rs = RuleSet.create("test")
                        .include(new And(this.leq("nA", 5)))
                        .exclude(new And(this.leq("nB", 5)))
                        .exclude(new And(this.leq("nB", 5)));
        assertEquals(
                RuleSet.create("test")
                    .include(new And(this.leq("nA", 5)))
                    .exclude(new And(this.leq("nB", 5))),
                rs.simplify(this.data()));
    }

    @Test
    public void testSimplifyImplies1() {
        final RuleSet rs = RuleSet.create("test")
                        .include(new And(this.leq("nA", 5)))
                        .include(new And(this.leq("nA", 5), this.leq("nB", 7)));
        assertEquals(
                RuleSet.create("test")
                    .include(new And(this.leq("nA", 5))),
                rs.simplify(this.data()));
    }

    @Test
    public void testSimplifyImplies2() {
        final RuleSet rs = RuleSet.create("test")
                        .include(new And(this.leq("nA", 6)))
                        .include(new And(this.leq("nA", 7)));
        assertEquals(
                RuleSet.create("test")
                    .include(new And(this.leq("nA", 7))),
                rs.simplify(this.data()));
    }

    @Test
    public void testSimplifyImplies3() {
        final RuleSet rs = RuleSet.create("test")
                        .include(new And(this.leq("nA", 5)))
                        .include(new And(new True()));
        assertEquals(
                RuleSet.create("test")
                    .include(new And()),
                rs.simplify(this.data()));
    }

    @Test
    public void testSimplifyBinaryFeatures1() {
        final RuleSet rs = RuleSet.create("test")
                        .include(new And(this.neq("sA", "X")));
        assertEquals(
                RuleSet.create("test")
                    .include(new And(this.eq("sA", "Y"))),
                rs.simplify(this.dataWithSaBinary()));
    }

    @Test
    public void testSimplifyBinaryFeatures2() {
        final RuleSet rs = RuleSet.create("test")
                        .include(new And(this.neq("sA", "Y")));
        assertEquals(
                RuleSet.create("test")
                    .include(new And(this.eq("sA", "X"))),
                rs.simplify(this.dataWithSaBinary()));
    }

    @Test
    public void testSimplifyBinaryFeatures3() {
        final RuleSet rs = RuleSet.create("test")
                        .include(new And(this.eq("sA", "Y"), this.neq("sA", "X"), this.neq("sB", "a")));
        assertEquals(
                RuleSet.create("test")
                    .include(new And(this.eq("sA", "Y"), this.neq("sB", "a"))),
                rs.simplify(this.dataWithSaBinary()));
    }

    @Test
    public void testToStringSimple() {
        final RuleSet rs = RuleSet.create("test")
                .include(new And(this.eq("sA", "Y"), this.neq("sB", "a")))
        		.exclude(new And(this.leq("nC", 1.2)));

        assertEquals(
        		"skip when one of\n" +
        		"  (sA == 'Y' and sB != 'a')\n" +
        		"unless one of\n" +
        		"  (nC<=1.2)\n",
        		rs.toString());
    }

    @Test
    public void testToStringWithGrouping() {
        final RuleSet rs = RuleSet.create("test")
                .include(new And(this.eq("sB", "b"), this.neq("sA", "a"), this.eq("sC", "y")))
        		.include(new And(this.eq("sC", "x"), this.neq("sA", "c")))
        		.include(new And(this.eq("sA", "b"), this.neq("sB", "a")))
				.include(new And(this.eq("sB", "c")))
				.include(new And(this.eq("sA", "z")));

        assertEquals(
        		"skip when one of\n" +
				"  (sB == 'c')\n" +
				"  or (sA == 'b' and sB != 'a')\n" +
        		"  or (sA != 'a' and sB == 'b' and sC == 'y')\n" +
        		"  or (sA != 'c' and sC == 'x')\n" +
        		"  or (sA == 'z')\n",
        		rs.toString());
    }

}
