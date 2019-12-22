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
import de.unihannover.gimo_m.mining.common.RuleSet;
import de.unihannover.gimo_m.mining.common.TargetFunction;
import de.unihannover.gimo_m.mining.common.ValuedResult;

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

    private void performPathRelinking(final RuleSet start, final RuleSet end, TargetFunction targetFunction) {
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

        if (!start.getDefault().equals(end.getDefault())) {
            ret.add((RuleSet rs) -> rs.changeDefault(end.getDefault()));
        }

        for (int exceptionId = 0; exceptionId < start.getExceptionCount(); exceptionId++) {
            final String strategy = start.getStrategy(exceptionId);
            for (final And rule : start.getRules(exceptionId)) {
                if (!end.getRules(strategy).contains(rule)) {
                    ret.add((RuleSet rs) -> rs.removeRule(strategy, rule));
                }
            }
        }
        for (int exceptionId = 0; exceptionId < end.getExceptionCount(); exceptionId++) {
            final String strategy = end.getStrategy(exceptionId);
            for (final And rule : end.getRules(exceptionId)) {
                if (!start.getRules(strategy).contains(rule)) {
                    ret.add((RuleSet rs) -> rs.addRule(strategy, rule));
                }
            }
        }
        return ret;
    }

}
