package de.unihannover.gimo_m.mining.common;

public abstract class OrdinalRule extends SimpleRule {

    public abstract Rule nextLargerValue(RecordSet records);
    public abstract Rule nextSmallerValue(RecordSet records);

    public abstract RecordScheme getScheme();
	public abstract double getValue();

}
