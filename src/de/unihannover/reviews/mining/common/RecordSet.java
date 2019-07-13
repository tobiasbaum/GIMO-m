package de.unihannover.reviews.mining.common;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

import de.unihannover.reviews.miningInputCreation.RemarkTriggerMap;
import de.unihannover.reviews.miningInputCreation.RemarkTriggerMap.TicketInfoProvider;
import de.unihannover.reviews.miningInputCreation.TriggerClassification;

public final class RecordSet {
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
        final TreeMap<Double, TriggerClassification> sortedMap = new TreeMap<>();
        for (final Record r : this.records) {
            final Double val = r.getValueDbl(columnIndex);
            if (val.isNaN()) {
                continue;
            }
            final TriggerClassification oldClassification = sortedMap.get(val);
            if (oldClassification == null) {
                sortedMap.put(val, r.getClassification());
            } else if (oldClassification != r.getClassification()) {
                //conflicting values behave the same as CAN_BE
                sortedMap.put(val, TriggerClassification.CAN_BE);
            }
        }

        if (sortedMap.isEmpty()) {
            return new double[0];
        }

        final Iterator<Entry<Double, TriggerClassification>> iter = sortedMap.entrySet().iterator();
        Entry<Double, TriggerClassification> prev = iter.next();
        final List<Double> splitValues = new ArrayList<>();
        while (iter.hasNext()) {
            final Entry<Double, TriggerClassification> cur = iter.next();
            if (cur.getValue() != prev.getValue()
                    || cur.getValue() == TriggerClassification.CAN_BE) {
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


    public int getRandomColumn(Random random) {
        return random.nextInt(this.scheme.getAllColumnCount());
    }

    public String getRandomColumnValueStr(Random random, int absoluteColumnIndex) {
        final String[] vals = this.stringValues[this.scheme.toStringIndex(absoluteColumnIndex)];
        return vals[random.nextInt(vals.length)];
    }

    public String getRandomColumnValueStr(Random random, int absoluteColumnIndex, Set<String> exclusions) {
        final String[] vals = this.stringValues[this.scheme.toStringIndex(absoluteColumnIndex)];
        final List<String> possibleValues = new ArrayList<>(Arrays.asList(vals));
        possibleValues.removeAll(exclusions);
        if (possibleValues.isEmpty()) {
            return null;
        }
        return possibleValues.get(random.nextInt(possibleValues.size()));
    }

    public double getRandomSplitValue(Random random, int absoluteColumnIndex) {
        final double[] vals = this.numericSplitValues[this.scheme.toNumericIndex(absoluteColumnIndex)];
        return vals[random.nextInt(vals.length)];
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

    public boolean isNumeric(int absoluteColumnIndex) {
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
            final List<String> columns = Arrays.asList(header.split(";"));

            String line;
            while ((line = r.readLine()) != null) {
                final String[] parts = line.split(";");
                final String lineFrom = "-1";
                final ChangePartId id;
                if (lineFrom.equals("-1")) {
                    id = new ChangePartId(
                                parts[columns.indexOf("id")],
                                "file",
                                "commit");
                } else {
                    id = new ChangePartId(
                                    parts[columns.indexOf("ticket")],
                                    parts[columns.indexOf("file")],
                                    parts[columns.indexOf("commit")],
                                    Integer.parseInt(parts[columns.indexOf("lineFrom")]),
                                    Integer.parseInt(parts[columns.indexOf("lineTo")]));
                }
                final TriggerClassification classification = TriggerClassification.CAN_BE;
                final List<Double> numericValues = new ArrayList<>();
                for (int i = 0; i < scheme.getNumericColumnCount(); i++) {
                    numericValues.add(parseNumber(parts[columns.indexOf(scheme.getNumName(i))]));
                }
                final List<String> stringValues = new ArrayList<>();
                for (int i = 0; i < scheme.getStringColumnCount(); i++) {
                    stringValues.add(parseStr(parts[columns.indexOf(scheme.getStrName(i))]));
                }
                records.add(new Record(id, numericValues, stringValues, classification));
            }
        }
        return new RecordSet(scheme, records.toArray(new Record[records.size()]));
    }

	private static BufferedReader open(String filename) throws IOException {
		return new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF-8"));
	}

    private static String parseStr(String string) {
        return string.equals(NA) ? null : string;
    }

    private static double parseNumber(String string) {
        return string.equals(NA) ? Double.NaN : Double.parseDouble(string);
    }

    private static RecordScheme determineScheme(String filename) throws IOException {
        try (BufferedReader r = open(filename)) {
            final String header = r.readLine();
            final String[] names = header.split(";");
            final List<String> missingNames = new ArrayList<>(Arrays.asList(names));
            removeReservedColumnNames(missingNames);

            final List<String> numericColumns = new ArrayList<>();
            final List<String> stringColumns = new ArrayList<>();
            while (!missingNames.isEmpty()) {
	            final String firstValues = r.readLine();
	            final String[] values = firstValues.split(";");
	            if (names.length != values.length) {
	                throw new RuntimeException("invalid header or values\n" + header + "\n" + firstValues);
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

    private static void removeReservedColumnNames(List<String> missingNames) {
        missingNames.remove("ticket");
        missingNames.remove("commit");
        missingNames.remove("file");
        missingNames.remove("lineFrom");
        missingNames.remove("lineTo");
        missingNames.remove("classification");
	}

    public List<String> getPossibleStringValues(int stringColumnIndex) {
        return Arrays.asList(this.stringValues[stringColumnIndex]);
    }

    public RecordSet[] split(Random random, int firstPart, int secondPart) {
        final Set<String> ticketKeys = new TreeSet<>();
        for (final Record r : this.records) {
            ticketKeys.add(r.getId().getTicket());
        }
        assert ticketKeys.size() > firstPart + secondPart;
        final List<String> shuffled = new ArrayList<>(ticketKeys);
        Collections.shuffle(shuffled, random);
        final int splitPoint = firstPart * shuffled.size() / (firstPart + secondPart);
        final Set<String> firstTickets = new HashSet<>(shuffled.subList(0, splitPoint));

        final List<Record> first = new ArrayList<>();
        final List<Record> second = new ArrayList<>();
        for (final Record r : this.records) {
            if (firstTickets.contains(r.getId().getTicket())) {
                first.add(r);
            } else {
                second.add(r);
            }
        }
        return new RecordSet[] {
                new RecordSet(this.scheme, first.toArray(new Record[first.size()])),
                new RecordSet(this.scheme, second.toArray(new Record[second.size()]))
        };
    }

    public SimpleRule createRandomSimpleRule(Random random) {
        return this.createRandomSimpleRule(random, this.getRandomColumn(random));
    }

    public SimpleRule createRandomSimpleRule(Random random, int column) {
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

	public RecordSet reclassify(RemarkTriggerMap newTriggerMap) {
		final Record[] newRecords = new Record[this.records.length];
		for (int i = 0; i < this.records.length; i++) {
			final TriggerClassification newClassification = this.classify(newTriggerMap, i);
			newRecords[i] = this.records[i].withClassification(newClassification);
		}
		return new RecordSet(this.scheme, newRecords);
	}

	private TriggerClassification classify(RemarkTriggerMap triggerMap, int recordIndex) {
		final ChangePartId id = this.records[recordIndex].getId();
		final String ticket = id.getTicket();
		final TicketInfoProvider infoProvider = new TicketInfoProvider() {
			@Override
			public boolean containsChangesOutside(String commit, String file) throws IOException {
				for (int i = recordIndex + 1; i < RecordSet.this.records.length; i++) {
					final ChangePartId curId = RecordSet.this.records[i].getId();
					if (!curId.getTicket().equals(ticket)) {
						break;
					}
					if (!curId.getFile().equals(id.getFile()) || !curId.getCommit().equals(id.getCommit())) {
						return true;
					}
				}
				for (int i = recordIndex - 1; i >= 0; i--) {
					final ChangePartId curId = RecordSet.this.records[i].getId();
					if (!curId.getTicket().equals(ticket)) {
						break;
					}
					if (!curId.getFile().equals(id.getFile()) || !curId.getCommit().equals(id.getCommit())) {
						return true;
					}
				}
				return false;
			}

			@Override
			public boolean containsChangesInFileOutside(String commit, String file, int lineFrom, int lineTo)
					throws IOException {
				for (int i = recordIndex + 1; i < RecordSet.this.records.length; i++) {
					final ChangePartId curId = RecordSet.this.records[i].getId();
					if (!curId.getTicket().equals(ticket)
						|| !curId.getFile().equals(id.getFile())
						|| !curId.getCommit().equals(id.getCommit())) {
						break;
					}
					if (!id.isLineGranularity() || id.getLineFrom() < lineFrom || id.getLineTo() > lineTo) {
						return true;
					}
				}
				for (int i = recordIndex - 1; i >= 0; i--) {
					final ChangePartId curId = RecordSet.this.records[i].getId();
					if (!curId.getTicket().equals(ticket)
						|| !curId.getFile().equals(id.getFile())
						|| !curId.getCommit().equals(id.getCommit())) {
						break;
					}
					if (!id.isLineGranularity() || id.getLineFrom() < lineFrom || id.getLineTo() > lineTo) {
						return true;
					}
				}
				return false;
			}
		};
		try {
			if (id.isLineGranularity()) {
				return triggerMap.getClassification(infoProvider, id.getTicket(), id.getCommit(), id.getFile(),
						id.getLineFrom(), id.getLineTo());
			} else {
				return triggerMap.getClassification(infoProvider, id.getTicket(), id.getCommit(), id.getFile());
			}
		} catch (final IOException e) {
			throw new RuntimeException("should not happen", e);
		}
	}

}
