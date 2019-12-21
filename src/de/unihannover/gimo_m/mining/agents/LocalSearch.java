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
package de.unihannover.gimo_m.mining.agents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import de.unihannover.gimo_m.mining.common.And;
import de.unihannover.gimo_m.mining.common.Blackboard;
import de.unihannover.gimo_m.mining.common.ConstantRule;
import de.unihannover.gimo_m.mining.common.NondominatedResults;
import de.unihannover.gimo_m.mining.common.OrdinalRule;
import de.unihannover.gimo_m.mining.common.Record;
import de.unihannover.gimo_m.mining.common.RecordSet;
import de.unihannover.gimo_m.mining.common.Rule;
import de.unihannover.gimo_m.mining.common.RuleSet;
import de.unihannover.gimo_m.mining.common.TargetFunction;
import de.unihannover.gimo_m.mining.common.ValuedResult;
import de.unihannover.gimo_m.util.Multimap;

public class LocalSearch {

    private static final int PLATEAU_STEP_LIMIT = 100;

    private final Blackboard blackboard;
    private final Random random;

    public LocalSearch(Blackboard blackboard, Random random) {
        this.blackboard = blackboard;
        this.random = random;
    }

    private static abstract class Move {

        public abstract RuleSet getNewRuleSet();

        public abstract void adaptPools(Multimap<String, And> inclusionPool);

        public abstract And getLastAddition();

        public abstract String getLastAdditionStrategy();

    }

    private static class AddInclusion extends Move {

        private final RuleSet soFar;
        private final String strategy;
        private final And newRule;

        public AddInclusion(RuleSet cur, String strategy, And incl) {
            this.soFar = cur;
            this.strategy = strategy;
            this.newRule = incl;
        }

        @Override
        public RuleSet getNewRuleSet() {
            return this.soFar.addRule(this.strategy, this.newRule);
        }

        @Override
        public void adaptPools(Multimap<String, And> inclusionPool) {
            inclusionPool.removeAll(this.strategy, Collections.singleton(this.newRule));
        }

        @Override
        public And getLastAddition() {
            return this.newRule;
        }

        @Override
        public String getLastAdditionStrategy() {
            return this.strategy;
        }

    }

    private static class RuleChange extends Move {

        private final RuleSet soFar;
        private final String strategy;
        private final And toReplace;
        private final And replacement;

        public RuleChange(RuleSet cur, String strategy, And toReplace, And replacement) {
            this.soFar = cur;
            this.strategy = strategy;
            this.toReplace = toReplace;
            this.replacement = replacement;
        }

        @Override
        public RuleSet getNewRuleSet() {
            return this.soFar.replaceRule(this.strategy, this.toReplace, this.replacement);
        }

        @Override
        public void adaptPools(Multimap<String, And> inclusionPool) {
        }

        @Override
        public And getLastAddition() {
            return this.replacement;
        }

        @Override
        public String getLastAdditionStrategy() {
            return this.strategy;
        }

    }

