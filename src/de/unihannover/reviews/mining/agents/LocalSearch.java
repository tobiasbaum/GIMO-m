package de.unihannover.reviews.mining.agents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

import de.unihannover.reviews.mining.common.And;
import de.unihannover.reviews.mining.common.Blackboard;
import de.unihannover.reviews.mining.common.Blackboard.RestrictionClassification;
import de.unihannover.reviews.mining.common.Blackboard.RuleRestrictions;
import de.unihannover.reviews.mining.common.ConstantRule;
import de.unihannover.reviews.mining.common.NondominatedResults;
import de.unihannover.reviews.mining.common.OrdinalRule;
import de.unihannover.reviews.mining.common.Record;
import de.unihannover.reviews.mining.common.RecordSet;
import de.unihannover.reviews.mining.common.Rule;
import de.unihannover.reviews.mining.common.RuleSet;
import de.unihannover.reviews.mining.common.TargetFunction;
import de.unihannover.reviews.mining.common.ValuedResult;

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

        public abstract void adaptPools(LinkedHashSet<And> inclusionPool, LinkedHashSet<And> exclusionPool);

        public abstract And getLastAddition();

    }

    private static class AddInclusion extends Move {

        private final RuleSet soFar;
        private final And newRule;

        public AddInclusion(RuleSet cur, And incl) {
            this.soFar = cur;
            this.newRule = incl;
        }

        @Override
        public RuleSet getNewRuleSet() {
            return this.soFar.include(this.newRule);
        }

        @Override
        public void adaptPools(LinkedHashSet<And> inclusionPool, LinkedHashSet<And> exclusionPool) {
            inclusionPool.remove(this.newRule);
        }

        @Override
        public And getLastAddition() {
            return this.newRule;
        }

    }

    private static class AddExclusion extends Move {

        private final RuleSet soFar;
        private final And newRule;

        public AddExclusion(RuleSet cur, And excl) {
            this.soFar = cur;
            this.newRule = excl;
        }

        @Override
        public RuleSet getNewRuleSet() {
            return this.soFar.exclude(this.newRule);
        }

        @Override
        public void adaptPools(LinkedHashSet<And> inclusionPool, LinkedHashSet<And> exclusionPool) {
            exclusionPool.remove(this.newRule);
        }

        @Override
        public And getLastAddition() {
            return this.newRule;
        }

    }

    private static class RuleChange extends Move {

        private final RuleSet soFar;
        private final And toReplace;
        private final And replacement;

        public RuleChange(RuleSet cur, And toReplace, And replacement) {
            this.soFar = cur;
            this.toReplace = toReplace;
            this.replacement = replacement;
        }

        @Override
        public RuleSet getNewRuleSet() {
            return this.soFar.replaceExclusion(this.toReplace, this.replacement).replaceInclusion(this.toReplace, this.replacement);
        }

        @Override
        public void adaptPools(LinkedHashSet<And> inclusionPool, LinkedHashSet<And> exclusionPool) {
        }

        @Override
        public And getLastAddition() {
            return this.replacement;
        }

    }

    public NondominatedResults<RuleSet> optimizeByLocalSearch(ValuedResult<RuleSet> initial, TargetFunction direction) {
        final NondominatedResults<RuleSet> ret = new NondominatedResults<>();
        ret.add(this.blackboard.makeValidAndEvaluate(initial.getItem()));
        final RuleSet initialStub = RuleSet.create(initial.getItem().getDefault());
        ret.add(this.blackboard.makeValidAndEvaluate(initialStub));

        ValuedResult<RuleSet> cur = this.blackboard.makeValidAndEvaluate(initialStub);

        final LinkedHashSet<And> inclusionPool = new LinkedHashSet<>();
        inclusionPool.addAll(initial.getItem().getInclusions());
        inclusionPool.removeAll(cur.getItem().getInclusions());
        this.removeRejected(inclusionPool, this.blackboard.inclusionRestrictions());

        final LinkedHashSet<And> exclusionPool = new LinkedHashSet<>();
        exclusionPool.addAll(initial.getItem().getExclusions());
        exclusionPool.removeAll(cur.getItem().getExclusions());
        this.removeRejected(exclusionPool, this.blackboard.exclusionRestrictions());

        And lastAddition = null;

        boolean searchInRuleChangeNeighborhood = false;
        int stepsOnPlateau = 0;

        while (!Thread.currentThread().isInterrupted()) {
            final List<? extends Move> neighborhood;
            if (searchInRuleChangeNeighborhood) {
                neighborhood = this.determineRuleChangeNeighborhood(cur.getItem(), lastAddition);
            } else {
                neighborhood = this.determineRuleAddNeighborhood(cur.getItem(), inclusionPool, exclusionPool);
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
            bestMove.adaptPools(inclusionPool, exclusionPool);
            lastAddition = bestMove.getLastAddition();
            searchInRuleChangeNeighborhood = true;
        }
        return ret;
    }

    private void removeRejected(Set<And> pool, RuleRestrictions restrictions) {
    	final Iterator<And> iter = pool.iterator();
    	while (iter.hasNext()) {
    		final And cur = iter.next();
    		if (restrictions.classify(cur) == RestrictionClassification.REJECTED) {
    			iter.remove();
    		}
    	}
	}

	private List<? extends Move> determineRuleAddNeighborhood(
                    RuleSet cur,
                    LinkedHashSet<And> inclusionPool,
                    LinkedHashSet<And> exclusionPool) {

        final List<Move> ret = new ArrayList<>();
        for (final And incl : inclusionPool) {
            ret.add(new AddInclusion(cur, incl));
        }
        for (final And excl : exclusionPool) {
            ret.add(new AddExclusion(cur, excl));
        }
        return ret;
    }

    private List<? extends Move> determineRuleChangeNeighborhood(
                    RuleSet cur,
                    And lastAddition) {

        final List<Move> ret = new ArrayList<>();
        if (lastAddition != null) {
            for (final And newRule : this.determineNeighborhood(lastAddition)) {
                ret.add(new RuleChange(cur, lastAddition, newRule));
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
