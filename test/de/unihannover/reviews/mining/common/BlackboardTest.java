package de.unihannover.reviews.mining.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import de.unihannover.reviews.miningInputCreation.TriggerClassification;

public class BlackboardTest {

    private static final double EPSILON = 0.0000000001;

	@Test
    public void testMakeValidWithRestrictedColumn() {
        final RecordScheme scheme = new RecordScheme(Arrays.asList("c1", "c2"), Arrays.asList("c3", "c4"));

        final RuleSet rs = RuleSet.SKIP_NONE
                        .include(new And(new Leq(scheme, scheme.getAbsIndex("c1"), 1.0), new Geq(scheme, scheme.getAbsIndex("c2"), 2.0)))
                        .include(new And(new Geq(scheme, scheme.getAbsIndex("c1"), 4.0), new Equals(scheme, scheme.getAbsIndex("c3"), "a")))
                        .exclude(new And(new NotEquals(scheme, scheme.getAbsIndex("c4"), "x"), new Equals(scheme, scheme.getAbsIndex("c3"), "b")));

        final RecordSet records = new RecordSet(scheme, new Record[0]);
        final Blackboard b = new Blackboard(records, null, null, 42);
        b.addRejectedColumns(Collections.singleton("c2"));
        b.addRejectedColumns(Collections.singleton("c4"));

        final RuleSet actual = b.makeValid(rs);

        final RuleSet expected = RuleSet.SKIP_NONE
                        .include(new And(new Geq(scheme, scheme.getAbsIndex("c1"), 4.0), new Equals(scheme, scheme.getAbsIndex("c3"), "a")));

        assertEquals(expected, actual);
    }

    @Test
    public void testCreationRestrictions1() {
        final RecordScheme scheme = new RecordScheme(Arrays.asList("c1", "c2"), Arrays.asList("c3", "c4"));
        final RecordSet records = new RecordSet(scheme, new Record[0]);
        final Blackboard b = new Blackboard(records, null, null, 42);

        b.inclusionRestrictions().reject(RulePattern.parse(scheme, "c1 <= * and *"));
        assertFalse(b.inclusionRestrictions().toCreationRestrictions(new And()).canBeValid(0, Leq.class));
        assertFalse(b.inclusionRestrictions().toCreationRestrictions(new And(new Geq(scheme, 1, 0.34))).canBeValid(0, Leq.class));
        assertTrue(b.inclusionRestrictions().toCreationRestrictions(new And()).canBeValid(0, Geq.class));
        assertTrue(b.inclusionRestrictions().toCreationRestrictions(new And()).canBeValid(1, Leq.class));

        assertFalse(b.inclusionRestrictions().toCreationRestrictions(new And()).canBeValid(new Leq(scheme, 0, 0.5)));
        assertFalse(b.inclusionRestrictions().toCreationRestrictions(new And()).canBeValid(new Leq(scheme, 0, 1.5)));
        assertTrue(b.inclusionRestrictions().toCreationRestrictions(new And()).canBeValid(new Leq(scheme, 1, 0.5)));
        assertTrue(b.inclusionRestrictions().toCreationRestrictions(new And()).canBeValid(new Geq(scheme, 0, 0.5)));
    }

    @Test
    public void testCreationRestrictions2() {
        final RecordScheme scheme = new RecordScheme(Arrays.asList("c1", "c2"), Arrays.asList("c3", "c4"));
        final RecordSet records = new RecordSet(scheme, new Record[0]);
        final Blackboard b = new Blackboard(records, null, null, 42);

        b.inclusionRestrictions().reject(RulePattern.parse(scheme, "c1 >= * and *"));
        b.inclusionRestrictions().reject(RulePattern.parse(scheme, "c1 <= * and *"));
        assertFalse(b.inclusionRestrictions().toCreationRestrictions(new And()).canBeValid(0, Leq.class));
        assertFalse(b.inclusionRestrictions().toCreationRestrictions(new And(new Geq(scheme, 1, 0.34))).canBeValid(0, Leq.class));
        assertFalse(b.inclusionRestrictions().toCreationRestrictions(new And()).canBeValid(0, Geq.class));
        assertTrue(b.inclusionRestrictions().toCreationRestrictions(new And()).canBeValid(1, Leq.class));
    }

