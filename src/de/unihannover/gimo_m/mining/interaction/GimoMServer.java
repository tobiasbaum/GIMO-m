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
package de.unihannover.gimo_m.mining.interaction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import de.unihannover.gimo_m.mining.agents.MiningAgent;
import de.unihannover.gimo_m.mining.common.And;
import de.unihannover.gimo_m.mining.common.Blackboard;
import de.unihannover.gimo_m.mining.common.Blackboard.RecordsAndRemarks;
import de.unihannover.gimo_m.mining.common.Blackboard.RuleRestrictions;
import de.unihannover.gimo_m.mining.common.ChangePartId;
import de.unihannover.gimo_m.mining.common.Multiset;
import de.unihannover.gimo_m.mining.common.NavigationLimits;
import de.unihannover.gimo_m.mining.common.NondominatedResults;
import de.unihannover.gimo_m.mining.common.Or;
import de.unihannover.gimo_m.mining.common.RawEvaluationResult;
import de.unihannover.gimo_m.mining.common.Record;
import de.unihannover.gimo_m.mining.common.RecordScheme;
import de.unihannover.gimo_m.mining.common.RecordSet;
import de.unihannover.gimo_m.mining.common.ResultData;
import de.unihannover.gimo_m.mining.common.Rule;
import de.unihannover.gimo_m.mining.common.RulePattern;
import de.unihannover.gimo_m.mining.common.RuleSet;
import de.unihannover.gimo_m.mining.common.RuleSetParser;
import de.unihannover.gimo_m.mining.common.TargetFunction;
import de.unihannover.gimo_m.mining.common.ValuedResult;
import de.unihannover.gimo_m.miningInputCreation.OffsetBitset;
import de.unihannover.gimo_m.miningInputCreation.RemarkTriggerMap;
import de.unihannover.gimo_m.predictionDataPreparation.Multimap;
import de.unihannover.gimo_m.util.consolidateRemarks.IndexedRemarkTable;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Spark;
import spark.template.thymeleaf.ThymeleafTemplateEngine;

public class GimoMServer {

    private static final File DEFAULT_SAVE_FILE = new File("ruleToolSession.txt");

    private static List<TargetFunction> targetFunctions;

    private static Blackboard blackboard;
    private static List<MiningAgent> agents;
	private static IndexedRemarkTable remarkFeatures;

    public static void main(String[] args) throws Exception {
    	if (args.length != 1) {
    		System.out.println("Needed command line arguments: <csv with classification column>");
    		return;
    	}

        System.out.println("Loading csv " + abs(args[0]) + " ...");
        final RecordSet records = RecordSet.loadCsv(args[0]);
//        System.out.println("Merging with simulation results " + abs(args[1]) + " ...");
//        final RecordSet aggregated = ResultAnalysis.loadAggregatedResults(args[1]);
//        records = ResultAnalysis.addColumnsForBestAndWorstStrategies(records, aggregated);
//        System.out.println("Loading trigger map " + abs(args[1]) + " ...");
        final RemarkTriggerMap triggerMap = new RemarkTriggerMap();
        triggerMap.finishCreation();
//        System.out.println("Loading remark csv " + abs(args[4]));
        remarkFeatures = new IndexedRemarkTable(new String[0]);

        final ResultData resultData = new ResultData(records);
        targetFunctions = RawEvaluationResult.createTargetFunctions(resultData);
        if (DEFAULT_SAVE_FILE.exists()) {
            System.out.println("Loading last session...");
            blackboard = Blackboard.load(records, triggerMap, resultData, remarkFeatures, targetFunctions, DEFAULT_SAVE_FILE);
        } else {
            System.out.println("Creating new session...");
            blackboard = new Blackboard(records, triggerMap, resultData, remarkFeatures, targetFunctions, System.currentTimeMillis());
            blackboard.addDefaultRulesForAllClasses();
        }

        System.out.println("Found " + resultData.getClassCount() + " classes: " + resultData.getAllClasses());

        agents = new CopyOnWriteArrayList<>();

        System.out.println("Starting server...");
        Spark.staticFileLocation("/public");
        Spark.get("/statusAndNav.html", GimoMServer::statusAndNavPage, new ThymeleafTemplateEngine());
        Spark.get("/curRule.html", GimoMServer::getCurRule);
        Spark.post("/goToRule.html", GimoMServer::goToRule);
        Spark.post("/setLimit.html", GimoMServer::setLimit);
        Spark.post("/evaluateRule.html", GimoMServer::evaluateRule);
        Spark.post("/startAgent.html", GimoMServer::startAgent);
        Spark.post("/stopAgent.html", GimoMServer::stopAgent);
        Spark.post("/saveResults.html", GimoMServer::saveResults);
        Spark.post("/purgeRules.html", GimoMServer::purgeRules);
        Spark.post("/statisticsForWholeDataset.html", GimoMServer::statisticsForWholeDataset, new ThymeleafTemplateEngine());
        Spark.post("/statisticsForSelection.html", GimoMServer::statisticsForSelection, new ThymeleafTemplateEngine());
        Spark.post("/statisticsForInverseSelection.html", GimoMServer::statisticsForInverseSelection, new ThymeleafTemplateEngine());
        Spark.post("/acceptSelection.html", GimoMServer::acceptSelection);
        Spark.post("/keepSelectionAsCandidate.html", GimoMServer::keepSelectionAsCandidate);
        Spark.post("/rejectSelection.html", GimoMServer::rejectSelection);
        Spark.post("/rejectPattern.html", GimoMServer::rejectPattern);
        Spark.post("/undoRestriction.html", GimoMServer::undoRestriction);
        Spark.post("/showRestrictions.html", GimoMServer::showRestrictions, new ThymeleafTemplateEngine());
        Spark.post("/analyzeBadChoices.html", GimoMServer::sampleUnmatchedRecords, new ThymeleafTemplateEngine());
        Spark.post("/sampleRemarks.html", GimoMServer::sampleRemarks, new ThymeleafTemplateEngine());
        Spark.get("/sampleRemarks.html", GimoMServer::sampleRemarks, new ThymeleafTemplateEngine());
        Spark.post("/analyzeDataPointDetails.html", GimoMServer::analyzeDataPointDetails, new ThymeleafTemplateEngine());
        Spark.get("/analyzeDataPointDetails.html", GimoMServer::analyzeDataPointDetails, new ThymeleafTemplateEngine());
        Spark.post("/analyzeRemarkDistribution.html", GimoMServer::analyzeRemarkDistribution, new ThymeleafTemplateEngine());
        Spark.get("/analyzeRemarkDistribution.html", GimoMServer::analyzeRemarkDistribution, new ThymeleafTemplateEngine());
        Spark.post("/triggersForRemark.html", GimoMServer::showTriggersForRemarks, new ThymeleafTemplateEngine());
        Spark.post("/removeRemarks.html", GimoMServer::removeRemarks);
        Spark.post("/addCalculatedColumn.html", GimoMServer::addCalculatedColumn);
        Spark.post("/saveData.html", GimoMServer::saveData);
        Spark.post("/ruleStyling.json", GimoMServer::determineRuleStyling);
        Spark.post("/ruleSizes.json", GimoMServer::determineRuleSizes);
    }

