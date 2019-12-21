package de.unihannover.gimo_m.mining.common;

public abstract class NominalRule extends SimpleRule {

    private final RecordScheme scheme;
    private final int column;
    private final int stringColumnIndex;
    private final String value;

    public NominalRule(RecordScheme scheme, int absoluteColumnIndex, String value) {
    	assert value != null;
        this.scheme = scheme;
        this.column = absoluteColumnIndex;
        this.stringColumnIndex = scheme.toStringIndex(absoluteColumnIndex);
        this.value = value;
    }

    @Override
    public final int getComplexity() {
        return 1;
    }

    @Override
    public final Multiset<String> getUsedFeatures() {
        return Multiset.singleton(this.scheme.getName(this.column));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NominalRule)) {
            return false;
        }
        final NominalRule e = (NominalRule) o;
        return e.value.equals(this.value)
            && e.column == this.column;
    }

    protected final RecordScheme getScheme() {
        return this.scheme;
    }

    @Override
	public final int getColumn() {
        return this.column;
    }

    public final String getColumnName() {
    	return this.getScheme().getName(this.getColumn());
    }

    protected final int getStringColumnIndex() {
        return this.stringColumnIndex;
    }

    public final String getValue() {
        return this.value;
    }

    protected abstract Rule createWithOtherValue(String value);

}
