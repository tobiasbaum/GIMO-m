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
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import de.unihannover.gimo_m.util.RandomUtil;

public class PurgeSelectionAlgorithm {

	private static final int RECORD_SAMPLE_SIZE = 100;
	private static final int MAX_SAMPLING_TRIES = 1000;

	private static final class Cluster {
		private final List<ValuedResult<RuleSet>> entries = new ArrayList<>();

		public Cluster(ValuedResult<RuleSet> item) {
			this.entries.add(item);
		}

		public double distanceTo(Cluster cluster, Map<ValuedResult<RuleSet>, BitSet> positionVectors) {
			int sum = 0;
			int count = 0;
			for (final ValuedResult<RuleSet> r1 : this.entries) {
				for (final ValuedResult<RuleSet> r2 : cluster.entries) {
					sum += this.distance(r1, r2, positionVectors);
					count++;
				}
			}
			return ((double) sum) / count;
		}

		private int distance(ValuedResult<RuleSet> r1, ValuedResult<RuleSet> r2,
				Map<ValuedResult<RuleSet>, BitSet> positionVectors) {
			final BitSet b1 = positionVectors.get(r1);
			final BitSet b2 = positionVectors.get(r2);

			final BitSet c = new BitSet(RECORD_SAMPLE_SIZE);
			c.or(b1);
			c.xor(b2);
			return c.cardinality();
		}

		public void add(Cluster cluster) {
			this.entries.addAll(cluster.entries);
		}

		public ValuedResult<RuleSet> getRandomItem(Random random) {
			return RandomUtil.randomItem(random, this.entries);
		}
	}


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
	 * Performs agglomerative hierarchical clustering of the rules, according to the Manhattan distance based on
	 * whether they apply or don't apply for a random subset of records.
	 */
	private static Collection<? extends ValuedResult<RuleSet>> getOneRulePerCluster(
			int clusterCount, List<ValuedResult<RuleSet>> items, List<Record> records, Random random) {
		if (clusterCount == 0) {
			return Collections.emptyList();
		}
		if (clusterCount == 1) {
			return Collections.singletonList(RandomUtil.randomItem(random, items));
		}

		final Map<ValuedResult<RuleSet>, BitSet> recordMatches = evaluateForSampleOfRecords(items, records, random);

		final List<Cluster> clusters = new ArrayList<>();
		for (final ValuedResult<RuleSet> item : items) {
			clusters.add(new Cluster(item));
		}

		while (clusters.size() > clusterCount) {
			int minDist1 = -1;
			int minDist2 = -1;
			double minDist = Double.POSITIVE_INFINITY;

			for (int i = 1; i < clusters.size(); i++) {
				//determine pair of clusters with minimum average distance
				for (int j = 0; j < i; j++) {
					final double curDist = clusters.get(i).distanceTo(clusters.get(j), recordMatches);
					if (curDist < minDist) {
						minDist1 = i;
						minDist2 = j;
						minDist = curDist;
					}
				}
			}

			clusters.get(minDist2).add(clusters.get(minDist1));
			clusters.remove(minDist1);
		}

		final List<ValuedResult<RuleSet>> ret = new ArrayList<>();
		for (final Cluster c : clusters) {
			ret.add(c.getRandomItem(random));
		}
		return ret;
	}

	private static Map<ValuedResult<RuleSet>, BitSet> evaluateForSampleOfRecords(List<ValuedResult<RuleSet>> items, List<Record> records, Random random) {

		final Map<ValuedResult<RuleSet>, BitSet> ret = new LinkedHashMap<>();
		for (final ValuedResult<RuleSet> rule : items) {
			ret.put(rule, new BitSet());
		}

		int bit = 0;
		int tries = 0;
		while (bit < RECORD_SAMPLE_SIZE && tries < MAX_SAMPLING_TRIES) {
			final Record r = RandomUtil.randomItem(random, records);
			boolean andAll = true;
			boolean orAll = false;
			for (final ValuedResult<RuleSet> rule : items) {
				final boolean curResult = rule.getItem().apply(r).hashCode() % 2 == 0;
				final BitSet bitset = ret.get(rule);
				bitset.set(bit, curResult);
				andAll &= curResult;
				orAll |= curResult;
			}
			if (andAll != orAll) {
				//only go to the next bit when the evaluation was not constant (the same for all rules)
				bit++;
			}
			tries++;
		}
		return ret;
	}

}
