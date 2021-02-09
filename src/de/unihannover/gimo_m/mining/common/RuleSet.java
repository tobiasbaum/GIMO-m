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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.setsoftware.reviewtool.ordering.efficientalgorithm.MatchSet;
import de.setsoftware.reviewtool.ordering.efficientalgorithm.TourCalculator;
import de.setsoftware.reviewtool.ordering.efficientalgorithm.TourCalculatorControl;
import de.unihannover.gimo_m.util.Multimap;

public class RuleSet extends ItemWithComplexity implements Function<Record, String>  {

    private final String defaultValue;
    private final String[] exceptionValues;
    private final Or[] exceptionConditions;
    private final int hash;

    private RuleSet(final String defaultValue, final Or[] exceptionConditions, final String[] exceptionValues) {
        this.defaultValue = defaultValue;
        this.exceptionConditions = exceptionConditions;
        this.exceptionValues = exceptionValues;
        this.hash = this.calculateHash();
    }

    private int calculateHash() {
        int ret = this.defaultValue.hashCode();
        ret += Arrays.hashCode(this.exceptionValues);
        for (final Or ex : this.exceptionConditions) {
            ret += ex.hashCode();
        }
        return ret;
    }

    public static RuleSet create(final String defaultValue2) {
        return new RuleSet(defaultValue2, new Or[0], new String[0]);
    }

    public RuleSet addRule(final String strategy, final And newRule) {
        final int index = this.getExceptionIndex(strategy);
        if (index >= 0) {
            return this.addRule(index, newRule);
        } else {
            return this.addException(strategy, new Or(newRule));
        }
    }

    public RuleSet addException(final String strategy, final Or or) {
        final int oldLen = this.exceptionValues.length;
        final String[] newExceptions = Arrays.copyOf(this.exceptionValues, oldLen + 1);
        final Or[] newConditions = Arrays.copyOf(this.exceptionConditions, oldLen + 1);
        newExceptions[oldLen] = strategy;
        newConditions[oldLen] = or;
        return new RuleSet(this.defaultValue, newConditions, newExceptions);
    }

    private RuleSet addRule(final int exceptionId, final And newRule) {
        final Or changedRules = this.exceptionConditions[exceptionId].or(newRule);
        return new RuleSet(this.defaultValue, this.changeOneException(exceptionId, changedRules), this.exceptionValues);
    }

    public RuleSet removeRule(final String strategy, final And toRemove) {
        for (int i = 0; i < this.exceptionValues.length; i++) {
            if (this.exceptionValues[i].equals(strategy)
                    && Arrays.asList(this.exceptionConditions[i].getChildren()).contains(toRemove)) {
                return this.removeRule(i, toRemove);
            }
        }
        return this;
    }

    public RuleSet removeRule(final int exceptionId, final And toRemove) {
        final Or changedRules = this.exceptionConditions[exceptionId].copyWithoutChild(toRemove);
        return new RuleSet(this.defaultValue, this.changeOneException(exceptionId, changedRules), this.exceptionValues);
    }

    private Or[] changeOneException(final int exceptionId, final Or changedRules) {
        final Or[] newConditions = new Or[this.exceptionConditions.length];
        for (int i = 0; i < newConditions.length; i++) {
            if (i == exceptionId) {
                newConditions[i] = changedRules;
            } else {
                newConditions[i] = this.exceptionConditions[i];
            }
        }
        return newConditions;
    }

    public RuleSet remove(
            final String classification,
			final RulePattern pattern,
			final List<And> whitelist1,
			final List<And> whitelist2) {

        RuleSet ret = this;
        for (int i = 0; i < this.exceptionValues.length; i++) {
            if (this.exceptionValues[i].equals(classification)) {
                for (final Rule child : this.exceptionConditions[i].getChildren()) {
                    final And and = (And) child;
                    if (pattern.matches(and) && !whitelist1.contains(and) && !whitelist2.contains(and)) {
                        ret = ret.removeRule(i, and);
                    }
                }
            }
        }
        return ret;
	}

    public RuleSet replaceRule(final String strategy, final And toReplace, final And replacement) {
        final int exceptionId = this.getExceptionIndex(strategy);
        if (exceptionId < 0) {
            return this;
        }
        final Or replaced = this.exceptionConditions[exceptionId].copyWithReplacedChild(toReplace, replacement);
        return new RuleSet(this.defaultValue, this.changeOneException(exceptionId, replaced), this.exceptionValues);
    }

