package de.unihannover.reviews.mining.agents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import de.unihannover.reviews.mining.common.And;
import de.unihannover.reviews.mining.common.Blackboard;
import de.unihannover.reviews.mining.common.RuleSet;
import de.unihannover.reviews.mining.common.TargetFunction;
import de.unihannover.reviews.mining.common.ValuedResult;

public class PathRelinking {

	private final Blackboard blackboard;
    private final Random random;

    public PathRelinking(Blackboard blackboard, Random random) {
        this.blackboard = blackboard;
        this.random = random;
    }

    public void performWith(ValuedResult<RuleSet> rs) {
        this.performPathRelinking(
        		this.blackboard.getBestResultInLimits(this.random).getItem(),
        		rs.getItem(),
        		this.blackboard.getCurrentTargetFunction());
        this.performPathRelinking(
        		this.blackboard.getRandomResult(this.random).getItem(),
        		rs.getItem(),
        		this.blackboard.getCurrentTargetFunction());
    }

    void performPathRelinking(final RuleSet start, final RuleSet end, TargetFunction targetFunction) {
		if (this.evalTargetFunction(start, targetFunction) > this.evalTargetFunction(end, targetFunction)) {
			//start with the better of the rules
			this.performPathRelinking(end, start, targetFunction);
			return;
		}

        final List<Function<RuleSet, RuleSet>> actions = this.determineRelinkActions(start, end);
        Collections.shuffle(actions, this.random);

        RuleSet cur = start;
        while (!actions.isEmpty()) {
        	final Function<RuleSet, RuleSet> action = this.chooseGoodAction(cur, actions, targetFunction);
        	actions.remove(action);
            cur = action.apply(cur);
            this.blackboard.simplifyEvaluateAndAdd(cur);
        }
    }

	private double evalTargetFunction(final RuleSet start, TargetFunction targetFunction) {
		return targetFunction.applyAsDouble(this.blackboard.simplifyEvaluateAndAdd(start));
	}

    private Function<RuleSet, RuleSet> chooseGoodAction(
    		RuleSet pos, List<Function<RuleSet, RuleSet>> actions, TargetFunction targetFunction) {

    	final ValuedResult<RuleSet> startResult = this.blackboard.simplifyEvaluateAndAdd(pos);
    	final double startValue = targetFunction.applyAsDouble(startResult);

    	double bestValue = Double.POSITIVE_INFINITY;
    	ValuedResult<RuleSet> bestResult = null;
    	Function<RuleSet, RuleSet> bestMove = null;
    	for (final Function<RuleSet, RuleSet> cur : actions) {
        	final ValuedResult<RuleSet> curResult = this.blackboard.simplifyEvaluateAndAdd(cur.apply(pos));
    		final double curValue = targetFunction.applyAsDouble(curResult);
    		if (curValue < startValue) {
    			//if it is an improvement over the current state, just take it to keep the runtime down
    			return cur;
    		}
    		if (curValue < bestValue || (curValue == bestValue && curResult.dominates(bestResult))) {
    			bestResult = curResult;
    			bestValue = curValue;
    			bestMove = cur;
    		}
    	}
    	//we did not find a move that improves the current state, so take the least bad instead
		return bestMove;
	}

	private List<Function<RuleSet, RuleSet>> determineRelinkActions(RuleSet start, RuleSet end) {
        final List<Function<RuleSet, RuleSet>> ret = new ArrayList<>();
        for (final And rule : start.getInclusions()) {
            if (!end.getInclusions().contains(rule)) {
                ret.add((RuleSet rs) -> rs.removeInclusion(rule));
            }
        }
        for (final And rule : start.getExclusions()) {
            if (!end.getExclusions().contains(rule)) {
                ret.add((RuleSet rs) -> rs.removeExclusion(rule));
            }
        }
        for (final And rule : end.getInclusions()) {
            if (!start.getInclusions().contains(rule)) {
                ret.add((RuleSet rs) -> rs.include(rule));
            }
        }
        for (final And rule : end.getExclusions()) {
            if (!start.getExclusions().contains(rule)) {
                ret.add((RuleSet rs) -> rs.exclude(rule));
            }
        }
        return ret;
    }

}
