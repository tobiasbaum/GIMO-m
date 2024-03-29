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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * The central communication hub ("Blackboard") between the various (human and non-human) agents.
 * Also contains the implementation for various of the actions the user can take.
 */
public class Blackboard {

    private static final int AUTO_PURGE_LIMIT = 100_000;

	public enum RestrictionClassification {
		ACCEPTED,
		REJECTED,
		CANDIDATE,
		UNKNOWN
	}

	public final class RuleRestrictions {
    	private final String name;
        private final List<And> accepted = new CopyOnWriteArrayList<>();
        private final List<RulePattern> rejected = new CopyOnWriteArrayList<>();
        private final List<And> candidates = new CopyOnWriteArrayList<>();

        public RuleRestrictions(String name) {
        	this.name = name;
        }

        public synchronized void accept(List<And> acceptedRules) {
        	Blackboard.this.log("accepting for " + this.name + ": " + acceptedRules);
            this.accepted.addAll(acceptedRules);
            this.candidates.removeAll(acceptedRules);
            Blackboard.this.revalidateParetoSet();
        }

        public synchronized void reject(List<And> rejectedRules) {
        	Blackboard.this.log("rejecting for " + this.name + ": " + rejectedRules);
            this.rejected.addAll(rejectedRules.stream().map(RulePattern::createExact).collect(Collectors.toList()));
            this.accepted.removeAll(rejectedRules);
            this.candidates.removeAll(rejectedRules);
            Blackboard.this.revalidateParetoSet();
        }

        public synchronized void reject(RulePattern pattern) {
        	Blackboard.this.log("rejecting for " + this.name + ": " + pattern);
            this.rejected.add(pattern);
            Blackboard.this.revalidateParetoSet();
        }

        public synchronized void keepAsCandidate(List<And> rules) {
        	Blackboard.this.log("mark as candidate for " + this.name + ": " + rules);
            this.candidates.addAll(rules);
            this.accepted.removeAll(rules);
            Blackboard.this.revalidateParetoSet();
        }

        public synchronized void remove(List<And> rules) {
        	Blackboard.this.log("removing restrictions for " + this.name + ": " + rules);
            this.accepted.removeAll(rules);
            this.candidates.removeAll(rules);
            Blackboard.this.revalidateParetoSet();
        }

		public void remove(RulePattern pattern) {
        	Blackboard.this.log("removing restriction pattern for " + this.name + ": " + pattern);
            this.rejected.remove(pattern);
            Blackboard.this.revalidateParetoSet();
		}

        public synchronized List<And> getAccepted() {
            return this.accepted;
        }

        public synchronized List<And> getCandidates() {
            return this.candidates;
        }

        public synchronized List<RulePattern> getRejected() {
            return this.rejected;
        }

		public synchronized RestrictionClassification classify(And rule) {
			if (Blackboard.this.containsForbiddenFeature(rule)) {
				return RestrictionClassification.REJECTED;
			}
			if (this.accepted.contains(rule)) {
				return RestrictionClassification.ACCEPTED;
			}
			if (this.candidates.contains(rule)) {
				return RestrictionClassification.CANDIDATE;
			}
			for (final RulePattern p : this.rejected) {
				if (p.matches(rule)) {
					return RestrictionClassification.REJECTED;
				}
			}
			return RestrictionClassification.UNKNOWN;
		}

		public synchronized RuleCreationRestriction toCreationRestrictions(And priorRule) {
			final Set<Rule> invalidRules = new HashSet<>();
			final Set<SimpleRulePattern> invalidPatterns = new HashSet<>();
			for (final RulePattern p : this.rejected) {
				p.findCompletionToInvalid(priorRule, invalidRules, invalidPatterns);
			}
			return new RuleCreationRestriction(invalidPatterns, invalidRules);
		}

    }

    public static final class RecordsAndRemarks {
    	private final RecordSet records;
        private final ResultData resultData;

        public RecordsAndRemarks(RecordSet records, ResultData resultData) {
        	this.records = records;
        	this.resultData = resultData;
		}