    public NondominatedResults<RuleSet> optimizeByLocalSearch(ValuedResult<RuleSet> initial, TargetFunction direction) {
        final NondominatedResults<RuleSet> ret = new NondominatedResults<>();
        ret.add(this.blackboard.makeValidAndEvaluate(initial.getItem()));
        final RuleSet initialStub = RuleSet.create(initial.getItem().getDefault());
        ret.add(this.blackboard.makeValidAndEvaluate(initialStub));

        ValuedResult<RuleSet> cur = this.blackboard.makeValidAndEvaluate(initialStub);

        final Multimap<String, And> inclusionPool = new Multimap<>();
        for (int exceptionId = 0; exceptionId < initial.getItem().getExceptionCount(); exceptionId++) {
            final String strategy = initial.getItem().getStrategy(exceptionId);
            inclusionPool.addAll(strategy, initial.getItem().getRules(exceptionId));
            inclusionPool.removeAll(strategy, cur.getItem().getRules(strategy));
        }
//        this.removeRejected(inclusionPool, this.blackboard.inclusionRestrictions());

        And lastAddition = null;
        String lastAdditionStrategy = null;

        boolean searchInRuleChangeNeighborhood = false;
        int stepsOnPlateau = 0;

        while (!Thread.currentThread().isInterrupted()) {
            final List<? extends Move> neighborhood;
            if (searchInRuleChangeNeighborhood) {
                neighborhood = this.determineRuleChangeNeighborhood(cur.getItem(), lastAdditionStrategy, lastAddition);
            } else {
                neighborhood = this.determineRuleAddNeighborhood(cur.getItem(), inclusionPool);
            }
            Collections.shuffle(neighborhood, this.random);
            //find the best neighbor
            Move bestMove = null;
            ValuedResult<RuleSet> bestSoFar = cur;
            boolean bestIsPlateau = false;
            for (final Move neighbor : neighborhood) {
                final ValuedResult<RuleSet> evaluated = this.blackboard.makeValidAndEvaluate(neighbor.getNewRuleSet());
                this.blackboard.simplifyEvaluateAndAdd(evaluated.getItem());
                final boolean couldBeAdded = ret.add(evaluated);
                //A neighbor is better when it improves (=minimizes) the target function. We also regard it as
                //  better if the value is the same and it has not been visited so far, to be able to traverse
                //  plateaus
                final double valueNew = direction.applyAsDouble(evaluated);
                final double valueBest = direction.applyAsDouble(bestSoFar);
                if (valueNew < valueBest || (valueNew == valueBest && couldBeAdded)) {
                	bestIsPlateau = evaluated.hasSameValues(cur);
                    bestMove = neighbor;
                    bestSoFar = evaluated;
                }
            }

            if (bestIsPlateau) {
            	stepsOnPlateau++;
        		if (stepsOnPlateau > PLATEAU_STEP_LIMIT) {
            		this.blackboard.log("terminating search in neighborhood after being on plateau for " + stepsOnPlateau + " steps");
            		bestMove = null;
        		}
            } else {
            	stepsOnPlateau = 0;
            }

            if (bestMove == null) {
                if (searchInRuleChangeNeighborhood) {
                    //rule changing does not improve anything, look for a new rule
                    searchInRuleChangeNeighborhood = false;
                    continue;
                } else {
                    //rule adding does not help, stop searching
                    break;
                }
            }
            cur = bestSoFar;
            bestMove.adaptPools(inclusionPool);
            lastAdditionStrategy = bestMove.getLastAdditionStrategy();
            lastAddition = bestMove.getLastAddition();
            searchInRuleChangeNeighborhood = true;
        }
        return ret;
    }

	private List<? extends Move> determineRuleAddNeighborhood(
                    RuleSet cur,
                    Multimap<String, And> inclusionPool) {

        final List<Move> ret = new ArrayList<>();
        for (final String strategy : inclusionPool.keySet()) {
            for (final And incl : inclusionPool.get(strategy)) {
                ret.add(new AddInclusion(cur, strategy, incl));
            }
        }
        return ret;
    }

    private List<? extends Move> determineRuleChangeNeighborhood(
                    RuleSet cur,
                    String lastAdditionStrategy,
                    And lastAddition) {

        final List<Move> ret = new ArrayList<>();
        if (lastAddition != null) {
            for (final And newRule : this.determineNeighborhood(lastAddition)) {
                ret.add(new RuleChange(cur, lastAdditionStrategy, lastAddition, newRule));
            }
        }
        return ret;
    }

    private List<And> determineNeighborhood(And and) {
    	final RecordSet records = this.blackboard.getRecords().getRecords();

        final List<And> ret = new ArrayList<>();
        if (and.getChildren().length > 1) {
            for (final Rule child : and.getChildren()) {
                ret.add(and.copyWithoutChild(child));
            }
        }
        ret.add(and.and(records.createRandomSimpleRule(this.random)));
        for (final Rule child : and.getChildren()) {
            if (child instanceof OrdinalRule) {
                this.findSplitPointWhereMatchedRecordsChange(ret, and, child, records, r -> r.nextLargerValue(records));
                this.findSplitPointWhereMatchedRecordsChange(ret, and, child, records, r -> r.nextSmallerValue(records));
            }
        }
        return ret;
    }

    /**
     * When there are multiple conditions in the rule, it is pretty unlikely that the next split point will
     * change the set of matched records. To avoid excessive meandering on the resulting plateau, search
     * for a split point that will change the set of matched records.
     */
    private void findSplitPointWhereMatchedRecordsChange(
    		List<And> ret, And and, Rule child, RecordSet records, Function<OrdinalRule, Rule> adjustment) {
    	final int initialCount = this.countMatchedRecords(and, records);
    	Rule cur = child;
    	And newAnd;
    	do {
    		cur = adjustment.apply((OrdinalRule) cur);
            if (cur instanceof ConstantRule) {
                return;
            }
            newAnd = and.copyWithReplacedChild(child, cur);
    	} while (this.countMatchedRecords(newAnd, records) == initialCount);
    	ret.add(newAnd);
	}

	private int countMatchedRecords(And newAnd, RecordSet records) {
		int count = 0;
		for (final Record r : records.getRecords()) {
			if (newAnd.test(r)) {
				count++;
			}
		}
		return count;
	}

}
