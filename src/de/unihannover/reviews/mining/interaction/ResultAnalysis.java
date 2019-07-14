package de.unihannover.reviews.mining.interaction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import de.unihannover.reviews.mining.common.Record;
import de.unihannover.reviews.mining.common.RecordScheme;
import de.unihannover.reviews.mining.common.RecordSet;
import de.unihannover.reviews.mining.common.Util;
import de.unihannover.reviews.miningInputCreation.TriggerClassification;
import de.unihannover.reviews.predictionDataPreparation.Multimap;

public class ResultAnalysis {

    public static RecordSet addResultColumns(RecordSet records, String resultsFile) throws IOException {
        final RecordSet results = RecordSet.loadCsv(resultsFile);
        final RecordSet aggregated = aggregate(results);
        return addColumnsForBestAndWorstStrategies(records, aggregated);
    }

    private static RecordSet aggregate(RecordSet results) {
        final List<String> aggregateColumns = new ArrayList<>();
        for (final String resultColumn : results.getScheme().getNumericColumnNames()) {
            if (resultColumn.equals("id")) {
                aggregateColumns.add(resultColumn);
            } else if (resultColumn.equals("seed")) {
                continue;
            } else {
                aggregateColumns.add(resultColumn + ".min");
                aggregateColumns.add(resultColumn + ".max");
                aggregateColumns.add(resultColumn + ".trMean");
            }
        }
        final RecordScheme aggregateScheme = new RecordScheme(aggregateColumns, Collections.singletonList("strategy"));
        final int idColumn = results.getScheme().getNumIndex("id");
        final int strategyColumn = results.getScheme().getStrIndex("strategy");
        double curId = -1;
        final Multimap<String, Record> recordsPerStrategy = new Multimap<>();
        final List<Record> aggregateRecords = new ArrayList<>();
        for (final Record r : results.getRecords()) {
            final double id = r.getValueDbl(idColumn);
            if (id != curId) {
                if (!recordsPerStrategy.isEmpty()) {
                    determineAggregateRecords(results, idColumn, recordsPerStrategy, aggregateRecords);
                    recordsPerStrategy.clear();
                }
                curId = id;
            }
            recordsPerStrategy.add(r.getValueStr(strategyColumn), r);
        }
        if (!recordsPerStrategy.isEmpty()) {
            determineAggregateRecords(results, idColumn, recordsPerStrategy, aggregateRecords);
        }
        return new RecordSet(aggregateScheme, aggregateRecords.toArray(new Record[aggregateRecords.size()]));
    }

    private static void determineAggregateRecords(RecordSet results, final int idColumn,
                    final Multimap<String, Record> recordsPerStrategy, final List<Record> aggregateRecords) {
        for (final String strategy : recordsPerStrategy.keySet()) {
            final Record firstRecord = recordsPerStrategy.get(strategy).get(0);
            final List<Double> numericValues = new ArrayList<>();
            for (final String resultColumn : results.getScheme().getNumericColumnNames()) {
                if (resultColumn.equals("id")) {
                    numericValues.add(firstRecord.getValueDbl(idColumn));
                } else if (resultColumn.equals("seed")) {
                    continue;
                } else {
                    final List<Double> values = extractValues(recordsPerStrategy.get(strategy), results.getScheme().getNumIndex(resultColumn));
                    numericValues.add(Collections.min(values));
                    numericValues.add(Collections.max(values));
                    numericValues.add(Util.trimmedMeanDbl(values));
                }
            }
            aggregateRecords.add(new Record(
                            firstRecord.getId(),
                            numericValues,
                            Collections.singletonList(strategy),
                            TriggerClassification.CAN_BE));
        }
    }

    private static List<Double> extractValues(List<Record> list, int numIndex) {
        final List<Double> ret = new ArrayList<>();
        for (final Record r : list) {
            ret.add(r.getValueDbl(numIndex));
        }
        return ret;
    }

    private static final class StrategyWithResult {
        String name;
        double result;

        public StrategyWithResult(String name, double result) {
            this.name = name;
            this.result = result;
        }
    }

    private static RecordSet addColumnsForBestAndWorstStrategies(RecordSet records, RecordSet aggregated) {
        RecordSet ret = records;
        for (final String col : aggregated.getScheme().getNumericColumnNames()) {
            if (col.endsWith(".trMean")) {
                ret = addColumnsForBestAndWorstStrategies(ret, aggregated, col);
            }
        }
        return ret;
    }

    private static RecordSet addColumnsForBestAndWorstStrategies(RecordSet ret, RecordSet aggregated, String col) {
        final Multimap<Double, StrategyWithResult> strategiesPerId = new Multimap<>();
        final int idIndex = aggregated.getScheme().getNumIndex("id");
        final int strategyIndex = aggregated.getScheme().getStrIndex("strategy");
        final int colIndex = aggregated.getScheme().getNumIndex(col);
        for (final Record r : aggregated.getRecords()) {
            strategiesPerId.add(r.getValueDbl(idIndex), new StrategyWithResult(r.getValueStr(strategyIndex), r.getValueDbl(colIndex)));
        }

        final int idIndexResult = ret.getScheme().getNumIndex("id");
        ret = RecordSet.addColumnStr(ret, "largeStratFor." + col, (rs, r) -> determineLargest(strategiesPerId.get(r.getValueDbl(idIndexResult))));
        ret = RecordSet.addColumnStr(ret, "smallStratFor." + col, (rs, r) -> determineSmallest(strategiesPerId.get(r.getValueDbl(idIndexResult))));
        return ret;
    }

    private static String determineLargest(List<StrategyWithResult> list) {
        final double max = list.stream().mapToDouble((sr) -> sr.result).max().getAsDouble();
        return determineWithValue(list, max);
    }

    private static String determineSmallest(List<StrategyWithResult> list) {
        final double min = list.stream().mapToDouble((sr) -> sr.result).min().getAsDouble();
        return determineWithValue(list, min);
    }

    private static String determineWithValue(List<StrategyWithResult> list, double value) {
        return list.stream()
                        .filter((rs) -> rs.result == value)
                        .map((rs) -> rs.name)
                        .sorted()
                        .collect(Collectors.joining(","))
                        .intern();
    }

}
