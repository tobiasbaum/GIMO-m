package de.unihannover.reviews.mining.agents;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import org.junit.Test;

import de.unihannover.reviews.mining.common.And;
import de.unihannover.reviews.mining.common.Blackboard;
import de.unihannover.reviews.mining.common.ChangePartId;
import de.unihannover.reviews.mining.common.Geq;
import de.unihannover.reviews.mining.common.Leq;
import de.unihannover.reviews.mining.common.NondominatedResults;
import de.unihannover.reviews.mining.common.Record;
import de.unihannover.reviews.mining.common.RecordScheme;
import de.unihannover.reviews.mining.common.RecordSet;
import de.unihannover.reviews.mining.common.RuleSet;
import de.unihannover.reviews.mining.common.TargetFunction;
import de.unihannover.reviews.mining.common.ValuedResult;
import de.unihannover.reviews.miningInputCreation.RemarkTriggerMap;
import de.unihannover.reviews.miningInputCreation.TriggerClassification;

public class MiningAgentTest {

	private static RecordSet createRandomlyDistributed2DRecords(Random r) {
		final RecordScheme scheme = new RecordScheme(Arrays.asList("x", "y"), Collections.emptyList());
		final Record[] records = new Record[1000];
		for (int i = 0; i < records.length; i++) {
			records[i] = new Record(
					new ChangePartId("TIC-123", "F" + i, "cccc"),
					Arrays.asList(r.nextDouble(), r.nextDouble()),
					Collections.emptyList(),
					//classification does not matter, will be redone anyway
					TriggerClassification.MUST_BE);
		}
		return new RecordSet(scheme, records);
	}

	private static RecordSet createRecordsWithRandomNumericColumns(Random r, int columnCount) {
		final List<String> columnNames = new ArrayList<>();
		for (int c = 0; c < columnCount; c++) {
			columnNames.add("c" + c);
		}
		final RecordScheme scheme = new RecordScheme(columnNames, Collections.emptyList());
		final Record[] records = new Record[1000];
		for (int i = 0; i < records.length; i++) {
			final List<Double> values = new ArrayList<>();
			for (int c = 0; c < columnCount; c++) {
				values.add(r.nextDouble());
			}
			records[i] = new Record(
					new ChangePartId("TIC-" + i, "File", "cccc"),
					values,
					Collections.emptyList(),
					//classification does not matter, will be redone anyway
					TriggerClassification.MUST_BE);
		}
		return new RecordSet(scheme, records);
	}

	private static RemarkTriggerMap createXorTriggerMap(RecordSet rs, Random r) {
		final RemarkTriggerMap tm = new RemarkTriggerMap();
		for (int i = 0; i < rs.getRecords().length; i++) {
			final ChangePartId id = rs.getRecords()[i].getId();
			if (i % 2 == 0) {
				//will become a CAN record
				tm.add(String.format("CanCommit,CanFile;%3$s;%1$s,%2$s", id.getCommit(), id.getFile(), id.getTicket()));
			} else {
				//will become a MUST when it's in the lower left or upper right corner
				final double x = rs.getRecords()[i].getValueDbl(0);
				final double y = rs.getRecords()[i].getValueDbl(1);
				if ((x <= 0.5 && y <= 0.5) || (x >= 0.5 && y >= 0.5)) {
					tm.add(String.format("RE%1$s,%2$s;%3$s;%1$s,%2$s", id.getCommit(), id.getFile(), id.getTicket()));
				}
			}
		}
		return tm;
	}

	private static RemarkTriggerMap createTriggerMapWithPredicate(RecordSet rs, Predicate<Record> pred) {
		final RemarkTriggerMap tm = new RemarkTriggerMap();
		for (int i = 0; i < rs.getRecords().length; i++) {
			final ChangePartId id = rs.getRecords()[i].getId();
			//will become a MUST when the predicate is satisfied
			if (pred.test(rs.getRecords()[i])) {
				tm.add(String.format("RE%1$s,%2$s;%3$s;%1$s,%2$s", id.getCommit(), id.getFile(), id.getTicket()));
			}
		}
		return tm;
	}

	private static NondominatedResults<RuleSet> performMining(
			RecordSet rs, RemarkTriggerMap tm, long seed, List<Integer> iters, RuleSet perfectRule) throws Exception {
		final ValuedResult<RuleSet> perfectResult = new Blackboard(rs, tm, null, seed).evaluate(perfectRule);
		System.err.println("looking for a result at least as good as " + perfectResult);
		final Blackboard blackboard = new Blackboard(rs, tm, null, seed);
		final MiningAgent agent = new MiningAgent(blackboard);
		for (int i = 0; i < 1000; i++) {
			agent.performIteration();
			final ValuedResult<RuleSet> actualResult = getPerfectResult(blackboard.getNondominatedResultsSnapshot(), rs, perfectResult);
			if (actualResult != null) {
				System.err.println("perfect result found in iter " + i + ": " + actualResult);
				iters.add(i);
				return blackboard.getNondominatedResultsSnapshot();
			}
		}
		fail("no perfect result found with seed " + seed + ". best ratio: "
				+ blackboard.getNondominatedResultsSnapshot().getBestItem(new Random(0), new TargetFunction("ratio", v -> v.getRatio(), "")));
		return null;
	}

