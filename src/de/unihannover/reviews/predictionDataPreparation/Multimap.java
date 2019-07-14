package de.unihannover.reviews.predictionDataPreparation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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

    public Set<V> getAsSet(K key) {
        return new LinkedHashSet<>(this.get(key));
    }

    public void addAll(Multimap<K, V> map) {
        for (final Entry<K, List<V>> e : map.backingMap.entrySet()) {
            for (final V v : e.getValue()) {
                this.add(e.getKey(), v);
            }
        }
    }

    public void addAll(K key, Collection<V> values) {
        for (final V value : values) {
            this.add(key, value);
        }
    }

    public Set<K> keySet() {
        return this.backingMap.keySet();
    }

    public boolean containsKey(K key) {
        final List<V> l = this.backingMap.get(key);
        return l != null && ! l.isEmpty();
    }

    public void removeAll(Collection<K> keysToRemove) {
        for (final K key : keysToRemove) {
            this.backingMap.remove(key);
        }
    }

    public void remove(K key) {
        this.backingMap.remove(key);
    }

    @Override
    public String toString() {
        return this.backingMap.toString();
    }

	public void clear() {
		this.backingMap.clear();
	}

    public boolean isEmpty() {
        return this.backingMap.isEmpty();
    }

}
