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

import java.util.Set;

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
    public final double getComplexity(Set<Object> usedValues) {
        if (usedValues.contains(this.value)) {
            return 0.9;
        } else {
            usedValues.add(this.value);
            return 1.0;
        }
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
