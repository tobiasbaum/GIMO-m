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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;

import de.unihannover.gimo_m.util.Util;

public final class RecordSet {
    private static final String CLASSIFICATION_COLUMN_NAME = "classification";

    private static final String NA = "?";

	private final RecordScheme scheme;
    private final Record[] records;

    private final double[][] numericSplitValues;
    private final String[][] stringValues;

    public RecordSet(RecordScheme scheme, Record[] records) {
        this.scheme = scheme;
        this.records = records;
        this.numericSplitValues = new double[scheme.getNumericColumnCount()][];
        for (int numericColumnIndex = 0; numericColumnIndex < scheme.getNumericColumnCount(); numericColumnIndex++) {
            this.numericSplitValues[numericColumnIndex] = this.determineNumericSplitValues(numericColumnIndex);
        }
        this.stringValues = new String[scheme.getStringColumnCount()][];
        for (int stringColumnIndex = 0; stringColumnIndex < scheme.getStringColumnCount(); stringColumnIndex++) {
            this.stringValues[stringColumnIndex] = this.extractStringValues(stringColumnIndex);
        }
    }

    private double[] determineNumericSplitValues(int columnIndex) {
        final TreeMap<Double, String> sortedMap = new TreeMap<>();
        for (final Record r : this.records) {
            final Double val = r.getValueDbl(columnIndex);
            if (val.isNaN()) {
                continue;
            }
            final String oldClassification = sortedMap.get(val);
            if (oldClassification == null) {
                sortedMap.put(val, r.getCorrectClass());
            } else if (!oldClassification.equals(r.getCorrectClass())) {
                //conflicting values have to be handled specially
                sortedMap.put(val, "");
            }
        }

        if (sortedMap.isEmpty()) {
            return new double[0];
        }

        final Iterator<Entry<Double, String>> iter = sortedMap.entrySet().iterator();
        Entry<Double, String> prev = iter.next();
        final List<Double> splitValues = new ArrayList<>();
        while (iter.hasNext()) {
            final Entry<Double, String> cur = iter.next();
            if (!cur.getValue().equals(prev.getValue()) || cur.getValue().isEmpty()) {
                splitValues.add(Util.determineSplitPointWithFewDigits(prev.getKey(), cur.getKey()));
            }
            prev = cur;
        }
        return Record.toArray(splitValues);
    }

    private String[] extractStringValues(int columnIndex) {
        final Set<String> values = new TreeSet<>();
        for (final Record r : this.records) {
            final String valueStr = r.getValueStr(columnIndex);
            if (valueStr != null) {
                values.add(valueStr);
            }
        }
        return values.toArray(new String[values.size()]);
    }


    private int getRandomColumn(Random random) {
        return random.nextInt(this.scheme.getAllColumnCount());
    }

    private String getRandomColumnValueStr(Random random, int absoluteColumnIndex) {
        final String[] vals = this.stringValues[this.scheme.toStringIndex(absoluteColumnIndex)];
        return vals[random.nextInt(vals.length)];
    }

    private double getRandomSplitValue(Random random, int absoluteColumnIndex) {
        final double[] vals = this.numericSplitValues[this.scheme.toNumericIndex(absoluteColumnIndex)];
        return vals.length == 0 ? 0.0 : vals[random.nextInt(vals.length)];
    }

    public double getSplitPointAbove(int numericColumnIndex, double value) {
        final double[] vals = this.numericSplitValues[numericColumnIndex];
        final int i = Arrays.binarySearch(vals, value);
        if (i >= 0) {
            return i + 1 < vals.length ? vals[i + 1] : vals[i];
        } else {
            final int insertionPoint = -(i + 1);
            return insertionPoint < vals.length ? vals[insertionPoint] : value;
        }
    }

    public double getSplitPointBelow(int numericColumnIndex, double value) {
        final double[] vals = this.numericSplitValues[numericColumnIndex];
        final int i = Arrays.binarySearch(vals, value);
        if (i >= 0) {
            return i > 0 ? vals[i - 1] : vals[i];
        } else {
            final int insertionPoint = -(i + 1);
            return insertionPoint > 0 ? vals[insertionPoint - 1] : value;
        }
    }

    private boolean isNumeric(int absoluteColumnIndex) {
        return this.scheme.isNumeric(absoluteColumnIndex);
    }

    public RecordScheme getScheme() {
        return this.scheme;
    }

    public Record[] getRecords() {
        return this.records;
    }

