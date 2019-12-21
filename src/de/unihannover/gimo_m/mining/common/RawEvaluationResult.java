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
import java.util.function.Function;

public class RawEvaluationResult {

    private final int[][] confusionMatrix;

    private RawEvaluationResult(int[][] confusionMatrix) {
        this.confusionMatrix = confusionMatrix;
    }

    public static RawEvaluationResult create(
                    Function<Record, String> pred, List<Record> records, ResultData aggregates) {
        final int[][] confMatrix = new int[aggregates.getClassCount()][aggregates.getClassCount()];
        for (final Record r : records) {
            final int correctClassIndex = aggregates.getClassIndex(r.getCorrectClass());
            final Integer predictedClassIndex = aggregates.getClassIndex(pred.apply(r));
            if (predictedClassIndex != null) {
                confMatrix[correctClassIndex][predictedClassIndex]++;
            }
        }
        return new RawEvaluationResult(confMatrix);
    }

    public double[] toMinimizableVector(double complexity, double featureCount) {
        final int classCount = this.confusionMatrix.length;
        final double[] ret = new double[classCount * classCount + 2];
        int retIdx = 0;
        ret[retIdx++] = complexity;
        ret[retIdx++] = featureCount;
        for (int i = 0; i < classCount; i++) {
            final int[] arr = this.confusionMatrix[i];
            for (int j = 0; j < classCount; j++) {
                if (i == j) {
                    ret[retIdx++] = -arr[j];
                } else {
                    ret[retIdx++] = arr[j];
                }
            }
        }
        return ret;
    }

    public static List<TargetFunction> createTargetFunctions(ResultData resultData) {
        final int classCount = resultData.getClassCount();
        final List<TargetFunction> ret = new ArrayList<>();
        ret.add(new TargetFunction("complexity", (ValuedResult<?> r) -> r.getValue(0), "complexity of the rule set"));
        ret.add(new TargetFunction("featureCount", (ValuedResult<?> r) -> r.getValue(1), "number of used features in the rule set"));
        int retIdx = 2;
        final List<TargetFunction> allMis = new ArrayList<>();
        for (int i = 0; i < classCount; i++) {
            final String iName = resultData.getClassName(i);
            for (int j = 0; j < classCount; j++) {
                final int curRetIdx = retIdx;
                TargetFunction f;
                if (i == j) {
                    f = new TargetFunction(
                                    "corr_" + iName,
                                    (ValuedResult<?> r) -> r.getValue(curRetIdx),
                                    "Record count correctly classified as " + iName);
                } else {
                    final String jName = resultData.getClassName(j);
                    f = new TargetFunction(
                                    "mis_" + iName + "_as_" + jName,
                                    (ValuedResult<?> r) -> r.getValue(curRetIdx),
                                    "Record count misclassified " + iName + " as " + jName);
                    allMis.add(f);
                }
                ret.add(f);
                retIdx++;
            }
        }
        ret.add(0, new TargetFunction(
                        "totalMis",
                        (ValuedResult<?> r) -> sum(allMis, r),
                        "Total number of misclassifications"));
        return ret;
    }

    private static final double sum(List<TargetFunction> fs, ValuedResult<?> r) {
        double ret = 0.0;
        for (final TargetFunction f : fs) {
            ret += f.applyAsDouble(r);
        }
        return ret;
    }

}
