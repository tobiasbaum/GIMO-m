package de.unihannover.reviews.mining.agents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.Test;

import de.unihannover.reviews.mining.agents.GreedyRuleCreation.ConditionResults;
import de.unihannover.reviews.mining.agents.GreedyRuleCreation.RuleQuality;
import de.unihannover.reviews.mining.common.And;
import de.unihannover.reviews.mining.common.Blackboard;
import de.unihannover.reviews.mining.common.Equals;
import de.unihannover.reviews.mining.common.Geq;
import de.unihannover.reviews.mining.common.Leq;
import de.unihannover.reviews.mining.common.Multiset;
import de.unihannover.reviews.mining.common.NotEquals;
import de.unihannover.reviews.mining.common.RecordSet;
import de.unihannover.reviews.mining.common.RsTestHelper;
import de.unihannover.reviews.mining.common.RulePattern;
import de.unihannover.reviews.mining.common.RuleSet;
import de.unihannover.reviews.miningInputCreation.TriggerClassification;

public class GreedyRuleCreationTest {

    private static RecordSubset toSubset(RecordSet rs) {
        return new RecordSubset(rs.getRecords());
    }

    private static ConditionResults fbc(RecordSet rs) {
        final Blackboard blackboard = new Blackboard(rs, null, null, 42);
		final GreedyRuleCreation rc = new GreedyRuleCreation(blackboard, new Random(42));
        return rc.findBestCondition(rs.getScheme(), toSubset(rs), featureSet(rs), Multiset.emptySet(), rc.determineTotalCounts(toSubset(rs)),
        		RuleQuality::getPrecision, blackboard.inclusionRestrictions().toCreationRestrictions(new And()));
    }

    private static Set<String> featureSet(RecordSet rs) {
		return new LinkedHashSet<>(rs.getScheme().getColumnNames());
	}

	private static And gtd(RecordSet rs) throws Exception {
        final Blackboard blackboard = new Blackboard(rs, null, null, 0);
		final GreedyRuleCreation rc = new GreedyRuleCreation(blackboard, new Random(0));
		return rc.greedyTopDown(rs.getScheme(), toSubset(rs), featureSet(rs), rc.determineTotalCounts(toSubset(rs)), true);
    }

    private static RuleSet findRuleSet(RecordSet rs, long seed) throws Exception {
    	return findRuleSet(rs, seed, blackboard -> {});
    }

    private static RuleSet findRuleSet(RecordSet rs, long seed, Consumer<Blackboard> blackboardAdjustments) throws Exception {
    	final Blackboard bb = new Blackboard(rs, RsTestHelper.createTriggerMap(rs), null, seed);
    	blackboardAdjustments.accept(bb);
        final GreedyRuleCreation rc = new GreedyRuleCreation(bb, new Random(seed));
        return rc.createRuleSet(50);
    }

    private static RuleQuality rq(int mustCount, int noCount) {
        return new RuleQuality(mustCount, noCount, null);
    }

	private static void checkWithSeveralSeeds(RecordSet rs, RuleSet expectedMostCommon) throws Exception {
		checkWithSeveralSeeds(rs, bb -> {}, expectedMostCommon);
	}

	private static void checkWithSeveralSeeds(RecordSet rs, Consumer<Blackboard> blackboardAdjustments, RuleSet expectedMostCommon) throws Exception {
		final Multiset<RuleSet> counts = new Multiset<>();
		for (int seed = 0; seed < 20; seed++) {
	        final RuleSet actual = findRuleSet(rs, seed, blackboardAdjustments);
	        counts.add(actual);
		}
        assertEquals("rule is not the most common", expectedMostCommon, counts.getPrefixOfMostCommon(1).iterator().next());
	}

