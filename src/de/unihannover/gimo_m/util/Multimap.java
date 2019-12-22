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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Multimap<K, V> {

    private final Map<K, List<V>> backingMap = new LinkedHashMap<>();

    public void add(K key, V value) {
        List<V> list = this.backingMap.get(key);
        if (list == null) {
            list = new ArrayList<>();
            this.backingMap.put(key, list);
        }
        list.add(value);
    }

    public List<V> get(K key) {
        final List<V> list = this.backingMap.get(key);
        return list == null ? Collections.emptyList() : list;
    }

    public void addAll(K key, Collection<V> values) {
        for (final V value : values) {
            this.add(key, value);
        }
    }

    public Set<K> keySet() {
        return this.backingMap.keySet();
    }

    @Override
    public String toString() {
        return this.backingMap.toString();
    }

    public boolean isEmpty() {
        return this.backingMap.isEmpty();
    }

    public void removeAll(K key, Collection<? super V> values) {
        final List<V> list = this.backingMap.get(key);
        if (list != null) {
            list.removeAll(values);
        }
    }

}
