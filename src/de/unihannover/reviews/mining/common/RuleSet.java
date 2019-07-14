package de.unihannover.reviews.mining.common;

import java.util.ArrayList;
import java.util.Arrays;
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

    private final And[] exclusions;
    private final And[] inclusions;
    private final String defaultValue;
    private final int hash;

    private RuleSet() {
        this("random", new And[0], new And[0]);
    }

    private RuleSet(String defaultValue, And[] exclusions, And[] inclusions) {
        this.defaultValue = defaultValue;
        this.exclusions = exclusions;
        this.inclusions = inclusions;
        this.hash = this.calculateHash();
    }

    private int calculateHash() {
        int ret = this.defaultValue.hashCode();
        for (final And ex : this.exclusions) {
            ret += ex.hashCode();
        }
        ret = Integer.reverse(ret);
        for (final And in : this.inclusions) {
            ret += in.hashCode();
        }
        return ret;
    }

    public static RuleSet create(String defaultValue2) {
        return create(defaultValue2, "random", new Or());
    }

    public static RuleSet create(String defaultStrategy, String otherStrategy, Or conditionForOther) {
        final And[] ands = new And[conditionForOther.getChildren().length];
        for (int i = 0; i < ands.length; i++) {
            ands[i] = (And) conditionForOther.getChildren()[i];
        }
        return new RuleSet(
                        defaultStrategy,
                        ands,
                        new And[0]);
    }

    public RuleSet include(And newRule) {
        if (Arrays.asList(this.inclusions).contains(newRule)) {
            return this;
        }
        final And[] newArr = Arrays.copyOf(this.inclusions, this.inclusions.length + 1);
        newArr[this.inclusions.length] = newRule;
        return new RuleSet(this.defaultValue, this.exclusions, newArr);
    }

    public RuleSet exclude(And newRule) {
        if (Arrays.asList(this.exclusions).contains(newRule)) {
            return this;
        }
        final And[] newArr = Arrays.copyOf(this.exclusions, this.exclusions.length + 1);
        newArr[this.exclusions.length] = newRule;
        return new RuleSet(this.defaultValue, newArr, this.inclusions);
    }

    public RuleSet removeInclusion(And toRemove) {
        return new RuleSet(this.defaultValue, this.exclusions, this.copyWithout(this.inclusions, toRemove));
    }

    public RuleSet removeExclusion(And toRemove) {
        return new RuleSet(this.defaultValue, this.copyWithout(this.exclusions, toRemove), this.inclusions);
    }

    private And[] copyWithout(And[] arr, And toRemove) {
        final List<And> ret = new ArrayList<>(Math.max(0, arr.length - 1));
        for (final And cur : arr) {
            if (!cur.equals(toRemove)) {
                ret.add(cur);
            }
        }
        return ret.toArray(new And[ret.size()]);
    }

	public RuleSet removeInclusions(
			RulePattern pattern,
			List<And> whitelist1,
			List<And> whitelist2) {

        return new RuleSet(this.defaultValue, this.exclusions, this.copyWithout(this.inclusions, pattern, whitelist1, whitelist2));
	}

	public RuleSet removeExclusions(
			RulePattern pattern,
			List<And> whitelist1,
			List<And> whitelist2) {

        return new RuleSet(this.defaultValue, this.copyWithout(this.exclusions, pattern, whitelist1, whitelist2), this.inclusions);
	}

    private And[] copyWithout(And[] arr, RulePattern pattern, List<And> whitelist1, List<And> whitelist2) {
        final List<And> ret = new ArrayList<>(arr.length);
        for (final And cur : arr) {
            if (!pattern.matches(cur)
        		|| whitelist1.contains(cur)
        		|| whitelist2.contains(cur)) {
                ret.add(cur);
            }
        }
        return ret.toArray(new And[ret.size()]);
    }

    public RuleSet replaceInclusion(And toReplace, And replacement) {
        return new RuleSet(this.defaultValue, this.exclusions, this.copyWithReplacement(this.inclusions, toReplace, replacement));
    }

    public RuleSet replaceExclusion(And toReplace, And replacement) {
        return new RuleSet(this.defaultValue, this.copyWithReplacement(this.exclusions, toReplace, replacement), this.inclusions);
    }

    private And[] copyWithReplacement(And[] arr, And toReplace, And replacement) {
        final And[] ret = Arrays.copyOf(arr, arr.length);
        for (int i = 0; i < ret.length; i++) {
            if (ret[i].equals(toReplace)) {
                ret[i] = replacement;
            }
        }
        return ret;
    }

    @Override
    public String apply(Record r) {
        for (final And exclusion : this.exclusions) {
            if (exclusion.test(r)) {
                return "fcfs";
            }
        }
        for (final And inclusion : this.inclusions) {
            if (inclusion.test(r)) {
                return "fcsls";
            }
        }
        return this.defaultValue;
    }

    @Override
    public int getComplexity() {
        int ret = 0;
        for (final And ex : this.exclusions) {
            ret += ex.getComplexity();
        }
        for (final And in : this.inclusions) {
            ret += in.getComplexity();
        }
        return ret;
    }

    @Override
    public int getFeatureCount() {
        final Set<String> features = new HashSet<>();
        for (final And ex : this.exclusions) {
            features.addAll(ex.getUsedFeatures().keySet());
        }
        for (final And in : this.inclusions) {
            features.addAll(in.getUsedFeatures().keySet());
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
        if (this.exclusions.length != r.exclusions.length || this.inclusions.length != r.inclusions.length) {
            return false;
        }
        return Arrays.asList(this.exclusions).containsAll(Arrays.asList(r.exclusions))
            && Arrays.asList(this.inclusions).containsAll(Arrays.asList(r.inclusions));
    }

    @Override
    public String toString() {
        final StringBuilder ret = new StringBuilder();
        ret.append("normally use ").append(this.defaultValue).append("\n");
        ret.append("skip when one of\n");
        this.printRules(ret, this.inclusions);
        if (this.exclusions.length > 0) {
            ret.append("unless one of\n");
            this.printRules(ret, this.exclusions);
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
        return Arrays.asList(this.inclusions);
    }

    public List<And> getExclusions() {
        return Arrays.asList(this.exclusions);
    }

    public RuleSet simplify(RecordSet data) {
        final Set<And> newInclusions = new LinkedHashSet<>(Arrays.asList(this.inclusions));
        final Set<And> newExclusions = new LinkedHashSet<>(Arrays.asList(this.exclusions));

        this.simplifySingleRules(newInclusions, data);
        this.simplifySingleRules(newExclusions, data);
        this.removeImplied(newInclusions, data);
        this.removeImplied(newExclusions, data);
        return new RuleSet(this.defaultValue, this.toArr(newExclusions), this.toArr(newInclusions));
    }

    private And[] toArr(Set<And> newExclusions) {
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

}