    @Test
    public void testFindBestConditionNumeric1() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0), Arrays.asList(2.0));
        final ConditionResults actual = fbc(rs);
        assertEquals(
                new ConditionResults(new Leq(rs.getScheme(), 0, 1.5), rq(0, 1)),
                actual);
    }

    @Test
    public void testFindBestConditionNumeric2() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0, 2.0), Arrays.asList(3.0));
        final ConditionResults actual = fbc(rs);
        assertEquals(
                new ConditionResults(new Leq(rs.getScheme(), 0, 2.5), rq(0, 2)),
                actual);
    }

    @Test
    public void testFindBestConditionNumeric3() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(2.0), Arrays.asList(1.0));
        final ConditionResults actual = fbc(rs);
        assertEquals(
                new ConditionResults(new Geq(rs.getScheme(), 0, 1.5), rq(0, 1)),
                actual);
    }

    @Test
    public void testFindBestConditionNumeric4() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(3.0), Arrays.asList(1.0, 2.0));
        final ConditionResults actual = fbc(rs);
        assertEquals(
                new ConditionResults(new Geq(rs.getScheme(), 0, 2.5), rq(0, 1)),
                actual);
    }

    @Test
    public void testFindBestConditionNumeric5() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0, 2.0, 4.0), Arrays.asList(3.0));
        final ConditionResults actual = fbc(rs);
        assertEquals(
                new ConditionResults(new Leq(rs.getScheme(), 0, 2.5), rq(0, 2)),
                actual);
    }

    @Test
    public void testFindBestConditionNumeric6() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0, 3.0, 4.0), Arrays.asList(2.0));
        final ConditionResults actual = fbc(rs);
        assertEquals(
                new ConditionResults(new Geq(rs.getScheme(), 0, 2.5), rq(0, 2)),
                actual);
    }

    @Test
    public void testFindBestConditionNumeric7() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0, 2.0, 3.0, 6.0, 7.0), Arrays.asList(4.0, 5.0, 8.0));
        final ConditionResults actual = fbc(rs);
        assertEquals(
                new ConditionResults(new Leq(rs.getScheme(), 0, 3.5), rq(0, 3)),
                actual);
    }

    @Test
    public void testFindBestConditionNumeric8() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0, 2.0), Arrays.asList(1.0, 2.0));
        final ConditionResults actual = fbc(rs);
        assertEquals(
                new ConditionResults(new Leq(rs.getScheme(), 0, 1.5), rq(1, 1)),
                actual);
    }

    @Test
    public void testFindBestConditionNumeric9() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0, 2.0), Arrays.asList(2.0));
        final ConditionResults actual = fbc(rs);
        assertEquals(
                new ConditionResults(new Leq(rs.getScheme(), 0, 1.5), rq(0, 1)),
                actual);
    }

    @Test
    public void testFindBestConditionNumeric10() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(2.0), Arrays.asList(1.0, 2.0));
        final ConditionResults actual = fbc(rs);
        assertEquals(
                new ConditionResults(new Geq(rs.getScheme(), 0, 1.5), rq(1, 1)),
                actual);
    }

    @Test
    public void testFindBestConditionNumeric11() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0, 2.0), Arrays.asList(2.0, 3.0));
        final ConditionResults actual = fbc(rs);
        assertEquals(
                new ConditionResults(new Leq(rs.getScheme(), 0, 1.5), rq(0, 1)),
                actual);
    }

    @Test
    public void testFindBestConditionNumeric12() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0, 1.0), Arrays.asList(1.0, 1.0, 1.0, 2.0));
        final ConditionResults actual = fbc(rs);
        assertEquals(
                new ConditionResults(new Leq(rs.getScheme(), 0, 1.5), rq(3, 2)),
                actual);
    }

    @Test
    public void testFindBestConditionNumeric13() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(2.0, 1.0), Arrays.asList(4.0, 5.0, 3.0));
        final ConditionResults actual = fbc(rs);
        assertEquals(
                new ConditionResults(new Leq(rs.getScheme(), 0, 2.5), rq(0, 2)),
                actual);
    }

    @Test
    public void testFindBestConditionNumeric14() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0), Arrays.asList(1.0));
        final ConditionResults actual = fbc(rs);
        assertNull(actual);
    }

    @Test
    public void testFindBestConditionNumeric15() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0, 2.0, 3.0), Collections.emptyList());
        final ConditionResults actual = fbc(rs);
        assertNull(actual);
    }

    @Test
    public void testFindBestConditionNumeric16() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Collections.emptyList(), Arrays.asList(1.0, 2.0, 3.0));
        final ConditionResults actual = fbc(rs);
        assertNull(actual);
    }

    @Test
    public void testFindBestConditionTwoNumericColumns() {
        final RecordSet rs = RsTestHelper.twoNumericColumns(
                        Arrays.asList(
                                        1.0, 1.0,
                                        3.0, 2.0,
                                        4.0, 2.0),
                        Arrays.asList(
                                        2.0, 4.0));
        final ConditionResults actual = fbc(rs);
        assertEquals(
                new ConditionResults(new Leq(rs.getScheme(), 1, 3.0), rq(0, 3)),
                actual);
    }

    @Test
    public void testFindBestConditionString1() {
        final RecordSet rs = RsTestHelper.oneStringColumn(Arrays.asList("a"), Arrays.asList("b"));
        final ConditionResults actual = fbc(rs);
        assertEquals(
                new ConditionResults(new Equals(rs.getScheme(), 0, "a"), rq(0, 1)),
                actual);
    }

    @Test
    public void testFindBestConditionString2() {
        final RecordSet rs = RsTestHelper.oneStringColumn(Arrays.asList("a", "c", "c", "c"), Arrays.asList("a", "b"));
        final ConditionResults actual = fbc(rs);
        assertEquals(
                new ConditionResults(new Equals(rs.getScheme(), 0, "c"), rq(0, 3)),
                actual);
    }

    @Test
    public void testFindBestConditionString3() {
        final RecordSet rs = RsTestHelper.oneStringColumn(Arrays.asList("a", "c", "c", "c"), Arrays.asList("b"));
        final ConditionResults actual = fbc(rs);
        assertEquals(
                new ConditionResults(new NotEquals(rs.getScheme(), 0, "b"), rq(0, 4)),
                actual);
    }

    @Test
    public void testFindBestConditionString4() {
        final RecordSet rs = RsTestHelper.twoStringColumns(
                        Arrays.asList(
                            "a", "x",
                            "a", "x",
                            "b", "x",
                            "b", "y"),
                        Arrays.asList(
                            "b", "y"));
        final ConditionResults actual = fbc(rs);
        assertEquals(
                new ConditionResults(new Equals(rs.getScheme(), 1, "x"), rq(0, 3)),
                actual);
    }

    @Test
    public void testFindBestConditionString5() {
        final RecordSet rs = RsTestHelper.oneStringColumn(Arrays.asList("a"), Arrays.asList("a"));
        final ConditionResults actual = fbc(rs);
        assertNull(actual);
    }

    @Test
    public void testFindBestConditionString6() {
        final RecordSet rs = RsTestHelper.oneStringColumn(Arrays.asList("a", "b"), Collections.emptyList());
        final ConditionResults actual = fbc(rs);
        assertNull(actual);
    }

    @Test
    public void testFindBestConditionString7() {
        final RecordSet rs = RsTestHelper.oneStringColumn(Collections.emptyList(), Arrays.asList("a", "b"));
        final ConditionResults actual = fbc(rs);
        assertNull(actual);
    }

    @Test
    public void testGreedyTopDownMultipleNotEquals() throws Exception {
        final RecordSet rs = RsTestHelper.oneStringColumn(Arrays.asList("a", "b", "c", "d", "e", "f"), Arrays.asList("X", "Y"));
        final And actual = gtd(rs);
        assertEquals(new And(new NotEquals(rs.getScheme(), 0, "X"), new NotEquals(rs.getScheme(), 0, "Y")), actual);
    }

    @Test
    public void testFindBestRuleSetSimple() throws Exception {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0), Arrays.asList(2.0));

        final RuleSet expected1 = RuleSet.SKIP_NONE
        		.include(new And(new Leq(rs.getScheme(), 0, 1.5)))
        		.exclude(new And(new Geq(rs.getScheme(), 0, 1.5)));
        checkWithSeveralSeeds(rs, expected1);
    }

    @Test
    public void testFindBestRuleSetSimpleImbalanced() throws Exception {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0), Arrays.asList(8.0));

        final RuleSet expected1 = RuleSet.SKIP_NONE
        		.include(new And(new Leq(rs.getScheme(), 0, 7.5)))
        		.exclude(new And(new Geq(rs.getScheme(), 0, 7.5)));
        checkWithSeveralSeeds(rs, expected1);
    }

    @Test
    public void testFindBestRuleSetTwoRulesForSameColumn() throws Exception {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 10.0, 11.0), Arrays.asList(8.0, 9.0));
        final RuleSet actual = findRuleSet(rs, 42);

        final RuleSet expected = RuleSet.SKIP_NONE
        		.include(new And(new Leq(rs.getScheme(), 0, 7.5)))
        		.include(new And(new Geq(rs.getScheme(), 0, 9.5)))
        		.exclude(new And(new Geq(rs.getScheme(), 0, 7.5), new Leq(rs.getScheme(), 0, 9.5)));
        assertEquals(expected, actual);
    }

    @Test
    public void testFindBestRuleSetTwoRulesForSameColumnInverted() throws Exception {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(8.0, 9.0), Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 10.0, 11.0));

        final RuleSet expected1 = RuleSet.SKIP_NONE
        		.include(new And(new Geq(rs.getScheme(), 0, 7.5), new Leq(rs.getScheme(), 0, 9.5)))
        		.exclude(new And(new Leq(rs.getScheme(), 0, 7.5)))
        		.exclude(new And(new Geq(rs.getScheme(), 0, 9.5)));
        checkWithSeveralSeeds(rs, expected1);
    }

    @Test
    public void testSimpleExclusionSuffices() throws Exception {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0, 2.0, 3.0, 4.0), Arrays.asList(5.0));

        final RuleSet expected1 = RuleSet.SKIP_NONE
        		.include(new And(new Leq(rs.getScheme(), 0, 4.5)))
        		.exclude(new And(new Geq(rs.getScheme(), 0, 4.5)));
        checkWithSeveralSeeds(rs, expected1);
    }

    @Test
    public void testRejectNumericColumn() throws Exception {
        final RecordSet rs = RsTestHelper.createRecords(20,
        		Collections.singletonList(i -> Double.valueOf(i)),
        		Collections.singletonList(i -> i < 15 ? "a" : "b"),
        		i -> i < 10 ? TriggerClassification.MUST_BE : TriggerClassification.NO_TRIGGER);

        final RuleSet expected1 = RuleSet.SKIP_NONE
        		.include(new And(new Equals(rs.getScheme(), 1, "b")))
        		.exclude(new And(new Equals(rs.getScheme(), 1, "a")));
        checkWithSeveralSeeds(
        		rs,
        		bb -> bb.addRejectedColumns(Collections.singletonList("numCol0")),
        		expected1);
    }

    @Test
    public void testRejectStringColumn() throws Exception {
        final RecordSet rs = RsTestHelper.createRecords(20,
        		Collections.singletonList(i -> Double.valueOf(i)),
        		Collections.singletonList(i -> i < 15 ? "a" : "b"),
        		i -> i < 10 ? TriggerClassification.MUST_BE : TriggerClassification.NO_TRIGGER);

        final RuleSet expected1 = RuleSet.SKIP_NONE
        		.include(new And(new Geq(rs.getScheme(), 0, 9.5)))
        		.exclude(new And(new Leq(rs.getScheme(), 0, 9.5)));
        checkWithSeveralSeeds(
        		rs,
        		bb -> bb.addRejectedColumns(Collections.singletonList("strCol0")),
        		expected1);
    }

    @Test
    public void testRejectExclusion() throws Exception {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0, 2.0, 3.0, 4.0), Arrays.asList(5.0));