	private static ValuedResult<RuleSet> getPerfectResult(NondominatedResults<RuleSet> results, RecordSet rs, ValuedResult<RuleSet> perfectResult) {
		for (final ValuedResult<RuleSet> v : results.getItems()) {
			if (v.getRuleSetComplexity() <= perfectResult.getRuleSetComplexity()
					&& v.getRatio() <= perfectResult.getRatio()) {
				return v;
			}
		}
		return null;
	}

	private static int median(List<Integer> iters) {
		Collections.sort(iters);
		//TEST
		System.out.println(iters);
		return iters.get(iters.size() / 2);
	}

	@Test
	public void testXorConvergence() throws Exception {
		final List<Integer> iters = new ArrayList<>();
		for (int seed = 0; seed < 50; seed++) {
			final Random r = new Random(seed);
			final RecordSet rs = createRandomlyDistributed2DRecords(r);
			final RemarkTriggerMap tm = createXorTriggerMap(rs, r);
			final RuleSet perfectRule = RuleSet.SKIP_NONE
					.include(new And(new Geq(rs.getScheme(), 0, 0.5), new Leq(rs.getScheme(), 1, 0.5)))
					.include(new And(new Leq(rs.getScheme(), 0, 0.5), new Geq(rs.getScheme(), 1, 0.5)));
			performMining(rs.reclassify(tm), tm, r.nextLong(), iters, perfectRule);
		}
		assertThat(median(iters), lessThanOrEqualTo(39));
	}

	@Test
	public void testSimpleCondition() throws Exception {
		final List<Integer> iters = new ArrayList<>();
		for (int seed = 0; seed < 50; seed++) {
			final Random r = new Random(seed);
			final RecordSet rs = createRecordsWithRandomNumericColumns(r, 1);
			final RemarkTriggerMap tm = createTriggerMapWithPredicate(rs, (Record rec) -> rec.getValueDbl(0) > 0.9);
			final RuleSet perfectRule = RuleSet.SKIP_NONE
					.include(new And(new Leq(rs.getScheme(), 0, 0.9)));
			performMining(rs.reclassify(tm), tm, r.nextLong(), iters, perfectRule);
		}
		assertThat(median(iters), lessThanOrEqualTo(5));
	}

	@Test
	public void testSimpleConditionWith1RandomColumn() throws Exception {
		final List<Integer> iters = new ArrayList<>();
		for (int seed = 0; seed < 50; seed++) {
			final Random r = new Random(seed);
			final RecordSet rs = createRecordsWithRandomNumericColumns(r, 2);
			final RemarkTriggerMap tm = createTriggerMapWithPredicate(rs, (Record rec) -> rec.getValueDbl(0) > 0.9);
			final RuleSet perfectRule = RuleSet.SKIP_NONE
					.include(new And(new Leq(rs.getScheme(), 0, 0.9)));
			performMining(rs.reclassify(tm), tm, r.nextLong(), iters, perfectRule);
		}
		assertThat(median(iters), lessThanOrEqualTo(5));
	}

	@Test
	public void testSimpleConditionWith10RandomColumns() throws Exception {
		final List<Integer> iters = new ArrayList<>();
		for (int seed = 0; seed < 50; seed++) {
			final Random r = new Random(seed);
			final RecordSet rs = createRecordsWithRandomNumericColumns(r, 11);
			final RemarkTriggerMap tm = createTriggerMapWithPredicate(rs, (Record rec) -> rec.getValueDbl(0) > 0.9);
			final RuleSet perfectRule = RuleSet.SKIP_NONE
					.include(new And(new Leq(rs.getScheme(), 0, 0.9)));
			performMining(rs.reclassify(tm), tm, r.nextLong(), iters, perfectRule);
		}
		assertThat(median(iters), lessThanOrEqualTo(5));
	}

	@Test
	public void testSimpleConditionWith50RandomColumns() throws Exception {
		final List<Integer> iters = new ArrayList<>();
		for (int seed = 0; seed < 50; seed++) {
			final Random r = new Random(seed);
			final RecordSet rs = createRecordsWithRandomNumericColumns(r, 51);
			final RemarkTriggerMap tm = createTriggerMapWithPredicate(rs, (Record rec) -> rec.getValueDbl(0) > 0.9);
			final RuleSet perfectRule = RuleSet.SKIP_NONE
					.include(new And(new Leq(rs.getScheme(), 0, 0.9)));
			performMining(rs.reclassify(tm), tm, r.nextLong(), iters, perfectRule);
		}
		assertThat(median(iters), lessThanOrEqualTo(5));
	}

