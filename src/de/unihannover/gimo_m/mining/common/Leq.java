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

public class Leq extends OrdinalRule {
    private final RecordScheme scheme;
    private final int column;
    private final int numericColumnIndex;
    private final double value;

    public Leq(RecordScheme scheme, int absoluteColumnIndex, double value) {
        assert Double.isFinite(value);
        this.scheme = scheme;
        this.column = absoluteColumnIndex;
        this.numericColumnIndex = scheme.toNumericIndex(absoluteColumnIndex);
        this.value = value;
    }

    @Override
    public boolean test(Record r) {
        return r.getValueDbl(this.numericColumnIndex) <= this.value;
    }

    @Override
    public Multiset<String> getUsedFeatures() {
        return Multiset.singleton(this.scheme.getName(this.column));
    }

    @Override
    public int hashCode() {
        return 9563367 + Double.hashCode(this.value) + this.column;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Leq)) {
            return false;
        }
        final Leq e = (Leq) o;
        return e.value == this.value
            && e.column == this.column;
    }

    @Override
    public String toString() {
        return this.scheme.getName(this.column) + "<=" + this.value;
    }

    @Override
    public Rule nextLargerValue(RecordSet records) {
        final double nextValue = records.getSplitPointAbove(this.numericColumnIndex, this.value);
        if (nextValue == this.value) {
            return new True();
        }
        return new Leq(this.scheme, this.column, nextValue);
    }

    @Override
    public Rule nextSmallerValue(RecordSet records) {
        final double nextValue = records.getSplitPointBelow(this.numericColumnIndex, this.value);
        if (nextValue == this.value) {
            return new False();
        }
        return new Leq(this.scheme, this.column, nextValue);
    }

    @Override
	public double getValue() {
        return this.value;
    }

    @Override
    public RecordScheme getScheme() {
        return this.scheme;
    }

	@Override
	public int getColumn() {
		return this.column;
	}

}
