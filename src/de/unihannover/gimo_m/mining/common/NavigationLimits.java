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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;

public class NavigationLimits {

	private final AtomicReference<Map<TargetFunction, Double>> limits =
			new AtomicReference<>(Collections.emptyMap());

	public double getLimit(TargetFunction target) {
		final Double limit = this.limits.get().get(target);
		return limit != null ? limit : Double.POSITIVE_INFINITY;
	}

	public<T> NondominatedResults<T> filter(NondominatedResults<T> snapshot) {
		final NondominatedResults<T> ret = new NondominatedResults<>();
		final Map<TargetFunction, Double> map = this.limits.get();
		for (final ValuedResult<T> v : snapshot.getItems()) {
			if (isWithinLimits(v, map)) {
				ret.add(v);
			}
		}
		return ret;
	}

	public boolean isInLimits(ValuedResult<?> item) {
		return isWithinLimits(item, this.limits.get());
	}

	private static boolean isWithinLimits(ValuedResult<?> v, Map<TargetFunction, Double> map) {
		for (final Entry<TargetFunction, Double> l : map.entrySet()) {
			final double val = l.getKey().applyAsDouble(v);
			if (val > l.getValue()) {
				return false;
			}
		}
		return true;
	}

	public void removeLimit(TargetFunction f) {
		Map<TargetFunction, Double> oldMap;
		Map<TargetFunction, Double> newMap;
		do {
			oldMap = this.limits.get();
			newMap = new HashMap<>(oldMap);
			newMap.remove(f);
		} while (!this.limits.compareAndSet(oldMap, newMap));
	}

	public void setLimit(TargetFunction target, double limit) {
		Map<TargetFunction, Double> oldMap;
		Map<TargetFunction, Double> newMap;
		do {
			oldMap = this.limits.get();
			newMap = new HashMap<>(oldMap);
			newMap.put(target, limit);
		} while (!this.limits.compareAndSet(oldMap, newMap));
	}

}