    @Override
    public String apply(final Record r) {
        for (int i = 0; i < this.exceptionConditions.length; i++) {
            if (this.exceptionConditions[i].test(r)) {
                return this.exceptionValues[i];
            }
        }
        return this.defaultValue;
    }

    @Override
    public double getComplexity(final Set<Object> usedValues) {
        double ret = 0.0;
        for (final Or ex : this.exceptionConditions) {
            ret += 1.0 + ex.getComplexity(usedValues);
        }
        return ret;
    }

    @Override
    public int getFeatureCount() {
        final Set<String> features = new HashSet<>();
        for (final Or ex : this.exceptionConditions) {
            features.addAll(ex.getUsedFeatures().keySet());
        }
        return features.size();
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof RuleSet)) {
            return false;
        }
        final RuleSet r = (RuleSet) o;
        if (this.hash != r.hash) {
            return false;
        }
        if (!this.defaultValue.equals(r.defaultValue)) {
            return false;
        }
        return Arrays.equals(this.exceptionValues, r.exceptionValues)
            && Arrays.equals(this.exceptionConditions, r.exceptionConditions);
    }

    @Override
    public String toString() {
        final StringBuilder ret = new StringBuilder();
        ret.append(RuleSetParser.DEFAULT_RULE).append(this.defaultValue).append("\n");
        for (int i = 0; i < this.exceptionValues.length; i++) {
            ret.append(RuleSetParser.EXCEPT_RULE).append(this.exceptionValues[i]).append(RuleSetParser.EXCEPT_RULE_SUFFIX).append("\n");
            this.printRules(ret, toArr(toAnd(this.exceptionConditions[i].getChildren())));
        }
        return ret.toString();
    }

    private static final class AndMatchSet extends MatchSet<And> {
    	private final String key;

    	public AndMatchSet(final String key, final List<And> list) {
    		super(list);
    		this.key = key;
		}

    }

    private void printRules(final StringBuilder ret, final And[] rules) {
    	//group related rules so that they stand close together
    	final Multimap<String, And> relatedRules = new Multimap<>();
    	for (final And rule : rules) {
    		for (final Rule condition : rule.getChildren()) {
    			for (final String feature : condition.getUsedFeatures().keySet()) {
    				relatedRules.add(feature, rule);
    				relatedRules.add(feature + "," + condition.getClass().getName(), rule);
    			}
    			relatedRules.add(condition.toString(), rule);
    		}
    	}

    	final List<AndMatchSet> matchSets = new ArrayList<>();
    	for (final String key : relatedRules.keySet()) {
    		if (relatedRules.get(key).size() > 1) {
    			matchSets.add(new AndMatchSet(key, relatedRules.get(key)));
    		}
    	}
    	Collections.sort(matchSets, Comparator.comparingInt((final MatchSet<?> m) -> m.getChangeParts().size()).reversed());

    	TourCalculator<And> tc;
		try {
			tc = TourCalculator.calculateFor(
					Arrays.asList(rules),
					new ArrayList<>(new LinkedHashSet<>(matchSets)),
					(final And o1, final And o2) -> Integer.compare(indexOf(rules, o1), indexOf(rules, o2)),
					new TourCalculatorControl() {
						@Override
						public boolean isFastModeNeeded() {
							return false;
						}
						@Override
						public boolean isCanceled() {
							return false;
						}
					});
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}

        boolean first = true;
        for (final And r : tc.getTour()) {
            ret.append(first ? "  " : "  or ")
            	.append(r.toString(matchSets.stream().map((final AndMatchSet m) -> m.key).collect(Collectors.toList())))
            	.append("\n");
            first = false;
        }
    }

    private static<T> int indexOf(final T[] arr, final T item) {
    	return Arrays.asList(arr).indexOf(item);
    }

    public List<And> getRules(final int exceptionId) {
        return toAnd(this.exceptionConditions[exceptionId].getChildren());
    }

    public List<And> getRules(final String name) {
        final int index = this.getExceptionIndex(name);
        return index >= 0 ? this.getRules(index) : Collections.emptyList();
    }

    private int getExceptionIndex(final String name) {
        for (int i = 0; i < this.exceptionValues.length; i++) {
            if (this.exceptionValues[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static List<And> toAnd(final Rule[] children) {
        final List<And> ret = new ArrayList<>();
        for (final Rule r : children) {
            ret.add((And) r);
        }
        return ret;
    }

    public RuleSet simplify(final RecordSet data) {
        RuleSet ret = RuleSet.create(this.defaultValue);
        for (int i = 0; i < this.exceptionValues.length; i++) {
            final Set<And> newRules = new LinkedHashSet<>(toAnd(this.exceptionConditions[i].getChildren()));
            this.simplifySingleRules(newRules, data);
            if (newRules.size() > 0
                            && (ret.getExceptionCount() > 0 || !this.defaultValue.equals(this.exceptionValues[i]))) {
                ret = ret.addException(this.exceptionValues[i], new Or(toArr(newRules)));
            }
        }
        return ret;
    }

    private static And[] toArr(final Collection<And> newExclusions) {
        return newExclusions.toArray(new And[newExclusions.size()]);
    }

    private void simplifySingleRules(final Set<And> rules, final RecordSet data) {
        final Set<And> newRules = new LinkedHashSet<>();
        for (final And rule : rules) {
            newRules.add(this.simplifySingleRule(rule, data));
        }
        rules.clear();
        rules.addAll(newRules);
    }

    private And simplifySingleRule(final And rule, final RecordSet data) {
        final Multimap<String, SimpleRule> rulesPerFeature = new Multimap<>();
        for (final Rule child : rule.getChildren()) {
            if (child instanceof False) {
                return new And(child);
            } else if (child instanceof True) {
                //don't add, is a no-op
            } else {
                final SimpleRule sr = this.changeBinaryFeatureToEquality((SimpleRule) child, data);
                assert sr.getUsedFeatures().size() == 1;
                rulesPerFeature.add(sr.getUsedFeatures().keySet().iterator().next(), sr);
            }
        }

        final Set<SimpleRule> newChildren = new LinkedHashSet<>();
        for (final String feature : rulesPerFeature.keySet()) {
            newChildren.addAll(this.combineSimpleRules(feature, rulesPerFeature.get(feature)));
        }
        return new And(newChildren.toArray(new Rule[newChildren.size()]));
    }

    private SimpleRule changeBinaryFeatureToEquality(final SimpleRule r, final RecordSet data) {
		if (r instanceof NotEquals) {
			final NotEquals ne = (NotEquals) r;
			if (data.getPossibleStringValues(ne.getStringColumnIndex()).size() == 2) {
				final Set<String> values = new HashSet<>(data.getPossibleStringValues(ne.getStringColumnIndex()));
				values.remove(ne.getValue());
				if (values.size() == 1) {
					return new Equals(ne.getScheme(), ne.getColumn(), values.iterator().next());
				}
			}
		}
		return r;
	}

	private List<SimpleRule> combineSimpleRules(final String featureName, final List<SimpleRule> list) {
        if (list.size() == 1) {
            return list;
        }
        if (list.get(0) instanceof OrdinalRule) {
            double lowerBound = Double.NEGATIVE_INFINITY;
            double upperBound = Double.POSITIVE_INFINITY;
            final RecordScheme scheme = ((OrdinalRule) list.get(0)).getScheme();
            for (final SimpleRule r : list) {
                if (r instanceof Leq) {
                    upperBound = Math.min(upperBound, ((Leq) r).getValue());
                } else {
                    lowerBound = Math.max(lowerBound, ((Geq) r).getValue());
                }
            }
            if (lowerBound > upperBound) {
                return Collections.singletonList(new False());
            }
            final List<SimpleRule> ret = new ArrayList<>();
            if (lowerBound > Double.NEGATIVE_INFINITY) {
                ret.add(new Geq(scheme, scheme.getAbsIndex(featureName), lowerBound));
            }
            if (upperBound < Double.POSITIVE_INFINITY) {
                ret.add(new Leq(scheme, scheme.getAbsIndex(featureName), upperBound));
            }
            return ret;
        } else {
            //TODO implement
            return list;
        }
    }


    public String getDefault() {
        return this.defaultValue;
    }

    public int getExceptionCount() {
        return this.exceptionValues.length;
    }

    public String getStrategy(final int exceptionId) {
        return this.exceptionValues[exceptionId];
    }

    public RuleSet changeDefault(final String default1) {
        return new RuleSet(default1, this.exceptionConditions, this.exceptionValues);
    }

    public RuleSet addAll(final RuleSet r) {
        RuleSet ret = this;
        for (int exceptionId = 0; exceptionId < r.getExceptionCount(); exceptionId++) {
            final String name = r.getStrategy(exceptionId);
            for (final And and : r.getRules(exceptionId)) {
                ret = ret.addRule(name, and);
            }
        }
        return ret;
    }

}
