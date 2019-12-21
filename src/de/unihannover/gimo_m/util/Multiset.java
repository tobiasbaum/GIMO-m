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
package de.unihannover.gimo_m.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

public class Multiset<T> {

    private static final class Counter {
        private int count;

        @Override
        public String toString() {
            return Integer.toString(this.count);
        }
    }

    private final Map<T, Counter> items;

    public Multiset() {
        this(new LinkedHashMap<>());
    }

    private Multiset(Map<T, Counter> backingMap) {
        this.items = backingMap;
    }

    public static<T> Multiset<T> emptySet() {
        return new Multiset<>();
    }

    public static<T> Multiset<T> singleton(T value) {
        final Multiset<T> ret = new Multiset<>();
        ret.add(value);
        return ret;
    }

    public int get(T value) {
        final Counter c = this.items.get(value);
        return c == null ? 0 : c.count;
    }

    public boolean contains(T value) {
        return this.items.containsKey(value);
    }

    public static<T> Multiset<T> createOrdered() {
        return new Multiset<>(new TreeMap<>());
    }

    public void add(T value) {
        Counter c = this.items.get(value);
        if (c == null) {
            c = new Counter();
            this.items.put(value, c);
        }
        c.count++;
    }

    public void add(T value, int count) {
        Counter c = this.items.get(value);
        if (c == null) {
            c = new Counter();
            this.items.put(value, c);
        }
        c.count += count;
    }

    public void addAll(Multiset<T> other) {
        for (final Entry<T, Counter> e : other.items.entrySet()) {
            this.add(e.getKey(), e.getValue().count);
        }
    }

    public Set<T> keySet() {
        return this.items.keySet();
    }

    public int sum() {
        int ret = 0;
        for (final Counter c : this.items.values()) {
            ret += c.count;
        }
        return ret;
    }

    @Override
    public String toString() {
        return this.items.toString();
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    public int size() {
        return this.items.size();
    }

    public List<T> getPrefixOfMostCommon(int maxSize) {
        final List<T> distinctValues = new ArrayList<>(this.keySet());
        distinctValues.sort((T v1, T v2) -> {
            return Integer.compare(this.get(v2), this.get(v1));
        });
        return distinctValues.size() < maxSize ? distinctValues : distinctValues.subList(0, maxSize);
    }

}