//        final RuleSet expected1 = RuleSet.SKIP_NONE
//        		.include(new And(new Leq(rs.getScheme(), 0, 4.5)));
//        final RuleSet expected2 = RuleSet.SKIP_NONE
//        		.include(new And(new Leq(rs.getScheme(), 0, 4.0)));
        final RuleSet expected1 = RuleSet.SKIP_NONE
        		.include(new And(new Leq(rs.getScheme(), 0, 4.5)))
        		.exclude(new And(new Geq(rs.getScheme(), 0, 3.5)));
        checkWithSeveralSeeds(rs,
        		bb -> bb.exclusionRestrictions().reject(RulePattern.parse(rs.getScheme(), "numCol>=4.5 and *")),
        		expected1);
    }

    @Test
    public void testRejectInclusion() throws Exception {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0, 2.0, 3.0, 4.0), Arrays.asList(5.0));

        final RuleSet expected1 = RuleSet.SKIP_NONE
        		.exclude(new And(new Geq(rs.getScheme(), 0, 4.5)));
        checkWithSeveralSeeds(rs,
        		bb -> bb.inclusionRestrictions().reject(RulePattern.parse(rs.getScheme(), "numCol<=* and *")),
        		expected1);
    }

    @Test
    public void testAcceptExclusion() throws Exception {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(2.0, 3.0, 4.0), Arrays.asList(1.0, 5.0));

        final RuleSet expected1 = RuleSet.SKIP_NONE
        		.include(new And(new Geq(rs.getScheme(), 0, 1.5)))
        		.exclude(new And(new Geq(rs.getScheme(), 0, 4.5)))
        		.exclude(new And(new Leq(rs.getScheme(), 0, 1.5)));
        checkWithSeveralSeeds(rs,
        		bb -> bb.exclusionRestrictions().accept(Collections.singletonList(new And(new Geq(rs.getScheme(), 0, 4.5)))),
        		expected1);
    }

    @Test
    public void testAcceptInclusion() throws Exception {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(2.0, 3.0, 4.0), Arrays.asList(1.0, 5.0));

        final RuleSet expected1 = RuleSet.SKIP_NONE
        		.include(new And(new Leq(rs.getScheme(), 0, 4.5)))
        		.exclude(new And(new Leq(rs.getScheme(), 0, 1.5)))
        		.exclude(new And(new Geq(rs.getScheme(), 0, 4.5)));
        checkWithSeveralSeeds(rs,
        		bb -> bb.inclusionRestrictions().accept(Collections.singletonList(new And(new Leq(rs.getScheme(), 0, 4.5)))),
        		expected1);
    }

    @Test
    public void testConstantColumnProMust() throws Exception {
        final RecordSet rs = RsTestHelper.oneStringColumn(Arrays.asList("a"), Arrays.asList("a", "a", "a", "a", "a"));

        final RuleSet expected1 = RuleSet.SKIP_NONE;
        checkWithSeveralSeeds(rs,
        		expected1);
    }

    @Test
    public void testConstantColumnProNo() throws Exception {
        final RecordSet rs = RsTestHelper.oneStringColumn(Arrays.asList("a", "a", "a", "a", "a"), Arrays.asList("a"));

        //due to the undersampling, the imbalance does not carry through to the mining
        final RuleSet expected1 = RuleSet.SKIP_NONE;
        checkWithSeveralSeeds(rs,
        		expected1);
    }
}
