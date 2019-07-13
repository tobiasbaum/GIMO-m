package de.unihannover.reviews.mining.interaction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.ObjectId;

import de.unihannover.reviews.mining.agents.MiningAgent;
import de.unihannover.reviews.mining.agents.RecordSubset;
import de.unihannover.reviews.mining.common.And;
import de.unihannover.reviews.mining.common.Blackboard;
import de.unihannover.reviews.mining.common.Blackboard.RecordsAndRemarks;
import de.unihannover.reviews.mining.common.Blackboard.RuleRestrictions;
import de.unihannover.reviews.mining.common.ChangePartId;
import de.unihannover.reviews.mining.common.Multiset;
import de.unihannover.reviews.mining.common.NavigationLimits;
import de.unihannover.reviews.mining.common.NondominatedResults;
import de.unihannover.reviews.mining.common.RawEvaluationResult;
import de.unihannover.reviews.mining.common.Record;
import de.unihannover.reviews.mining.common.RecordScheme;
import de.unihannover.reviews.mining.common.RecordSet;
import de.unihannover.reviews.mining.common.RulePattern;
import de.unihannover.reviews.mining.common.RuleSet;
import de.unihannover.reviews.mining.common.RuleSetParser;
import de.unihannover.reviews.mining.common.TargetFunction;
import de.unihannover.reviews.mining.common.ValuedResult;
import de.unihannover.reviews.miningInputCreation.OffsetBitset;
import de.unihannover.reviews.miningInputCreation.RemarkTriggerMap;
import de.unihannover.reviews.predictionDataPreparation.GitLog;
import de.unihannover.reviews.predictionDataPreparation.GitLog.ChangedFile;
import de.unihannover.reviews.predictionDataPreparation.GitLog.CommitInfo;
import de.unihannover.reviews.predictionDataPreparation.JiraDump;
import de.unihannover.reviews.predictionDataPreparation.Multimap;
import de.unihannover.reviews.predictionDataPreparation.Ticket;
import de.unihannover.reviews.util.consolidateRemarks.IndexedRemarkTable;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Spark;
import spark.template.thymeleaf.ThymeleafTemplateEngine;

public class RuleToolServer {

    private static final File DEFAULT_SAVE_FILE = new File("ruleToolSession.txt");

	private static int ticketCount;

    private static final List<TargetFunction> TARGET_FUNCTIONS = Arrays.asList(
                    new TargetFunction("missed remarks", ValuedResult::getMissedRemarkCount,
                    		"The count of remarks that would probably not be found with the current rule in effect"),
                    new TargetFunction("missed rem. log.", ValuedResult::getMissedRemarkLog,
                    		"Similar to missed remarks, but instead counting 1 for each remark, the remark's value is based on the logarithm of the total number of remarks in the ticket (i.e., ln(count + 1) / count)"),
                    new TargetFunction("saved record count", ValuedResult::getSavedHunkCount,
                    		"The count of records that could be left out from review according to the current rule"),
                    new TargetFunction("saved Java lines", ValuedResult::getSavedJavaLineCount,
                    		"The count of Lines of Code in Java files that could be left out from review according to the current rule"),
                    new TargetFunction("tmean saved rec.", ValuedResult::getSavedHunkTrimmedMean,
                    		"The mean number of saved records per ticket, leaving out the tickets with the largest and smallest counts"),
                    new TargetFunction("complexity", ValuedResult::getRuleSetComplexity,
                    		"The complexity of the current rule, based on its number of conditions"),
                    new TargetFunction("feature count", ValuedResult::getFeatureCount,
                    		"The number of distinct features/columns used in the current rule"),
                    new TargetFunction("ratio (hunks)", ValuedResult::getRatio,
                    		"The ratio of saved records divided by the count of missed remarks + 1"),
                    new TargetFunction("ratio (Java)", (ValuedResult<?> r) -> ((double) r.getSavedJavaLineCount()) / (r.getMissedRemarkCount() + 1),
                    		"The ratio of saved Java lines divided by the number of missed remarks + 1"),
                    new TargetFunction("ratio (log-tmean)", (ValuedResult<?> r) -> (r.getSavedHunkTrimmedMean() * ticketCount) / (r.getMissedRemarkLog() + 1),
                    		"The ratio of saved hunks based on the trimmed mean divided by the sum of the log values missed remarks + 1"),
                    new TargetFunction("cost (f=100)", (ValuedResult<?> r) -> r.calcCost(100, ticketCount),
                    		"The potential cost of using the current rule per ticket, assuming that missing a remark is 100 times as expensive than reviewing a record"),
                    new TargetFunction("cost (f=1000)", (ValuedResult<?> r) -> r.calcCost(1000, ticketCount),
                    		"The potential cost of using the current rule per ticket, assuming that missing a remark is 1000 times as expensive than reviewing a record"),
                    new TargetFunction("cost (f=10000)", (ValuedResult<?> r) -> r.calcCost(10000, ticketCount),
                    		"The potential cost of using the current rule per ticket, assuming that missing a remark is 10000 times as expensive than reviewing a record"),
                    new TargetFunction("cost/ticket log-tmean (f=100)", (ValuedResult<?> r) -> r.calcCostLogTmean(100, ticketCount),
                    		"The potential cost of using the current rule per ticket, but based on the log for missed remarks and the trimmed mean for saved records"),
                    new TargetFunction("cost/ticket log-tmean (f=1000)", (ValuedResult<?> r) -> r.calcCostLogTmean(1000, ticketCount),
                    		"The potential cost of using the current rule per ticket, but based on the log for missed remarks and the trimmed mean for saved records"),
                    new TargetFunction("cost/ticket log-tmean (f=10000)", (ValuedResult<?> r) -> r.calcCostLogTmean(10000, ticketCount),
                    		"The potential cost of using the current rule per ticket, but based on the log for missed remarks and the trimmed mean for saved records")
    );