	@Test
	public void testSimpleConditionWithSmallNoise() throws Exception {
		final List<Integer> iters = new ArrayList<>();
		for (int seed = 0; seed < 50; seed++) {
			final Random r = new Random(seed);
			final RecordSet rs = createRecordsWithRandomNumericColumns(r, 1);
			final RemarkTriggerMap tm = createTriggerMapWithPredicate(rs, (Record rec) -> rec.getValueDbl(0) + (r.nextDouble() - 0.5) / 100.0 > 0.9);
			final RuleSet perfectRule = RuleSet.SKIP_NONE
					.include(new And(new Leq(rs.getScheme(), 0, 0.9)));
			performMining(rs.reclassify(tm), tm, r.nextLong(), iters, perfectRule);
		}
		assertThat(median(iters), lessThanOrEqualTo(5));
	}

	@Test
	public void testSimpleConditionWithSmallNoiseAnd10RandomColumns() throws Exception {
		final List<Integer> iters = new ArrayList<>();
		for (int seed = 0; seed < 50; seed++) {
			final Random r = new Random(seed);
			final RecordSet rs = createRecordsWithRandomNumericColumns(r, 11);
			final RemarkTriggerMap tm = createTriggerMapWithPredicate(rs, (Record rec) -> rec.getValueDbl(0) + (r.nextDouble() - 0.5) / 100.0 > 0.9);
			final RuleSet perfectRule = RuleSet.SKIP_NONE
					.include(new And(new Leq(rs.getScheme(), 0, 0.9)));
			performMining(rs.reclassify(tm), tm, r.nextLong(), iters, perfectRule);
		}
		assertThat(median(iters), lessThanOrEqualTo(5));
	}

	@Test
	public void testSimpleConditionWithSmallNoiseAnd50RandomColumns() throws Exception {
		final List<Integer> iters = new ArrayList<>();
		for (int seed = 0; seed < 50; seed++) {
			final Random r = new Random(seed);
			final RecordSet rs = createRecordsWithRandomNumericColumns(r, 51);
			final RemarkTriggerMap tm = createTriggerMapWithPredicate(rs, (Record rec) -> rec.getValueDbl(0) + (r.nextDouble() - 0.5) / 100.0 > 0.9);
			final RuleSet perfectRule = RuleSet.SKIP_NONE
					.include(new And(new Leq(rs.getScheme(), 0, 0.9)));
			performMining(rs.reclassify(tm), tm, r.nextLong(), iters, perfectRule);
		}
		assertThat(median(iters), lessThanOrEqualTo(5));
	}

	@Test
	public void testMoreComplexCondition() throws Exception {
		final List<Integer> iters = new ArrayList<>();
		for (int seed = 0; seed < 50; seed++) {
			final Random r = new Random(seed);
			final RecordSet rs = createRecordsWithRandomNumericColumns(r, 3);
			final RuleSet perfectRule = RuleSet.SKIP_NONE
					.include(new And(new Leq(rs.getScheme(), 0, 0.9)))
					.include(new And(new Geq(rs.getScheme(), 1, 0.05), new Geq(rs.getScheme(), 2, 0.05)));
			final RemarkTriggerMap tm = createTriggerMapWithPredicate(rs, perfectRule.negate());
			performMining(rs.reclassify(tm), tm, r.nextLong(), iters, perfectRule);
		}
		assertThat(median(iters), lessThanOrEqualTo(8));
	}

	@Test
	public void testMoreComplexConditionWithBitFlipNoise() throws Exception {
		final List<Integer> iters = new ArrayList<>();
		for (int seed = 0; seed < 50; seed++) {
			final Random r = new Random(seed);
			final RecordSet rs = createRecordsWithRandomNumericColumns(r, 3);
			final RuleSet perfectRule = RuleSet.SKIP_NONE
					.include(new And(new Leq(rs.getScheme(), 0, 0.9)))
					.include(new And(new Geq(rs.getScheme(), 1, 0.05), new Geq(rs.getScheme(), 2, 0.05)));
			final RemarkTriggerMap tm = createTriggerMapWithPredicate(rs, this.bitFlipNoise(r, perfectRule.negate(), 0.1));
			performMining(rs.reclassify(tm), tm, r.nextLong(), iters, perfectRule);
		}
		assertThat(median(iters), lessThanOrEqualTo(5));
	}

	private Predicate<Record> bitFlipNoise(Random rand, Predicate<Record> originalPredicate, double flipProbability) {
		return (Record r) -> rand.nextDouble() < flipProbability ? !originalPredicate.test(r) : originalPredicate.test(r);
	}

}
