package de.unihannover.reviews.mining.common;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class ValuedResult<R> {

    public static final Comparator<ValuedResult<?>> LEXICOGRAPHIC_COMPARATOR = new Comparator<ValuedResult<?>>() {
        @Override
        public int compare(ValuedResult<?> o1, ValuedResult<?> o2) {
            final int cmp2 = Integer.compare(o1.suboptimalChosenCount, o2.suboptimalChosenCount);
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
            final int cmp6 = Double.compare(o1.lostValueMean, o2.lostValueMean);
            if (cmp6 != 0) {
                return cmp6;
            }
            final int cmp7 = Double.compare(o1.lostValueTrimmedMean, o2.lostValueTrimmedMean);
            if (cmp7 != 0) {
                return cmp7;
            }
            return 0;
        }
    };

    private final R rule;
    private final int suboptimalChosenCount;
    private final int ruleSetComplexity;
    private final int featureCount;
    private final double lostValueMean;
    private final double lostValueTrimmedMean;
    private final double maxLostValue;

    public ValuedResult(R rule,
    		int suboptimalChosenCount,
    		int complexity,
    		int featureCount,
    		double lostValueMean,
    		double lostValueTrimmedMean,
    		double maxLostValue) {
        this.rule = rule;
        this.suboptimalChosenCount = suboptimalChosenCount;
        this.ruleSetComplexity = complexity;
        this.featureCount = featureCount;
        this.lostValueMean = lostValueMean;
        this.lostValueTrimmedMean = lostValueTrimmedMean;
        this.maxLostValue = maxLostValue;
    }

    public static ValuedResult<RuleSet> create(RuleSet rule, RecordSet records, ResultData aggregates) {
        return create(rule, rule, Arrays.asList(records.getRecords()), aggregates);
    }

    public static<R extends ItemWithComplexity> ValuedResult<R> create(
                    Function<Record, String> pred, R itemToStore, List<Record> records, ResultData aggregates) {
        final RawEvaluationResult e = RawEvaluationResult.create(pred, records, aggregates);

        return new ValuedResult<>(itemToStore,
                        e.getSuboptimalChosenCount(),
                        itemToStore.getComplexity(),
                        itemToStore.getFeatureCount(),
                        e.getLostValueMean(),
                        e.getLostValueTrimmedMean(),
                        e.getMaxLostValue());
    }

    public R getItem() {
        return this.rule;
    }

    public int getSuboptimalChosenCount() {
        return this.suboptimalChosenCount;
    }

    public int getRuleSetComplexity() {
        return this.ruleSetComplexity;
    }

    public int getFeatureCount() {
        return this.featureCount;
    }

    public double getLostValueTrimmedMean() {
        return this.lostValueTrimmedMean;
    }

    public double getLostValueMean() {
        return this.lostValueMean;
    }

    public double getMaxLostValue() {
        return this.maxLostValue;
    }

    public boolean dominates(ValuedResult<?> v) {
        return this.suboptimalChosenCount <= v.suboptimalChosenCount
            && this.ruleSetComplexity <= v.ruleSetComplexity
            && this.featureCount <= v.featureCount
            && this.lostValueMean <= v.lostValueMean
            && this.lostValueTrimmedMean <= v.lostValueTrimmedMean
            && this.maxLostValue <= v.maxLostValue
            && (this.suboptimalChosenCount < v.suboptimalChosenCount
                || this.ruleSetComplexity < v.ruleSetComplexity
                || this.featureCount < v.featureCount
                || this.lostValueMean < v.lostValueMean
                || this.lostValueTrimmedMean < v.lostValueTrimmedMean
                || this.maxLostValue < v.maxLostValue);
    }

    @Override
    public int hashCode() {
        return this.suboptimalChosenCount
    		+ this.rule.hashCode();
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
        return "[" + this.suboptimalChosenCount
        		+ ", " + this.ruleSetComplexity
        		+ ", " + this.featureCount
        		+ ", " + this.lostValueMean
        		+ ", " + this.lostValueTrimmedMean
                + ", " + this.maxLostValue
        		+ "] " + this.rule;
    }

    public<T> ValuedResult<T> copyWithNewItem(T newItem) {
        return new ValuedResult<T>(newItem,
        		this.suboptimalChosenCount,
        		this.ruleSetComplexity,
        		this.featureCount,
        		this.lostValueMean,
        		this.lostValueTrimmedMean,
        		this.maxLostValue);
    }

    public boolean hasSameValues(ValuedResult<?> v) {
        return this.suboptimalChosenCount == v.suboptimalChosenCount
            && this.ruleSetComplexity == v.ruleSetComplexity
            && this.featureCount == v.featureCount
            && this.lostValueMean == v.lostValueMean
    		&& this.lostValueTrimmedMean == v.lostValueTrimmedMean;
    }

    public ValuedResult<R> distanceVectorTo(ValuedResult<RuleSet> cur) {
        return new ValuedResult<R>(
                this.rule,
                Math.abs(this.suboptimalChosenCount - cur.suboptimalChosenCount),
                Math.abs(this.ruleSetComplexity - cur.ruleSetComplexity),
                Math.abs(this.featureCount - cur.featureCount),
                Math.abs(this.lostValueMean - cur.lostValueMean),
                Math.abs(this.lostValueTrimmedMean - cur.lostValueTrimmedMean),
                Math.abs(this.maxLostValue - cur.maxLostValue)
        );
    }

}
