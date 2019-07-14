package de.unihannover.reviews.mining.common;

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

    public int getNumIndex(String name) {
        final int ret = this.numericColumns.indexOf(name);
        if (ret < 0) {
            throw new RuntimeException("Invalid numeric colum name " + name + ". Known columns: " + this.getNumericColumnNames());
        }
        return ret;
    }

    public int getStrIndex(String name) {
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

    public static RecordScheme addColumnStr(RecordScheme oldScheme, String column) {
        if (oldScheme.getColumnNames().contains(column)) {
            throw new RuntimeException("Column " + column + " already exists and cannot be added.");
        }

        final List<String> stringColumns = new ArrayList<>(oldScheme.getStringColumnNames());
        stringColumns.add(column);
        return new RecordScheme(oldScheme.getNumericColumnNames(), stringColumns);
    }

}
