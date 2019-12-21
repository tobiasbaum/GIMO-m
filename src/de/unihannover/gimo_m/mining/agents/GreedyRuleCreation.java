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
package de.unihannover.gimo_m.mining.agents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.ToDoubleFunction;

import de.unihannover.gimo_m.mining.common.And;
import de.unihannover.gimo_m.mining.common.Blackboard;
import de.unihannover.gimo_m.mining.common.Blackboard.RecordsAndRemarks;
import de.unihannover.gimo_m.mining.common.Blackboard.RuleRestrictions;
import de.unihannover.gimo_m.mining.common.Equals;
import de.unihannover.gimo_m.mining.common.Geq;
import de.unihannover.gimo_m.mining.common.Leq;
import de.unihannover.gimo_m.mining.common.Multiset;
import de.unihannover.gimo_m.mining.common.NotEquals;
import de.unihannover.gimo_m.mining.common.Or;
import de.unihannover.gimo_m.mining.common.RandomUtil;
import de.unihannover.gimo_m.mining.common.Record;
import de.unihannover.gimo_m.mining.common.RecordScheme;
import de.unihannover.gimo_m.mining.common.RuleCreationRestriction;
import de.unihannover.gimo_m.mining.common.RuleSet;
import de.unihannover.gimo_m.mining.common.SimpleRule;
import de.unihannover.gimo_m.mining.common.Util;

public class GreedyRuleCreation {

    static final class RuleQuality {
        private final int mustCount;
        private final int noTriggerCount;
        private final RuleQuality totalTrainingSetCounts;

        public RuleQuality(int mustRecordCount, int noRecordCount, RuleQuality totalTrainingSetCounts) {
            this.mustCount = mustRecordCount;
            this.noTriggerCount = noRecordCount;
            this.totalTrainingSetCounts = totalTrainingSetCounts;
        }

        public boolean isProMust() {
            return this.mustCount >= this.noTriggerCount;
        }

        public double getPrecision() {
            if (this.getSize() == 0) {
                return 0.0;
            }
            return ((double) this.noTriggerCount) / this.getSize();
        }

        public double getLaplace() {
            return ((double) this.noTriggerCount + 1) / (this.getSize() + 2);
        }

        public double getMEstimate(double m) {
            final double trainingSetSize = this.totalTrainingSetCounts.getSize();
            return (this.noTriggerCount + m * this.noTriggerCount / trainingSetSize) / (this.getSize() + m);
        }

        public double getRelativeCost(double cr) {
            final double htpr = ((double) this.noTriggerCount) / this.totalTrainingSetCounts.noTriggerCount;
            final double hfpr = ((double) this.mustCount) / this.totalTrainingSetCounts.mustCount;
            return cr * htpr - (1.0 - cr) * hfpr;
        }

        private int getSize() {
            return this.mustCount + this.noTriggerCount;
        }