        public RecordSet getRecords() {
        	return this.records;
        }

        public ResultData getResultData() {
            return this.resultData;
        }
    }

    public static abstract class DataCleaningAction {
    	public abstract String execute();
    	public abstract String getUserString();
    	public abstract String serialize();
    }

    private final class RemoveRecordAction extends DataCleaningAction {

    	private final int id;

		public RemoveRecordAction(int id) {
			this.id = id;
		}

		@Override
		public String getUserString() {
			return "remove record " + this.id;
		}

		@Override
		public String serialize() {
			return "removeRecord," + this.id;
		}

		@Override
		public String execute() {
			final RecordsAndRemarks oldRR = Blackboard.this.recordsAndRemarks.get();
			final RecordSet newRecordSet = oldRR.records.copyWithout((Record r) -> r.getId() == this.id);

			Blackboard.this.recordsAndRemarks.set(new RecordsAndRemarks(newRecordSet, oldRR.resultData));

			Blackboard.this.reevaluateAfterDataChange();

			final int recordCount = oldRR.records.getRecords().length - newRecordSet.getRecords().length;
			return "Removed " + recordCount + " record with ID " + this.id;
		}

    }

    private final class AddComputedColumnAction extends DataCleaningAction {

    	private final String name;
    	private final String computationScript;

		public AddComputedColumnAction(String name, String computationScript) {
			this.name = name;
			this.computationScript = computationScript;
		}

		@Override
		public String getUserString() {
			return "add computed column " + this.name + ": " + this.computationScript;
		}

		@Override
		public String serialize() {
			return "addComputedColumn," + this.name + "," + this.computationScript;
		}

		@Override
		public String execute() {
			try {
				final RecordsAndRemarks oldRR = Blackboard.this.recordsAndRemarks.get();

				final ScriptEngineManager manager = new ScriptEngineManager();
				final ScriptEngine engine = manager.getEngineByName("nashorn");
				engine.eval("function calculate() { return " + this.computationScript + "}");

				final RecordSet newRecordSet = RecordSet.addColumn(oldRR.records, this.name,
				                (RecordScheme rs, Record r) -> this.invokeCalculation(rs, r, engine));
				Blackboard.this.recordsAndRemarks.set(new RecordsAndRemarks(newRecordSet, oldRR.resultData));

				//no need to reevaluate, as nothing existing was changed

				return "Added column " + this.name;
			} catch (final ScriptException e) {
				throw new RuntimeException(e);
			}
		}

		private double invokeCalculation(RecordScheme scheme, Record record, ScriptEngine inv) {
			for (int i = 0; i < scheme.getNumericColumnCount(); i++) {
				inv.put(scheme.getNumName(i), record.getValueDbl(i));
			}
			for (int i = 0; i < scheme.getStringColumnCount(); i++) {
				inv.put(scheme.getStrName(i), record.getValueStr(i));
			}
			try {
                return ((Number) ((Invocable) inv).invokeFunction("calculate")).doubleValue();
            } catch (NoSuchMethodException | ScriptException e) {
                throw new RuntimeException(e);
            }
		}

    }

    private static final String BLOCK_START_PREFIX = "######## ";
    private static final String END_OF_RULE_PREFIX = "**** ";

    private static final String DATA_CLEANING = "DATA CLEANING";
    private static final String REJECTED_COLUMNS = "REJECTED COLUMNS";
	private static final String ACCEPTED = "ACCEPTED";
	private static final String CANDIDATE = "CANDIDATE";
	private static final String REJECTED_PATTERNS = "REJECTED PATTERNS";
	private static final String PARETO_FRONT = "PARETO FRONT";

    private final AtomicReference<RecordsAndRemarks> recordsAndRemarks;

    private final ConcurrentHashMap<RuleSet, ValuedResult<RuleSet>> cache;
    private final NondominatedResults<RuleSet> nondominatedResults;

