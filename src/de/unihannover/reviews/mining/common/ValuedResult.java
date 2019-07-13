package de.unihannover.reviews.mining.common;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import de.unihannover.reviews.miningInputCreation.RemarkTriggerMap;

public class ValuedResult<R> {

    public static final Comparator<ValuedResult<?>> LEXICOGRAPHIC_COMPARATOR = new Comparator<ValuedResult<?>>() {
        @Override
        public int compare(ValuedResult<?> o1, ValuedResult<?> o2) {
            final int cmp1 = Integer.compare(o1.missedRemarkCount, o2.missedRemarkCount);
            if (cmp1 != 0) {
                return cmp1;
            }
            final int cmp2 = Integer.compare(o1.savedHunkCount, o2.savedHunkCount);
            if (cmp2 != 0) {
                return cmp2;
            }
            final int cmp3 = Integer.compare(o1.ruleSetComplexity, o2.ruleSetComplexity);
            if (cmp3 != 0) {
                return cmp3;
            }
            final int cmp4 = Integer.compare(o1.featureCount, o2.featureCount);
            if (cmp4 != 0) {
                return cmp4;
            }
            final int cmp5 = Integer.compare(o1.savedJavaLineCount, o2.savedJavaLineCount);
            if (cmp5 != 0) {
                return cmp5;
            }
            final int cmp6 = Double.compare(o1.missedRemarkLog, o2.missedRemarkLog);
            if (cmp6 != 0) {
                return cmp6;
            }
            final int cmp7 = Double.compare(o1.savedHunkTrimmedMean, o2.savedHunkTrimmedMean);
            if (cmp7 != 0) {
                return cmp7;
            }
            return 0;
        }
    };

    private final R rule;
    private final int missedRemarkCount;
    private final int savedHunkCount;
    private final int ruleSetComplexity;
    private final int featureCount;
    private final int savedJavaLineCount;
    private final double missedRemarkLog;
    private final double savedHunkTrimmedMean;

    public ValuedResult(R rule,
    		int missedRemarkCount,
    		int savedHunks,
    		int complexity,
    		int featureCount,
    		int savedJavaLineCount,
    		double missedRemarkLog,
    		double savedHunkTrimmedMean) {
        this.rule = rule;
        this.missedRemarkCount = missedRemarkCount;
        this.savedHunkCount = savedHunks;
        this.ruleSetComplexity = complexity;
        this.featureCount = featureCount;
        this.savedJavaLineCount = savedJavaLineCount;
        this.missedRemarkLog = missedRemarkLog;
        this.savedHunkTrimmedMean = savedHunkTrimmedMean;
    }

    public static ValuedResult<RuleSet> create(RuleSet rule, RecordSet records, RemarkTriggerMap triggerMap) {
        return create(rule, rule, Arrays.asList(records.getRecords()), triggerMap);
    }

    public static<R extends ItemWithComplexity> ValuedResult<R> create(
                    Predicate<Record> pred, R itemToStore, List<Record> records, RemarkTriggerMap triggerMap) {
        final RawEvaluationResult e = RawEvaluationResult.create(pred, records, triggerMap);
        final int savedHunks = e.getSavedHunkCount();
        final int missedRemarks = e.getRemarkWithoutTriggerCount();
        final double missedRemarksLog = e.getRemarkWithoutTriggerLog();

        return new ValuedResult<>(itemToStore,
                        missedRemarks,
                        -savedHunks,
                        itemToStore.getComplexity(),
                        itemToStore.getFeatureCount(),
                        e.getSavedJavaLineCount(),
                        missedRemarksLog,
                        e.getSavedHunkTrimmedMean());
    }

    public R getItem() {
        return this.rule;
    }

    public double getRatio() {
        return ((double) this.savedHunkCount) / (this.missedRemarkCount + 1);
    }

    public double calcCost(double factor, int ticketCount) {
        return (this.missedRemarkCount * factor + this.savedHunkCount) / ticketCount;
    }

