package de.unihannover.reviews.mining.agents;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import de.unihannover.reviews.mining.common.And;
import de.unihannover.reviews.mining.common.ChangePartId;
import de.unihannover.reviews.mining.common.Multiset;
import de.unihannover.reviews.mining.common.Record;
import de.unihannover.reviews.mining.common.Rule;
import de.unihannover.reviews.miningInputCreation.RemarkTriggerMap;
import de.unihannover.reviews.miningInputCreation.RemarkTriggerMap.TicketInfoProvider;

public class RecordSubsetTest {

	private static RecordSubset distributeDeterministically(RecordSubset rs, RemarkTriggerMap triggerMap) {
		return rs.distributeCan(triggerMap, 0.0, new Random(42), Collections.emptyList(), Collections.emptyList());
	}

	private static RecordSubset distributeWithRandomness(
			RecordSubset rs, RemarkTriggerMap triggerMap, double randomness, long seed) {
		return rs.distributeCan(triggerMap, randomness, new Random(seed), Collections.emptyList(), Collections.emptyList());
	}

	private static RecordSubset distributeWithConditions(RecordSubset rs, RemarkTriggerMap triggerMap,
			List<And> inclusions, List<And> exclusions) {
		return rs.distributeCan(triggerMap, 0.0, new Random(42), inclusions, exclusions);
	}

	private static And cond(String filename) {
		return new And(new Rule() {
			@Override
			public int getComplexity() {
				return 0;
			}
			@Override
			public boolean test(Record r) {
				return r.getId().getFile().equals(filename);
			}
			@Override
			public Multiset<String> getUsedFeatures() {
				return new Multiset<>();
			}
		});
	}

	private List<String> toFiles(List<Record> records) {
		final List<String> ret = new ArrayList<>();
		for (final Record r : records) {
			ret.add(r.getId().getFile());
		}
		Collections.sort(ret);
		return ret;
	}

	private static RemarkTriggerMap createTriggerMap(String... lines) {
		final RemarkTriggerMap tm = new RemarkTriggerMap();
		for (final String line : lines) {
			tm.add(line);
		}
		return tm;
	}

	private static Record[] createRecords(RemarkTriggerMap tm, String... files) throws Exception {
		final List<Record> ret = new ArrayList<>();
		for (final String file : files) {
			ret.add(new Record(
					new ChangePartId("TIC-123", file, "ffff"),
					Collections.emptyList(),
					Collections.emptyList(),
					tm.getClassification(stubProvider(), "TIC-123", "ffff", file)));
		}
		return ret.toArray(new Record[ret.size()]);
	}

	private static TicketInfoProvider stubProvider() {
		return new TicketInfoProvider() {
			@Override
			public boolean containsChangesOutside(String commit, String file) throws IOException {
				return true;
			}
			@Override
			public boolean containsChangesInFileOutside(String commit, String file, int lineFrom, int lineTo)
					throws IOException {
				return false;
			}
		};
	}

	@Test
	public void testWithOneMustThatMakesOtherCansIrrelevant() throws Exception {
		final RemarkTriggerMap tm = createTriggerMap(
				"aaaa,Rem1;TIC-123;ffff,Trig1",
				"aaaa,Rem2;TIC-123;ffff,Trig1",
				"aaaa,Rem2;TIC-123;ffff,Trig2",
				"aaaa,Rem2;TIC-123;ffff,Trig3");
		final RecordSubset rs = new RecordSubset(createRecords(tm, "Trig1", "Trig2", "Trig3", "Trig4"));

		final RecordSubset actual = distributeDeterministically(rs, tm);
		assertEquals(Arrays.asList("Trig1"), this.toFiles(actual.getMustRecords()));
		assertEquals(Arrays.asList("Trig2", "Trig3", "Trig4"), this.toFiles(actual.getNoRecords()));
	}

	@Test
	public void testNoCan() throws Exception {
		final RemarkTriggerMap tm = createTriggerMap(
				"aaaa,Rem1;TIC-123;ffff,Trig1",
				"aaaa,Rem2;TIC-123;ffff,Trig2");
		final RecordSubset rs = new RecordSubset(createRecords(tm, "Trig1", "Trig2", "Trig3", "Trig4"));

		final RecordSubset actual = distributeDeterministically(rs, tm);
		assertEquals(Arrays.asList("Trig1", "Trig2"), this.toFiles(actual.getMustRecords()));
		assertEquals(Arrays.asList("Trig3", "Trig4"), this.toFiles(actual.getNoRecords()));
	}

	@Test
	public void testSimpleCan() throws Exception {
		final RemarkTriggerMap tm = createTriggerMap(
				"aaaa,Rem1;TIC-123;ffff,Trig1",
				"aaaa,Rem1;TIC-123;ffff,Trig2",
				"aaaa,Rem1;TIC-123;ffff,Trig3");
		final RecordSubset rs = new RecordSubset(createRecords(tm, "Trig1", "Trig2", "Trig3"));

		final RecordSubset actual = distributeDeterministically(rs, tm);
		assertEquals(Arrays.asList("Trig3"), this.toFiles(actual.getMustRecords()));
		assertEquals(Arrays.asList("Trig1", "Trig2"), this.toFiles(actual.getNoRecords()));
	}

