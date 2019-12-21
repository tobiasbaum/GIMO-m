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
package de.unihannover.gimo_m.miningInputCreation;

import java.util.Arrays;
import java.util.Comparator;

/**
 * For performance and space reasons, we use array in the remark trigger map.
 * To help avoid too much time wasted during building the map, the arrays are built with a length doubling approach
 * similar to ArrayList. This class implements this approach.
 */
public class FlyweightArrayList {

    private static final Object SEARCH_DUMMY = new Object();

    private static final Comparator<Object> INSERT_POINT_COMPARATOR = new Comparator<Object>() {
        @Override
        public int compare(Object o1, Object o2) {
            final int t1 = this.toTypeNumber(o1);
            final int t2 = this.toTypeNumber(o2);
            return Integer.compare(t1, t2);
        }

        private int toTypeNumber(Object o) {
            if (o == null) {
                return 2;
            } else if (o == SEARCH_DUMMY) {
                return 1;
            } else {
                return 0;
            }
        }

    };

    public static<T> T[] add(T[] list, T toAdd) {
        final int insertPoint = determineInsertPoint(list);
        T[] arr;
        if (insertPoint < list.length) {
            arr = list;
        } else {
            arr = Arrays.copyOf(list, Math.max(1, list.length * 2));
        }
        arr[insertPoint] = toAdd;
        return arr;
    }

    public static<T> T[] shrink(T[] list) {
        final int insertPoint = determineInsertPoint(list);
        if (insertPoint < list.length) {
            return Arrays.copyOfRange(list, 0, insertPoint);
        } else {
            return list;
        }
    }

    private static<T> int determineInsertPoint(T[] list) {
        final int pos = Arrays.binarySearch(list, SEARCH_DUMMY, INSERT_POINT_COMPARATOR);
        return -(pos + 1);
    }

}
