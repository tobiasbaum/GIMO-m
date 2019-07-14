package de.unihannover.reviews.mining.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResultData {

    private final RecordSet aggregated;
    private final Map<ChangePartId, Integer> startIndices;
    private final Map<ChangePartId, Integer> endIndices;
    private final int strategyIdx;
    private final int meanIdx;

    public ResultData(RecordSet aggregated) {
        this.aggregated = aggregated;

        this.startIndices = new HashMap<>();
        this.endIndices = new HashMap<>();
        for (int i = 0; i < aggregated.getRecords().length; i++) {
            final Record r = aggregated.getRecords()[i];
            if (!this.startIndices.containsKey(r.getId())) {
                this.startIndices.put(r.getId(), i);
            }
            this.endIndices.put(r.getId(), i);
        }

        this.strategyIdx = aggregated.getScheme().getStrIndex("strategy");
        int firstMean = -1;
        for (final String column : aggregated.getScheme().getColumnNames()) {
            if (column.endsWith(".trMean")) {
                firstMean = aggregated.getScheme().getNumIndex(column);
                break;
            }
        }
        this.meanIdx = firstMean;
    }

    public double getDiffToBest(ChangePartId id, String strategy) {
        final int startIndex = this.startIndices.get(id);
        final int endIndex = this.endIndices.get(id);
        double best = Double.MIN_VALUE;
        double val = Double.NaN;
        for (int i = startIndex; i <= endIndex; i++) {
            final Record r = this.aggregated.getRecords()[i];
            final double cur = r.getValueDbl(this.meanIdx);
            best = Math.max(best, cur);
            if (r.getValueStr(this.strategyIdx).equals(strategy)) {
                val = cur;
            }
        }
        return best - val;
    }

    public List<String> getBest(ChangePartId id) {
        final int startIndex = this.startIndices.get(id);
        final int endIndex = this.endIndices.get(id);
        double best = Double.MIN_VALUE;
        for (int i = startIndex; i <= endIndex; i++) {
            final Record r = this.aggregated.getRecords()[i];
            final double cur = r.getValueDbl(this.meanIdx);
            best = Math.max(best, cur);
        }

        final List<String> ret = new ArrayList<>();
        for (int i = startIndex; i <= endIndex; i++) {
            final Record r = this.aggregated.getRecords()[i];
            final double cur = r.getValueDbl(this.meanIdx);
            if (cur == best) {
                ret.add(r.getValueStr(this.strategyIdx));
            }
        }
        return ret;
    }

    public List<String> getAllStrategies() {
        return this.aggregated.getPossibleStringValues(this.strategyIdx);
    }

}