    public static RecordSet loadCsv(String filename) throws IOException {
        final RecordScheme scheme = determineScheme(filename);

        final List<Record> records = new ArrayList<>();
        try (BufferedReader r = open(filename)) {
            final String header = r.readLine();
            final List<String> columns = Arrays.asList(toValidColumnNames(header.split(";")));

            final Map<String, Integer> indices = new HashMap<>();
            for (int i = 0; i < columns.size(); i++) {
            	indices.put(columns.get(i), i);
            }

            String line;
            int recordNumber = 0;
            try {
	            while ((line = r.readLine()) != null) {
	                final String[] parts = line.split(";");
	                final String classification = parts[indices.get(CLASSIFICATION_COLUMN_NAME)].intern();
	                final List<Double> numericValues = new ArrayList<>();
	                for (int i = 0; i < scheme.getNumericColumnCount(); i++) {
	                    numericValues.add(parseNumber(parts[indices.get(scheme.getNumName(i))]));
	                }
	                final List<String> stringValues = new ArrayList<>();
	                for (int i = 0; i < scheme.getStringColumnCount(); i++) {
	                    stringValues.add(parseStr(parts[indices.get(scheme.getStrName(i))]));
	                }
	                records.add(new Record(recordNumber, numericValues, stringValues, classification));
	                recordNumber++;
	            }
            } catch (final Exception e) {
            	throw new RuntimeException("Problem at record " + (recordNumber + 1), e);
            }
        }
        return new RecordSet(scheme, records.toArray(new Record[records.size()]));
    }

	private static BufferedReader open(String filename) throws IOException {
		return new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF-8"));
	}

    private static String parseStr(String string) {
        return string.equals(NA) ? null : string.intern();
    }

    private static double parseNumber(String string) {
        return string.equals(NA) ? Double.NaN : Double.parseDouble(string);
    }

    private static RecordScheme determineScheme(String filename) throws IOException {
        try (BufferedReader r = open(filename)) {
            final String header = r.readLine();
            final String[] names = toValidColumnNames(header.split(";"));
            final List<String> missingNames = new ArrayList<>(Arrays.asList(names));
            removeReservedColumnNames(missingNames);

            final List<String> numericColumns = new ArrayList<>();
            final List<String> stringColumns = new ArrayList<>();
            while (!missingNames.isEmpty()) {
	            final String firstValues = r.readLine();
	            final String[] values = firstValues.split(";");
	            if (names.length != values.length) {
	                throw new RuntimeException("invalid header or values: header length "
	                		+ header.length() + " vs values length " + values.length + ":\n"
	                		+ header + "\n" + firstValues);
	            }
	            for (int i = 0; i < names.length; i++) {
	            	if (NA.equals(values[i])) {
	            		continue;
	            	}
	                if (!missingNames.contains(names[i])) {
	                    continue;
	                }
	                try {
	                    Double.parseDouble(values[i]);
	                    numericColumns.add(names[i]);
	                } catch (final NumberFormatException e) {
	                    stringColumns.add(names[i]);
	                }
	                missingNames.remove(names[i]);
	            }
            }
            return new RecordScheme(numericColumns, stringColumns);
        }
    }

    private static String[] toValidColumnNames(String[] split) {
        for (int i = 0; i < split.length; i++) {
            split[i] = RuleSetParser.toValidColumnName(split[i]);
        }
        return split;
    }

    private static void removeReservedColumnNames(List<String> missingNames) {
        missingNames.remove(CLASSIFICATION_COLUMN_NAME);
	}

    public List<String> getPossibleStringValues(int stringColumnIndex) {
        return Arrays.asList(this.stringValues[stringColumnIndex]);
    }

    public SimpleRule createRandomSimpleRule(Random random) {
        return this.createRandomSimpleRule(random, this.getRandomColumn(random));
    }

    private SimpleRule createRandomSimpleRule(Random random, int column) {
        if (this.isNumeric(column)) {
            return random.nextBoolean()
                    ? new Leq(this.getScheme(), column, this.getRandomSplitValue(random, column))
                    : new Geq(this.getScheme(), column, this.getRandomSplitValue(random, column));
        } else {
            return random.nextBoolean()
                    ? new Equals(this.getScheme(), column, this.getRandomColumnValueStr(random, column))
                    : new NotEquals(this.getScheme(), column, this.getRandomColumnValueStr(random, column));
        }
    }

	public RecordSet copyWithout(Predicate<Record> pred) {
		final List<Record> newRecords = new ArrayList<>(this.records.length);
		for (final Record r : this.records) {
			if (!pred.test(r)) {
				newRecords.add(r);
			}
		}
		return new RecordSet(this.scheme, newRecords.toArray(new Record[newRecords.size()]));
	}

	public static RecordSet addColumn(RecordSet old, String columnName, ToDoubleBiFunction<RecordScheme, Record> function) {
        final RecordScheme newScheme = RecordScheme.addColumn(old.getScheme(), columnName);

        final Record[] oldRecords = old.getRecords();
        final Record[] newRecords = new Record[oldRecords.length];
        for (int i = 0; i < oldRecords.length; i++) {
            newRecords[i] = addNumber(old.getScheme(), oldRecords[i], function.applyAsDouble(old.getScheme(), oldRecords[i]));
        }

        return new RecordSet(newScheme, newRecords);
	}

    private static Record addNumber(RecordScheme oldScheme, Record record, double valueToAdd) {
        final List<Double> newNumbers = new ArrayList<>();
        for (int i = 0; i < oldScheme.getNumericColumnCount(); i++) {
            newNumbers.add(record.getValueDbl(i));
        }
        newNumbers.add(valueToAdd);
        final List<String> strings = new ArrayList<>();
        for (int i = 0; i < oldScheme.getStringColumnCount(); i++) {
            strings.add(record.getValueStr(i));
        }
        return new Record(record.getId(), newNumbers, strings, record.getCorrectClass());
    }

}
