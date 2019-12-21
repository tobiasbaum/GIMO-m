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