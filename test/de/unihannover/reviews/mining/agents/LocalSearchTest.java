package de.unihannover.reviews.mining.agents;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import de.unihannover.reviews.mining.common.And;
import de.unihannover.reviews.mining.common.Blackboard;
import de.unihannover.reviews.mining.common.ChangePartId;
import de.unihannover.reviews.mining.common.Leq;
import de.unihannover.reviews.mining.common.NondominatedResults;
import de.unihannover.reviews.mining.common.Record;
import de.unihannover.reviews.mining.common.RecordScheme;
import de.unihannover.reviews.mining.common.RecordSet;
import de.unihannover.reviews.mining.common.RsTestHelper;
import de.unihannover.reviews.mining.common.Rule;
import de.unihannover.reviews.mining.common.RuleSet;
import de.unihannover.reviews.miningInputCreation.TriggerClassification;

public class LocalSearchTest {

    private static NondominatedResults<RuleSet> optimize(final Blackboard blackboard, final LocalSearch ls, final RuleSet initial) {
        return ls.optimizeByLocalSearch(blackboard.evaluate(initial), blackboard.getCurrentTargetFunction());
    }

    @Test
    public void testResultsAreSimplified() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0, 2.0, 3.0), Arrays.asList(4.0, 5.0, 6.0));
        final Blackboard blackboard = new Blackboard(rs, RsTestHelper.createTriggerMap(rs), null, 42);
        final LocalSearch ls = new LocalSearch(blackboard, new Random(42));

        final RuleSet initial = RuleSet.SKIP_NONE
                        .include(new And(new Leq(rs.getScheme(), 0, 3.5), new Leq(rs.getScheme(), 0, 4.5)));

        final RuleSet expected = RuleSet.SKIP_NONE
                        .include(new And(new Leq(rs.getScheme(), 0, 3.5)));

        final NondominatedResults<RuleSet> result = optimize(blackboard, ls, initial);
        assertThat(result.getItems(), hasItem(blackboard.evaluate(expected)));
    }

    @Test
    public void testThresholdsAreMoved() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0, 2.0, 3.0), Arrays.asList(4.0, 5.0, 6.0));
        final Blackboard blackboard = new Blackboard(rs, RsTestHelper.createTriggerMap(rs), null, 42);
        final LocalSearch ls = new LocalSearch(blackboard, new Random(42));

        final RuleSet initial = RuleSet.SKIP_NONE
                        .include(new And(new Leq(rs.getScheme(), 0, 2.5), new Leq(rs.getScheme(), 0, 4.5)));

        final RuleSet expected = RuleSet.SKIP_NONE
                        .include(new And(new Leq(rs.getScheme(), 0, 3.5)));

        final NondominatedResults<RuleSet> result = optimize(blackboard, ls, initial);
        assertThat(result.getItems(), hasItem(blackboard.evaluate(expected)));
    }

    @Test
    public void testRejectedInclusionsAreTakenIntoAccount() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0, 2.0, 3.0), Arrays.asList(4.0, 5.0, 6.0));
        final Blackboard blackboard = new Blackboard(rs, RsTestHelper.createTriggerMap(rs), null, 42);

        blackboard.inclusionRestrictions().reject(Collections.singletonList(new And(new Leq(rs.getScheme(), 0, 3.5))));

        final LocalSearch ls = new LocalSearch(blackboard, new Random(42));

        final RuleSet initial = RuleSet.SKIP_NONE
                        .include(new And(new Leq(rs.getScheme(), 0, 2.5), new Leq(rs.getScheme(), 0, 4.5)));

        final RuleSet expected = RuleSet.SKIP_NONE
                        .include(new And(new Leq(rs.getScheme(), 0, 2.5)));

        final NondominatedResults<RuleSet> result = optimize(blackboard, ls, initial);
        assertThat(result.getItems(), hasItem(blackboard.evaluate(expected)));
    }

    @Test
    public void testHighlyDimensional() {
    	final List<String> cols = new ArrayList<>();
    	final List<Record> records = new ArrayList<>();
		records.add(new Record(new ChangePartId("TIC-123", "zero", "cccc"), toList(new double[200]), Collections.emptyList(), TriggerClassification.NO_TRIGGER));
    	for (int i = 0; i < 200; i++) {
    		cols.add("c" + i);
    		final double[] vals1 = new double[200];
    		vals1[i] = 1.0;
    		records.add(new Record(new ChangePartId("TIC-123", "f" + i, "cccc"), toList(vals1), Collections.emptyList(), TriggerClassification.NO_TRIGGER));
    		final double[] vals2 = new double[200];
    		vals2[i] = 2.0;
    		records.add(new Record(new ChangePartId("TIC-123", "f" + i, "dddd"), toList(vals2), Collections.emptyList(), TriggerClassification.MUST_BE));
    	}
    	final RecordScheme scheme = new RecordScheme(cols, Collections.emptyList());
    	final RecordSet rs = new RecordSet(scheme, records.toArray(new Record[records.size()]));
    	final List<Rule> startConditions = new ArrayList<>();
    	final List<Rule> expectedConditions = new ArrayList<>();
    	for (int i = 0; i < cols.size(); i++) {
    		startConditions.add(new Leq(scheme, i, 0.5));
    		expectedConditions.add(new Leq(scheme, i, 1.5));
    	}

        final Blackboard blackboard = new Blackboard(rs, RsTestHelper.createTriggerMap(rs), null, 42);
        final LocalSearch ls = new LocalSearch(blackboard, new Random(42));

        final RuleSet initial = RuleSet.SKIP_NONE
                .include(new And(startConditions.toArray(new Rule[startConditions.size()])));

        final RuleSet expected = RuleSet.SKIP_NONE
                .include(new And(expectedConditions.toArray(new Rule[expectedConditions.size()])));

        final NondominatedResults<RuleSet> result = optimize(blackboard, ls, initial);
        assertThat(result.getItems(), hasItem(blackboard.evaluate(expected)));
    }

	private static List<Double> toList(double[] vals) {
		final List<Double> ret = new ArrayList<>();
		for (final double d : vals) {
			ret.add(d);
		}
		return ret;
	}
}