	@Test
	public void testRandomness() throws Exception {
		final RemarkTriggerMap tm = createTriggerMap(
				"aaaa,Rem1;TIC-123;ffff,Trig1",
				"aaaa,Rem1;TIC-123;ffff,Trig2",
				"aaaa,Rem1;TIC-123;ffff,Trig3");
		final RecordSubset rs = new RecordSubset(createRecords(tm, "Trig1", "Trig2", "Trig3"));

		final RecordSubset actual1 = distributeWithRandomness(rs, tm, 0.1, 1);
		assertEquals(Arrays.asList("Trig1"), this.toFiles(actual1.getMustRecords()));
		assertEquals(Arrays.asList("Trig2", "Trig3"), this.toFiles(actual1.getNoRecords()));

		final RecordSubset actual2 = distributeWithRandomness(rs, tm, 0.1, 2);
		assertEquals(Arrays.asList("Trig2"), this.toFiles(actual2.getMustRecords()));
		assertEquals(Arrays.asList("Trig1", "Trig3"), this.toFiles(actual2.getNoRecords()));

		final RecordSubset actual3 = distributeWithRandomness(rs, tm, 0.1, 3);
		assertEquals(Arrays.asList("Trig3"), this.toFiles(actual3.getMustRecords()));
		assertEquals(Arrays.asList("Trig1", "Trig2"), this.toFiles(actual3.getNoRecords()));
	}

	@Test
	public void testRecordThatIsTriggerForTwoIsChosen() throws Exception {
		final RemarkTriggerMap tm = createTriggerMap(
				"aaaa,Rem1;TIC-123;ffff,Trig1",
				"aaaa,Rem1;TIC-123;ffff,Trig2",
				"aaaa,Rem1;TIC-123;ffff,Trig3",
				"aaaa,Rem2;TIC-123;ffff,Trig2",
				"aaaa,Rem2;TIC-123;ffff,Trig4");
		final RecordSubset rs = new RecordSubset(createRecords(tm, "Trig1", "Trig2", "Trig3", "Trig4"));

		final RecordSubset actual = distributeDeterministically(rs, tm);
		assertEquals(Arrays.asList("Trig2"), this.toFiles(actual.getMustRecords()));
		assertEquals(Arrays.asList("Trig1", "Trig3", "Trig4"), this.toFiles(actual.getNoRecords()));
	}

	@Test
	public void testAlgorithmDoesNotLoopWhenNotAllCanBeCovered() throws Exception {
		final RemarkTriggerMap tm = createTriggerMap(
				"aaaa,Rem1;TIC-123;ffff,TrigMissing",
				"aaaa,Rem2;TIC-123;ffff,Trig1",
				"aaaa,Rem2;TIC-123;ffff,Trig2");
		final RecordSubset rs = new RecordSubset(createRecords(tm, "Trig1", "Trig2", "Trig3"));

		final RecordSubset actual = distributeDeterministically(rs, tm);
		assertEquals(Arrays.asList("Trig2"), this.toFiles(actual.getMustRecords()));
		assertEquals(Arrays.asList("Trig1", "Trig3"), this.toFiles(actual.getNoRecords()));
	}

	@Test
	public void testInclusionConditionIsRespected() throws Exception {
		final RemarkTriggerMap tm = createTriggerMap(
				"aaaa,Rem1;TIC-123;ffff,Trig1",
				"aaaa,Rem1;TIC-123;ffff,Trig2",
				"aaaa,Rem2;TIC-123;ffff,Trig2",
				"aaaa,Rem2;TIC-123;ffff,Trig3");
		final RecordSubset rs = new RecordSubset(createRecords(tm, "Trig1", "Trig2", "Trig3", "Trig4"));

		final RecordSubset actual = distributeWithConditions(rs, tm,
				Collections.singletonList(cond("Trig2")),
				Collections.emptyList());
		assertEquals(Arrays.asList("Trig1", "Trig3"), this.toFiles(actual.getMustRecords()));
		assertEquals(Arrays.asList("Trig2", "Trig4"), this.toFiles(actual.getNoRecords()));
	}

	@Test
	public void testExclusionConditionIsRespected() throws Exception {
		final RemarkTriggerMap tm = createTriggerMap(
				"aaaa,Rem1;TIC-123;ffff,Trig1",
				"aaaa,Rem1;TIC-123;ffff,Trig2",
				"aaaa,Rem2;TIC-123;ffff,Trig2",
				"aaaa,Rem2;TIC-123;ffff,Trig3");
		final RecordSubset rs = new RecordSubset(createRecords(tm, "Trig1", "Trig2", "Trig3", "Trig4"));

		final RecordSubset actual = distributeWithConditions(rs, tm,
				Collections.emptyList(),
				Arrays.asList(cond("Trig1"), cond("Trig3")));
		assertEquals(Arrays.asList("Trig1", "Trig3"), this.toFiles(actual.getMustRecords()));
		assertEquals(Arrays.asList("Trig2", "Trig4"), this.toFiles(actual.getNoRecords()));
	}

}