    private static Blackboard blackboard;
    private static List<MiningAgent> agents;
    private static GitLog gitLog;
    private static String ticketBaseDir;
	private static IndexedRemarkTable remarkFeatures;

    public static void main(String[] args) throws Exception {
    	if (args.length != 5) {
    		System.out.println("Needed command line args: <trigger csv> <traces> <git repo> <ticket dir> <remark csv>");
    		return;
    	}

        System.out.println("Loading trigger csv " + abs(args[0]) + " ...");
        final RecordSet records = RecordSet.loadCsv(args[0]);
        System.out.println("Loading trigger map " + abs(args[1]) + " ...");
        final RemarkTriggerMap triggerMap = RemarkTriggerMap.loadFromFile(new File(args[1]));
        System.out.println("Loading git log " + abs(args[2]) + " ...");
        gitLog = GitLog.load(new File(args[2]));
        System.out.println("Ticket base dir " + abs(args[3]));
        ticketBaseDir = args[3];
        System.out.println("Loading remark csv " + abs(args[4]));
        remarkFeatures = IndexedRemarkTable.load(args[4]);

        if (DEFAULT_SAVE_FILE.exists()) {
            System.out.println("Loading last session...");
            blackboard = Blackboard.load(records, triggerMap, remarkFeatures, DEFAULT_SAVE_FILE);
        } else {
            System.out.println("Creating new session...");
            blackboard = new Blackboard(records, triggerMap, remarkFeatures, System.currentTimeMillis());
            blackboard.simplifyEvaluateAndAdd(RuleSet.SKIP_NONE);
            blackboard.simplifyEvaluateAndAdd(RuleSet.SKIP_ALL);
        }

        ticketCount = countTickets(blackboard.getRecords());

        agents = new CopyOnWriteArrayList<>();

        System.out.println("Starting server...");
        Spark.staticFileLocation("/public");
        Spark.get("/statusAndNav.html", RuleToolServer::statusAndNavPage, new ThymeleafTemplateEngine());
        Spark.get("/curRule.html", RuleToolServer::getCurRule);
        Spark.get("/diff/:commit/*", RuleToolServer::showDiff, new ThymeleafTemplateEngine());
        Spark.get("/ticket/:ticket", RuleToolServer::showTicket, new ThymeleafTemplateEngine());
        Spark.get("/ticketRemarks/:ticket", RuleToolServer::showTicketRemarks, new ThymeleafTemplateEngine());
        Spark.get("/commit/:commit", RuleToolServer::showCommit, new ThymeleafTemplateEngine());
        Spark.post("/goToRule.html", RuleToolServer::goToRule);
        Spark.post("/setLimit.html", RuleToolServer::setLimit);
        Spark.post("/evaluateRule.html", RuleToolServer::evaluateRule);
        Spark.post("/startAgent.html", RuleToolServer::startAgent);
        Spark.post("/stopAgent.html", RuleToolServer::stopAgent);
        Spark.post("/saveResults.html", RuleToolServer::saveResults);
        Spark.post("/purgeRules.html", RuleToolServer::purgeRules);
        Spark.post("/statisticsForWholeDataset.html", RuleToolServer::statisticsForWholeDataset, new ThymeleafTemplateEngine());
        Spark.post("/statisticsForSelection.html", RuleToolServer::statisticsForSelection, new ThymeleafTemplateEngine());
        Spark.post("/statisticsForInverseSelection.html", RuleToolServer::statisticsForInverseSelection, new ThymeleafTemplateEngine());
        Spark.post("/acceptSelection.html", RuleToolServer::acceptSelection);
        Spark.post("/keepSelectionAsCandidate.html", RuleToolServer::keepSelectionAsCandidate);
        Spark.post("/rejectSelection.html", RuleToolServer::rejectSelection);
        Spark.post("/rejectPattern.html", RuleToolServer::rejectPattern);
        Spark.post("/undoRestriction.html", RuleToolServer::undoRestriction);
        Spark.post("/showRestrictions.html", RuleToolServer::showRestrictions, new ThymeleafTemplateEngine());
        Spark.post("/analyzeMissedTriggers.html", RuleToolServer::analyzeMissedTriggers, new ThymeleafTemplateEngine());
        Spark.post("/analyzeMissedRemarks.html", RuleToolServer::analyzeMissedRemarks, new ThymeleafTemplateEngine());
        Spark.post("/sampleUnmatchedRecords.html", RuleToolServer::sampleUnmatchedRecords, new ThymeleafTemplateEngine());
        Spark.post("/sampleRemarks.html", RuleToolServer::sampleRemarks, new ThymeleafTemplateEngine());
        Spark.get("/sampleRemarks.html", RuleToolServer::sampleRemarks, new ThymeleafTemplateEngine());
        Spark.post("/analyzeRemarkDistribution.html", RuleToolServer::analyzeRemarkDistribution, new ThymeleafTemplateEngine());
        Spark.get("/analyzeRemarkDistribution.html", RuleToolServer::analyzeRemarkDistribution, new ThymeleafTemplateEngine());
        Spark.post("/triggersForRemark.html", RuleToolServer::showTriggersForRemarks, new ThymeleafTemplateEngine());
        Spark.post("/removeTicket.html", RuleToolServer::removeTicket);
        Spark.post("/removeRemarks.html", RuleToolServer::removeRemarks);
        Spark.post("/addCalculatedColumn.html", RuleToolServer::addCalculatedColumn);
        Spark.post("/saveData.html", RuleToolServer::saveData);
        Spark.post("/ruleStyling.json", RuleToolServer::determineRuleStyling);
        Spark.post("/ruleSizes.json", RuleToolServer::determineRuleSizes);
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
        for (final TargetFunction f : TARGET_FUNCTIONS) {
            ret.add(new TargetStats(f, snapshot, currentRule, limits, filteredSnapshot));
        }
        return ret;
    }

