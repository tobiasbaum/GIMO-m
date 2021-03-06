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

public abstract class OrdinalRule extends SimpleRule {

    @Override
    public final double getComplexity(Set<Object> usedValues) {
        if (usedValues.contains(this.getValue())) {
            return 0.9;
        } else {
            usedValues.add(this.getValue());
            return 1.0;
        }
    }

    public abstract Rule nextLargerValue(RecordSet records);
    public abstract Rule nextSmallerValue(RecordSet records);

    public abstract RecordScheme getScheme();
	public abstract double getValue();

}
