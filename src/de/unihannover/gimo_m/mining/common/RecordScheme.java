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

import java.util.ArrayList;
import java.util.List;

public class RecordScheme {

    private final List<String> numericColumns;
    private final List<String> stringColumns;

    public RecordScheme(List<String> numericColumns, List<String> stringColumns) {
        this.numericColumns = numericColumns;
        this.stringColumns = stringColumns;
    }

    public int getNumericColumnCount() {
        return this.numericColumns.size();
    }

    public int getStringColumnCount() {
        return this.stringColumns.size();
    }

    public int getAllColumnCount() {
        return this.getNumericColumnCount() + this.getStringColumnCount();
    }

    public int toNumericIndex(int absoluteColumnIndex) {
        return absoluteColumnIndex;
    }

    public int toStringIndex(int absoluteColumnIndex) {
        return absoluteColumnIndex - this.getNumericColumnCount();
    }

    public boolean isNumeric(int absoluteColumnIndex) {
        return absoluteColumnIndex < this.getNumericColumnCount();
    }

    public String getName(int absoluteColumnIndex) {
        return this.isNumeric(absoluteColumnIndex)
                        ? this.numericColumns.get(this.toNumericIndex(absoluteColumnIndex))
                        : this.stringColumns.get(this.toStringIndex(absoluteColumnIndex));
    }

    public int getAbsIndex(String name) {
        if (this.numericColumns.contains(name)) {
            return this.getAbsIndexFromNum(this.getNumIndex(name));
        } else {
            return this.getAbsIndexFromStr(this.getStrIndex(name));
        }
    }

    public int getAbsIndexFromNum(int numericColumnIndex) {
        return numericColumnIndex;
    }

    public int getAbsIndexFromStr(int stringColumnIndex) {
        return this.numericColumns.size() + stringColumnIndex;
    }

    private int getNumIndex(String name) {
        final int ret = this.numericColumns.indexOf(name);
        if (ret < 0) {
            throw new RuntimeException("Invalid numeric colum name " + name + ". Known columns: " + this.getNumericColumnNames());
        }
        return ret;
    }

    private int getStrIndex(String name) {
        final int ret = this.stringColumns.indexOf(name);
        if (ret < 0) {
            throw new RuntimeException("Invalid string colum name " + name +  ". Known columns: " + this.getStringColumnNames());
        }
        return ret;
    }

    public String getNumName(int numericColumnIndex) {
        return this.numericColumns.get(numericColumnIndex);
    }

    public String getStrName(int stringColumnIndex) {
        return this.stringColumns.get(stringColumnIndex);
    }

    public List<String> getColumnNames() {
        final List<String> ret = new ArrayList<>();
        for (int i = 0; i < this.getAllColumnCount(); i++) {
            ret.add(this.getName(i));
        }
        return ret;
    }

    public List<String> getNumericColumnNames() {
    	return this.numericColumns;
    }

    public List<String> getStringColumnNames() {
    	return this.stringColumns;
    }

    public static RecordScheme addColumn(RecordScheme oldScheme, String column) {
        if (oldScheme.getColumnNames().contains(column)) {
            throw new RuntimeException("Column " + column + " already exists and cannot be added.");
        }

        final List<String> numericColumns = new ArrayList<>(oldScheme.getNumericColumnNames());
        numericColumns.add(column);
        return new RecordScheme(numericColumns, oldScheme.getStringColumnNames());
    }

}
