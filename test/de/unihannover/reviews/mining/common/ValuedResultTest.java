package de.unihannover.reviews.mining.common;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import de.unihannover.reviews.miningInputCreation.RemarkTriggerMap;

public class ValuedResultTest {

    @Test
    public void testCreate() {
        final RecordSet rs = RsTestHelper.oneNumericColumn(Arrays.asList(1.0, 2.0, 3.0), Arrays.asList(4.0, 5.0, 6.0));
        final RemarkTriggerMap tm = RsTestHelper.createTriggerMap(rs);

        checkEval(RuleSet.SKIP_NONE, rs, tm, 0, 0, 0, 0, 0, 0.0, 0.0);
        checkEval(RuleSet.SKIP_NONE.include(new And(new Leq(rs.getScheme(), 0, 1.5))), rs, tm, 0, -1, 2, 1, 0, 0.0, -1.0/6.0);
        checkEval(RuleSet.SKIP_NONE.include(new And(new Leq(rs.getScheme(), 0, 2.5))), rs, tm, 0, -2, 2, 1, 0, 0.0, -2.0/6.0);
        checkEval(RuleSet.SKIP_NONE.include(new And(new Leq(rs.getScheme(), 0, 3.5))), rs, tm, 0, -3, 2, 1, 0, 0.0, -3.0/6.0);
        checkEval(RuleSet.SKIP_NONE.include(new And(new Leq(rs.getScheme(), 0, 4.5))), rs, tm, 1, -4, 2, 1, 0, Math.log(2.0), -4.0/6.0);
        checkEval(RuleSet.SKIP_NONE.include(new And(new Leq(rs.getScheme(), 0, 5.5))), rs, tm, 2, -5, 2, 1, 0, 2 * Math.log(2.0), -5.0/6.0);
        checkEval(RuleSet.SKIP_NONE.include(new And(new Leq(rs.getScheme(), 0, 6.5))), rs, tm, 3, -6, 2, 1, 0, 3 * Math.log(2.0), -6.0/6.0);
    }

    private static void checkEval(RuleSet rule, RecordSet rs, RemarkTriggerMap tm,
    		int missedRemarkCount, int savedHunks, int complexity, int featureCount, int savedJavaLines, double missedRemarkTMean, double savedHunkTMean) {
        assertEquals(
                new ValuedResult<>(rule, missedRemarkCount, savedHunks, complexity, featureCount, savedJavaLines, missedRemarkTMean, savedHunkTMean),
                ValuedResult.create(rule, rs, tm));
    }

}
