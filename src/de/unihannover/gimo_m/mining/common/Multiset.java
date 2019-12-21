package de.unihannover.gimo_m.mining.common;

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
