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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import de.unihannover.gimo_m.mining.common.ChangePartId;
import de.unihannover.gimo_m.mining.common.RandomUtil;
import de.unihannover.gimo_m.mining.common.Record;
import de.unihannover.gimo_m.miningInputCreation.OffsetBitset;
import de.unihannover.gimo_m.miningInputCreation.RemarkTriggerMap;
import de.unihannover.gimo_m.predictionDataPreparation.Multimap;

public class SubsetCoverHeuristic {

    private static final class RecordCoverageSetInfo implements Comparable<RecordCoverageSetInfo> {
        private final Record record;
        private int notYetCoveredRemarkCount;

        public RecordCoverageSetInfo(Record r) {
            this.record = r;
        }

		@Override
		public int compareTo(RecordCoverageSetInfo o) {
			return Integer.compare(this.notYetCoveredRemarkCount, o.notYetCoveredRemarkCount);
		}

    }

    public static Set<Record> selectSubset(
                    List<Record> candidateRecords,
                    RemarkTriggerMap triggerMap,
                    double randomness,
                    Random random,
                    List<Record> alreadySelected) {

    	//determine remarks that are already covered by the already selected records
        final OffsetBitset alreadyCoveredRemarks = new OffsetBitset();
        for (final Record r : alreadySelected) {
            alreadyCoveredRemarks.addAll(getCoveredRemarks(triggerMap, r.getId()));
        }

        //determine counters with the number of uncovered remarks per record and
        // build a map that links remarks to their corresponding counters
        final Multimap<Integer, RecordCoverageSetInfo> recordsForRemarks = new Multimap<>();
        final List<RecordCoverageSetInfo> recordCoverages = new ArrayList<>();
        for (final Record r : candidateRecords) {
        	final RecordCoverageSetInfo coverageInfo = new RecordCoverageSetInfo(r);
        	recordCoverages.add(coverageInfo);
        	final OffsetBitset coveredRemarks = getCoveredRemarks(triggerMap, r.getId());
        	coveredRemarks.removeAll(alreadyCoveredRemarks);
        	for (final Integer remarkId : coveredRemarks.toSet()) {
    			recordsForRemarks.add(remarkId, coverageInfo);
    			coverageInfo.notYetCoveredRemarkCount++;
        	}
        }

        //shuffle to break ties (more or less) randomly
        Collections.shuffle(recordCoverages, random);
        Collections.sort(recordCoverages);

        final Set<Record> ret = new LinkedHashSet<>();
        while (thereAreRecordsThatCanCoverRemarks(recordCoverages)) {
        	//choose a record that covers a rather high number of remarks (depending on the randomness)
    		int indexOfChosenItem = RandomUtil.getIndexAtEnd(recordCoverages, randomness, random);
    		while (recordCoverages.get(indexOfChosenItem).notYetCoveredRemarkCount <= 0) {
    			//ensure that the chosen record covers at least something
    			indexOfChosenItem = RandomUtil.intBetween(random, indexOfChosenItem + 1, recordCoverages.size());
    		}
    		//note that the remarks covered by the chosen record do not need to be covered by any other record
        	final RecordCoverageSetInfo chosenRecord = recordCoverages.remove(indexOfChosenItem);
        	ret.add(chosenRecord.record);
        	for (final Integer remarkId : getCoveredRemarks(triggerMap, chosenRecord.record.getId()).toSet()) {
        		for (final RecordCoverageSetInfo notNeededRecord : recordsForRemarks.get(remarkId)) {
        			notNeededRecord.notYetCoveredRemarkCount--;
        		}
        	}
        	Collections.sort(recordCoverages);
        }

        return ret;
    }

    private static boolean thereAreRecordsThatCanCoverRemarks(List<RecordCoverageSetInfo> recordCoverages) {
    	//assumes that the list is sorted
    	final int size = recordCoverages.size();
		return size > 0 && recordCoverages.get(size - 1).notYetCoveredRemarkCount > 0;
	}

	private static OffsetBitset getCoveredRemarks(RemarkTriggerMap triggerMap, ChangePartId id) {
        if (id.isLineGranularity()) {
            return triggerMap.getCoveredRemarks(id.getTicket(), id.getCommit(), id.getFile(), id.getLineFrom(), id.getLineTo());
        } else {
            return triggerMap.getCoveredRemarks(id.getTicket(), id.getCommit(), id.getFile());
        }
    }
}
