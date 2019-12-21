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

public class SimpleRulePattern {

	private final RecordScheme scheme;
	private final int absIndex;
	private final Class<? extends SimpleRule> ruleType;

	public SimpleRulePattern(RecordScheme scheme, int absIndex, Class<? extends SimpleRule> ruleType) {
		this.scheme = scheme;
		this.absIndex = absIndex;
		this.ruleType = ruleType;
	}

	public boolean matches(SimpleRule cur) {
		return cur.getColumn() == this.absIndex
			&& cur.getClass().equals(this.ruleType);
	}

	@Override
	public int hashCode() {
		return this.absIndex + this.ruleType.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SimpleRulePattern)) {
			return false;
		}
		final SimpleRulePattern p = (SimpleRulePattern) o;
		return this.absIndex == p.absIndex
			&& this.ruleType.equals(p.ruleType);
	}

	@Override
	public String toString() {
		return this.scheme.getName(this.absIndex) + " " + this.getOperator() + " *";
	}

	private String getOperator() {
		if (this.ruleType.equals(Leq.class)) {
			return "<=";
		} else if (this.ruleType.equals(Geq.class)) {
			return ">=";
		} else if (this.ruleType.equals(Equals.class)) {
			return "==";
		} else if (this.ruleType.equals(NotEquals.class)) {
			return "!=";
		} else {
			throw new AssertionError("unexpected type " + this.ruleType);
		}
	}

}
