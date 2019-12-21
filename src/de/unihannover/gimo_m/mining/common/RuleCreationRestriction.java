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
