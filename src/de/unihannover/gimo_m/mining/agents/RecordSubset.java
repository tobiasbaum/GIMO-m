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
import java.util.function.Predicate;
import java.util.stream.Collectors;

import de.unihannover.gimo_m.mining.common.Record;
import de.unihannover.gimo_m.util.Util;

public final class RecordSubset {
    private final List<Record> must;
    private final List<Record> no;

    public RecordSubset(List<Record> must, List<Record> no) {
        this.must = must;
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
                        this.no.stream().filter(rule).collect(Collectors.toList()));
    }

    public RecordSubset keepNotSatisfying(Predicate<Record> rule) {
        return this.keepSatisfying(rule.negate());
    }

    public boolean isEmpty() {
        return this.must.isEmpty() && this.no.isEmpty();
    }

    public RecordSubset swapMustAndNo() {
        return new RecordSubset(this.no, this.must);
    }

	public RecordSubset downsample(Random random, double factor, int minSizePerClass) {
		int minoritySize = this.no.size();
		if (this.must.size() > 0 && this.must.size() < minoritySize) {
			minoritySize = this.must.size();
		}
		final int wantedSize = Math.max(minSizePerClass, (int) (factor * minoritySize));

		return new RecordSubset(
				createSample(random, this.must, wantedSize),
				createSample(random, this.no, wantedSize));
	}

	private static List<Record> createSample(Random random, List<Record> source, int wantedSize) {
		if (source.isEmpty()) {
			return Collections.emptyList();
		}
		final List<Record> ret = new ArrayList<>(wantedSize);
		for (int i = 0; i < wantedSize; i++) {
			ret.add(Util.randomItem(random, source));
		}
		return ret;
	}

}