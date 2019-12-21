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

import de.unihannover.gimo_m.miningInputCreation.TriggerClassification;

public final class Record {
    private final int id;
    private final double[] numericValues;
    private final String[] stringValues;
    private String classification;

    public Record(int id, List<Double> numericValues, List<String> stringValues, String classification) {
        this.id = id;
        this.numericValues = toArray(numericValues);
        this.stringValues = stringValues.toArray(new String[stringValues.size()]);
        this.classification = classification;
    }

    @Deprecated
    public Record(ChangePartId id, List<Double> numericValues, List<String> stringValues) {
        this.id = id.getId();
        this.numericValues = toArray(numericValues);
        this.stringValues = stringValues.toArray(new String[stringValues.size()]);
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

    public ChangePartId getId() {
        return new ChangePartId(this.id);
    }

    @Deprecated
    public TriggerClassification getClassification() {
        return TriggerClassification.CAN_BE;
    }

    public boolean containsValueForColumn(RecordScheme scheme, int absoluteColumnIndex) {
        if (scheme.isNumeric(absoluteColumnIndex)) {
            return !Double.isNaN(this.getValueDbl(scheme.toNumericIndex(absoluteColumnIndex)));
        } else {
            return this.getValueStr(scheme.toStringIndex(absoluteColumnIndex)) != null;
        }
    }

    /**
     * Returns a record with the data of the current one and the given classification.
     * Creates a copy unless the classification does not change.
     */
	public Record withClassification(TriggerClassification newClassification) {
		return this;
	}

    public String getCorrectClass() {
        return this.classification;
    }

}