    public double calcCostLogTmean(double factor, int ticketCount) {
    	return this.getMissedRemarkLog() * factor / ticketCount + this.getSavedHunkTrimmedMean();
    }

    public int getMissedRemarkCount() {
        return this.missedRemarkCount;
    }

    public int getSavedHunkCount() {
        return this.savedHunkCount;
    }

    public int getRuleSetComplexity() {
        return this.ruleSetComplexity;
    }

    public int getFeatureCount() {
        return this.featureCount;
    }

    public int getSavedJavaLineCount() {
        return this.savedJavaLineCount;
    }

    public double getSavedHunkTrimmedMean() {
        return this.savedHunkTrimmedMean;
    }

    public double getMissedRemarkLog() {
        return this.missedRemarkLog;
    }

    public boolean dominates(ValuedResult<?> v) {
        return this.missedRemarkCount <= v.missedRemarkCount
            && this.savedHunkCount <= v.savedHunkCount
            && this.ruleSetComplexity <= v.ruleSetComplexity
            && this.featureCount <= v.featureCount
            && this.savedJavaLineCount <= v.savedJavaLineCount
            && this.missedRemarkLog <= v.missedRemarkLog
            && this.savedHunkTrimmedMean <= v.savedHunkTrimmedMean
            && (this.missedRemarkCount < v.missedRemarkCount
                || this.savedHunkCount < v.savedHunkCount
                || this.ruleSetComplexity < v.ruleSetComplexity
                || this.featureCount < v.featureCount
                || this.savedJavaLineCount < v.savedJavaLineCount
                || this.missedRemarkLog < v.missedRemarkLog
                || this.savedHunkTrimmedMean < v.savedHunkTrimmedMean);
    }

    @Override
    public int hashCode() {
        return this.missedRemarkCount * 23
    		+ this.savedHunkCount
    		+ this.rule.hashCode()
    		+ this.savedJavaLineCount;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ValuedResult)) {
            return false;
        }
        final ValuedResult<?> v = (ValuedResult<?>) o;
        return this.hasSameValues(v)
            && this.rule.equals(v.rule);
    }

    @Override
    public String toString() {
        return "[" + this.missedRemarkCount
        		+ ", " + this.savedHunkCount
        		+ ", " + this.ruleSetComplexity
        		+ ", " + this.featureCount
        		+ ", " + this.savedJavaLineCount
        		+ ", " + this.missedRemarkLog
        		+ ", " + this.savedHunkTrimmedMean
        		+ "] " + this.rule;
    }

    public<T> ValuedResult<T> copyWithNewItem(T newItem) {
        return new ValuedResult<T>(newItem,
        		this.missedRemarkCount,
        		this.savedHunkCount,
        		this.ruleSetComplexity,
        		this.featureCount,
        		this.savedJavaLineCount,
        		this.missedRemarkLog,
        		this.savedHunkTrimmedMean);
    }

    public boolean hasSameValues(ValuedResult<?> v) {
        return this.missedRemarkCount == v.missedRemarkCount
            && this.savedHunkCount == v.savedHunkCount
            && this.ruleSetComplexity == v.ruleSetComplexity
            && this.featureCount == v.featureCount
            && this.savedJavaLineCount == v.savedJavaLineCount
            && this.missedRemarkLog == v.missedRemarkLog
    		&& this.savedHunkTrimmedMean == v.savedHunkTrimmedMean;
    }

    public ValuedResult<R> distanceVectorTo(ValuedResult<RuleSet> cur) {
        return new ValuedResult<R>(
                this.rule,
                Math.abs(this.missedRemarkCount - cur.missedRemarkCount),
                Math.abs(this.savedHunkCount - cur.savedHunkCount),
                Math.abs(this.ruleSetComplexity - cur.ruleSetComplexity),
                Math.abs(this.featureCount - cur.featureCount),
                Math.abs(this.savedJavaLineCount - cur.savedJavaLineCount),
                Math.abs(this.missedRemarkLog - cur.missedRemarkLog),
                Math.abs(this.savedHunkTrimmedMean - cur.savedHunkTrimmedMean)
        );
    }

}
