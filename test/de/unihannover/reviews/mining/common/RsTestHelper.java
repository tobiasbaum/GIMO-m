package de.unihannover.reviews.mining.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import de.unihannover.reviews.miningInputCreation.RemarkTriggerMap;
import de.unihannover.reviews.miningInputCreation.TriggerClassification;

public class RsTestHelper {

    public static RecordSet oneNumericColumn(List<Double> noValues, List<Double> mustValues) {
        final RecordScheme scheme = new RecordScheme(Collections.singletonList("numCol"), Collections.emptyList());
        final List<Record> records = new ArrayList<>();
        int count = 0;
        for (final Double d : noValues) {
            records.add(new Record(new ChangePartId("T" + (count++), "F", "C"), Collections.singletonList(d), Collections.emptyList(), TriggerClassification.NO_TRIGGER));
        }
        for (final Double d : mustValues) {
            records.add(new Record(new ChangePartId("T" + (count++), "F", "C"), Collections.singletonList(d), Collections.emptyList(), TriggerClassification.MUST_BE));
        }
        return new RecordSet(scheme, records.toArray(new Record[records.size()]));
    }

    public static RecordSet twoNumericColumns(List<Double> noValues, List<Double> mustValues) {
        final RecordScheme scheme = new RecordScheme(Arrays.asList("numCol1", "numCol2"), Collections.emptyList());
        final List<Record> records = new ArrayList<>();
        int count = 0;
        for (int i = 0; i < noValues.size(); i += 2) {
            records.add(new Record(new ChangePartId("T" + (count++), "F", "C"), Arrays.asList(noValues.get(i), noValues.get(i + 1)), Collections.emptyList(), TriggerClassification.NO_TRIGGER));
        }
        for (int i = 0; i < mustValues.size(); i += 2) {
            records.add(new Record(new ChangePartId("T" + (count++), "F", "C"), Arrays.asList(mustValues.get(i), mustValues.get(i + 1)), Collections.emptyList(), TriggerClassification.MUST_BE));
        }
        return new RecordSet(scheme, records.toArray(new Record[records.size()]));
    }

    public static RecordSet oneStringColumn(List<String> noValues, List<String> mustValues) {
        final RecordScheme scheme = new RecordScheme(Collections.emptyList(), Collections.singletonList("strCol"));
        final List<Record> records = new ArrayList<>();
        int count = 0;
        for (final String d : noValues) {
            records.add(new Record(new ChangePartId("T" + (count++), "F", "C"), Collections.emptyList(), Collections.singletonList(d), TriggerClassification.NO_TRIGGER));
        }
        for (final String d : mustValues) {
            records.add(new Record(new ChangePartId("T" + (count++), "F", "C"), Collections.emptyList(), Collections.singletonList(d), TriggerClassification.MUST_BE));
        }
        return new RecordSet(scheme, records.toArray(new Record[records.size()]));
    }

    public static RecordSet twoStringColumns(List<String> noValues, List<String> mustValues) {
        final RecordScheme scheme = new RecordScheme(Collections.emptyList(), Arrays.asList("strCol1", "strCol2"));
        final List<Record> records = new ArrayList<>();
        int count = 0;
        for (int i = 0; i < noValues.size(); i += 2) {
            records.add(new Record(new ChangePartId("T" + (count++), "F", "C"), Collections.emptyList(), Arrays.asList(noValues.get(i), noValues.get(i + 1)), TriggerClassification.NO_TRIGGER));
        }
        for (int i = 0; i < mustValues.size(); i += 2) {
            records.add(new Record(new ChangePartId("T" + (count++), "F", "C"), Collections.emptyList(), Arrays.asList(mustValues.get(i), mustValues.get(i + 1)), TriggerClassification.MUST_BE));
        }
        return new RecordSet(scheme, records.toArray(new Record[records.size()]));
    }

    public static RecordSet createRecords(
    		int count,
    		List<Function<Integer, Double>> numericColumns,
    		List<Function<Integer, String>> stringColumns,
    		Function<Integer, TriggerClassification> classification) {
        final RecordScheme scheme = new RecordScheme(createColumnNames("numCol", numericColumns), createColumnNames("strCol", stringColumns));
        final List<Record> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
        	records.add(new Record(new ChangePartId("T" + i, "F", "C"), evalAll(i, numericColumns), evalAll(i, stringColumns), classification.apply(i)));
        }
        return new RecordSet(scheme, records.toArray(new Record[records.size()]));
    }

    private static List<String> createColumnNames(String prefix, List<?> columns) {
    	final List<String> ret = new ArrayList<>();
    	for (int i = 0; i < columns.size(); i++) {
    		ret.add(prefix + i);
    	}
		return ret;
	}

    private static<T> List<T> evalAll(int row, List<Function<Integer, T>> functions) {
    	final List<T> ret = new ArrayList<>();
    	for (final Function<Integer, T> f : functions) {
    		ret.add(f.apply(row));
    	}
		return ret;
	}

	public static RemarkTriggerMap createTriggerMap(RecordSet rs) {
        final RemarkTriggerMap ret = new RemarkTriggerMap();
        for (final Record r : rs.getRecords()) {
            switch (r.getClassification()) {
            case MUST_BE:
                final String mapFileLine =
                    r.getId().getCommit() + "Rev," +
                    r.getId().getFile() + ";" +
                    r.getId().getTicket() + ";" +
                    r.getId().getCommit() + "," +
                    r.getId().getFile();
                ret.add(mapFileLine);
                break;
            case NO_TRIGGER:
                break;
            case CAN_BE:
                throw new AssertionError("only MUST und NO allowed for creation of dummy map");
            }
        }
        return ret;
    }

}
