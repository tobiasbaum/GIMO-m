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

import java.util.Arrays;
import java.util.Comparator;

public abstract class CompositeRule extends Rule {
    protected Rule[] subRules;

    public CompositeRule(Rule[] rules) {
        this.subRules = rules;
        Arrays.sort(this.subRules, new Comparator<Rule>() {
            @Override
            public int compare(Rule o1, Rule o2) {
            	final int cmp1 = o1.getUsedFeatures().toString().compareTo(o2.getUsedFeatures().toString());
            	if (cmp1 != 0) {
            		return cmp1;
            	}
            	if (o1 instanceof OrdinalRule && o2 instanceof OrdinalRule) {
            		final int cmp2 = Double.compare(((OrdinalRule) o1).getValue(), ((OrdinalRule) o2).getValue());
            		if (cmp2 != 0) {
            			return cmp2;
            		}
            		if (o1 instanceof Leq && o2 instanceof Geq) {
            			return -1;
            		} else if (o1 instanceof Geq && o2 instanceof Leq) {
            			return 1;
            		}
            	} else if (o2 instanceof NominalRule && o2 instanceof NominalRule) {
            		final int cmp2 = ((NominalRule) o1).getValue().compareTo(((NominalRule) o2).getValue());
            		if (cmp2 != 0) {
            			return cmp2;
            		}
            	}
                return Integer.compare(o1.hashCode(), o2.hashCode());
            }
        });
    }

    public abstract CompositeRule createSameType(Rule child1, Rule child2);

    public Rule[] getChildren() {
        return this.subRules;
    }

    @Override
    public int getComplexity() {
        int c = 0;
        for (final Rule r : this.subRules) {
            if (!r.getClass().equals(this.getClass())) {
                c++;
            }
            c+= r.getComplexity();
        }
        return c;
    }

    @Override
    public Multiset<String> getUsedFeatures() {
        final Multiset<String> ret = Multiset.createOrdered();
        for (final Rule r : this.subRules) {
            ret.addAll(r.getUsedFeatures());
        }
        return ret;
    }

    @Override
    public int hashCode() {
        //assumes that the children are deterministically sorted
        return Arrays.hashCode(this.subRules);
    }

    @Override
    public boolean equals(Object o) {
        final CompositeRule r = (CompositeRule) o;
        //assumes that the children are deterministically sorted
        return Arrays.equals(this.subRules, r.subRules);
    }

    @Override
    public String toString() {
        return Arrays.toString(this.subRules);
    }

}
