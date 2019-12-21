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
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class RawEvaluationResult {

    private final List<Double> diffsToBest;

    private RawEvaluationResult(List<Double> diffsToBest) {
        this.diffsToBest = diffsToBest;
    }

    public static RawEvaluationResult create(
                    Function<Record, String> pred, List<Record> records, ResultData aggregates) {
        final List<Double> diffsToBest = new ArrayList<>(records.size());
        for (final Record r : records) {
            final String chosen = pred.apply(r);
            diffsToBest.add(aggregates.getDiffToBest(r.getId(), chosen));
        }
        return new RawEvaluationResult(diffsToBest);
    }

    public int getSuboptimalChosenCount() {
        int cnt = 0;
        for (final double d : this.diffsToBest) {
            if (d > 0.0) {
                cnt++;
            }
        }
        return cnt;
    }

    public double getLostValueMean() {
        double sum = 0.0;
        for (final double d : this.diffsToBest) {
            sum += d;
        }
        return sum / this.diffsToBest.size();
    }

    public double getLostValueTrimmedMean() {
        return Util.trimmedMeanDbl(this.diffsToBest);
    }

    public double getMaxLostValue() {
        return this.diffsToBest.stream().max(Comparator.naturalOrder()).get();
    }

}