    private static File abs(String s) {
    	return new File(s).getAbsoluteFile();
    }

    public static ModelAndView statusAndNavPage(Request req, Response res) {
        final Map<String, Object> params = new HashMap<>();
        final NondominatedResults<RuleSet> snapshot = blackboard.getNondominatedResultsSnapshot();
        final NavigationLimits limits = blackboard.getNavigationLimits();
        final NondominatedResults<RuleSet> filteredSnapshot = limits.filter(snapshot);
        final List<ValuedResult<RuleSet>> snapshotItems = snapshot.getItems();
		params.put("paretoSize", snapshotItems.size());
        params.put("withinLimitsSize", filteredSnapshot.getItems().size());
        params.put("currentTarget", blackboard.getCurrentTargetFunction().getId());
        params.put("targets", createTargetStats(req, snapshot, limits, filteredSnapshot));

        final ValuedResult<RuleSet> currentRule = blackboard.evaluate(getCurrentRule(req));
        final String currentRuleStatus;
        final String currentRuleStatusClass;
        if (snapshotItems.contains(currentRule) || snapshot.add(currentRule)) {
        	if (limits.isInLimits(currentRule)) {
        		currentRuleStatus = "Current rule is on Pareto front";
        		currentRuleStatusClass = "ruleIsOn";
        	} else {
        		currentRuleStatus = "Current rule is on Pareto front, but outside limits";
        		currentRuleStatusClass = "ruleOutsideLimits";
        	}
        } else {
    		currentRuleStatus = "Current rule is not on Pareto front";
    		currentRuleStatusClass = "ruleIsOff";
        }
        params.put("currentRuleStatus", currentRuleStatus);
        params.put("currentRuleStatusClass", currentRuleStatusClass);

        removeDeadAgents();
        params.put("agentCount", agents.size());
        return new ModelAndView(params, "statusAndNav");
    }

    private static void removeDeadAgents() {
    	agents.removeIf((MiningAgent a) -> !a.isAlive());
	}

	public static final class TargetStats {

        private final TargetFunction target;
        private final NondominatedResults<RuleSet> snapshot;
        private final NondominatedResults<RuleSet> filteredSnapshot;
        private final ValuedResult<RuleSet> cur;
        private final NavigationLimits limits;

        public TargetStats(
        		TargetFunction f,
        		NondominatedResults<RuleSet> snapshot,
        		ValuedResult<RuleSet> currentRule,
        		NavigationLimits limits,
        		NondominatedResults<RuleSet> filteredSnapshot) {
            this.target = f;
            this.snapshot = snapshot;
            this.limits = limits;
            this.filteredSnapshot = filteredSnapshot;
            this.cur = currentRule;
        }

        public String getName() {
            return this.target.getId();
        }

        public String getTooltip() {
        	return this.target.getTooltip();
        }

        public double getMin() {
            double min = Double.POSITIVE_INFINITY;
            for (final ValuedResult<?> r : this.snapshot.getItemsSorted()) {
                final double val = this.target.applyAsDouble(r);
                min = Math.min(min, val);
            }
            return min;
        }

        public double getMinFiltered() {
            double min = Double.POSITIVE_INFINITY;
            for (final ValuedResult<?> r : this.filteredSnapshot.getItemsSorted()) {
                final double val = this.target.applyAsDouble(r);
                min = Math.min(min, val);
            }
            return min;
        }

        public ValuedResult<RuleSet> determineMinFilteredResult() {
            double min = Double.POSITIVE_INFINITY;
            ValuedResult<RuleSet> minResult = null;
            for (final ValuedResult<RuleSet> r : this.filteredSnapshot.getItems()) {
                final double val = this.target.applyAsDouble(r);
                if (val < min) {
                    min = val;
                    minResult = r;
                }
            }
            return minResult;
        }

        public double getMax() {
            double max = Double.NEGATIVE_INFINITY;
            for (final ValuedResult<?> r : this.snapshot.getItemsSorted()) {
                final double val = this.target.applyAsDouble(r);
                max = Math.max(max, val);
            }
            return max;
        }

        public double getMaxFiltered() {
            double max = Double.NEGATIVE_INFINITY;
            for (final ValuedResult<?> r : this.filteredSnapshot.getItemsSorted()) {
                final double val = this.target.applyAsDouble(r);
                max = Math.max(max, val);
            }
            return max;
        }

        public double getCurValue() {
            return this.target.applyAsDouble(this.cur);
        }

        public String getCurValueF() {
            return Double.toString(Math.round(this.target.applyAsDouble(this.cur) * 1000000.0) / 1000000.0);
        }

        public double getNextBetterValue() {
            final ValuedResult<RuleSet> neighbor = this.determineLeftNeighbor();
            return this.target.applyAsDouble(neighbor == null ? this.cur : neighbor);
        }

        public double getNextWorseValue() {
            final ValuedResult<RuleSet> neighbor = this.determineRightNeighbor();
            return this.target.applyAsDouble(neighbor == null ? this.cur : neighbor);
        }

        private ValuedResult<RuleSet> determineLeftNeighbor() {
            final double curValue = this.target.applyAsDouble(this.cur);
            return this.determineClosestNeighbor(
                            this.filteredSnapshot.getItems().stream()
                            .filter((ValuedResult<RuleSet> r) -> this.target.applyAsDouble(r) < curValue)
                            .collect(Collectors.toList()));
        }

