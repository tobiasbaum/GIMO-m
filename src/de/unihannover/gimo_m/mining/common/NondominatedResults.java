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
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

public class NondominatedResults<R> {

    private final List<ValuedResult<Set<R>>> items = new ArrayList<>();

    /**
     * Adds an item to the set when it is not dominated by an already existing item.
     * Removes newly dominated items from the set.
     * @return true when the item was added
     */
    public boolean add(ValuedResult<R> candidate) {
        final Iterator<ValuedResult<Set<R>>> iter = this.items.iterator();
        while (iter.hasNext()) {
            final ValuedResult<Set<R>> cur = iter.next();
            if (cur.dominates(candidate)) {
                return false;
            }
            if (cur.hasSameValues(candidate)) {
                return cur.getItem().add(candidate.getItem());
            }
            if (candidate.dominates(cur)) {
                iter.remove();
            }
        }
        final Set<R> newSet = new LinkedHashSet<>();
        newSet.add(candidate.getItem());
        this.items.add(candidate.copyWithNewItem(newSet));
        return true;
    }

    public boolean addAll(NondominatedResults<R> candidates) {
        boolean hadSomeImprovement = false;
        for (final ValuedResult<Set<R>> c : candidates.items) {
            for (final R rule : c.getItem()) {
                hadSomeImprovement |= this.add(c.copyWithNewItem(rule));
            }
        }
        return hadSomeImprovement;
    }

    public List<ValuedResult<R>> getItems() {
        final List<ValuedResult<R>> ret = new ArrayList<>();
        for (final ValuedResult<Set<R>> v : this.items) {
            for (final R rule : v.getItem()) {
                ret.add(v.copyWithNewItem(rule));
            }
        }
        return ret;
    }

    public List<ValuedResult<Set<R>>> getItemsSorted() {
        Collections.sort(this.items, ValuedResult.LEXICOGRAPHIC_COMPARATOR);
        return this.items;
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    @Override
    public String toString() {
        return this.items.toString();
    }

    public ValuedResult<R> getRandomItem(Random random) {
        return RandomUtil.randomItem(random, this.getItems());
    }

	public ValuedResult<R> getBestItem(Random random, TargetFunction functionToMinimize) {
		final List<ValuedResult<R>> bestItems = new ArrayList<>();
		double minValue = Double.POSITIVE_INFINITY;

		for (final ValuedResult<R> item : this.getItems()) {
			final double curValue = functionToMinimize.applyAsDouble(item);
			if (curValue < minValue) {
				bestItems.clear();
				bestItems.add(item);
				minValue = curValue;
			} else if (curValue == minValue) {
				bestItems.add(item);
			}
		}
		return RandomUtil.randomItem(random, bestItems);
	}

    public void clear() {
        this.items.clear();
    }

    public void removeIf(Predicate<? super R> rulePredicate) {
        for (final ValuedResult<Set<R>> e : this.items) {
            e.getItem().removeIf(rulePredicate);
        }
        this.items.removeIf((ValuedResult<Set<R>> e) -> e.getItem().isEmpty());
    }

}
