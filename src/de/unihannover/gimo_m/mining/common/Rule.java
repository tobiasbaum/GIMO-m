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

import java.util.function.Predicate;

import de.unihannover.gimo_m.util.Multiset;

public abstract class Rule extends ItemWithComplexity implements Predicate<Record> {
    @Override
    public abstract boolean test(Record r);
    public abstract Multiset<String> getUsedFeatures();

    @Override
    public final int getFeatureCount() {
        return this.getUsedFeatures().size();
    }

}