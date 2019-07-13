package de.unihannover.reviews.mining.common;

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.junit.Test;

import de.unihannover.reviews.miningInputCreation.RemarkTriggerMap;
import de.unihannover.reviews.miningInputCreation.TriggerClassification;

public class RecordSetTest {

	private static Record record(String ticket, String commit, String file, TriggerClassification cl) {
		return new Record(new ChangePartId(ticket, file, commit),
				Collections.emptyList(),
				Collections.emptyList(),
				cl);
	}

	private static Record record(String ticket, String commit, String file,
			int lineFrom, int lineTo, TriggerClassification cl) {
		return new Record(new ChangePartId(ticket, file, commit, lineFrom, lineTo),
				Collections.emptyList(),
				Collections.emptyList(),
				cl);
	}

	@Test
	public void testReclassify1() {
		final RecordScheme scheme = new RecordScheme(Collections.emptyList(), Collections.emptyList());
		final RecordSet rs = new RecordSet(scheme, new Record[] {
				record("TIC-123", "abab", "File", TriggerClassification.MUST_BE)
		});

		final RemarkTriggerMap tm = new RemarkTriggerMap();
		final RecordSet reclassified = rs.reclassify(tm);

		assertEquals(TriggerClassification.NO_TRIGGER, reclassified.getRecords()[0].getClassification());
	}

	@Test
	public void testReclassify2() {
		final RecordScheme scheme = new RecordScheme(Collections.emptyList(), Collections.emptyList());
		final RecordSet rs = new RecordSet(scheme, new Record[] {
				record("TIC-123", "abab", "File", TriggerClassification.MUST_BE)
		});

		final RemarkTriggerMap tm = new RemarkTriggerMap();
		tm.add("cdcd,A.java;TIC-123;WT");
		final RecordSet reclassified = rs.reclassify(tm);

		assertEquals(TriggerClassification.MUST_BE, reclassified.getRecords()[0].getClassification());
	}

	@Test
	public void testReclassify3() {
		final RecordScheme scheme = new RecordScheme(Collections.emptyList(), Collections.emptyList());
		final RecordSet rs = new RecordSet(scheme, new Record[] {
				record("TIC-123", "abab", "File", TriggerClassification.MUST_BE),
				record("TIC-123", "abab", "File2", TriggerClassification.MUST_BE)
		});

		final RemarkTriggerMap tm = new RemarkTriggerMap();
		tm.add("cdcd,A.java;TIC-123;WT");
		final RecordSet reclassified = rs.reclassify(tm);

		assertEquals(TriggerClassification.CAN_BE, reclassified.getRecords()[0].getClassification());
		assertEquals(TriggerClassification.CAN_BE, reclassified.getRecords()[1].getClassification());
	}

	@Test
	public void testReclassify4() {
		final RecordScheme scheme = new RecordScheme(Collections.emptyList(), Collections.emptyList());
		final RecordSet rs = new RecordSet(scheme, new Record[] {
				record("TIC-123", "abab", "File", 5, 10, TriggerClassification.MUST_BE),
				record("TIC-123", "abab", "File", 15, 20, TriggerClassification.MUST_BE),
				record("TIC-123", "abab", "File", 25, 30, TriggerClassification.MUST_BE)
		});

		final RemarkTriggerMap tm = new RemarkTriggerMap();
		tm.add("cdcd,A.java;TIC-123;abab,File,15");
		final RecordSet reclassified = rs.reclassify(tm);

		assertEquals(TriggerClassification.NO_TRIGGER, reclassified.getRecords()[0].getClassification());
		assertEquals(TriggerClassification.MUST_BE, reclassified.getRecords()[1].getClassification());
		assertEquals(TriggerClassification.NO_TRIGGER, reclassified.getRecords()[2].getClassification());
	}

}