        private ValuedResult<RuleSet> determineRightNeighbor() {
            final double curValue = this.target.applyAsDouble(this.cur);
            return this.determineClosestNeighbor(
                            this.filteredSnapshot.getItems().stream()
                            .filter((ValuedResult<RuleSet> r) -> this.target.applyAsDouble(r) > curValue)
                            .collect(Collectors.toList()));
        }

        private ValuedResult<RuleSet> determineClosestNeighbor(List<ValuedResult<RuleSet>> filter) {
            //determine the pareto front of closest items
            final NondominatedResults<RuleSet> closestFront = new NondominatedResults<>();
            for (final ValuedResult<RuleSet> v : filter) {
                final ValuedResult<RuleSet> distanceVector = v.distanceVectorTo(this.cur);
                closestFront.add(distanceVector);
            };

            if (closestFront.isEmpty()) {
                return null;
            }
            final RuleSet chosen = closestFront.getItems().get(0).getItem();
            for (final ValuedResult<RuleSet> cur : filter) {
                if (cur.getItem().equals(chosen)) {
                    return cur;
                }
            }
            throw new AssertionError("should not happen");
        }

        public boolean isLimited() {
        	return Double.isFinite(this.getLimit());
        }

        public double getLimit() {
        	return this.limits.getLimit(this.target);
        }

    }

    private static List<TargetStats> createTargetStats(
    		Request request,
    		NondominatedResults<RuleSet> snapshot,
    		NavigationLimits limits,
    		NondominatedResults<RuleSet> filteredSnapshot) {
        final List<TargetStats> ret = new ArrayList<>();
        final ValuedResult<RuleSet> currentRule = blackboard.evaluate(getCurrentRule(request));
        for (final TargetFunction f : targetFunctions) {
            ret.add(new TargetStats(f, snapshot, currentRule, limits, filteredSnapshot));
        }
        return ret;
    }

    private static RuleSet getCurrentRule(Request request) {
        final RuleSet attribute = request.session().attribute("curRule");
        return attribute == null ? blackboard.getNondominatedResultsSnapshot().getItems().get(0).getItem() : attribute;
    }

    private static void setCurrentRule(Request request, ValuedResult<RuleSet> r) {
        if (r == null) {
            return;
        }
        request.session().attribute("curRule", blackboard.makeValid(r.getItem()));
    }

    private static String getCurRule(Request req, Response res) {
        return getCurrentRule(req).toString();
    }

    private static String goToRule(Request req, Response res) {
        final String target = req.queryParams("target");
        final String step = req.queryParams("step");
    	blackboard.log("user goes to " + step + " rule for " + target);

    	if (step.equals("prev")) {
    		setCurrentRule(req, getNeighboringRule(getCurRule(req, res), -1));
            return getCurRule(req, res);
    	} else if (step.equals("next")) {
    		setCurrentRule(req, getNeighboringRule(getCurRule(req, res), 1));
            return getCurRule(req, res);
    	}

        final TargetStats f = getTargetStatsWithId(req, target);
        if (f != null) {
            switch (step) {
            case "best":
                blackboard.setCurrentTargetFunction(f.target);
                setCurrentRule(req, f.determineMinFilteredResult());
                break;
            case "better":
                setCurrentRule(req, f.determineLeftNeighbor());
                break;
            case "worse":
                setCurrentRule(req, f.determineRightNeighbor());
                break;
            }
        }
        return getCurRule(req, res);
    }

    private static ValuedResult<RuleSet> getNeighboringRule(String curRule, int move) {
        final RuleSet rs = new RuleSetParser(getScheme()).parse(curRule);
        final ValuedResult<RuleSet> v = blackboard.evaluate(rs);
    	final List<ValuedResult<RuleSet>> allRules =
    			blackboard.getNavigationLimits().filter(blackboard.getNondominatedResultsSnapshot()).getItems();
    	final int oldIndex = allRules.indexOf(v);
    	int newIndex = oldIndex + move;
    	if (newIndex < 0) {
    		newIndex = allRules.size() - 1;
    	} else if (newIndex >= allRules.size()) {
    		newIndex = 0;
    	}
    	return newIndex >= allRules.size() ? v : allRules.get(newIndex);
	}

	private static String setLimit(Request req, Response res) {
        final String target = req.queryParams("target");
        final String value = req.queryParams("value").trim();
        final TargetFunction f = getTargetFunction(target);
        if (f == null) {
        	return "Unknown target function " + target;
        }

        if (value.isEmpty()) {
        	blackboard.log("user removes navigation limit for " + target);
        	blackboard.getNavigationLimits().removeLimit(f);
        } else {
        	final double parsedValue = Double.parseDouble(value);
        	blackboard.log("user sets navigation limit for " + target + " to " + parsedValue);
        	blackboard.getNavigationLimits().setLimit(f, parsedValue);
        }
        return "";
    }

    private static TargetFunction getTargetFunction(String target) {
        for (final TargetFunction f : targetFunctions) {
        	if (f.getId().equals(target)) {
        		return f;
        	}
        }
		return null;
	}

	private static TargetStats getTargetStatsWithId(Request req, String target) {
        final NondominatedResults<RuleSet> snapshot = blackboard.getNondominatedResultsSnapshot();
        final NavigationLimits limits = blackboard.getNavigationLimits();
		for (final TargetStats f : createTargetStats(req, snapshot, limits, limits.filter(snapshot))) {
            if (f.getName().equals(target)) {
                return f;
            }
        }
        return null;
    }

    private static String evaluateRule(Request req, Response res) {
        final RuleSet rs = parseRule(req);
        final ValuedResult<RuleSet> result = blackboard.simplifyEvaluateAndAdd(rs);
        blackboard.log("user evaluates rule: " + result);
        blackboard.addToUserFedLocalSearchQueue(result);
        setCurrentRule(req, result);
        return getCurRule(req, res);
    }

    private static RuleSet parseRule(Request req) {
        final String text = req.queryParams("rule");
        final RuleSet rs = new RuleSetParser(getScheme()).parse(text);
        return rs;
    }

    private static String startAgent(Request req, Response res) {
        final MiningAgent agent = new MiningAgent(blackboard);
        agents.add(agent);
        agent.start();
        return String.format("Agent started. %d agents now running.", agents.size());
    }

