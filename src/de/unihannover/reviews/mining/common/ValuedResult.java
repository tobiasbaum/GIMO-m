package de.unihannover.reviews.mining.common;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class ValuedResult<R> {

    public static final Comparator<ValuedResult<?>> LEXICOGRAPHIC_COMPARATOR = new Comparator<ValuedResult<?>>() {
        @Override
        public int compare(ValuedResult<?> o1, ValuedResult<?> o2) {
            final int cmp2 = Integer.compare(o1.bestChosenCount, o2.bestChosenCount);
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
    private final int bestChosenCount;
    private final int ruleSetComplexity;
    private final int featureCount;
    private final double lostValueMean;
    private final double lostValueTrimmedMean;

    public ValuedResult(R rule,
    		int bestChosenCount,
    		int complexity,
    		int featureCount,
    		double lostValueMean,
    		double lostValueTrimmedMean) {
        this.rule = rule;
        this.bestChosenCount = bestChosenCount;
        this.ruleSetComplexity = complexity;
        this.featureCount = featureCount;
        this.lostValueMean = lostValueMean;
        this.lostValueTrimmedMean = lostValueTrimmedMean;
    }

    public static ValuedResult<RuleSet> create(RuleSet rule, RecordSet records, ResultData aggregates) {
        return create(rule, rule, Arrays.asList(records.getRecords()), aggregates);
    }

    public static<R extends ItemWithComplexity> ValuedResult<R> create(
                    Function<Record, String> pred, R itemToStore, List<Record> records, ResultData aggregates) {
        final RawEvaluationResult e = RawEvaluationResult.create(pred, records, aggregates);

        return new ValuedResult<>(itemToStore,
                        -e.getBestChosenCount(),
                        itemToStore.getComplexity(),
                        itemToStore.getFeatureCount(),
                        e.getLostValueMean(),
                        e.getLostValueTrimmedMean());
    }

    public R getItem() {
        return this.rule;
    }

    public int getBestChosenCount() {
        return this.bestChosenCount;
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

    public boolean dominates(ValuedResult<?> v) {
        return this.bestChosenCount <= v.bestChosenCount
            && this.ruleSetComplexity <= v.ruleSetComplexity
            && this.featureCount <= v.featureCount
            && this.lostValueMean <= v.lostValueMean
            && this.lostValueTrimmedMean <= v.lostValueTrimmedMean
            && (this.bestChosenCount < v.bestChosenCount
                || this.ruleSetComplexity < v.ruleSetComplexity
                || this.featureCount < v.featureCount
                || this.lostValueMean < v.lostValueMean
                || this.lostValueTrimmedMean < v.lostValueTrimmedMean);
    }

    @Override
    public int hashCode() {
        return this.bestChosenCount
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
        return "[" + this.bestChosenCount
        		+ ", " + this.ruleSetComplexity
        		+ ", " + this.featureCount
        		+ ", " + this.lostValueMean
        		+ ", " + this.lostValueTrimmedMean
        		+ "] " + this.rule;
    }

    public<T> ValuedResult<T> copyWithNewItem(T newItem) {
        return new ValuedResult<T>(newItem,
        		this.bestChosenCount,
        		this.ruleSetComplexity,
        		this.featureCount,
        		this.lostValueMean,
        		this.lostValueTrimmedMean);
    }

    public boolean hasSameValues(ValuedResult<?> v) {
        return this.bestChosenCount == v.bestChosenCount
            && this.ruleSetComplexity == v.ruleSetComplexity
            && this.featureCount == v.featureCount
            && this.lostValueMean == v.lostValueMean
    		&& this.lostValueTrimmedMean == v.lostValueTrimmedMean;
    }

    public ValuedResult<R> distanceVectorTo(ValuedResult<RuleSet> cur) {
        return new ValuedResult<R>(
                this.rule,
                Math.abs(this.bestChosenCount - cur.bestChosenCount),
                Math.abs(this.ruleSetComplexity - cur.ruleSetComplexity),
                Math.abs(this.featureCount - cur.featureCount),
                Math.abs(this.lostValueMean - cur.lostValueMean),
                Math.abs(this.lostValueTrimmedMean - cur.lostValueTrimmedMean)
        );
    }

}
