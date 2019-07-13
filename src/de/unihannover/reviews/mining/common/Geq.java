package de.unihannover.reviews.mining.common;

public class Geq extends OrdinalRule {
    private final RecordScheme scheme;
    private final int column;
    private final int numericColumnIndex;
    private final double value;

    public Geq(RecordScheme scheme, int absoluteColumnIndex, double value) {
        assert Double.isFinite(value);
        this.scheme = scheme;
        this.column = absoluteColumnIndex;
        this.numericColumnIndex = scheme.toNumericIndex(absoluteColumnIndex);
        this.value = value;
    }

    @Override
    public boolean test(Record r) {
        return r.getValueDbl(this.numericColumnIndex) >= this.value;
    }

    @Override
    public int getComplexity() {
        return 1;
    }

    @Override
    public Multiset<String> getUsedFeatures() {
        return Multiset.singleton(this.scheme.getName(this.column));
    }

    @Override
    public int hashCode() {
        return Double.hashCode(this.value) + this.column;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Geq)) {
            return false;
        }
        final Geq e = (Geq) o;
        return e.value == this.value
            && e.column == this.column;
    }

    @Override
    public String toString() {
        return this.scheme.getName(this.column) + ">=" + this.value;
    }

    @Override
    public Rule nextLargerValue(RecordSet records) {
        final double nextValue = records.getSplitPointAbove(this.numericColumnIndex, this.value);
        if (nextValue == this.value) {
            return new False();
        }
        return new Geq(this.scheme, this.column, nextValue);
    }

    @Override
    public Rule nextSmallerValue(RecordSet records) {
        final double nextValue = records.getSplitPointBelow(this.numericColumnIndex, this.value);
        if (nextValue == this.value) {
            return new True();
        }
        return new Geq(this.scheme, this.column, nextValue);
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
