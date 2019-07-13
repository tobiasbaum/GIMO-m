package de.unihannover.reviews.mining.common;

import java.util.Set;

public class RuleCreationRestriction {

	private final Set<SimpleRulePattern> invalidPatterns;
	private final Set<Rule> invalidRules;

	public RuleCreationRestriction(Set<SimpleRulePattern> invalidPatterns, Set<Rule> invalidRules) {
		this.invalidPatterns = invalidPatterns;
		this.invalidRules = invalidRules;
	}

	public boolean canBeValid(int columnIndex, Class<? extends SimpleRule> ruleType) {
		return !this.invalidPatterns.contains(new SimpleRulePattern(null, columnIndex, ruleType));
	}

	public boolean canBeValid(SimpleRule rule) {
		return !this.invalidRules.contains(rule)
			&& this.canBeValid(rule.getColumn(), rule.getClass());
	}

}
