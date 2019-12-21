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
import java.util.LinkedHashSet;
import java.util.List;

public class And extends CompositeRule {

    public And(Rule... subrules) {
        super(subrules);
    }

    @Override
    public boolean test(Record r) {
        for (final Rule rule : this.subRules) {
            if (!rule.test(r)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof And) && super.equals(o);
    }

    @Override
    public String toString() {
        final List<String> elements = new ArrayList<>();
        for (final Rule r : this.subRules) {
            elements.add(r.toString());
        }
        return "(" + String.join(" and ", elements.toArray(new String[elements.size()])) + ")";
    }

    public String toString(List<String> featureOrder) {
    	final List<Rule> sortedRules = new ArrayList<>(Arrays.asList(this.subRules));
    	sortedRules.sort((Rule r1, Rule r2) -> {
    		return Integer.compare(
    				this.determineFeatureIndex(featureOrder, r1),
    				this.determineFeatureIndex(featureOrder, r2));
    	});
        final List<String> elements = new ArrayList<>();
        for (final Rule r : sortedRules) {
            elements.add(r.toString());
        }
        return "(" + String.join(" and ", elements.toArray(new String[elements.size()])) + ")";
    }

    private int determineFeatureIndex(List<String> featureOrder, Rule r) {
    	int ret = -1;
    	for (final String feature : r.getUsedFeatures().keySet()) {
    		final int idx = featureOrder.indexOf(feature);
    		if (idx < 0) {
    			ret = Integer.MAX_VALUE;
    		} else {
    			ret = Math.max(ret, idx);
    		}
    	}
		return ret;
	}

	@Override
    public CompositeRule createSameType(Rule child1, Rule child2) {
        return new And(child1, child2);
    }

    public And copyWithoutChild(Rule childToExclude) {
        final List<Rule> newChildren = new ArrayList<>(this.subRules.length - 1);
        for (final Rule child : this.subRules) {
            if (!child.equals(childToExclude)) {
                newChildren.add(child);
            }
        }
        return new And(newChildren.toArray(new Rule[newChildren.size()]));
    }

    public And copyWithReplacedChild(Rule childToReplace, Rule replacement) {
        final LinkedHashSet<Rule> newChildren = new LinkedHashSet<>(this.subRules.length);
        for (final Rule child : this.subRules) {
            if (child.equals(childToReplace)) {
                newChildren.add(replacement);
            } else {
                newChildren.add(child);
            }
        }
        return new And(newChildren.toArray(new Rule[newChildren.size()]));
    }

    public And and(SimpleRule rule) {
        final Rule[] newChildren = Arrays.copyOf(this.subRules, this.subRules.length + 1);
        newChildren[newChildren.length - 1] = rule;
        return new And(newChildren);
    }

}
