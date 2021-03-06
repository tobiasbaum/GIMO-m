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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public final class Record {
    private final int id;
    private final double[] numericValues;
    private final String[] stringValues;
    private final String classification;

    public Record(int id, List<Double> numericValues, List<String> stringValues, String classification) {
        this.id = id;
        this.numericValues = toArray(numericValues);
        this.stringValues = stringValues.toArray(new String[stringValues.size()]);
        this.classification = classification;
    }

    static double[] toArray(Collection<Double> compl) {
        final double[] ret = new double[compl.size()];
        final Iterator<Double> iter = compl.iterator();
        for (int i = 0; i < compl.size(); i++) {
            ret[i] = iter.next();
        }
        return ret;
    }

    public String getValueStr(int stringColumnIndex) {
        return this.stringValues[stringColumnIndex];
    }

    public double getValueDbl(int numericColumnIndex) {
        return this.numericValues[numericColumnIndex];
    }

    public int getId() {
        return this.id;
    }

    public String getCorrectClass() {
        return this.classification;
    }

}
