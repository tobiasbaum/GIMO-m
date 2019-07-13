package de.unihannover.reviews.mining.common;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import de.unihannover.reviews.miningInputCreation.RemarkTriggerMap;

public class RawEvaluationResult {

    private final MissedTriggerCounter missedTriggerCounter;
	private final int savedJavaLineCount;

    private RawEvaluationResult(MissedTriggerCounter missCounter, int savedJavaLineCount) {
        this.missedTriggerCounter = missCounter;
        this.savedJavaLineCount = savedJavaLineCount;
    }

    public static RawEvaluationResult create(
                    Predicate<Record> pred, List<Record> records, RemarkTriggerMap triggerMap) {
        int savedJavaLineCounts = 0;
        final MissedTriggerCounter missCounter = new MissedTriggerCounter(triggerMap);
        for (final Record r : records) {
            final boolean eval = pred.test(r);
            if (eval) {
                if (isJava(r)) {
                	savedJavaLineCounts += getAddedLineCount(r);
                }
                missCounter.handleInactive(r.getId());
            } else {
                missCounter.handleActive(r.getId());
            }
        }
        return new RawEvaluationResult(missCounter, savedJavaLineCounts);
    }

    private static int getAddedLineCount(Record r) {
    	if (!r.getId().isLineGranularity()) {
    		return 0;
    	}
		return r.getId().getLineTo() - r.getId().getLineFrom() + 1;
	}

	private static boolean isJava(Record r) {
		return r.getId().getFile().endsWith(".java");
	}

	public int getSavedHunkCount() {
        return this.missedTriggerCounter.getSavedHunkCount();
    }

    public int getRemarkWithoutTriggerCount() {
        return this.missedTriggerCounter.countRemarksWithoutTriggers();
    }

    public List<String> getTicketsWithMisses() {
        return Collections.unmodifiableList(this.missedTriggerCounter.getTicketsWithMisses());
    }

    public Set<Integer> getMissedRemarkIdsForLastTicket() {
        return this.missedTriggerCounter.getMissedRemarkIdsForCurrentTicket();
    }

	public int getSavedJavaLineCount() {
		return -this.savedJavaLineCount;
	}

	public double getRemarkWithoutTriggerLog() {
		return this.missedTriggerCounter.getRemarkWithoutTriggerLog();
	}

	public double getSavedHunkTrimmedMean() {
		return this.missedTriggerCounter.getSavedHunkTrimmedMean();
	}

}