    private static String stopAgent(Request req, Response res) {
        MiningAgent toStop;
        try {
            toStop = agents.remove(0);
        } catch (final IndexOutOfBoundsException e) {
            return "No agents running.";
        }
        toStop.interrupt();
        try {
            toStop.join(30000);
        } catch (final InterruptedException e) {
            return "Interrupted while waiting for agent to end.";
        }
        if (toStop.isAlive()) {
            return "Agent could not be stopped in time. Retrying in the background.";
        } else {
            return String.format("Agent stopped. %d agents now running.", agents.size());
        }
    }

    private static String saveResults(Request req, Response res) {
    	try {
			final String filename = "ruleToolSession." + System.currentTimeMillis() + ".txt";
			blackboard.save(new File(filename));
			return "results saved as " + filename;
		} catch (final IOException e) {
			return "error while saving " + e;
		}
    }

    private static String saveData(Request req, Response res) {
    	try {
			final String filename = "ruleToolData." + System.currentTimeMillis() + ".csv";
			saveData(filename, blackboard.getRecords().getRecords(), blackboard.getRejectedColumns());
			return "data saved as " + filename;
		} catch (final IOException e) {
			return "error while saving " + e;
		}
    }

    private static void saveData(String filename, RecordSet records, Set<String> rejectedColumns) throws IOException {
    	blackboard.log("user saves current data as " + filename);
    	final RecordScheme scheme = records.getScheme();
    	try (Writer w = new OutputStreamWriter(new FileOutputStream(filename), "UTF-8")) {
    		w.write("ticket;commit;file;lineFrom;lineTo;");
    		for (int i = 0; i < scheme.getAllColumnCount(); i++) {
    			final String name = scheme.getName(i);
    			if (!rejectedColumns.contains(name)) {
    				w.write(name + ";");
    			}
    		}
    		w.write("classification\n");

    		for (final Record r : records.getRecords()) {
    			w.write(r.getId().getTicket() + ";");
    			w.write(r.getId().getCommit() + ";");
    			w.write(r.getId().getFile() + ";");
    			w.write(r.getId().getLineFrom() + ";");
    			w.write(r.getId().getLineTo() + ";");
        		for (int i = 0; i < scheme.getAllColumnCount(); i++) {
        			final String name = scheme.getName(i);
        			if (!rejectedColumns.contains(name)) {
        				String val;
        				if (scheme.isNumeric(i)) {
        					final double d = r.getValueDbl(scheme.toNumericIndex(i));
        					val = Double.isNaN(d) ? "?" : Double.toString(d);
        				} else {
        					final String s = r.getValueStr(scheme.toStringIndex(i));
        					val = s == null ? "?" : s;
        				}
        				w.write(val + ";");
        			}
        		}
        		w.write(r.getClassification() + "\n");
    		}
    	}
	}

	private static String purgeRules(Request req, Response res) {
		final int countToKeep = Integer.parseInt(req.queryParams("countToKeep"));
		blackboard.purgeRules(countToKeep);
		return "purging done";
    }

    private static ModelAndView statisticsForWholeDataset(Request req, Response res) {
    	blackboard.log("user opens statistics for whole dataset");
        final RecordSet records = blackboard.getRecords().getRecords();
        return statisticsForRecords("whole dataset", records.getScheme(), Arrays.asList(records.getRecords()));
    }

    private static ModelAndView statisticsForSelection(Request req, Response res) {
        final Or selection = parseSelection(req, false);
        blackboard.log("user opens statistics for selection " + selection);
        return statisticsForMatches(selection.toString(),
                        (Record r) -> selection.test(r));
    }

    private static ModelAndView statisticsForInverseSelection(Request req, Response res) {
        final Or selection = parseSelection(req, false);
        blackboard.log("user opens statistics for inverse selection " + selection);
        return statisticsForMatches("not (" + selection.toString() + ")",
                        (Record r) -> !selection.test(r));
    }

    private static Or parseSelection(Request req, boolean exclusionsAsExclusions) {
        String selection = req.queryParams("selection");

        selection = selection.trim();
        if (selection.startsWith(RuleSetParser.DEFAULT_RULE)) {
            selection = skipFirstLine(selection);
        }
        if (selection.startsWith(RuleSetParser.EXCEPT_RULE)) {
            selection = skipFirstLine(selection);
        }
        if (!selection.startsWith("(") && !selection.startsWith("or (")) {
            selection = "(" + selection;
        }
        if (!selection.endsWith(")")) {
            selection = selection + ")";
        }
        selection = RuleSetParser.DEFAULT_RULE + " dummy\n"
                        + RuleSetParser.EXCEPT_RULE + "dummy2" + RuleSetParser.EXCEPT_RULE_SUFFIX + "\n"
                        + selection;

        final RuleSet dummy = new RuleSetParser(getScheme()).parse(selection);
        return new Or(dummy.getRules(0).toArray(new Rule[0]));
    }

    private static String skipFirstLine(String selection) {
        final int index = selection.indexOf('\n');
        if (index >= 0) {
            return selection.substring(index).trim();
        } else {
            return selection.trim();
        }
    }

    private static ModelAndView statisticsForMatches(String title, Predicate<Record> pred) {
        final List<Record> subset = new ArrayList<>();
        final RecordSet records = blackboard.getRecords().getRecords();
		for (final Record r : records.getRecords()) {
            if (pred.test(r)) {
                subset.add(r);
            }
        }
        return statisticsForRecords(title, records.getScheme(), subset);
    }

    public static final class NumericColumnInfo {

        private final String name;
        private final double min;
        private final double max;
        private final double mean;
        private final int missing;

        public NumericColumnInfo(String name, double min, double max, double mean, int missing) {
            this.name = name;
            this.min = min;
            this.max = max;
            this.mean = mean;
            this.missing = missing;
        }

        public String getName() {
            return this.name;
        }

        public double getMin() {
            return this.min;
        }

        public double getMax() {
            return this.max;
        }

        public double getMean() {
            return this.mean;
        }

        public int getMissing() {
            return this.missing;
        }

    }

    public static final class StringColumnInfo {

        private final String name;
        private final List<String> mostCommon;
        private final Multiset<String> values;
        private final int missing;

