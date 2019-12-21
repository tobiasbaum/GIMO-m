package de.unihannover.gimo_m.mining.common;

import java.util.function.Predicate;

public abstract class Rule implements ItemWithComplexity, Predicate<Record> {
    @Override
    public abstract boolean test(Record r);
    public abstract Multiset<String> getUsedFeatures();

    @Override
    public final int getFeatureCount() {
        return this.getUsedFeatures().size();
    }

}