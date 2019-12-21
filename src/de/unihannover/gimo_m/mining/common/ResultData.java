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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;

public class ResultData {

    private final Map<String, Integer> classIndices = new LinkedHashMap<>();

    public ResultData(RecordSet aggregated) {
        final TreeSet<String> classes = new TreeSet<>();
        for (final Record r : aggregated.getRecords()) {
            classes.add(r.getCorrectClass());
        }

        int index = 0;
        for (final String className : classes) {
            this.classIndices.put(className, index++);
        }
    }

    public Integer getClassIndex(String className) {
        return this.classIndices.get(className);
    }

    public int getClassCount() {
        return this.classIndices.size();
    }

    public Collection<String> getAllClasses() {
        return this.classIndices.keySet();
    }

    public String getClassName(int classIndex) {
        for (final Entry<String, Integer> e : this.classIndices.entrySet()) {
            if (e.getValue().intValue() == classIndex) {
                return e.getKey();
            }
        }
        throw new AssertionError("Found no class for index " + classIndex);
    }

}