        public StringColumnInfo(String name, List<String> mostCommon, Multiset<String> values, int missing) {
            this.name = name;
            this.mostCommon = mostCommon;
            this.values = values;
            this.missing = missing;
        }

        public String getName() {
            return this.name;
        }

        public int getDistinctValueCount() {
            return this.values.keySet().size();
        }

        public List<String> getMostCommonValues() {
            return this.mostCommon;
        }

        public int getOccurenceCount(String value) {
            return this.values.get(value);
        }

        public int getMissing() {
            return this.missing;
        }

    }

    public static final class MultisetEntryWrapper {
    	private final Multiset<String> set;
    	private final String key;

    	public MultisetEntryWrapper(String s, Multiset<String> set) {
    		this.key = s;
    		this.set = set;
		}

		public String getKey() {
    		return this.key;
    	}

    	public int getCount() {
    		return this.set.get(this.key);
    	}

		public static List<MultisetEntryWrapper> wrap(Multiset<String> set, List<String> keys) {
			final List<MultisetEntryWrapper> ret = new ArrayList<>();
			for (final String key : keys) {
				ret.add(new MultisetEntryWrapper(key, set));
			}
			return ret;
		}
    }

    private static ModelAndView statisticsForRecords(String title, RecordScheme scheme, List<Record> sel) {
        final Map<String, Object> params = new HashMap<>();

        params.put("title", title);
        params.put("recordCount", sel.size());
        params.put("columnCount", scheme.getAllColumnCount());

        final List<NumericColumnInfo> numericColumns = new ArrayList<>();
        final List<StringColumnInfo> stringColumns = new ArrayList<>();
        for (int column = 0; column < scheme.getAllColumnCount(); column++) {
            if (scheme.isNumeric(column)) {
                int missing = 0;
                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;
                double sum = 0.0;
                int count = 0;
                for (final Record r : sel) {
                    final double d = r.getValueDbl(scheme.toNumericIndex(column));
                    if (Double.isNaN(d)) {
                        missing++;
                    } else {
                        min = Math.min(min, d);
                        max = Math.max(max, d);
                        sum += d;
                        count++;
                    }
                }

                numericColumns.add(new NumericColumnInfo(
                            scheme.getName(column), min, max, sum / count, missing));
            } else {
                final Multiset<String> values = new Multiset<>();
                int missing = 0;
                for (final Record r : sel) {
                    final String val = r.getValueStr(scheme.toStringIndex(column));
                    if (val != null) {
                        values.add(val);
                    } else {
                        missing++;
                    }
                }
                final List<String> mostCommon = values.getPrefixOfMostCommon(10);

                stringColumns.add(new StringColumnInfo(
                            scheme.getName(column), mostCommon, values, missing));
            }
        }

        params.put("numericColumns", numericColumns);
        params.put("stringColumns", stringColumns);

        params.put("records", RecordWrapper.wrap(scheme, selectSubset(sel)));
        params.put("columns", scheme.getColumnNames());

        final Multimap<String, Integer> coverCount = determineClassStatistics(sel);
        params.put("strategyCover", CoverInfo.map(coverCount));
        final List<String> notNeeded = new ArrayList<>(blackboard.getRecords().getResultData().getAllClasses());
        notNeeded.removeAll(coverCount.keySet());
        params.put("notNeeded", notNeeded);

        return new ModelAndView(params, "statistics");
    }

    public static final class CoverInfo {
        private final String name;
        private final List<Integer> ids;

        public CoverInfo(String key, List<Integer> ids) {
            this.name = key;
            this.ids = ids;
        }

        public static List<CoverInfo> map(Multimap<String, Integer> coverCount) {
            final List<CoverInfo> ret = new ArrayList<>();
            for (final String key : coverCount.keySet()) {
                ret.add(new CoverInfo(key, coverCount.get(key)));
            }
            return ret;
        }

        public String getName() {
            return this.name;
        }

        public int getCount() {
            return this.ids.size();
        }

        public List<Integer> getIdSample() {
            final List<Integer> ret = new ArrayList<>();
            for (int i = 0; i < Math.min(this.ids.size(), 10); i++) {
                ret.add(this.ids.get(i));
            }
            return ret;
        }
    }

    private static Multimap<String, Integer> determineClassStatistics(List<Record> sel) {
        final Multimap<String, Integer> ret = new Multimap<>();
        for (final Record r : sel) {
            ret.add(r.getCorrectClass(), r.getId().getId());
        }
        return ret;
    }

    private static List<Record> selectSubset(List<Record> sel) {
    	final int maxCount = 10;
    	if (sel.size() < maxCount) {
    		return sel;
    	}

    	final List<Record> ret = new ArrayList<>();
    	ret.add(sel.get(0));
    	ret.addAll(selectSubset(sel.subList(1, sel.size() - 1), maxCount - 2));
    	ret.add(sel.get(sel.size() - 1));
		return ret;
	}

	private static List<Record> selectSubset(List<Record> list, int countToAdd) {
		if (countToAdd == 0 || list.isEmpty()) {
			return Collections.emptyList();
		}
		final int middleIndex = list.size() / 2;
		final List<Record> before = list.subList(0, middleIndex);
		final List<Record> after = list.subList(middleIndex + 1, list.size());
		final int missingBefore = (countToAdd - 1) / 2;

		final List<Record> ret = new ArrayList<>();
		ret.addAll(selectSubset(before, missingBefore));
		ret.add(list.get(middleIndex));
		ret.addAll(selectSubset(after, countToAdd - 1 - missingBefore));
		return ret;
	}

	private static String acceptSelection(Request req, Response res) {
	    //TODO
//        final RuleSet selection = parseSelection(req, true);
//        blackboard.inclusionRestrictions().accept(selection.getInclusions());
//        blackboard.exclusionRestrictions().accept(selection.getExclusions());
        return "acceptance added";
    }

    private static String keepSelectionAsCandidate(Request req, Response res) {
        //TODO
//        final RuleSet selection = parseSelection(req, true);
//        blackboard.inclusionRestrictions().keepAsCandidate(selection.getInclusions());
//        blackboard.exclusionRestrictions().keepAsCandidate(selection.getExclusions());
        return "candidates added";
    }