    @Test
    public void testCreationRestrictions3() {
        final RecordScheme scheme = new RecordScheme(Arrays.asList("c1", "c2"), Arrays.asList("c3", "c4"));
        final RecordSet records = new RecordSet(scheme, new Record[0]);
        final Blackboard b = new Blackboard(records, null, null, 42);

        b.inclusionRestrictions().reject(RulePattern.parse(scheme, "c1 >= * and c1 <= * and *"));
        assertTrue(b.inclusionRestrictions().toCreationRestrictions(new And()).canBeValid(0, Leq.class));
        assertTrue(b.inclusionRestrictions().toCreationRestrictions(new And()).canBeValid(0, Geq.class));
        assertFalse(b.inclusionRestrictions().toCreationRestrictions(new And(new Leq(scheme, 0, 0.1))).canBeValid(0, Geq.class));
        assertFalse(b.inclusionRestrictions().toCreationRestrictions(new And(new Geq(scheme, 0, 0.1))).canBeValid(0, Leq.class));
        assertTrue(b.inclusionRestrictions().toCreationRestrictions(new And(new Leq(scheme, 0, 0.1))).canBeValid(0, Leq.class));
        assertTrue(b.inclusionRestrictions().toCreationRestrictions(new And(new Geq(scheme, 0, 0.1))).canBeValid(0, Geq.class));
    }
    @Test
    public void testCreationRestrictions4() {
        final RecordScheme scheme = new RecordScheme(Arrays.asList("c1", "c2"), Arrays.asList("c3", "c4"));
        final RecordSet records = new RecordSet(scheme, new Record[0]);
        final Blackboard b = new Blackboard(records, null, null, 42);

        b.inclusionRestrictions().reject(RulePattern.parse(scheme, "c1 <= 0.5 and *"));
        assertFalse(b.inclusionRestrictions().toCreationRestrictions(new And()).canBeValid(new Leq(scheme, 0, 0.5)));
        assertTrue(b.inclusionRestrictions().toCreationRestrictions(new And()).canBeValid(new Leq(scheme, 0, 1.5)));
        assertTrue(b.inclusionRestrictions().toCreationRestrictions(new And()).canBeValid(new Geq(scheme, 0, 0.5)));
        assertTrue(b.inclusionRestrictions().toCreationRestrictions(new And()).canBeValid(new Leq(scheme, 1, 0.5)));
        assertTrue(b.inclusionRestrictions().toCreationRestrictions(new And()).canBeValid(0, Leq.class));
    }

    @Test
    public void testAddColumn() {
        final RecordScheme scheme = new RecordScheme(Arrays.asList("c1", "c2"), Arrays.asList("c3", "c4"));
        final Record[] records = new Record[] {
        		new Record(new ChangePartId("TIC-123", "A.java", "cccc"), Arrays.asList(1.0, 2.0), Arrays.asList("a", "b"), TriggerClassification.NO_TRIGGER),
        		new Record(new ChangePartId("TIC-123", "B.java", "cccc"), Arrays.asList(4.0, 5.0), Arrays.asList("c", "d"), TriggerClassification.CAN_BE),
        		new Record(new ChangePartId("TIC-123", "C.java", "cccc"), Arrays.asList(1.0, Double.NaN), Arrays.asList("e", "f"), TriggerClassification.MUST_BE),
        		new Record(new ChangePartId("TIC-123", "D.java", "cccc"), Arrays.asList(Double.NaN, 2.0), Arrays.asList("g", "h"), TriggerClassification.NO_TRIGGER),
        		new Record(new ChangePartId("TIC-123", "E.java", "cccc"), Arrays.asList(Double.NaN, Double.NaN), Arrays.asList("i", "j"), TriggerClassification.NO_TRIGGER)
        };
        final RecordSet recordSet = new RecordSet(scheme, records);
        final Blackboard b = new Blackboard(recordSet, null, null, 42);

        b.addComputedColumn("sum", "c1 + c2");

        this.checkRecord(b, 0, new ChangePartId("TIC-123", "A.java", "cccc"), Arrays.asList(1.0, 2.0, 3.0), Arrays.asList("a", "b"), TriggerClassification.NO_TRIGGER);
        this.checkRecord(b, 1, new ChangePartId("TIC-123", "B.java", "cccc"), Arrays.asList(4.0, 5.0, 9.0), Arrays.asList("c", "d"), TriggerClassification.CAN_BE);
        this.checkRecord(b, 2, new ChangePartId("TIC-123", "C.java", "cccc"), Arrays.asList(1.0, Double.NaN, Double.NaN), Arrays.asList("e", "f"), TriggerClassification.MUST_BE);
        this.checkRecord(b, 3, new ChangePartId("TIC-123", "D.java", "cccc"), Arrays.asList(Double.NaN, 2.0, Double.NaN), Arrays.asList("g", "h"), TriggerClassification.NO_TRIGGER);
        this.checkRecord(b, 4, new ChangePartId("TIC-123", "E.java", "cccc"), Arrays.asList(Double.NaN, Double.NaN, Double.NaN), Arrays.asList("i", "j"), TriggerClassification.NO_TRIGGER);
        assertEquals(5, b.getRecords().getRecords().getRecords().length);
    }

	private void checkRecord(Blackboard b, int record, ChangePartId expId, List<Double> expNum, List<String> expStr, TriggerClassification expCl) {
		final Record r = b.getRecords().getRecords().getRecords()[record];
		final RecordScheme s = b.getRecords().getRecords().getScheme();
		assertEquals(expId, r.getId());
		assertEquals(expCl, r.getClassification());
		for (int i = 0; i < s.getNumericColumnCount(); i++) {
			assertEquals(expNum.get(i), r.getValueDbl(i), EPSILON);
		}
		assertEquals(expNum.size(), s.getNumericColumnCount());
		for (int i = 0; i < s.getStringColumnCount(); i++) {
			assertEquals(expStr.get(i), r.getValueStr(i));
		}
		assertEquals(expStr.size(), s.getStringColumnCount());
	}

}