    private static RuleSet getCurrentRule(Request request) {
        final RuleSet attribute = request.session().attribute("curRule");
        return attribute == null ? RuleSet.SKIP_NONE : attribute;
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
        for (final TargetFunction f : TARGET_FUNCTIONS) {
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
		blackboard.purgeRules(countToKeep, TARGET_FUNCTIONS);
		return "purging done";
    }

    private static ModelAndView statisticsForWholeDataset(Request req, Response res) {
    	blackboard.log("user opens statistics for whole dataset");
        final RecordSet records = blackboard.getRecords().getRecords();
        return statisticsForRecords("whole dataset", records.getScheme(), Arrays.asList(records.getRecords()));
    }

    private static ModelAndView statisticsForSelection(Request req, Response res) {
        final RuleSet selection = parseSelection(req, false);
        blackboard.log("user opens statistics for selection " + selection);
        return statisticsForMatches(selection.toString(), selection);
    }

    private static ModelAndView statisticsForInverseSelection(Request req, Response res) {
        final RuleSet selection = parseSelection(req, false);
        blackboard.log("user opens statistics for inverse selection " + selection);
        return statisticsForMatches("not (" + selection.toString() + ")", selection.negate());
    }

    private static RuleSet parseSelection(Request req, boolean exclusionsAsExclusions) {
        String selection = req.queryParams("selection");

        selection = selection.trim();
        if (selection.startsWith(RuleSetParser.HEADER)) {
            selection = selection.substring(RuleSetParser.HEADER.length()).trim();
        }
        if (!selection.startsWith("(") && !selection.startsWith("or (")) {
            selection = "(" + selection;
        }
        if (!selection.endsWith(")")) {
            selection = selection + ")";
        }

        if (exclusionsAsExclusions && selectionIsAfterExclusions(req)) {
            selection = RuleSetParser.EXCLUSION_BREAK + "\n" + selection;
        }

        selection = RuleSetParser.HEADER + "\n" + selection;

        return new RuleSetParser(getScheme()).parse(selection);
    }

    private static boolean selectionIsAfterExclusions(Request req) {
        return req.queryParams("beforeSelection").contains(RuleSetParser.EXCLUSION_BREAK);
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

        final Set<String> distinctTickets = new LinkedHashSet<>();
        final Set<String> distinctCommits = new LinkedHashSet<>();
        for (final Record r : sel) {
            distinctTickets.add(r.getId().getTicket());
            distinctCommits.add(r.getId().getCommit());
        }

        params.put("title", title);
        params.put("recordCount", sel.size());
        params.put("distinctTicketCount", distinctTickets.size());
        params.put("distinctCommitCount", distinctCommits.size());
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

        final Multiset<String> tickets = new Multiset<>();
        for (final Record r : sel) {
        	tickets.add(r.getId().getTicket());
        }
        params.put("matchedTickets", MultisetEntryWrapper.wrap(tickets, tickets.getPrefixOfMostCommon(10)));

        params.put("records", RecordWrapper.wrap(scheme, selectSubset(sel)));
        params.put("columns", scheme.getColumnNames());

        return new ModelAndView(params, "statistics");
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
		if (countToAdd == 0) {
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
        final RuleSet selection = parseSelection(req, true);
        blackboard.inclusionRestrictions().accept(selection.getInclusions());
        blackboard.exclusionRestrictions().accept(selection.getExclusions());
        return "acceptance added";
    }

    private static String keepSelectionAsCandidate(Request req, Response res) {
        final RuleSet selection = parseSelection(req, true);
        blackboard.inclusionRestrictions().keepAsCandidate(selection.getInclusions());
        blackboard.exclusionRestrictions().keepAsCandidate(selection.getExclusions());
        return "candidates added";
    }

    private static String rejectSelection(Request req, Response res) {
        final String selectedColumn = getSelectedColumnIfPossible(req);
        if (selectedColumn != null) {
            blackboard.addRejectedColumns(Collections.singleton(selectedColumn));
            return "rejection for column " + selectedColumn + " added";
        }
        final RuleSet selection = parseSelection(req, true);
        blackboard.inclusionRestrictions().reject(selection.getInclusions());
        blackboard.exclusionRestrictions().reject(selection.getExclusions());
        return "rejection added";
    }

    private static String rejectPattern(Request req, Response res) {
    	final RulePattern pattern = RulePattern.parse(getScheme(),
    			req.queryParams("selection"));
        if (selectionIsAfterExclusions(req)) {
        	blackboard.exclusionRestrictions().reject(pattern);
        } else {
        	blackboard.inclusionRestrictions().reject(pattern);
        }
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
        	final RuleSet selection = parseSelection(req, false);
        	rr.remove(selection.getInclusions());
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
        if (!selection.matches("[a-zA-Z]+")) {
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

		public String getTicket() {
            return this.record.getId().getTicket();
        }

        public String getCommit() {
            return this.record.getId().getCommit();
        }

        public String getFile() {
            return this.record.getId().getFile();
        }

        public String getLineFrom() {
            return this.record.getId().isLineGranularity() ? Integer.toString(this.record.getId().getLineFrom()) : "";
        }

        public String getLineTo() {
            return this.record.getId().isLineGranularity() ? Integer.toString(this.record.getId().getLineTo()) : "";
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
            return this.record.getClassification().toString();
        }

    }

    private static ModelAndView analyzeMissedTriggers(Request req, Response res) {
        final RuleSet rs = parseRule(req);
        final RecordsAndRemarks rr = blackboard.getRecords();
        final RecordScheme scheme = rr.getRecords().getScheme();
        blackboard.log("user analyzes missed triggers for " + rs);

        final Multimap<String, Record> recordsPerTicket = determineRecordsPerTicket(rr.getRecords());

        Predicate<Record> contrast;
        final String selection = req.queryParams("selection");
        if (selection == null || selection.isEmpty()) {
            contrast = null;
        } else {
            contrast = getRuleWithoutSelection(rr.getRecords(), req);
        }

        final List<RecordWrapper> missedTriggers = new ArrayList<>();
        for (final String ticket : recordsPerTicket.keySet()) {
            final List<Record> ticketRecords = recordsPerTicket.get(ticket);
            final Set<Integer> missedRemarks = determineMissedRemarks(rr.getTriggerMap(), rs, ticketRecords);

            if (contrast != null) {
                //the contrast with and without selection shall be calculated, remove all remarks
                //  that were already missed without the selection
                final Set<Integer> missedRemarksWithoutSelection = determineMissedRemarks(rr.getTriggerMap(), contrast, ticketRecords);
                missedRemarks.removeAll(missedRemarksWithoutSelection);
            }

            if (!missedRemarks.isEmpty()) {
                for (final Record r : ticketRecords) {
                    if (isTriggerFor(rr.getTriggerMap(), r, missedRemarks)) {
                        missedTriggers.add(new RecordWrapper(scheme, r));
                    }
                }
            }
        }

        final Map<String, Object> params = new HashMap<>();
        params.put("selection", selection);
        params.put("rule", rs.toString());
        params.put("columns", scheme.getColumnNames());
        params.put("records", missedTriggers);
        return new ModelAndView(params, "triggers");
    }

    private static Set<Integer> determineMissedRemarks(RemarkTriggerMap triggerMap, final Predicate<Record> rs, final List<Record> ticketRecords) {
        final RawEvaluationResult result = RawEvaluationResult.create(rs, ticketRecords, triggerMap);
        return result.getMissedRemarkIdsForLastTicket();
    }

    private static boolean isTriggerFor(RemarkTriggerMap triggerMap, Record r, Set<Integer> missedRemarks) {
        final ChangePartId id = r.getId();
        final OffsetBitset coveredRemarks;
        if (id.isLineGranularity()) {
            coveredRemarks = triggerMap.getCoveredRemarks(id.getTicket(), id.getCommit(), id.getFile(), id.getLineFrom(), id.getLineTo());
        } else {
            coveredRemarks = triggerMap.getCoveredRemarks(id.getTicket(), id.getCommit(), id.getFile());
        }
        return !Collections.disjoint(coveredRemarks.toSet(), missedRemarks);
    }

    private static Predicate<Record> getRuleWithoutSelection(RecordSet records, Request req) {
        final String rule = req.queryParams("rule");
        final String selection = req.queryParams("selection");
        final String beforeSelection = req.queryParams("beforeSelection");

        final String withoutSelection = beforeSelection + rule.substring(beforeSelection.length() + selection.length());
        return new RuleSetParser(records.getScheme()).parse(withoutSelection);
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

    private static ModelAndView analyzeMissedRemarks(Request req, Response res) {
        final RuleSet rs = parseRule(req);
        final RecordsAndRemarks rr = blackboard.getRecords();
        blackboard.log("user analyzes missed remarks for " + rs);

        final Multimap<String, Record> recordsPerTicket = determineRecordsPerTicket(rr.getRecords());

        Predicate<Record> contrast;
        final String selection = req.queryParams("selection");
        if (selection == null || selection.isEmpty()) {
            contrast = null;
        } else {
            contrast = getRuleWithoutSelection(rr.getRecords(), req);
        }

        final List<RemarkWrapper> missedRemarks = new ArrayList<>();
        for (final String ticket : recordsPerTicket.keySet()) {
            final List<Record> ticketRecords = recordsPerTicket.get(ticket);
            final Set<Integer> missedRemarkIds = determineMissedRemarks(rr.getTriggerMap(), rs, ticketRecords);

            if (contrast != null) {
                //the contrast with and without selection shall be calculated, remove all remarks
                //  that were already missed without the selection
                final Set<Integer> missedRemarksWithoutSelection = determineMissedRemarks(rr.getTriggerMap(), contrast, ticketRecords);
                missedRemarkIds.removeAll(missedRemarksWithoutSelection);
            }

            final OffsetBitset ob = new OffsetBitset();
            ob.addAll(missedRemarkIds);
            missedRemarks.addAll(RemarkWrapper.wrap(rr.getTriggerMap().getRemarksWithIds(ticket, ob)));
        }

        final Map<String, Object> params = new HashMap<>();
        params.put("selection", selection);
        params.put("rule", rs.toString());
        params.put("records", missedRemarks);
        return new ModelAndView(params, "remarks");
    }

    private static ModelAndView sampleUnmatchedRecords(Request req, Response res) {
    	final RecordsAndRemarks rr = blackboard.getRecords();
    	final String providedSeed = req.queryParams("seed");
    	long seed;
    	if (providedSeed == null) {
    		seed = blackboard.nextRandomSeed();
    	} else {
    		seed = Long.parseLong(providedSeed);
    	}
    	blackboard.log("using seed " + seed + " for sampling unmatched records");
    	final Random random = new Random(seed);
        final RecordSubset allRecords = new RecordSubset(rr.getRecords().getRecords());
        final RuleSet rule = parseRule(req);
        final RecordSubset withoutCan = allRecords.distributeCan(
        		rr.getTriggerMap(), 0.1, random, rule.getInclusions(), rule.getExclusions());
        blackboard.log("user samples unmatched records for " + rule);

        final List<And> allAnds = new ArrayList<>();
        allAnds.addAll(rule.getInclusions());
        allAnds.addAll(rule.getExclusions());

        final List<Record> sampleNo = sample(withoutCan.getNoRecords(), random, allAnds);
        final List<Record> sampleMust = sample(withoutCan.getMustRecords(), random, allAnds);

        final Map<String, Object> params = new HashMap<>();
        params.put("seed", seed);
        params.put("rule", rule.toString());
        final RecordScheme scheme = rr.getRecords().getScheme();
		params.put("columns", scheme.getColumnNames());
        params.put("sampleNo", RecordWrapper.wrap(scheme, sampleNo));
        params.put("sampleMust", RecordWrapper.wrap(scheme, sampleMust));
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
    	ret[5] = countTangledCommits(ticket);

		return new RemarkDistributionWrapper(ticket, columnNames, ret);
	}

	private static int countTangledCommits(String ticket) {
		int ret = 0;
		for (final CommitInfo ci : gitLog.getCommitsFor(ticket)) {
			if (ci.containsMultipleTickets()) {
				ret++;
			}
		}
		return ret;
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
        boolean afterUnless = false;
        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            final String trimmed = lines[lineIdx].trim();
            if (trimmed.equals(RuleSetParser.HEADER) || trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.equals(RuleSetParser.EXCLUSION_BREAK)) {
                afterUnless = true;
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
            if (trimmed.equals(RuleSetParser.HEADER)
            		|| trimmed.isEmpty()
            		|| trimmed.equals(RuleSetParser.EXCLUSION_BREAK)) {
                continue;
            }
            final And rule = new RuleSetParser(records.getScheme()).parseRule(trimmed);

            //TODO cache to make more efficient
            int recordCount = 0;
            final Set<String> commits = new HashSet<>();
            final Set<String> tickets = new HashSet<>();
            for (final Record r : records.getRecords()) {
            	if (rule.test(r)) {
            		recordCount++;
            		commits.add(r.getId().getCommit());
            		tickets.add(r.getId().getTicket());
            	}
            }
            appendToJson(ret, createSizeObject(trimmed, recordCount, commits.size(), tickets.size()));
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

    private static String createSizeObject(
    		String line, int recordCount, int commitCount, int ticketCount) {
        return String.format("{\"text\": \"%1$s\", \"values\": {\"recordCount\": %2$d, \"commitCount\": %3$d, \"ticketCount\": %4$d}}",
                        line, recordCount, commitCount, ticketCount);
    }

    private static ModelAndView showDiff(Request req, Response res) throws IOException, GitAPIException {
    	final String commit = req.params(":commit");
    	final String path = req.splat()[0];
    	final boolean oldSide = req.queryParamOrDefault("side", "new").equals("old");

    	final Map<String, Object> params = new HashMap<>();
    	params.put("commit", commit);
    	params.put("path", path);

        blackboard.log("user shows diff for commit " + commit + ", path " + path + ", oldSide " + oldSide);

    	final CommitInfo ci = CommitInfo.load(ObjectId.fromString(commit), gitLog.getRepository());
    	if (ci == null) {
    		res.status(404);
            return new ModelAndView(params, "diff");
    	}
    	params.put("commitTime", new Date(ci.getTime()));
    	params.put("commitMsg", ci.getMessage());

    	final ChangedFile file = findFile(ci, path, oldSide);
    	if (file == null) {
    		res.status(404);
            return new ModelAndView(params, "diff");
    	}

    	params.put("changeType", file.getChangeType().toString());
    	if (file.isAddition()) {
        	params.put("prevCommit", "NONE");
    	} else {
    		final ObjectId prevCommit = determinePrevCommitFor(ci, file.getOldPath());
        	params.put("prevCommit", prevCommit.name());
    	}
    	params.put("prevPath", file.getOldPath());
		if (file.getChangeType() == ChangeType.ADD) {
			params.put("oldFileContent", "");
		} else {
			params.put("oldFileContent", file.isBinary() ? "binary" : readFileContent(file.getOldContent()));
		}
		if (file.getChangeType() == ChangeType.DELETE) {
			params.put("newFileContent", "");
		} else {
			params.put("newFileContent", file.isBinary() ? "binary" : readFileContent(file.getNewContent()));
		}
        params.put("line", req.queryParamOrDefault("line", "1"));
        return new ModelAndView(params, "diff");
    }

	private static ObjectId determinePrevCommitFor(CommitInfo ci, String oldPath) throws IOException, GitAPIException {
		CommitInfo cur = ci;
		while (true) {
			if (cur.getParentCommit() == null) {
				return ObjectId.zeroId();
			}
			final CommitInfo prevCi = CommitInfo.load(cur.getParentCommit(), cur.getRepository());
			if (containsFile(prevCi, oldPath)) {
				return prevCi.getObjectId();
			}
			cur = prevCi;
		}
	}

	private static boolean containsFile(CommitInfo ci, String path) throws IOException, GitAPIException {
		for (final ChangedFile f : ci.determineChangedFiles()) {
			if (f.getNewPath().equals(path)) {
				return true;
			}
		}
		return false;
	}

	private static String readFileContent(InputStream content) throws IOException {
		final Reader r = new InputStreamReader(content, "UTF-8");
		final StringBuilder ret = new StringBuilder();
		int ch;
		while ((ch = r.read()) >= 0) {
			ret.append((char) ch);
		}
		content.close();
		return ret.toString();
	}

	private static ChangedFile findFile(CommitInfo ci, String path, boolean oldSide) throws IOException, GitAPIException {
		for (final ChangedFile f : ci.determineChangedFiles()) {
			if ((oldSide && f.getOldPath().equals(path)) || f.getPath().equals(path)) {
				return f;
			}
		}
		return null;
	}

    public static final class CommitWrapper {
        private final CommitInfo ci;
		private final Date implReviewBorder;

        public CommitWrapper(CommitInfo ci, Date implReviewBorder) {
            this.ci = ci;
            this.implReviewBorder = implReviewBorder;
        }

        public String getId() {
        	return this.ci.getId();
        }

        public Date getTime() {
        	return new Date(this.ci.getTime());
        }

        public String getMessage() {
        	return this.ci.getMessage();
        }

        public String getAuthor() {
        	return this.ci.getAuthor();
        }

        public int getFileCount() throws IOException, GitAPIException {
        	return this.ci.determineChangedFiles().size();
        }

        public String getCssClass() {
        	return this.getTime().compareTo(this.implReviewBorder) <= 0 ? "commitNoReview" : "commitReview";
        }
    }

    private static ModelAndView showTicket(Request req, Response res) throws IOException, GitAPIException {
    	final String ticket = req.params(":ticket");
    	blackboard.log("user shows details for ticket " + ticket);

    	final Ticket t = JiraDump.ticket(ticketBaseDir, ticket);

    	final Map<String, Object> params = new HashMap<>();
    	params.put("ticket", ticket);
    	params.put("ticketTitle", t.getSummary());
    	params.put("reviewRemarks", t.getReviewRemarks());

    	final Date implReviewBorder = t.getImplementationReviewBorder();
    	final List<CommitWrapper> commits = new ArrayList<>();
    	for (final CommitInfo ci : gitLog.getCommitsFor(ticket)) {
    		commits.add(new CommitWrapper(ci, implReviewBorder));
    	}
    	commits.sort((CommitWrapper c1, CommitWrapper c2) -> c1.getTime().compareTo(c2.getTime()));
    	params.put("commits", commits);
        return new ModelAndView(params, "ticket");
    }

    private static ModelAndView showTicketRemarks(Request req, Response res) throws IOException, GitAPIException {
    	final String ticket = req.params(":ticket");
    	blackboard.log("user shows remarks for ticket " + ticket);

    	final RecordsAndRemarks rr = blackboard.getRecords();
        final OffsetBitset remarkIds = rr.getTriggerMap().getAllRemarksFor(ticket);
        final Collection<? extends RemarkWrapper> remarks =
        		RemarkWrapper.wrap(rr.getTriggerMap().getRemarksWithIds(ticket, remarkIds));

    	final Map<String, Object> params = new HashMap<>();
    	params.put("ticket", ticket);
    	params.put("records", remarks);

        return new ModelAndView(params, "remarksForTicket");
    }

    public static final class ChangedFileWrapper {

    	private final ChangedFile file;

    	public ChangedFileWrapper(ChangedFile f) {
    		this.file = f;
		}

		public String getPath() {
			return this.file.getPath();
    	}

    	public String getChangeType() {
    		return this.file.getChangeType().toString();
    	}

    	public String getHunkCount() throws IOException, GitAPIException {
    		if (this.file.isBinary()) {
    			return "";
    		} else {
    			return Integer.toString(this.file.getHunkRanges().size());
    		}
    	}

    }

    private static ModelAndView showCommit(Request req, Response res) throws IOException, GitAPIException {
    	final String id = req.params(":commit");
    	blackboard.log("user shows details for commit " + id);

    	final CommitInfo ci = CommitInfo.load(ObjectId.fromString(id), gitLog.getRepository());
    	final CommitWrapper w = new CommitWrapper(ci, new Date(0));

    	final Map<String, Object> params = new HashMap<>();
    	params.put("id", id);
    	params.put("time", w.getTime());
    	params.put("author", w.getAuthor());
    	params.put("message", w.getMessage());

    	final List<ChangedFileWrapper> files = new ArrayList<>();
    	for (final ChangedFile f : ci.determineChangedFiles()) {
    		files.add(new ChangedFileWrapper(f));
    	}

    	params.put("files", files);
        return new ModelAndView(params, "commit");
    }

    private static String removeTicket(Request req, Response res) {
    	final String ticket = req.queryParams("ticket");
    	final String ret = blackboard.removeTicket(ticket);
        ticketCount = countTickets(blackboard.getRecords());
    	return ret;
    }

    private static int countTickets(RecordsAndRemarks records) {
    	final Set<String> ids = new HashSet<>();
    	for (final Record r : records.getRecords().getRecords()) {
    		ids.add(r.getId().getTicket());
    	}
		return ids.size();
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
