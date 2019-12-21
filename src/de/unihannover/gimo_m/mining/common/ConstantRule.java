package de.unihannover.gimo_m.mining.common;

public abstract class ConstantRule extends SimpleRule {

    @Override
    public final int getComplexity() {
        return 0;
    }

    @Override
    public final Multiset<String> getUsedFeatures() {
        return Multiset.emptySet();
    }

    @Override
	public int getColumn() {
    	return -1;
    }

}
