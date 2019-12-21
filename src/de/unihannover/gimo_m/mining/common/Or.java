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
        return "(" + String.join(" or ", elements.toArray(new String[elements.size()])) + ")";
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

    public Or or(Rule rule) {
        final Rule[] newChildren = Arrays.copyOf(this.subRules, this.subRules.length + 1);
        newChildren[newChildren.length - 1] = rule;
        return new Or(newChildren);
    }

}