    private static String rejectSelection(Request req, Response res) {
        final String selectedColumn = getSelectedColumnIfPossible(req);
        if (selectedColumn != null) {
            blackboard.addRejectedColumns(Collections.singleton(selectedColumn));
            return "rejection for column " + selectedColumn + " added";
        }
        //TODO
//        final RuleSet selection = parseSelection(req, true);
//        blackboard.inclusionRestrictions().reject(selection.getInclusions());
//        blackboard.exclusionRestrictions().reject(selection.getExclusions());
        return "rejection added";
    }

    private static String rejectPattern(Request req, Response res) {
    	final RulePattern pattern = RulePattern.parse(getScheme(),
    			req.queryParams("selection"));
    	blackboard.inclusionRestrictions().reject(pattern);
        return "rejection added";
    }

    private static String undoRestriction(Request req, Response res) {
        final String selectedColumn = getSelectedColumnIfPossible(req);
        if (selectedColumn != null) {
        	blackboard.removeColumnRejection(selectedColumn);
            return "rejection for column " + selectedColumn + " removed";
        }
        final boolean incl = Boolean.parseBoolean(req.queryParams("incl"));
        final boolean isPattern = Boolean.parseBoolean(req.queryParams("isPattern"));
        final RuleRestrictions rr = incl ? blackboard.inclusionRestrictions() : blackboard.exclusionRestrictions();
        if (isPattern) {
            //TODO
//        	final RuleSet selection = parseSelection(req, false);
//        	rr.remove(selection.getInclusions());
        } else {
        	rr.remove(RulePattern.parse(getScheme(), req.queryParams("selection")));
        }
        return "restriction removed";
    }

	private static RecordScheme getScheme() {
		return blackboard.getRecords().getRecords().getScheme();
	}

    private static String getSelectedColumnIfPossible(Request req) {
        final String selection = req.queryParams("selection");
        if (!selection.matches("[a-zA-Z._]+")) {
            return null;
        }
        try {
            getScheme().getAbsIndex(selection);
            return selection;
        } catch (final Exception e) {
            return null;
        }
    }

    private static ModelAndView showRestrictions(Request req, Response res) {
        blackboard.log("user opens restrictions summary");
        final Map<String, Object> params = new HashMap<>();
        params.put("acceptedInclusions", blackboard.inclusionRestrictions().getAccepted());
        params.put("acceptedExclusions", blackboard.exclusionRestrictions().getAccepted());
        params.put("candidateInclusions", blackboard.inclusionRestrictions().getCandidates());
        params.put("candidateExclusions", blackboard.exclusionRestrictions().getCandidates());
        params.put("rejectedInclusions", blackboard.inclusionRestrictions().getRejected());
        params.put("rejectedExclusions", blackboard.exclusionRestrictions().getRejected());
        params.put("rejectedColumns", blackboard.getRejectedColumns());
        params.put("cleaningActions", blackboard.getCleaningActionHistory());
        return new ModelAndView(params, "restrictions");
    }

    public static final class RecordWrapper {
        private final RecordScheme scheme;
        private final Record record;

        public RecordWrapper(RecordScheme scheme, Record record) {
            this.scheme = scheme;
            this.record = record;
        }

        public static List<RecordWrapper> wrap(RecordScheme scheme, List<Record> records) {
        	final List<RecordWrapper> ret = new ArrayList<>();
        	for (final Record r : records) {
        		ret.add(new RecordWrapper(scheme, r));
        	}
			return ret;
		}

        public int getId() {
            return this.record.getId().getId();
        }

        public String getValue(String columnName) {
            final int idx = this.scheme.getAbsIndex(columnName);
            if (this.scheme.isNumeric(idx)) {
                final double val = this.record.getValueDbl(this.scheme.toNumericIndex(idx));
                return Double.isNaN(val) ? "" : Double.toString(val);
            } else {
                final String val = this.record.getValueStr(this.scheme.toStringIndex(idx));
                return val == null ? "" : val;
            }
        }

        public String getClassification() {
            return this.record.getCorrectClass();
        }

    }

    private static Multimap<String, Record> determineRecordsPerTicket(RecordSet records) {
        final Multimap<String, Record> recordsPerTicket = new Multimap<>();
        for (final Record r : records.getRecords()) {
            recordsPerTicket.add(r.getId().getTicket(), r);
        }
        return recordsPerTicket;
    }

    public static final class RemarkWrapper {
        private final String remark;

        public RemarkWrapper(String remark) {
            this.remark = remark;
        }

        public String getTicket() {
            return this.getPart(0);
        }

        public String getCommit() {
            return this.getPart(1);
        }

        public String getFile() {
            return this.getPart(2);
        }

        public String getLine() {
            return this.getPart(3);
        }

        private String getPart(int idx) {
            final String[] parts = this.remark.split(",");
            return idx < parts.length ? parts[idx] : "";
        }

        public String getIdString() {
            return this.remark;
        }

        public boolean hasLine() {
            return !this.getLine().isEmpty();
        }

        public static Collection<? extends RemarkWrapper> wrap(Collection<String> remarks) {
            final List<RemarkWrapper> ret = new ArrayList<>();
            for (final String remark : remarks) {
                ret.add(new RemarkWrapper(remark));
            }
            return ret;
        }

    }

    private static final class RecordAndDist {
        private final Record record;
        private final double dist;

        public RecordAndDist(Record r, double diff) {
            this.record = r;
            this.dist = diff;
        }
    }

    private static ModelAndView sampleUnmatchedRecords(Request req, Response res) {
    	final RecordsAndRemarks rr = blackboard.getRecords();
        final RuleSet rule = parseRule(req);

        final List<RecordAndDist> list = new ArrayList<>();
        for (final Record r : rr.getRecords().getRecords()) {
            final double diff = rr.getResultData().getDiffToBest(r.getId(), rule.apply(r));
            list.add(new RecordAndDist(r, diff));
        }
        list.sort((RecordAndDist r1, RecordAndDist r2) -> Double.compare(r2.dist, r1.dist));

        final List<Record> sample = new ArrayList<>();
        final int size = Math.min(50, list.size());
        for (int i = 0; i < size; i++) {
            sample.add(list.get(i).record);
        }
        blackboard.log("user analyzes bad choices for " + rule);

        final Map<String, Object> params = new HashMap<>();
        params.put("seed", 42);
        params.put("rule", rule.toString());
        final RecordScheme scheme = rr.getRecords().getScheme();
		params.put("columns", scheme.getColumnNames());
        params.put("sample", RecordWrapper.wrap(scheme, sample));
        return new ModelAndView(params, "samples");
    }