    private final LinkedBlockingDeque<ValuedResult<RuleSet>> userFedLocalSearchQueue = new LinkedBlockingDeque<>();
    private final LinkedBlockingDeque<ValuedResult<RuleSet>> agentFedLocalSearchQueue = new LinkedBlockingDeque<>();
    private final LinkedBlockingDeque<ValuedResult<RuleSet>> userFedPathRelinkingQueue = new LinkedBlockingDeque<>();
    private final LinkedBlockingDeque<ValuedResult<RuleSet>> agentFedPathRelinkingQueue = new LinkedBlockingDeque<>();

    private final AtomicLong seedCounter;

    private final Map<String, RuleRestrictions> restrictions = new LinkedHashMap<>();
    private final CopyOnWriteArraySet<String> rejectedColumns = new CopyOnWriteArraySet<>();
    private final List<DataCleaningAction> cleaningActionHistory = new CopyOnWriteArrayList<>();

    private final ObjectiveStrategy objectives;
    private final List<TargetFunction> targetFunctions;
    private final AtomicReference<TargetFunction> targetFunction = new AtomicReference<>();

    private final Executor revalidateExecutor;
	private final NavigationLimits navigationLimits;

    public Blackboard(RecordSet records, ResultData resultData, ObjectiveStrategy objectives, long initialSeed) {
    	this.objectives = objectives;
        this.targetFunctions = new ArrayList<>(objectives.getTargetFunctions());
        this.targetFunction.set(this.targetFunctions.get(0));
        this.recordsAndRemarks = new AtomicReference<>(new RecordsAndRemarks(records, resultData));
        this.cache = new ConcurrentHashMap<>();
        this.nondominatedResults = new NondominatedResults<>();
        this.seedCounter = new AtomicLong(initialSeed);
        this.revalidateExecutor = new ThreadPoolExecutor(0, 1, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        this.navigationLimits = new NavigationLimits();
        for (final String classification : resultData.getAllClasses()) {
            this.restrictions.put(classification, new RuleRestrictions(classification));
        }
        this.log("initial random seed: " + initialSeed);
    }

    private static abstract class BlockParser {
    	public abstract void handleLine(Blackboard ret, String line);
    }

    private static class RuleBlockParser extends BlockParser {

        private final StringBuilder curRule = new StringBuilder();
		@Override
		public void handleLine(Blackboard ret, String line) {
            if (line.startsWith(END_OF_RULE_PREFIX)) {
                final String afterPrefix = line.substring(END_OF_RULE_PREFIX.length());
                final String[] parts = afterPrefix.split(",");
                final ValuedResult<RuleSet> vr = new ValuedResult<RuleSet>(
                                new RuleSetParser(ret.getRecords().getRecords().getScheme()).parse(this.curRule.toString()),
                                this.parseValues(ret, parts));
                this.curRule.setLength(0);
                synchronized (ret) {
                	ret.nondominatedResults.add(vr);
                	ret.cache.put(vr.getItem(), vr);
                }
            } else {
                this.curRule.append(line).append('\n');
            }
		}

		private double[] parseValues(Blackboard bb, String[] parts) {
		    final int objectiveCount = bb.objectives.getObjectiveNames().size();
		    final double[] ret = new double[objectiveCount];
		    for (int i = 0; i < objectiveCount; i++) {
		        ret[i] = this.parseDoubleIfExists(parts, i);
		    }
            return ret;
        }

		private double parseDoubleIfExists(String[] parts, int index) {
			if (index < parts.length) {
				return Double.parseDouble(parts[index].trim());
			} else {
				return 0;
			}
		}

    }

    private static class SimpleLineParser extends BlockParser {
    	private final BiConsumer<Blackboard, String> consumer;

    	public SimpleLineParser(BiConsumer<Blackboard, String> consumer) {
    		this.consumer = consumer;
    	}

		@Override
		public void handleLine(Blackboard ret, String line) {
			this.consumer.accept(ret, line);
		}
    }

    private static class AndPerLineParser extends BlockParser {
    	private final BiConsumer<RuleRestrictions, And> consumer;

    	public AndPerLineParser(BiConsumer<RuleRestrictions, And> consumer) {
    		this.consumer = consumer;
    	}

		@Override
		public void handleLine(Blackboard ret, String line) {
		    final int colonIndex = line.indexOf(':');
		    final String classification = line.substring(0, colonIndex);
			final And and =
                new RuleSetParser(ret.getRecords().getRecords().getScheme()).parseRule(line.substring(colonIndex + 1));
			this.consumer.accept(ret.restrictionsFor(classification), and);
		}
    }

    private static class RulePatternPerLineParser extends BlockParser {
    	private final BiConsumer<RuleRestrictions, RulePattern> consumer;

    	public RulePatternPerLineParser(BiConsumer<RuleRestrictions, RulePattern> consumer) {
    		this.consumer = consumer;
    	}

		@Override
		public void handleLine(Blackboard ret, String line) {
            final int colonIndex = line.indexOf(':');
            final String classification = line.substring(0, colonIndex);
			final RulePattern pattern =
                RulePattern.parse(ret.getRecords().getRecords().getScheme(), line.substring(colonIndex + 1));
			this.consumer.accept(ret.restrictionsFor(classification), pattern);
		}
    }

    private static class CleaningActionParser extends BlockParser {

		@Override
		public void handleLine(Blackboard ret, String line) {
			final String[] parts = line.split(",", 2);
			switch (parts[0]) {
			case "removeRecord":
				ret.executeDataCleaningAction(ret.new RemoveRecordAction(Integer.parseInt(parts[1])));
				break;
			case "addComputedColumn":
				final String[] nameAndComputation = parts[1].split(",", 2);
				ret.executeDataCleaningAction(ret.new AddComputedColumnAction(nameAndComputation[0], nameAndComputation[1]));
				break;
			default:
				throw new RuntimeException("unknown cleaning action: " + line);
			}
		}

    }

    public static Blackboard load(RecordSet records2, ResultData resultData2, ObjectiveStrategy objectives, File saveFile) throws IOException {
        final Blackboard ret = new Blackboard(records2, resultData2, objectives, System.currentTimeMillis());
        try (BufferedReader r = new BufferedReader(new FileReader(saveFile))) {
            String line;
            BlockParser blockParser = null;
            while ((line = r.readLine()) != null) {
            	if (line.startsWith(BLOCK_START_PREFIX)) {
            		blockParser = createBlockParser(line.substring(BLOCK_START_PREFIX.length()));
            	} else {
            		blockParser.handleLine(ret, line);
            	}
            }
        }
    	ret.reevaluateAfterDataChange();
        ret.log("results loaded from " + saveFile);
        return ret;
    }

    private static BlockParser createBlockParser(String blockName) {
    	switch (blockName) {
    	case DATA_CLEANING:
    		return new CleaningActionParser();
    	case REJECTED_COLUMNS:
    		return new SimpleLineParser((Blackboard b, String line) -> b.addRejectedColumns(Collections.singletonList(line)));
    	case ACCEPTED:
    		return new AndPerLineParser((RuleRestrictions r, And line) -> r.accept(Collections.singletonList(line)));
    	case CANDIDATE:
    		return new AndPerLineParser((RuleRestrictions r, And line) -> r.keepAsCandidate(Collections.singletonList(line)));
    	case REJECTED_PATTERNS:
    		return new RulePatternPerLineParser((RuleRestrictions r, RulePattern line) -> r.reject(line));
		case PARETO_FRONT:
			return new RuleBlockParser();
		default:
			throw new RuntimeException("invalid block name: " + blockName);
    	}
	}

	public synchronized void save(File saveFile) throws IOException {
    	this.log("saving results to " + saveFile);
        try (FileWriter w = new FileWriter(saveFile)) {
        	w.write(BLOCK_START_PREFIX + DATA_CLEANING + "\n");
        	for (final DataCleaningAction c : this.cleaningActionHistory) {
        		w.write(c.serialize() + "\n");
        	}

        	w.write(BLOCK_START_PREFIX + REJECTED_COLUMNS + "\n");
        	for (final String column : this.rejectedColumns) {
        		w.write(column + "\n");
        	}

        	w.write(BLOCK_START_PREFIX + ACCEPTED + "\n");
        	for (final String key : this.restrictions.keySet()) {
            	for (final And and : this.restrictions.get(key).getAccepted()) {
            		w.write(key + ":" + and + "\n");
            	}
        	}

        	w.write(BLOCK_START_PREFIX + CANDIDATE + "\n");
            for (final String key : this.restrictions.keySet()) {
            	for (final And and : this.restrictions.get(key).getCandidates()) {
            		w.write(key + ":" + and + "\n");
            	}
            }

        	w.write(BLOCK_START_PREFIX + REJECTED_PATTERNS + "\n");
            for (final String key : this.restrictions.keySet()) {
            	for (final RulePattern and : this.restrictions.get(key).getRejected()) {
            		w.write(key + ":" + and + "\n");
            	}
            }

        	w.write(BLOCK_START_PREFIX + PARETO_FRONT + "\n");
            for (final ValuedResult<RuleSet> r : this.nondominatedResults.getItems()) {
                w.write(r.getItem().toString());
                w.write(END_OF_RULE_PREFIX + String.join(", ", toStrings(r.getAllValues())) + "\n");
            }
        }
    }

    private static List<String> toStrings(double[] allValues) {
        final List<String> ret = new ArrayList<>();
        for (final double d : allValues) {
            ret.add(Double.toString(d));
        }
        return ret;
    }

    /**
     * Changes the given rule so that it satisfies all restrictions (e.g. accepted rules)
     * and returns the result.
     */
    public RuleSet makeValid(final RuleSet rs) {
        RuleSet ret = rs;
        for (int exceptionId = 0; exceptionId < rs.getExceptionCount(); exceptionId++) {
            for (final And rule : rs.getRules(exceptionId)) {
                if (this.containsForbiddenFeature(rule)) {
                    ret = ret.removeRule(exceptionId, rule);
                }
            }
        }
        for (final Entry<String, RuleRestrictions> e : this.restrictions.entrySet()) {
            for (final And incl : e.getValue().accepted) {
                ret = ret.addRule(e.getKey(), incl);
            }
            for (final RulePattern incl : e.getValue().rejected) {
                ret = ret.remove(e.getKey(), incl, e.getValue().accepted, e.getValue().candidates);
            }
        }
        return ret;
    }

    private boolean containsForbiddenFeature(And rule) {
        for (final Rule r : rule.getChildren()) {
            if (!Collections.disjoint(r.getUsedFeatures().keySet(), this.rejectedColumns)) {
                return true;
            }
        }
        return false;
    }

    public ValuedResult<RuleSet> simplifyEvaluateAndAdd(RuleSet rs) {
    	return this.evaluateAndAdd(rs.simplify(this.getRecords().getRecords()));
    }

    private ValuedResult<RuleSet> evaluateAndAdd(RuleSet rs) {
        final ValuedResult<RuleSet> r = this.makeValidAndEvaluate(rs);
        synchronized (this) {
            this.nondominatedResults.add(r);
        }
        return r;
    }

    public ValuedResult<RuleSet> makeValidAndEvaluate(RuleSet rs) {
        final RuleSet validRuleset = this.makeValid(rs);
        return this.evaluate(validRuleset);
    }

    public ValuedResult<RuleSet> evaluate(RuleSet rs) {
        final ValuedResult<RuleSet> cached = this.cache.get(rs);
        if (cached != null) {
            return cached;
        }

        final RecordsAndRemarks rr = this.recordsAndRemarks.get();
        final ValuedResult<RuleSet> r = ValuedResult.create(rs, rr.records, rr.resultData, this.objectives);
        this.cache.put(rs, r);
        if (this.cache.size() > AUTO_PURGE_LIMIT) {
            this.checkAutoPurge();
        }
        return r;
    }

    private synchronized void checkAutoPurge() {
        final int size = this.cache.size();
        if (size > AUTO_PURGE_LIMIT) {
            this.log("auto-purge triggered at cache size " + this.cache.size());
            this.purgeRules(100);
        }
    }

    public synchronized NondominatedResults<RuleSet> getNondominatedResultsSnapshot() {
        final NondominatedResults<RuleSet> copy = new NondominatedResults<>();
        copy.addAll(this.nondominatedResults);
        return copy;
    }

    public synchronized void addAll(NondominatedResults<RuleSet> results) {
        for (final ValuedResult<RuleSet> r : results.getItems()) {
        	this.simplifyEvaluateAndAdd(r.getItem());
        }
    }

    public RecordsAndRemarks getRecords() {
        return this.recordsAndRemarks.get();
    }

    public int getCacheSize() {
        return this.cache.size();
    }

    public synchronized ValuedResult<RuleSet> getRandomResult(Random random) {
        return this.nondominatedResults.getRandomItem(random);
    }

    public synchronized ValuedResult<RuleSet> getBestResultInLimits(Random random) {
        return this.getNavigationLimits().filter(this.nondominatedResults)
        		.getBestItem(random, this.getCurrentTargetFunction());
    }

    public void addToUserFedLocalSearchQueue(ValuedResult<RuleSet> rs) {
        //in the user queues we add to the front to favor the most recent items
        this.userFedLocalSearchQueue.addFirst(rs);
    }

    public void addToUserFedPathRelinkingQueue(ValuedResult<RuleSet> rs) {
        //in the user queues we add to the front to favor the most recent items
        this.userFedPathRelinkingQueue.addFirst(rs);
    }

    public void addToAgentFedLocalSearchQueue(ValuedResult<RuleSet> rs) {
        this.agentFedLocalSearchQueue.addLast(rs);
    }

    public void addToAgentFedPathRelinkingQueue(ValuedResult<RuleSet> rs) {
        this.agentFedPathRelinkingQueue.addLast(rs);
    }

    public ValuedResult<RuleSet> getOpenItemFromUserFedLocalSearchQueue() {
        return this.userFedLocalSearchQueue.pollFirst();
    }

    public ValuedResult<RuleSet> getOpenItemFromUserFedPathRelinkingQueue() {
        return this.userFedPathRelinkingQueue.pollFirst();
    }

    public ValuedResult<RuleSet> getOpenItemFromAgentFedLocalSearchQueue() {
        return this.agentFedLocalSearchQueue.pollFirst();
    }

    public ValuedResult<RuleSet> getOpenItemFromAgentFedPathRelinkingQueue() {
        return this.agentFedPathRelinkingQueue.pollFirst();
    }

    public Random createNewRandom() {
        final long seed = this.nextRandomSeed();
        this.log("creating new random with seed " + seed);
		return new Random(seed);
    }

	public long nextRandomSeed() {
		return this.seedCounter.getAndIncrement();
	}

    public RuleRestrictions restrictionsFor(String classification) {
        return this.restrictions.get(classification);
    }

    public Set<String> getRejectedColumns() {
        return this.rejectedColumns;
    }

    public void addRejectedColumns(Collection<String> columns) {
    	this.log("adding rejected columns " + columns);
        this.rejectedColumns.addAll(columns);
        this.revalidateParetoSet();
    }

    public void removeColumnRejection(String column) {
    	this.log("undoing rejection of column " + column);
        this.rejectedColumns.remove(column);
        this.revalidateParetoSet();
    }

    private void revalidateParetoSet() {
        //first remove all invalid entries from the pareto set. this should be pretty fast
        synchronized (this) {
            this.nondominatedResults.removeIf((RuleSet rs) -> this.isInvalid(rs));
            //ensure that there is at least one entry in the set
            this.addDefaultRulesForAllClasses();
        }

        //revalidating the old entries can take a long time, do so in the background
        this.revalidateExecutor.execute(() -> this.refillParetoSet(new ArrayList<>(this.cache.values())));
    }

    private void refillParetoSet(List<ValuedResult<RuleSet>> cacheEntries) {
    	this.log("start refilling pareto set");
    	//sort entries so that the more promising ones are re-evaluated first
    	cacheEntries.sort(Comparator.comparingDouble(this.targetFunction.get()));
    	//and first try the ones that were within the navigation limits
        for (final ValuedResult<RuleSet> oldItem : cacheEntries) {
        	if (this.navigationLimits.isInLimits(oldItem)) {
        		this.simplifyEvaluateAndAdd(oldItem.getItem());
        	}
        }
        for (final ValuedResult<RuleSet> oldItem : cacheEntries) {
            this.simplifyEvaluateAndAdd(oldItem.getItem());
        }
    	this.log("refilling pareto set finished");
    }

    private boolean isInvalid(RuleSet rs) {
        return !rs.equals(this.makeValid(rs));
    }

    public void setCurrentTargetFunction(TargetFunction targetFunction) {
        this.targetFunction.set(targetFunction);
    }

    public TargetFunction getCurrentTargetFunction() {
        return this.targetFunction.get();
    }

    public synchronized void log(String string) {
        System.out.println(String.format("===LOG=== %s, %s, %s",
                        Thread.currentThread().getName(),
                        Instant.now().toString(),
                        string));
    }

	public synchronized String removeRecord(int id) {
		return this.executeDataCleaningAction(new RemoveRecordAction(id));
	}

	public synchronized String addComputedColumn(String name, String computationScript) {
		return this.executeDataCleaningAction(new AddComputedColumnAction(name, computationScript));
	}

	private String executeDataCleaningAction(DataCleaningAction action) {
		this.log(action.getUserString());
		final String resultMessage = action.execute();
		this.log(resultMessage);
		this.cleaningActionHistory.add(action);
		return resultMessage;
	}

	public List<DataCleaningAction> getCleaningActionHistory() {
		return this.cleaningActionHistory;
	}

	private void reevaluateAfterDataChange() {
		//all the known result values need to be re-calculated (in the background)
		final List<ValuedResult<RuleSet>> oldCacheContent = new ArrayList<>(this.cache.values());
		this.cache.clear();
		this.nondominatedResults.clear();
        this.addDefaultRulesForAllClasses();
        this.revalidateExecutor.execute(() -> this.refillParetoSet(oldCacheContent));
	}

	public NavigationLimits getNavigationLimits() {
		return this.navigationLimits;
	}

	/**
	 * Throws away all but a certain number of rules from the Pareto set as well as from the cache.
	 * The given number is used as a rough indicator and not always met exactly.
	 * Tries to keep all best results in the limits for the given target functions and also tries to keep the variety
	 * of rules (in terms of matched records) to a maximum.
	 */
	public synchronized void purgeRules(int countToKeep) {
		this.log("purging all but ~" + countToKeep + " rules");
		final Set<ValuedResult<RuleSet>> rulesToKeep = PurgeSelectionAlgorithm.determineRulesToKeep(
				this.getNondominatedResultsSnapshot(),
				this.getNavigationLimits(),
				countToKeep,
				this.targetFunctions,
				Arrays.asList(this.getRecords().records.getRecords()),
				this.createNewRandom());

		this.nondominatedResults.clear();
		this.cache.clear();
		for(final ValuedResult<RuleSet> e : rulesToKeep) {
			this.nondominatedResults.add(e);
			this.cache.put(e.getItem(), e);
		}
		this.log("purging done, " + this.nondominatedResults.getItems().size() + " rules remaining in Pareto front");
	}

    public void addDefaultRulesForAllClasses() {
        final ResultData results = this.recordsAndRemarks.get().resultData;
        for (final String strategy : results.getAllClasses()) {
            this.simplifyEvaluateAndAdd(RuleSet.create(strategy));
        }
    }

}