        @Override
        public int hashCode() {
            return this.mustCount * 31 + this.noTriggerCount;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof RuleQuality)) {
                return false;
            }
            final RuleQuality other = (RuleQuality) obj;
            return this.mustCount == other.mustCount
                && this.noTriggerCount == other.noTriggerCount;
        }

        @Override
        public String toString() {
            return "(" + this.mustCount + ", " + this.noTriggerCount + ")";
        }

    }

    static final class ConditionResults {
        private final SimpleRule condition;
        private final RuleQuality quality;

        public ConditionResults(SimpleRule condition, RuleQuality quality) {
            this.condition = condition;
            this.quality = quality;
        }

        public RuleQuality getQuality() {
            return this.quality;
        }

        @Override
        public int hashCode() {
            return this.condition.hashCode() + this.quality.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ConditionResults)) {
                return false;
            }
            final ConditionResults other = (ConditionResults) obj;
            return this.condition.equals(other.condition)
                && this.quality.equals(other.quality);
        }

        @Override
        public String toString() {
            return this.condition + " " + this.quality;
        }
    }

    private final Random random;
    private final Blackboard blackboard;

    public GreedyRuleCreation(Blackboard blackboard, Random random) {
        this.random = random;
        this.blackboard = blackboard;
    }

    public RuleSet createRuleSet(int limit) throws InterruptedException {
        return this.createRuleSet(limit, null);
    }

    public RuleSet createRuleSet(int limit, RuleSet basis) throws InterruptedException {
    	final RecordsAndRemarks rr = this.blackboard.getRecords();
    	final String targetClass = this.getRandomClass(rr);
    	final RuleRestrictions restrictions = this.blackboard.restrictionsFor(targetClass);
        if (basis == null) {
            basis = RuleSet.create(this.getRandomClass(rr));
        }
        final RecordSubset withoutCan = this.makeBinary(rr, targetClass, basis);
        this.blackboard.log(String.format(
        		"%d must and %d other records after binarization",
        		withoutCan.getMustRecordCount(),
        		withoutCan.getNoRecordCount()));

        final RecordScheme scheme = rr.getRecords().getScheme();

        final Set<String> selectedFeatures = this.sampleFeatureSubset(scheme);

        RecordSubset uncovered = withoutCan.downsample(this.random, 0.5, selectedFeatures.size() * 50);

        final RuleQuality totalCount = this.determineTotalCounts(uncovered);
        final RuleQuality totalCountReversed = this.determineTotalCounts(uncovered.swapMustAndNo());

        Or ret = new Or();
        for (final And excl : restrictions.getAccepted()) {
            ret = ret.or(excl);
        }
        uncovered = uncovered.keepNotSatisfying(ret);
        final RecordSubset uncoveredBeforeExclusions = uncovered;

        final int maxExclIter = this.random.nextInt(limit);
        for (int i = 0; i < maxExclIter; i++) {
            if (uncovered.getMustRecordCount() == 0 || Thread.currentThread().isInterrupted()) {
                break;
            }
            final And bestRule = this.greedyTopDown(
                    scheme, uncovered.swapMustAndNo(), selectedFeatures, totalCountReversed, restrictions);
            if (bestRule != null) {
                ret = ret.or(bestRule);
                uncovered = uncovered.keepNotSatisfying(bestRule);
            }
        }
        return basis.addException(targetClass, ret);
    }

    private RecordSubset makeBinary(RecordsAndRemarks rr, String targetStrategy, RuleSet basis) {
        final List<Record> must = new ArrayList<>();
        final List<Record> no = new ArrayList<>();
        for (final Record r : rr.getRecords().getRecords()) {
            if (basis.apply(r).equals(r.getCorrectClass())) {
                continue;
            }
            if (r.getCorrectClass().equals(targetStrategy)) {
                must.add(r);
            } else {
                no.add(r);
            }
        }
        return new RecordSubset(must, no);
    }

    private String getRandomClass(RecordsAndRemarks rr) {
        final Record record = RandomUtil.randomItem(this.random, Arrays.asList(rr.getRecords().getRecords()));
        return record.getCorrectClass();
    }

    /**
     * "Random subspace selection": Select a random subset of the features.
     */
    private Set<String> sampleFeatureSubset(RecordScheme scheme) {
    	final List<String> possibleFeatures = new ArrayList<>(scheme.getColumnNames());
    	possibleFeatures.removeAll(this.blackboard.getRejectedColumns());
    	Collections.shuffle(possibleFeatures, this.random);

    	final int countToUse = Math.max(5, scheme.getAllColumnCount() / 2);
    	return new LinkedHashSet<>(possibleFeatures.subList(0, Math.min(countToUse, possibleFeatures.size())));
	}

	private ToDoubleFunction<RuleQuality> getRandomQualityFunction() {
        switch (this.random.nextInt(4)) {
        case 0:
            return (RuleQuality q) -> q.getPrecision();
        case 1:
            return (RuleQuality q) -> q.getLaplace();
        case 2:
        	final double factor = this.random.nextDouble() * 0.8;
            return (RuleQuality q) -> q.getRelativeCost(factor);
        case 3:
        	final int m = this.random.nextInt(100) + 1;
            return (RuleQuality q) -> q.getMEstimate(m);
        default:
            throw new AssertionError();
        }
    }

    RuleQuality determineTotalCounts(RecordSubset uncovered) {
        return determineQuality(uncovered, determineQuality(uncovered, null));
    }

    And greedyTopDown(
					RecordScheme scheme,
                    RecordSubset toCover,
                    Set<String> selectedFeatures,
                    RuleQuality totalTrainingSetCounts,
                    RuleRestrictions restr) throws InterruptedException {

        RuleQuality priorQuality = determineQuality(toCover, totalTrainingSetCounts);
        And priorRule = new And();
        RuleQuality bestQuality = priorQuality;
        And bestRule = priorRule;
        final ToDoubleFunction<RuleQuality> qualityFunction = this.getRandomQualityFunction();
        while (true) {
            final RuleCreationRestriction creationRestriction = restr.toCreationRestrictions(priorRule);
            final ConditionResults condition;
            if (this.random.nextDouble() < 0.05) {
                condition = this.createRandomCondition(
                			scheme,
                            toCover,
                            selectedFeatures,
                            priorRule.getUsedFeatures(),
                            totalTrainingSetCounts,
                            creationRestriction);
            } else {
                condition = this.findBestCondition(
                			scheme,
                            toCover,
                            selectedFeatures,
                            priorRule.getUsedFeatures(),
                            totalTrainingSetCounts,
                            qualityFunction,
                            creationRestriction);
            }
            if (condition == null) {
                break;
            }
            priorRule = priorRule.and(condition.condition);
            if (this.compare(condition.getQuality(), bestQuality, qualityFunction) > 0
            		|| bestRule.getChildren().length == 0) {
                bestRule = priorRule;
                bestQuality = condition.getQuality();
            }
            toCover = toCover.keepSatisfying(condition.condition);
            priorQuality = condition.getQuality();
        }

        if (bestQuality.isProMust()) {
            return null;
        } else {
            return bestRule;
        }
    }

	final ConditionResults createRandomCondition(
					RecordScheme scheme,
					RecordSubset toCover,
					Set<String> selectedFeatures,
                    Multiset<String> alreadyUsedFeatures,
                    RuleQuality totalTrainingSetCounts,
                    RuleCreationRestriction creationRestriction) throws InterruptedException {

        final List<Integer> remainingFeatures = new ArrayList<>();
        for (int i = 0; i < scheme.getAllColumnCount(); i++) {
            final String name = scheme.getName(i);
            if (alreadyUsedFeatures.get(name) == 0
                    && selectedFeatures.contains(name)) {
                remainingFeatures.add(i);
            }
        }
        Collections.shuffle(remainingFeatures, this.random);

        for (final Integer feature : remainingFeatures) {
            if (toCover.getMustRecords().isEmpty() || toCover.getNoRecords().isEmpty()) {
                continue;
            }
            final SimpleRule r = this.createRandomRuleForColumnWithRetries(scheme, feature, toCover, creationRestriction);
            if (r == null) {
                continue;
            }
            return new ConditionResults(r, determineQuality(toCover.keepSatisfying(r), totalTrainingSetCounts));
        }
        return null;
    }

    private SimpleRule createRandomRuleForColumnWithRetries(
				RecordScheme scheme, int feature, RecordSubset toCover, RuleCreationRestriction creationRestriction) {
        for (int i = 0; i < 10; i++) {
            final SimpleRule r = this.createRandomRuleForColumn(scheme, feature, toCover);
            if (r != null && creationRestriction.canBeValid(r)) {
                return r;
            }
        }
        return null;
    }

    private SimpleRule createRandomRuleForColumn(RecordScheme scheme, int feature, RecordSubset toCover) {
        if (scheme.isNumeric(feature)) {
            final Record must = RandomUtil.randomItem(this.random, toCover.getMustRecords());
            final Record no = RandomUtil.randomItem(this.random, toCover.getNoRecords());
            final int numIdx = scheme.toNumericIndex(feature);
            final double mustValue = must.getValueDbl(numIdx);
            final double noValue = no.getValueDbl(numIdx);
            if (Double.isNaN(mustValue) || Double.isNaN(noValue)) {
                return null;
            }
            if (noValue < mustValue) {
                return new Leq(scheme, feature, noValue);
            } else {
                return new Geq(scheme, feature, mustValue);
            }
        } else {
            if (this.random.nextBoolean()) {
                final Record r = RandomUtil.randomItem(this.random, toCover.getNoRecords());
                final String value = r.getValueStr(scheme.toStringIndex(feature));
                if (value == null) {
                    return null;
                }
                return new Equals(scheme, feature, value);
            } else {
                final Record r = RandomUtil.randomItem(this.random, toCover.getMustRecords());
                final String value = r.getValueStr(scheme.toStringIndex(feature));
                if (value == null) {
                    return null;
                }
                return new NotEquals(scheme, feature, value);
            }
        }
    }

    final ConditionResults findBestCondition(
    				RecordScheme scheme,
                    RecordSubset toCover,
                    Set<String> selectedFeatures,
                    Multiset<String> alreadyUsedFeatures,
                    RuleQuality totalTrainingSetCounts,
                    ToDoubleFunction<RuleQuality> qualityFunction,
                    RuleCreationRestriction creationRestriction) {
        ConditionResults best = null;

        for (int column = 0; column < scheme.getStringColumnCount(); column++) {
            final String name = scheme.getStrName(column);
            if (!selectedFeatures.contains(name)) {
                continue;
            }

            final Multiset<String> mustCounts = this.countStringValues(toCover.getMustRecords(), column);
            if (mustCounts.isEmpty()) {
                continue;
            }
            final Multiset<String> noCounts = this.countStringValues(toCover.getNoRecords(), column);
            if (noCounts.isEmpty()) {
                continue;
            }
            if (this.noMultipleValues(mustCounts, noCounts)) {
                continue;
            }
            if (alreadyUsedFeatures.get(name) == 0
            		&& creationRestriction.canBeValid(scheme.getAbsIndexFromStr(column), Equals.class)) {
	            for (final String value : noCounts.keySet()) {
	                best = this.evaluateCandidate(toCover, best,
	                            new RuleQuality(
	                                            mustCounts.get(value),
	                                            noCounts.get(value),
	                                            totalTrainingSetCounts),
	                            new Equals(scheme, scheme.getAbsIndexFromStr(column), value),
	                            qualityFunction,
	                            creationRestriction);
	            }
            }
            if (creationRestriction.canBeValid(scheme.getAbsIndexFromStr(column), NotEquals.class)) {
	            for (final String value : mustCounts.keySet()) {
	                best = this.evaluateCandidate(toCover, best,
	                            new RuleQuality(
	                                            toCover.getMustRecordCount() - mustCounts.get(value),
	                                            toCover.getNoRecordCount() - noCounts.get(value),
	                                            totalTrainingSetCounts),
	                            new NotEquals(scheme, scheme.getAbsIndexFromStr(column), value),
	                            qualityFunction,
	                            creationRestriction);
	            }
            }
        }

        for (int column = 0; column < scheme.getNumericColumnCount(); column++) {
            final String name = scheme.getNumName(column);
            if (alreadyUsedFeatures.get(name) > 1) {
                //numeric columns can be used twice to allow ranges
                continue;
            }
            if (this.blackboard.getRejectedColumns().contains(name)) {
                continue;
            }

            final Multiset<Double> mustCounts = this.countNumericValues(toCover.getMustRecords(), column);
            if (mustCounts.isEmpty()) {
                continue;
            }
            final Multiset<Double> noCounts = this.countNumericValues(toCover.getNoRecords(), column);
            if (noCounts.isEmpty()) {
                continue;
            }
            final TreeSet<Double> values = new TreeSet<>();
            values.addAll(mustCounts.keySet());
            values.addAll(noCounts.keySet());

            final Iterator<Double> iter = values.iterator();
            Double prevValue = iter.next();
            final int totalMustCount = mustCounts.sum();
            final int totalNoCount = noCounts.sum();
            int mustSum = mustCounts.get(prevValue);
            int noSum = noCounts.get(prevValue);
            final boolean tryLeq = creationRestriction.canBeValid(scheme.getAbsIndexFromNum(column), Leq.class);
            final boolean tryGeq = creationRestriction.canBeValid(scheme.getAbsIndexFromNum(column), Geq.class);
            while (iter.hasNext()) {
                final Double d = iter.next();
                if (tryLeq) {
                	best = this.evaluateCandidate(toCover, best,
                                new RuleQuality(mustSum, noSum, totalTrainingSetCounts),
                                new Leq(scheme,
                                        scheme.getAbsIndexFromNum(column),
                                        Util.determineSplitPointWithFewDigits(prevValue, d)),
                                qualityFunction,
                                creationRestriction);
                }
                if (tryGeq) {
                	best = this.evaluateCandidate(toCover, best,
                                new RuleQuality(totalMustCount - mustSum, totalNoCount - noSum, totalTrainingSetCounts),
                                new Geq(scheme,
                                        scheme.getAbsIndexFromNum(column),
                                        Util.determineSplitPointWithFewDigits(prevValue, d)),
                                qualityFunction,
                                creationRestriction);
                }
                mustSum += mustCounts.get(d);
                noSum += noCounts.get(d);
                prevValue = d;
            }
        }

        return best;
    }

    private Multiset<Double> countNumericValues(Collection<Record> rs, int column) {
        final Multiset<Double> m = new Multiset<>();
        for (final Record r : rs) {
            final double v = r.getValueDbl(column);
            if (!Double.isNaN(v)) {
                m.add(v);
            }
        }
        return m;
    }

    private boolean noMultipleValues(Multiset<String> mustCounts, Multiset<String> noCounts) {
        if (mustCounts.keySet().size() > 1 || noCounts.keySet().size() > 1) {
            return false;
        }
        final Set<String> joined = new HashSet<>(mustCounts.keySet());
        joined.addAll(noCounts.keySet());
        return joined.size() <= 1;
    }

    private Multiset<String> countStringValues(Iterable<Record> rs, int column) {
        final Multiset<String> m = new Multiset<>();
        for (final Record r : rs) {
            final String s = r.getValueStr(column);
            if (s != null) {
                m.add(s);
            }
        }
        return m;
    }

    private ConditionResults evaluateCandidate(
                    RecordSubset toCover,
                    ConditionResults best,
                    RuleQuality qualityForCandidate,
                    SimpleRule newCandidate,
                    ToDoubleFunction<RuleQuality> qualityFunction,
                    RuleCreationRestriction creationRestriction) {
        if ((best == null || (this.compare(qualityForCandidate, best.quality, qualityFunction) > 0))
                && creationRestriction.canBeValid(newCandidate)) {
            return new ConditionResults(newCandidate, qualityForCandidate);
        } else {
            return best;
        }
    }

    private static RuleQuality determineQuality(RecordSubset toCover, RuleQuality totalTrainingSetCounts) {
        return new RuleQuality(toCover.getMustRecordCount(), toCover.getNoRecordCount(), totalTrainingSetCounts);
    }

    private int compare(RuleQuality q1, RuleQuality q2, ToDoubleFunction<RuleQuality> qualityFunction) {
        final int cmp = Double.compare(qualityFunction.applyAsDouble(q1), qualityFunction.applyAsDouble(q2));
        if (cmp != 0) {
            return cmp;
        } else {
            return Integer.compare(q1.getSize(), q2.getSize());
        }
    }

}