    public static final class RemarkDistributionWrapper {
    	private final String ticket;
    	private final List<String> columnNames;
    	private final double[] values;

    	public RemarkDistributionWrapper(String ticket, List<String> columnNames, double[] values) {
    		this.ticket = ticket;
    		this.columnNames = columnNames;
    		this.values = values;
		}

		public String getTicket() {
    		return this.ticket;
    	}

    	public double getValue(String col) {
    		return this.values[this.columnNames.indexOf(col)];
    	}
    }

    public static final class Setting {
        private final String name;
        private final String value;

        public Setting(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return this.name;
        }

        public String getValue() {
            return this.value;
        }
    }

    public static final class StrategyData {

        private final String name;
        private final double diffToBest;
        private String color;

        public StrategyData(String name, double diffToBest) {
            this.name = name;
            this.diffToBest = diffToBest;
        }

        public String getName() {
            return this.name;
        }

        public double getDiffToBest() {
            return this.diffToBest;
        }

        public String getStyle() {
            return "background-color:" + this.color;
        }

        public void determineColor(double maxDiff) {
            this.color = determineColor(this.diffToBest, maxDiff);
        }

        private static String determineColor(double diff, double maxDiff) {
            if (maxDiff == 0.0) {
                return "#FFFFFF";
            }

            if (diff == 0.0) {
                return "#00FF00";
            } else if (diff == maxDiff) {
                return "#FF0000";
            } else if (diff < 0.01) {
                return "#BAEB34";
            } else if (diff < 0.05) {
                return "#DAEB34";
            } else if (diff < 0.10) {
                return "#EBCF34";
            } else {
                return "#EBB134";
            }
        }

    }

    private static ModelAndView analyzeDataPointDetails(Request req, Response res) {
        final RecordsAndRemarks recordsAndRemarks = blackboard.getRecords();
        final RecordScheme scheme = recordsAndRemarks.getRecords().getScheme();
        final int id = Integer.parseInt(req.queryParams("id"));
        blackboard.log("user shows details for record id " + id);
        final Record record = findRecordById(recordsAndRemarks, id);

        final Map<String, Object> params = new HashMap<>();
        params.put("rec", new RecordWrapper(scheme, record));
        params.put("columns", scheme.getColumnNames());
        return new ModelAndView(params, "dataPointDetails");
    }

    private static Record findRecordById(RecordsAndRemarks recordsAndRemarks, int id) {
        return findRecordById(recordsAndRemarks, new ChangePartId(id));
    }

    private static Record findRecordById(RecordsAndRemarks recordsAndRemarks, ChangePartId changePartId) {
        for (final Record r : recordsAndRemarks.getRecords().getRecords()) {
            if (r.getId().equals(changePartId)) {
                return r;
            }
        }
        return null;
    }

    private static Map<String, String> getSimulationParamsFromUser(Request req) {
        final Map<String, String> map = new TreeMap<String, String>();
        for (final String name : req.queryParams()) {
            if (name.startsWith("sp_")) {
                map.put(name.substring(3), req.queryParams(name));
            }
        }
        return map;
    }

    private static ModelAndView analyzeRemarkDistribution(Request req, Response res) {

        final String sortedColumn = req.queryParamOrDefault("sort", "remarkRecordQuotient");
        final boolean asc = sortedColumn.equals(req.queryParams("oldSort"));

    	blackboard.log("user shows remark distribution in tickets sorted by "
    			+ sortedColumn + ", " + (asc ? "asc" : "desc"));

    	final List<String> columnNames = Arrays.asList(
    			"implCommits", "reviewCommits", "records", "remarks", "remarkRecordQuotient", "tangledCommits");

    	final RecordsAndRemarks rr = blackboard.getRecords();
    	final Multimap<String,Record> recordsPerTicket = determineRecordsPerTicket(rr.getRecords());
        final List<RemarkDistributionWrapper> result = new ArrayList<>();
        for (final String ticket : recordsPerTicket.keySet()) {
        	result.add(determineRemarkDistributionForTicket(
        			ticket, columnNames, recordsPerTicket.get(ticket), rr.getTriggerMap()));
        }

        Comparator<RemarkDistributionWrapper> comp =
        		(RemarkDistributionWrapper w1, RemarkDistributionWrapper w2) ->
        			Double.compare(w2.getValue(sortedColumn), w1.getValue(sortedColumn));

		if (asc) {
			comp = comp.reversed();
		}
		result.sort(comp);

        final Map<String, Object> params = new HashMap<>();
        params.put("columns", columnNames);
        params.put("sortedCol", asc ? (sortedColumn + "Asc") : sortedColumn);
        params.put("records", result);
        return new ModelAndView(params, "remarkDistribution");
    }

    private static RemarkDistributionWrapper determineRemarkDistributionForTicket(
    		String ticket,
    		List<String> columnNames,
    		List<Record> recordsForTicket,
			RemarkTriggerMap triggerMap) {

    	final Collection<? extends RemarkWrapper> remarksForTicket = getRemarksForTicket(triggerMap, ticket);

    	final double[] ret = new double[6];
    	// implCommits
    	ret[0] = countDistinct(recordsForTicket, (Record r) -> r.getId().getCommit());
    	// reviewCommits
    	ret[1] = countDistinct(remarksForTicket, RemarkWrapper::getCommit);
    	// records
    	ret[2] = recordsForTicket.size();
    	// remarks
    	ret[3] = remarksForTicket.size();
    	// remarkRecordQuotient
    	ret[4] = ret[3] / ret[2];
    	// tangledCommits
    	ret[5] = 0;

		return new RemarkDistributionWrapper(ticket, columnNames, ret);
	}

	private static<T> long countDistinct(Collection<? extends T> c, Function<T, String> map) {
		return c.stream().map(map).distinct().count();
	}

	private static List<Record> sample(List<Record> records, Random random, List<And> allAnds) {
    	Collections.shuffle(records, random);

    	final List<Record> ret = new ArrayList<>();
    	for (final Record r : records) {
    		if (matchesAny(r, allAnds)) {
    			//don't return records that are already matched
    			continue;
    		}
    		ret.add(r);
    		if (ret.size() >= 10) {
    			break;
    		}
    	}
		return ret;
	}

