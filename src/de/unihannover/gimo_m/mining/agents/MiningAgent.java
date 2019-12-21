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

import java.util.Random;

import de.unihannover.gimo_m.mining.common.And;
import de.unihannover.gimo_m.mining.common.Blackboard;
import de.unihannover.gimo_m.mining.common.NondominatedResults;
import de.unihannover.gimo_m.mining.common.RuleSet;
import de.unihannover.gimo_m.mining.common.ValuedResult;

public class MiningAgent extends Thread {

    private static final int START_PHASE_SIZE = 5;

	private final Blackboard blackboard;
    private final GreedyRuleCreation greedyRuleCreation;
    private final LocalSearch localSearch;
    private final PathRelinking pathRelinking;
    private final Random random;
    private int iterationCount;
    private int createNewCount;

    public MiningAgent(Blackboard blackboard) {
        this.blackboard = blackboard;
        this.random = blackboard.createNewRandom();
        this.greedyRuleCreation = new GreedyRuleCreation(blackboard, this.random);
        this.localSearch = new LocalSearch(blackboard, this.random);
        this.pathRelinking = new PathRelinking(blackboard, this.random);

        this.setDaemon(true);
        blackboard.log("creating new agent " + this.getName());
    }

    @Override
    public void run() {
        try {
            while (!this.isInterrupted()) {
                this.performIteration();
            }
        } catch (final InterruptedException e) {
        }
        this.blackboard.log("agent thread ended");
    }

	public void performIteration() throws InterruptedException {
		if (this.iterationCount < START_PHASE_SIZE) {
		    this.createNewRuleSet(false);
		} else {
		    this.workOnHighestPriorityTask();
		}
		this.iterationCount++;
	}

    private void workOnHighestPriorityTask() throws InterruptedException {
        ValuedResult<RuleSet> rs;
        rs = this.blackboard.getOpenItemFromUserFedPathRelinkingQueue();
        if (rs != null) {
            this.blackboard.log("iteration " + this.iterationCount + ": performing path relinking with rule from user");
            this.pathRelinking.performWith(rs);
            return;
        }
        rs = this.blackboard.getOpenItemFromUserFedLocalSearchQueue();
        if (rs != null) {
            this.blackboard.log("iteration " + this.iterationCount + ": performing local search with rule from user");
            //when there are good partial rules in the new rule set, try to get them into the current best one as fast as possible
            final NondominatedResults<RuleSet> resultsCombined = this.localSearch.optimizeByLocalSearch(
            		this.combine(rs, this.blackboard.getBestResultInLimits(this.random)), this.blackboard.getCurrentTargetFunction());
            this.blackboard.addAll(resultsCombined);
            final NondominatedResults<RuleSet> results = this.localSearch.optimizeByLocalSearch(rs, this.blackboard.getCurrentTargetFunction());
            this.blackboard.addAll(results);
            this.blackboard.addToUserFedPathRelinkingQueue(results.getBestItem(this.random, this.blackboard.getCurrentTargetFunction()));
            this.blackboard.addToUserFedPathRelinkingQueue(results.getRandomItem(this.random));
            return;
        }
        rs = this.blackboard.getOpenItemFromAgentFedLocalSearchQueue();
        if (rs != null) {
            this.blackboard.log("iteration " + this.iterationCount + ": performing local search with rule from agent");
            final NondominatedResults<RuleSet> resultsCombined = this.localSearch.optimizeByLocalSearch(
            		this.combine(rs, this.blackboard.getBestResultInLimits(this.random)), this.blackboard.getCurrentTargetFunction());
            this.blackboard.addAll(resultsCombined);
            final NondominatedResults<RuleSet> results = this.localSearch.optimizeByLocalSearch(rs, this.blackboard.getCurrentTargetFunction());
            this.blackboard.addAll(results);
            this.blackboard.addToAgentFedPathRelinkingQueue(results.getBestItem(this.random, this.blackboard.getCurrentTargetFunction()));
            this.blackboard.addToAgentFedPathRelinkingQueue(results.getRandomItem(this.random));
            return;
        }
        rs = this.blackboard.getOpenItemFromAgentFedPathRelinkingQueue();
        if (rs != null) {
            this.blackboard.log("iteration " + this.iterationCount + ": performing path relinking with rule from agent");
            this.pathRelinking.performWith(rs);
            return;
        }

        final int action = this.random.nextInt(10);
        switch (action) {
        case 0:
        case 1:
        case 2:
            this.blackboard.log("iteration " + this.iterationCount + ": performing path relinking with rule from result pool");
            this.pathRelinking.performWith(this.blackboard.getRandomResult(this.random));
            break;
        case 3:
        case 4:
        case 5:
            this.blackboard.log("iteration " + this.iterationCount + ": performing local search with rule from result pool");
            this.blackboard.addAll(this.localSearch.optimizeByLocalSearch(this.blackboard.getRandomResult(this.random), this.blackboard.getCurrentTargetFunction()));
            break;
        case 6:
        case 7:
        case 8:
            this.createNewRuleSet(false);
            break;
        case 9:
            this.createNewRuleSet(true);
            break;
        }
    }

    private void createNewRuleSet(boolean basedOnBest) throws InterruptedException {
    	final int limit = this.createNewCount + 5;
    	this.createNewCount++;
    	final RuleSet rs;
    	if (basedOnBest) {
    		final ValuedResult<RuleSet> bestResult = this.blackboard.getBestResultInLimits(this.random);
    		this.blackboard.log("iteration " + this.iterationCount + ": creating new rule set with limit " + limit +  " based on " + bestResult);
    		rs = this.greedyRuleCreation.createRuleSet(limit, bestResult.getItem().getInclusions(), bestResult.getItem().getExclusions());
    	} else {
    		this.blackboard.log("iteration " + this.iterationCount + ": creating new rule set with limit " + limit);
    		rs = this.greedyRuleCreation.createRuleSet(limit);
    	}
    	final ValuedResult<RuleSet> newRuleSet = this.blackboard.simplifyEvaluateAndAdd(rs);
        this.blackboard.log("created new rule set " + newRuleSet);
        this.blackboard.addToAgentFedLocalSearchQueue(newRuleSet);
    }

	private ValuedResult<RuleSet> combine(ValuedResult<RuleSet> vr1, ValuedResult<RuleSet> vr2) {
		RuleSet ret = vr1.getItem();
		for (final And and : vr2.getItem().getInclusions()) {
			ret = ret.include(and);
		}
		for (final And and : vr2.getItem().getExclusions()) {
			ret = ret.exclude(and);
		}
		return this.blackboard.simplifyEvaluateAndAdd(ret);
	}

}
