/**
 * Copyright 2019 Tobias Baum
 *
 * This file is part of GIMO-m.
 *
 * GIMO-m is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GIMO-m is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package de.unihannover.gimo_m.mining.common;

import java.util.Arrays;
import java.util.Comparator;

public class ValuedResult<R> {

    public static final Comparator<ValuedResult<?>> LEXICOGRAPHIC_COMPARATOR = new Comparator<ValuedResult<?>>() {
        @Override
        public int compare(ValuedResult<?> o1, ValuedResult<?> o2) {
            for (int i = 0; i < o1.values.length; i++) {
                final int cmp = Double.compare(o1.values[i], o2.values[i]);
                if (cmp != 0) {
                    return cmp;
                }
            }
            return 0;
        }
    };

    private final R rule;
    private final double[] values;

    public ValuedResult(R rule, double... values) {
        this.rule = rule;
        this.values = values;
    }

    public static ValuedResult<RuleSet> create(RuleSet rule, RecordSet records, ResultData aggregates) {
        final RawEvaluationResult e = RawEvaluationResult.create(rule, Arrays.asList(records.getRecords()), aggregates);
        return new ValuedResult<>(rule, e.toMinimizableVector(rule.getComplexity(), rule.getFeatureCount()));
    }

    public R getItem() {
        return this.rule;
    }

    public double getValue(int index) {
        return this.values[index];
    }

    @Deprecated
    public int getSuboptimalChosenCount() {
        return (int) this.getValue(0);
    }

    @Deprecated
    public int getRuleSetComplexity() {
        return (int) this.getValue(1);
    }

    @Deprecated
    public int getFeatureCount() {
        return (int) this.getValue(2);
    }

    @Deprecated
    public double getLostValueTrimmedMean() {
        return this.getValue(4);
    }

    @Deprecated
    public double getLostValueMean() {
        return this.getValue(3);
    }

    @Deprecated
    public double getMaxLostValue() {
        return this.getValue(5);
    }

    public boolean dominates(ValuedResult<?> v) {
        assert this.values.length == v.values.length;
        for (int i = 0; i < this.values.length; i++) {
            if (this.values[i] > v.values[i]) {
                return false;
            }
        }
        for (int i = 0; i < this.values.length; i++) {
            if (this.values[i] < v.values[i]) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.values)
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
        return Arrays.toString(this.values) + " " + this.rule;
    }

    public<T> ValuedResult<T> copyWithNewItem(T newItem) {
        return new ValuedResult<T>(newItem, this.values);
    }

    public boolean hasSameValues(ValuedResult<?> v) {
        return Arrays.equals(this.values, v.values);
    }

    public ValuedResult<R> distanceVectorTo(ValuedResult<RuleSet> cur) {
        final double[] dist = new double[this.values.length];
        for (int i = 0; i < dist.length; i++) {
            dist[i] = Math.abs(this.values[i] - cur.values[i]);
        }
        return new ValuedResult<R>(this.rule, dist);
    }

    public double[] getAllValues() {
        return this.values;
    }
}
