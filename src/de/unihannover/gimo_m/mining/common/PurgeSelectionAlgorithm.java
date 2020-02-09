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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import de.unihannover.gimo_m.util.Util;

public class PurgeSelectionAlgorithm {

	private static final int RECORD_SAMPLE_SIZE = 100;
	private static final int MAX_SAMPLING_TRIES = 1000;

	/**
	 * Determines which rules to keep when purging the known rules.
	 * All the best elements in the limits according to the given target functions are kept.
	 * Furthermore, the rules are clustered based on the records matched by them, to keep the variety of rules up.
	 */
	static Set<ValuedResult<RuleSet>> determineRulesToKeep(
			NondominatedResults<RuleSet> paretoFront,
			NavigationLimits navigationLimits,
			int countToKeep,
			List<TargetFunction> targetFunctions,
			List<Record> records,
			Random random) {

		final NondominatedResults<RuleSet> inLimits = navigationLimits.filter(paretoFront);
		final Set<ValuedResult<RuleSet>> toKeep = new LinkedHashSet<>();
		for (final TargetFunction f : targetFunctions) {
			toKeep.add(inLimits.getBestItem(random, f));
		}

		if (toKeep.size() >= countToKeep) {
			return toKeep;
		}

		if (inLimits.getItems().size() <= countToKeep) {
			toKeep.addAll(inLimits.getItems());
			toKeep.addAll(getOneRulePerCluster(countToKeep - toKeep.size(), paretoFront.getItems(), records, random));
		} else {
			toKeep.addAll(getOneRulePerCluster(countToKeep - toKeep.size(), inLimits.getItems(), records, random));
		}
		return toKeep;
	}

	/**
	 * Performs a fast randomized clustering of the rules, according to the Manhattan distance based on
	 * their classification vector for a random subset of records.
	 */
	static Collection<? extends ValuedResult<RuleSet>> getOneRulePerCluster(
			int clusterCount, List<ValuedResult<RuleSet>> items, List<Record> records, Random random) {
		if (clusterCount == 0) {
			return Collections.emptyList();
		}
		if (clusterCount == 1) {
			return Collections.singletonList(Util.randomItem(random, items));
		}

		final Map<ValuedResult<RuleSet>, String[]> recordMatches = evaluateForSampleOfRecords(items, records, random);
		final List<ValuedResult<RuleSet>> remainingItems = new ArrayList<>(items);

		while (remainingItems.size() > clusterCount) {
			final int s1 = random.nextInt(remainingItems.size());
			final int s2 = random.nextInt(remainingItems.size());
			final int s3 = random.nextInt(remainingItems.size());
			final int distance12 = distance(recordMatches, remainingItems, s1, s2);
			final int distance23 = distance(recordMatches, remainingItems, s2, s3);
			final int distance13 = distance(recordMatches, remainingItems, s1, s3);
			if (distance12 >= distance23) {
				if (distance12 >= distance13) {
					//1 and 2 are farthest apart => remove 3
					fastRemove(remainingItems, s3);
				} else {
					//1 and 3 are farthest apart => remove 2
					fastRemove(remainingItems, s2);
				}
			} else {
				if (distance23 >= distance13) {
					//2 and 3 are farthest apart => remove 1
					fastRemove(remainingItems, s1);
				} else {
					//1 and 3 are farthest apart => remove 2
					fastRemove(remainingItems, s2);
				}
			}
		}

		return remainingItems;
	}

	private static int distance(Map<ValuedResult<RuleSet>, String[]> recordMatches,
			List<ValuedResult<RuleSet>> items, int index1, int index2) {
		final String[] vec1 = recordMatches.get(items.get(index1));
		final String[] vec2 = recordMatches.get(items.get(index2));
		int distance = 0;
		for (int i = 0; i < vec1.length; i++) {
			if (!Objects.equals(vec1[i], vec2[i])) {
				distance++;
			}
		}
		return distance;
	}

	private static void fastRemove(List<ValuedResult<RuleSet>> list, int index) {
		final int end = list.size() - 1;
		list.set(index, list.get(end));
		list.remove(end);
	}

	private static Map<ValuedResult<RuleSet>, String[]> evaluateForSampleOfRecords(List<ValuedResult<RuleSet>> items, List<Record> records, Random random) {

		final Map<ValuedResult<RuleSet>, String[]> ret = new LinkedHashMap<>();
		for (final ValuedResult<RuleSet> rule : items) {
			ret.put(rule, new String[RECORD_SAMPLE_SIZE]);
		}

		int bit = 0;
		int tries = 0;
		while (bit < RECORD_SAMPLE_SIZE && tries < MAX_SAMPLING_TRIES) {
			final Record r = Util.randomItem(random, records);
			String firstValue = null;
			boolean allTheSame = true;
			for (final ValuedResult<RuleSet> rule : items) {
				final String curResult = rule.getItem().apply(r);
				final String[] vectorForRule = ret.get(rule);
				vectorForRule[bit] = curResult;
				if (firstValue == null) {
					firstValue = curResult;
				} else {
					allTheSame &= firstValue.equals(curResult);
				}
			}
			if (!allTheSame) {
				//only go to the next bit when the evaluation was not constant (the same for all rules)
				bit++;
			}
			tries++;
		}
		return ret;
	}

}
