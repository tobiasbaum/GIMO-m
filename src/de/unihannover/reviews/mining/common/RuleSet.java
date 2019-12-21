package de.unihannover.reviews.mining.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.setsoftware.reviewtool.ordering.efficientalgorithm.MatchSet;
import de.setsoftware.reviewtool.ordering.efficientalgorithm.TourCalculator;
import de.setsoftware.reviewtool.ordering.efficientalgorithm.TourCalculatorControl;
import de.unihannover.reviews.predictionDataPreparation.Multimap;

public class RuleSet implements Function<Record, String>, ItemWithComplexity {

    private final String defaultValue;
    private final String[] exceptionValues;
    private final Or[] exceptionConditions;
    private final int hash;

    private RuleSet(String defaultValue, Or[] exceptionConditions, String[] exceptionValues) {
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

    public static RuleSet create(String defaultValue2) {
        return new RuleSet(defaultValue2, new Or[0], new String[0]);
    }

    public RuleSet addRule(String strategy, And newRule) {
        final int index = this.getExceptionIndex(strategy);
        if (index >= 0) {
            return this.addRule(index, newRule);
        } else {
            return this.addException(strategy, new Or(newRule));
        }
    }

    public RuleSet addException(String strategy, Or or) {
        final int oldLen = this.exceptionValues.length;
        final String[] newExceptions = Arrays.copyOf(this.exceptionValues, oldLen + 1);
        final Or[] newConditions = Arrays.copyOf(this.exceptionConditions, oldLen + 1);
        newExceptions[oldLen] = strategy;
        newConditions[oldLen] = or;
        return new RuleSet(this.defaultValue, newConditions, newExceptions);
    }

    private RuleSet addRule(int exceptionId, And newRule) {
        final Or changedRules = this.exceptionConditions[exceptionId].or(newRule);
        return new RuleSet(this.defaultValue, this.changeOneException(exceptionId, changedRules), this.exceptionValues);
    }

    public RuleSet include(And newRule) {
        //TEST TODO
        return this;
    }

    public RuleSet exclude(And newRule) {
        //TEST TODO
        return this;
    }

    public RuleSet removeInclusion(And toRemove) {
        //TEST TODO
        return this;
    }

    public RuleSet removeExclusion(And toRemove) {
        //TEST TODO
        return this;
    }

    public RuleSet removeRule(String strategy, And toRemove) {
        final int exceptionId = this.getExceptionIndex(strategy);
        if (exceptionId >= 0) {
            return this.removeRule(exceptionId, toRemove);
        } else {
            return this;
        }
    }

    public RuleSet removeRule(int exceptionId, And toRemove) {
        final Or changedRules = this.exceptionConditions[exceptionId].copyWithoutChild(toRemove);
        return new RuleSet(this.defaultValue, this.changeOneException(exceptionId, changedRules), this.exceptionValues);
    }

    private Or[] changeOneException(int exceptionId, Or changedRules) {
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

	public RuleSet removeInclusions(
			RulePattern pattern,
			List<And> whitelist1,
			List<And> whitelist2) {

        //TEST TODO
        return this;
	}

	public RuleSet removeExclusions(
			RulePattern pattern,
			List<And> whitelist1,
			List<And> whitelist2) {

        //TEST TODO
        return this;
	}

    public RuleSet replaceRule(String strategy, And toReplace, And replacement) {
        final int exceptionId = this.getExceptionIndex(strategy);
        if (exceptionId < 0) {
            return this;
        }
        final Or replaced = this.exceptionConditions[exceptionId].copyWithReplacedChild(toReplace, replacement);
        return new RuleSet(this.defaultValue, this.changeOneException(exceptionId, replaced), this.exceptionValues);
    }

    public RuleSet replaceInclusion(And toReplace, And replacement) {
        //TEST TODO
        return this;
    }

    public RuleSet replaceExclusion(And toReplace, And replacement) {
        //TEST TODO
        return this;
    }

    @Override
    public String apply(Record r) {
        for (int i = 0; i < this.exceptionConditions.length; i++) {
            if (this.exceptionConditions[i].test(r)) {
                return this.exceptionValues[i];
            }
        }
        return this.defaultValue;
    }

    @Override
    public int getComplexity() {
        int ret = 0;
        for (final Or ex : this.exceptionConditions) {
            ret += 1 + ex.getComplexity();
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
    public boolean equals(Object o) {
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

    	public AndMatchSet(String key, List<And> list) {
    		super(list);
    		this.key = key;
		}

    }

    private void printRules(StringBuilder ret, And[] rules) {
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
    	Collections.sort(matchSets, Comparator.comparingInt((MatchSet<?> m) -> m.getChangeParts().size()).reversed());

    	TourCalculator<And> tc;
		try {
			tc = TourCalculator.calculateFor(
					Arrays.asList(rules),
					new ArrayList<>(matchSets),
					Collections.emptyList(),
					(And o1, And o2) -> Integer.compare(indexOf(rules, o1), indexOf(rules, o2)),
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
            	.append(r.toString(matchSets.stream().map((AndMatchSet m) -> m.key).collect(Collectors.toList())))
            	.append("\n");
            first = false;
        }
    }

    private static<T> int indexOf(T[] arr, T item) {
    	return Arrays.asList(arr).indexOf(item);
    }

    public List<And> getInclusions() {
        return Collections.emptyList();
    }

    public List<And> getRules(int exceptionId) {
        return toAnd(this.exceptionConditions[exceptionId].getChildren());
    }

    public List<And> getRules(String name) {
        final int index = this.getExceptionIndex(name);
        return index >= 0 ? this.getRules(index) : Collections.emptyList();
    }

    private int getExceptionIndex(String name) {
        for (int i = 0; i < this.exceptionValues.length; i++) {
            if (this.exceptionValues[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    public List<And> getExclusions() {
        return this.exceptionConditions.length == 0 ? Collections.emptyList() : toAnd(this.exceptionConditions[0].getChildren());
    }

    private static List<And> toAnd(Rule[] children) {
        final List<And> ret = new ArrayList<>();
        for (final Rule r : children) {
            ret.add((And) r);
        }
        return ret;
    }

    public RuleSet simplify(RecordSet data) {
        return this.removeUnnecessaryExceptions();
//        final Set<And> newInclusions = new LinkedHashSet<>(Arrays.asList(this.inclusions));
//        final Set<And> newExclusions = new LinkedHashSet<>(Arrays.asList(this.exclusions));
//
//        this.simplifySingleRules(newInclusions, data);
//        this.simplifySingleRules(newExclusions, data);
//        this.removeImplied(newInclusions, data);
//        this.removeImplied(newExclusions, data);
//        return new RuleSet(this.defaultValue, this.toArr(newExclusions), this.toArr(newInclusions));
    }

    private RuleSet removeUnnecessaryExceptions() {
        final List<String> strategies = new ArrayList<>(this.exceptionValues.length);
        final List<Or> conditions = new ArrayList<>(this.exceptionConditions.length);
        for (int i = 0; i < this.exceptionConditions.length; i++) {
            if (this.exceptionConditions[i].getChildren().length > 0) {
                strategies.add(this.exceptionValues[i]);
                conditions.add(this.exceptionConditions[i]);
            }
        }
        if (strategies.size() == this.exceptionValues.length) {
            return this;
        } else {
            return new RuleSet(this.defaultValue,
                            conditions.toArray(new Or[conditions.size()]),
                            strategies.toArray(new String[strategies.size()]));
        }
    }

    private static And[] toArr(Collection<And> newExclusions) {
        return newExclusions.toArray(new And[newExclusions.size()]);
    }

    private void simplifySingleRules(Set<And> rules, RecordSet data) {
        final Set<And> newRules = new LinkedHashSet<>();
        for (final And rule : rules) {
            newRules.add(this.simplifySingleRule(rule, data));
        }
        rules.clear();
        rules.addAll(newRules);
    }

    private And simplifySingleRule(And rule, RecordSet data) {
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

    private SimpleRule changeBinaryFeatureToEquality(SimpleRule r, RecordSet data) {
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

	private List<SimpleRule> combineSimpleRules(String featureName, List<SimpleRule> list) {
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

    private void removeImplied(Set<And> rules, RecordSet data) {
        final Iterator<And> iter = rules.iterator();
        while (iter.hasNext()) {
            final And cur = iter.next();
            if (this.isMoreSpecific(cur, rules, data)) {
                iter.remove();
            }
        }
    }

    private boolean isMoreSpecific(And cur, Set<And> rules, RecordSet data) {
        for (final And rule : rules) {
            if (!cur.equals(rule) && this.isMoreSpecific(cur, rule, data)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMoreSpecific(And r1, And r2, RecordSet data) {
        final List<Rule> rules = new ArrayList<>();
        rules.addAll(Arrays.asList(r1.getChildren()));
        rules.addAll(Arrays.asList(r2.getChildren()));
        final And combined = new And(rules.toArray(new Rule[rules.size()]));
        final And simplifiedCombined = this.simplifySingleRule(combined, data);
        return simplifiedCombined.equals(r1);
    }

    public String getDefault() {
        return this.defaultValue;
    }

    public int getExceptionCount() {
        return this.exceptionValues.length;
    }

    public String getStrategy(int exceptionId) {
        return this.exceptionValues[exceptionId];
    }

    public RuleSet changeDefault(String default1) {
        return new RuleSet(default1, this.exceptionConditions, this.exceptionValues);
    }

}