	private static boolean matchesAny(Record r, List<And> allAnds) {
		for (final And and : allAnds) {
			if (and.test(r)) {
				return true;
			}
		}
		return false;
	}

    private static ModelAndView sampleRemarks(Request req, Response res) {
    	final RecordsAndRemarks rr = blackboard.getRecords();
    	final String providedSeed = req.queryParams("seed");
    	long seed;
    	if (providedSeed == null) {
    		seed = blackboard.nextRandomSeed();
    	} else {
    		seed = Long.parseLong(providedSeed);
    	}
    	blackboard.log("user shows  " + seed);
    	final Random random = new Random(seed);

        final List<RemarkWrapper> allRemarks = new ArrayList<>();
        for (final String ticket : determineRecordsPerTicket(rr.getRecords()).keySet()) {
            allRemarks.addAll(getRemarksForTicket(rr.getTriggerMap(), ticket));
        }
        Collections.shuffle(allRemarks, random);
        final List<RemarkWrapper> sample = allRemarks.subList(0, Math.min(100, allRemarks.size()));

        final Map<String, Object> params = new HashMap<>();
        params.put("rule", "sample of all remarks, seed=" + seed);
        params.put("records", sample);
        return new ModelAndView(params, "remarks");
    }

	private static Collection<? extends RemarkWrapper> getRemarksForTicket(final RemarkTriggerMap triggerMap, final String ticket) {
		final OffsetBitset remarkIds = triggerMap.getAllRemarksFor(ticket);
		return RemarkWrapper.wrap(triggerMap.getRemarksWithIds(ticket, remarkIds));
	}

	private static ModelAndView showTriggersForRemarks(Request req, Response res) {
        final String remarkId = req.queryParams("id");
        blackboard.log("user shows triggers for remark " + remarkId);
        final RemarkWrapper w = new RemarkWrapper(remarkId);
        final RecordsAndRemarks rr = blackboard.getRecords();
        Set<String> potentialTriggers;
        if (w.hasLine()) {
            potentialTriggers = rr.getTriggerMap().getPotentialTriggersFor(
                            w.getTicket(), w.getCommit(), w.getFile(), Integer.parseInt(w.getLine()));
        } else {
            potentialTriggers = rr.getTriggerMap().getPotentialTriggersFor(w.getTicket(), w.getCommit(), w.getFile());
        }

        final Map<String, Object> params = new HashMap<>();
        params.put("remarkId", remarkId);
        params.put("columns", rr.getRecords().getScheme().getColumnNames());
        params.put("records", RemarkWrapper.wrap(potentialTriggers));
        return new ModelAndView(params, "triggerIds");
    }

    private static String determineRuleStyling(Request req, Response res) {
        final String[] lines = req.queryParams("rule").split("\n");
        res.type("application/json");
        final StringBuilder ret = new StringBuilder();
        ret.append('[');
        final boolean afterUnless = false;
        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            final String trimmed = lines[lineIdx].trim();
            if (trimmed.startsWith(RuleSetParser.EXCEPT_RULE)
                    || trimmed.startsWith(RuleSetParser.DEFAULT_RULE)
                    || trimmed.isEmpty()) {
                continue;
            }
            final And rule = new RuleSetParser(getScheme()).parseRule(trimmed);
            RuleRestrictions restr;
            if (afterUnless) {
                restr = blackboard.exclusionRestrictions();
            } else {
                restr = blackboard.inclusionRestrictions();
            }
            switch (restr.classify(rule)) {
            case ACCEPTED:
            	appendToJson(ret, createStyleObject(lineIdx, lines[lineIdx], "acceptedRule"));
            	break;
            case REJECTED:
            	appendToJson(ret, createStyleObject(lineIdx, lines[lineIdx], "rejectedRule"));
            	break;
            case CANDIDATE:
            	appendToJson(ret, createStyleObject(lineIdx, lines[lineIdx], "candidateRule"));
            	break;
            case UNKNOWN:
            	break;
            }
        }
        ret.append(']');
        return ret.toString();
    }

    private static String createStyleObject(int lineIdx, String line, String cssClass) {
        return String.format("{\"from\": {\"line\": %1$d, \"ch\": 0},\"to\": {\"line\": %1$d, \"ch\": %2$d}, \"className\": \"%3$s\"}",
                        lineIdx, line.length(), cssClass);
    }

    private static String determineRuleSizes(Request req, Response res) {
        final String[] lines = req.queryParams("rule").split("\n");
        final RecordSet records = blackboard.getRecords().getRecords();
        res.type("application/json");
        final StringBuilder ret = new StringBuilder();
        ret.append('[');
        for (final String line : lines) {
            final String trimmed = line.trim();
            if (trimmed.startsWith(RuleSetParser.EXCEPT_RULE)
                    || trimmed.startsWith(RuleSetParser.DEFAULT_RULE)
            		|| trimmed.isEmpty()) {
                continue;
            }
            final And rule = new RuleSetParser(records.getScheme()).parseRule(trimmed);

            int recordCount = 0;
            for (final Record r : records.getRecords()) {
            	if (rule.test(r)) {
            		recordCount++;
            	}
            }
            appendToJson(ret, createSizeObject(trimmed, recordCount));
        }
        ret.append(']');
        return ret.toString();
    }

    private static void appendToJson(StringBuilder ret, String item) {
        if (ret.length() > 1) {
            ret.append(',');
        }
        ret.append(item);
    }

    private static String createSizeObject(String line, int recordCount) {
        return String.format("{\"text\": \"%1$s\", \"values\": {\"recordCount\": %2$d}}",
                        line, recordCount);
    }

	private static String removeRemarks(Request req, Response res) {
    	final String condition = req.queryParams("condition");

    	try {
    		return blackboard.removeRemarksWithFieldValue(condition);
    	} catch (final Exception e) {
    		return "error while parsing condition: " + e;
    	}
    }

    private static String addCalculatedColumn(Request req, Response res) {
    	final String name = req.queryParams("name");
    	final String calculationScript = req.queryParams("calculationScript");
    	return blackboard.addComputedColumn(name, calculationScript);
    }
}
