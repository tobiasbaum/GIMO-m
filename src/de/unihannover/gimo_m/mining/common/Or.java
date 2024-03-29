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

public class Or extends CompositeRule {

    public Or(Rule... subrules) {
        super(subrules);
    }

    @Override
    public boolean test(Record r) {
        for (final Rule rule : this.subRules) {
            if (rule.test(r)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof Or) && super.equals(o);
    }

    @Override
    public String toString() {
        final List<String> elements = new ArrayList<>();
        for (final Rule r : this.subRules) {
            elements.add(r.toString());
        }
        return "(" + String.join(" or ", elements.toArray(new String[elements.size()])) + ")";
    }

    @Override
    public CompositeRule createSameType(Rule child1, Rule child2) {
        return new Or(child1, child2);
    }

    public Or copyWithoutChild(Rule childToExclude) {
        final List<Rule> newChildren = new ArrayList<>(this.subRules.length - 1);
        for (final Rule child : this.subRules) {
            if (!child.equals(childToExclude)) {
                newChildren.add(child);
            }
        }
        return new Or(newChildren.toArray(new Rule[newChildren.size()]));
    }

    public Or copyWithReplacedChild(Rule childToReplace, Rule replacement) {
        final LinkedHashSet<Rule> newChildren = new LinkedHashSet<>(this.subRules.length);
        for (final Rule child : this.subRules) {
            if (child.equals(childToReplace)) {
                newChildren.add(replacement);
            } else {
                newChildren.add(child);
            }
        }
        return new Or(newChildren.toArray(new Rule[newChildren.size()]));
    }

    public Or or(And rule) {
        final Rule[] newChildren = Arrays.copyOf(this.subRules, this.subRules.length + 1);
        newChildren[newChildren.length - 1] = rule;
        return new Or(newChildren);
    }

    public Or or(Or rule) {
        final Rule[] newChildren = Arrays.copyOf(this.subRules, this.subRules.length + rule.subRules.length);
        System.arraycopy(rule.subRules, 0, newChildren, this.subRules.length, rule.subRules.length);
        return new Or(newChildren);
    }

}
