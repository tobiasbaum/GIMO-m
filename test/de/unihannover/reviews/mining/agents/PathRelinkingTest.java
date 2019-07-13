package de.unihannover.reviews.mining.agents;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

import de.unihannover.reviews.mining.common.And;
import de.unihannover.reviews.mining.common.Blackboard;
import de.unihannover.reviews.mining.common.Equals;
import de.unihannover.reviews.mining.common.RecordSet;
import de.unihannover.reviews.mining.common.RsTestHelper;
import de.unihannover.reviews.mining.common.RuleSet;
import de.unihannover.reviews.mining.common.TargetFunction;

public class PathRelinkingTest {

    private static void perform(final Blackboard blackboard, final RuleSet r1, RuleSet r2) {
    	final PathRelinking pr = new PathRelinking(blackboard, new Random(42));
    	pr.performPathRelinking(r1, r2, blackboard.getCurrentTargetFunction());
    }

    @Test
    public void testSimpleOptimumFound() {
        final RecordSet rs = RsTestHelper.oneStringColumn(Arrays.asList("no1",  "no2"), Arrays.asList("m1", "m2"));
        final Blackboard blackboard = new Blackboard(rs, RsTestHelper.createTriggerMap(rs), null, 42);

        final RuleSet r1 = RuleSet.SKIP_NONE
                        .include(new And(new Equals(rs.getScheme(), 0, "no1")));
        final RuleSet r2 = RuleSet.SKIP_NONE
        				.include(new And(new Equals(rs.getScheme(), 0, "no2")));

        final RuleSet expected = RuleSet.SKIP_NONE
		                .include(new And(new Equals(rs.getScheme(), 0, "no1")))
						.include(new And(new Equals(rs.getScheme(), 0, "no2")));

        perform(blackboard, r1, r2);
        assertThat(blackboard.getNondominatedResultsSnapshot().getItems(), hasItem(blackboard.evaluate(expected)));
    }

    @Test
    public void testFindsGoodRouteAmongBadOnes() {
        final RecordSet rs = RsTestHelper.oneStringColumn(Arrays.asList("no1",  "no2", "no3"), Arrays.asList("m1", "m2", "m3", "m4", "m5", "m6"));
        final Blackboard blackboard = new Blackboard(rs, RsTestHelper.createTriggerMap(rs), null, 42);

        final RuleSet r1 = RuleSet.SKIP_NONE
                        .include(new And(new Equals(rs.getScheme(), 0, "no1")))
                        .include(new And(new Equals(rs.getScheme(), 0, "m6")));
        final RuleSet r2 = RuleSet.SKIP_NONE
        				.include(new And(new Equals(rs.getScheme(), 0, "m1")))
        				.include(new And(new Equals(rs.getScheme(), 0, "no2")))
        				.include(new And(new Equals(rs.getScheme(), 0, "m2")))
        				.include(new And(new Equals(rs.getScheme(), 0, "m3")))
        				.include(new And(new Equals(rs.getScheme(), 0, "m4")))
        				.include(new And(new Equals(rs.getScheme(), 0, "no3")))
        				.include(new And(new Equals(rs.getScheme(), 0, "m5")));

        final RuleSet expected = RuleSet.SKIP_NONE
		                .include(new And(new Equals(rs.getScheme(), 0, "no1")))
						.include(new And(new Equals(rs.getScheme(), 0, "no2")))
						.include(new And(new Equals(rs.getScheme(), 0, "no3")));

        perform(blackboard, r1, r2);
        assertThat(blackboard.getNondominatedResultsSnapshot().getItems(), hasItem(blackboard.evaluate(expected)));
    }

    @Test
    public void testFindsGoodRouteAmongBadOnesEvenWhenTargetFunctionIsNotHelpful() {
        final RecordSet rs = RsTestHelper.oneStringColumn(Arrays.asList("no1",  "no2", "no3"), Arrays.asList("m1", "m2", "m3", "m4", "m5", "m6"));
        final Blackboard blackboard = new Blackboard(rs, RsTestHelper.createTriggerMap(rs), null, 42);
        blackboard.setCurrentTargetFunction(new TargetFunction("unhelpful", v -> 0.0, ""));

        final RuleSet r1 = RuleSet.SKIP_NONE
                        .include(new And(new Equals(rs.getScheme(), 0, "no1")))
                        .include(new And(new Equals(rs.getScheme(), 0, "m6")));
        final RuleSet r2 = RuleSet.SKIP_NONE
        				.include(new And(new Equals(rs.getScheme(), 0, "m1")))
        				.include(new And(new Equals(rs.getScheme(), 0, "no2")))
        				.include(new And(new Equals(rs.getScheme(), 0, "m2")))
        				.include(new And(new Equals(rs.getScheme(), 0, "m3")))
        				.include(new And(new Equals(rs.getScheme(), 0, "m4")))
        				.include(new And(new Equals(rs.getScheme(), 0, "no3")))
        				.include(new And(new Equals(rs.getScheme(), 0, "m5")));

        final RuleSet expected = RuleSet.SKIP_NONE
		                .include(new And(new Equals(rs.getScheme(), 0, "no1")))
						.include(new And(new Equals(rs.getScheme(), 0, "no2")))
						.include(new And(new Equals(rs.getScheme(), 0, "no3")));

        perform(blackboard, r1, r2);
        assertThat(blackboard.getNondominatedResultsSnapshot().getItems(), hasItem(blackboard.evaluate(expected)));
    }

}
