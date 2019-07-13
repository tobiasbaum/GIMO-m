package de.unihannover.reviews.mining.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class RulePattern {

	private final List<Rule> fullyQualified;
	private final boolean otherConditionWildcard;
	private final List<SimpleRulePattern> valueWildcards;

	public RulePattern(List<Rule> fullyQualified, List<SimpleRulePattern> valueWildcards, boolean otherConditionWildcard) {
		this.fullyQualified = fullyQualified;
		this.valueWildcards = valueWildcards;
		this.otherConditionWildcard = otherConditionWildcard;
	}

	public boolean matches(And and) {
		final Set<Rule> notMatched = new HashSet<>(Arrays.asList(and.getChildren()));
		for (final Rule r : this.fullyQualified) {
			final boolean couldRemove = notMatched.remove(r);
			if (!couldRemove) {
				return false;
			}
		}
		for (final SimpleRulePattern p : this.valueWildcards) {
			final boolean foundMatch = this.removeMatching(notMatched, p);
			if (!foundMatch) {
				return false;
			}
		}
		return this.otherConditionWildcard || notMatched.isEmpty();
	}

	private boolean removeMatching(final Set<Rule> notMatched, final SimpleRulePattern p) {
		final Iterator<Rule> iter = notMatched.iterator();
		while (iter.hasNext()) {
			final Rule cur = iter.next();
			if (cur instanceof SimpleRule && p.matches((SimpleRule) cur)) {
				iter.remove();
				return true;
			}
		}
		return false;
	}

	public static RulePattern createExact(And and) {
		return new RulePattern(Arrays.asList(and.getChildren()), Collections.emptyList(), false);
	}

	public static RulePattern parse(RecordScheme scheme, String string) {
		final RuleSetParser p = new RuleSetParser(scheme);
		final List<Rule> fullyQualified = new ArrayList<>();
		final List<SimpleRulePattern> valueWildcards = new ArrayList<>();
		boolean otherConditionWildcard = false;
		for (final String part : p.splitAtAnd(string.startsWith("(") ? string : ("(" + string + ")"))) {
			final String trimmed = part.trim();
			if (trimmed.equals("*")) {
				otherConditionWildcard = true;
			} else if (trimmed.endsWith("*")) {
				valueWildcards.add(parseSimpleRulePattern(scheme, p, trimmed));
			} else {
				fullyQualified.add(p.parseSimpleRule(trimmed));
			}
		}
		return new RulePattern(fullyQualified, valueWildcards, otherConditionWildcard);
	}

	private static SimpleRulePattern parseSimpleRulePattern(RecordScheme scheme, RuleSetParser p, String trimmed) {
		final String withoutStar = trimmed.substring(0, trimmed.length() - 1);
		SimpleRule dummyRule;
		try {
			dummyRule = p.parseSimpleRule(withoutStar + "''");
		} catch (final IllegalArgumentException e) {
			dummyRule = p.parseSimpleRule(withoutStar + "0");
		}
		return new SimpleRulePattern(
				scheme,
				scheme.getAbsIndex(dummyRule.getUsedFeatures().keySet().iterator().next()),
				dummyRule.getClass());
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RulePattern)) {
			return false;
		}
		final RulePattern p = (RulePattern) o;
		return this.fullyQualified.equals(p.fullyQualified)
			&& this.valueWildcards.equals(p.valueWildcards)
			&& this.otherConditionWildcard == p.otherConditionWildcard;
	}

	@Override
	public int hashCode() {
		return this.fullyQualified.hashCode()
				+ this.valueWildcards.hashCode()
				+ (this.otherConditionWildcard ? 23 : 34);
	}

	@Override
	public String toString() {
		final StringBuilder ret = new StringBuilder();
		boolean first = true;
		for (final Rule r : this.fullyQualified) {
			if (!first) {
				ret.append(" and ");
			} else {
				first = false;
			}
			ret.append(r);
		}
		for (final SimpleRulePattern r : this.valueWildcards) {
			if (!first) {
				ret.append(" and ");
			} else {
				first = false;
			}
			ret.append(r);
		}
		if (this.otherConditionWildcard) {
			if (!first) {
				ret.append(" and ");
			}
			ret.append("*");
		}
		return ret.toString();
	}

	public void findCompletionToInvalid(
			And priorRule, Collection<Rule> ruleBuffer, Collection<SimpleRulePattern> patternBuffer) {
		if (!this.otherConditionWildcard) {
			//when there is no wildcard in the pattern, it is always possible to make the
			//  rule valid again by adding more conditions
			return;
		}

		final Set<Rule> notMatched = new HashSet<>(Arrays.asList(priorRule.getChildren()));
		int missingCount = 0;
		Rule missingRule = null;
		for (final Rule r : this.fullyQualified) {
			final boolean couldRemove = notMatched.remove(r);
			if (!couldRemove) {
				missingCount++;
				if (missingCount > 1) {
					return;
				}
				missingRule = r;
			}
		}
		SimpleRulePattern missingPattern = null;
		for (final SimpleRulePattern p : this.valueWildcards) {
			final boolean foundMatch = this.removeMatching(notMatched, p);
			if (!foundMatch) {
				missingCount++;
				if (missingCount > 1) {
					return;
				}
				missingPattern = p;
			}
		}

		if (missingCount == 1) {
			if (missingPattern != null) {
				patternBuffer.add(missingPattern);
			} else {
				ruleBuffer.add(missingRule);
			}
		}
	}

}
