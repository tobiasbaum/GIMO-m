package de.unihannover.gimo_m.mining.common;

public class Equals extends NominalRule {

    public Equals(RecordScheme scheme, int absoluteColumnIndex, String value) {
        super(scheme, absoluteColumnIndex, value);
    }

    @Override
    public boolean test(Record r) {
        return this.getValue().equals(r.getValueStr(this.getStringColumnIndex()));
    }

    @Override
    public int hashCode() {
        return this.getValue().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Equals)) {
            return false;
        }
        return super.equals(o);
    }

    @Override
    public String toString() {
        return this.getColumnName() + " == '" + this.getValue().replace("\\", "\\\\").replaceAll("'", "\\'") + "'";
    }

    @Override
    protected Equals createWithOtherValue(String value) {
        return new Equals(this.getScheme(), this.getColumn(), value);
    }

}