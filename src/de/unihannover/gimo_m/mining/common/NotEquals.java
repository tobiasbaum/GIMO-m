package de.unihannover.gimo_m.mining.common;

public class NotEquals extends NominalRule {

    public NotEquals(RecordScheme scheme, int absoluteColumnIndex, String value) {
        super(scheme, absoluteColumnIndex, value);
    }

    @Override
    public boolean test(Record r) {
        final String v = r.getValueStr(this.getStringColumnIndex());
        return v != null && !this.getValue().equals(v);
    }

    @Override
    public int hashCode() {
        return -this.getValue().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NotEquals)) {
            return false;
        }
        return super.equals(o);
    }

    @Override
    public String toString() {
        return this.getColumnName() + " != '" + this.getValue().replace("\\", "\\\\").replaceAll("'", "\\'") + "'";
    }

    @Override
    protected Rule createWithOtherValue(String value) {
        return new NotEquals(this.getScheme(), this.getColumn(), value);
    }

}