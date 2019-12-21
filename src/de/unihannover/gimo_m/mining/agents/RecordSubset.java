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
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import de.unihannover.gimo_m.mining.common.And;
import de.unihannover.gimo_m.mining.common.RandomUtil;
import de.unihannover.gimo_m.mining.common.Record;
import de.unihannover.gimo_m.miningInputCreation.RemarkTriggerMap;

public final class RecordSubset {
    private final List<Record> must;
    private final List<Record> can;
    private final List<Record> no;

    public RecordSubset(Record[] records) {
        this.must = new ArrayList<>();
        this.can = new ArrayList<>();
        this.no = new ArrayList<>();
        for (final Record r : records) {
            switch (r.getClassification()) {
            case MUST_BE:
                this.must.add(r);
                break;
            case CAN_BE:
                this.can.add(r);
                break;
            case NO_TRIGGER:
                this.no.add(r);
                break;
            }
        }
    }

    public RecordSubset(List<Record> must, List<Record> no) {
        this.must = must;
        this.can = Collections.emptyList();
        this.no = no;
    }

    private RecordSubset(List<Record> must, List<Record> can, List<Record> no) {
        this.must = must;
        this.can = can;
        this.no = no;
    }

    public List<Record> getMustRecords() {
        return this.must;
    }

    public List<Record> getNoRecords() {
        return this.no;
    }

    public int getMustRecordCount() {
        return this.must.size();
    }

    public int getNoRecordCount() {
        return this.no.size();
    }

    public RecordSubset keepSatisfying(Predicate<Record> rule) {
        return new RecordSubset(
                        this.must.stream().filter(rule).collect(Collectors.toList()),
                        this.can.stream().filter(rule).collect(Collectors.toList()),
                        this.no.stream().filter(rule).collect(Collectors.toList()));
    }

    public RecordSubset keepNotSatisfying(Predicate<Record> rule) {
        return this.keepSatisfying(rule.negate());
    }

    public RecordSubset distributeCan(
    		RemarkTriggerMap triggerMap,
    		double randomness,
    		Random random,
    		List<And> inclusions,
    		List<And> exclusions) {
        final List<Record> must = new ArrayList<>(this.must);
        final List<Record> no = new ArrayList<>(this.no);

        String prevId = null;
        final List<Record> inCurrentTicket = new ArrayList<>();
        for (final Record r : this.can) {
        	if (this.matchesOneOf(r, exclusions)) {
        		must.add(r);
        		continue;
        	}
        	if (this.matchesOneOf(r, inclusions)) {
        		no.add(r);
        		continue;
        	}
            if (!r.getId().getTicket().equals(prevId)) {
                if (!inCurrentTicket.isEmpty()) {
                    this.splitSubset(inCurrentTicket, triggerMap, must, no, randomness, random);
                    inCurrentTicket.clear();
                }
                prevId = r.getId().getTicket();
            }
            inCurrentTicket.add(r);
        }
        if (!inCurrentTicket.isEmpty()) {
            this.splitSubset(inCurrentTicket, triggerMap, must, no, randomness, random);
        }
        return new RecordSubset(must, Collections.emptyList(), no);
    }

    private boolean matchesOneOf(Record r, List<And> ands) {
    	for (final And and : ands) {
    		if (and.test(r)) {
    			return true;
    		}
    	}
		return false;
	}

	private void splitSubset(
                    List<Record> inCurrentTicket,
                    RemarkTriggerMap triggerMap,
                    List<Record> must2,
                    List<Record> no2,
                    double randomness,
                    Random random) {
        final Set<Record> subset = SubsetCoverHeuristic.selectSubset(inCurrentTicket, triggerMap, randomness, random, must2);
        for (final Record r : inCurrentTicket) {
            if (subset.contains(r)) {
                must2.add(r);
            } else {
                no2.add(r);
            }
        }
    }

    public boolean isEmpty() {
        return this.must.isEmpty() && this.no.isEmpty() && this.can.isEmpty();
    }

    public RecordSubset swapMustAndNo() {
        return new RecordSubset(this.no, this.can, this.must);
    }

	public RecordSubset downsample(Random random, double factor, int minSizePerClass) {
		int minoritySize = this.no.size();
		if (this.can.size() > 0 && this.can.size() < minoritySize) {
			minoritySize = this.can.size();
		}
		if (this.must.size() > 0 && this.must.size() < minoritySize) {
			minoritySize = this.must.size();
		}
		final int wantedSize = Math.max(minSizePerClass, (int) (factor * minoritySize));

		return new RecordSubset(
				createSample(random, this.must, wantedSize),
				createSample(random, this.can, wantedSize),
				createSample(random, this.no, wantedSize));
	}

	private static List<Record> createSample(Random random, List<Record> source, int wantedSize) {
		if (source.isEmpty()) {
			return Collections.emptyList();
		}
		final List<Record> ret = new ArrayList<>(wantedSize);
		for (int i = 0; i < wantedSize; i++) {
			ret.add(RandomUtil.randomItem(random, source));
		}
		return ret;
	}

}