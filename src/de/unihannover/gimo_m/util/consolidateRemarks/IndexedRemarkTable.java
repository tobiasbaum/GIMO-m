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
package de.unihannover.gimo_m.util.consolidateRemarks;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IndexedRemarkTable {

	private final Map<String, Integer> indices = new HashMap<>();
	private final Map<String, List<String[]>> records = new HashMap<>();

	public IndexedRemarkTable(String[] fields) {
		for (int i = 0; i < fields.length; i++) {
			this.indices.put(fields[i], i);
		}
	}

	public static IndexedRemarkTable load(String filename) throws IOException {
		try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF-8"))) {
			return load(r);
		}
	}

	public static IndexedRemarkTable load(BufferedReader g) throws IOException {
		final String firstLine = g.readLine();
		final IndexedRemarkTable ret = new IndexedRemarkTable(firstLine.split(";"));
		String line;
		while ((line = g.readLine()) != null) {
			ret.add(line.split(";"));
		}
		return ret;
	}

	private void add(String[] values) {
		final String key = this.getVal(values, "ticket") + ";" + this.getVal(values, "commit") + ";" + this.getVal(values, "file");
		List<String[]> recordsForKey = this.records.get(key);
		if (recordsForKey == null) {
			recordsForKey = new ArrayList<>();
			this.records.put(key, recordsForKey);
		}
		recordsForKey.add(values);
	}

	private String getVal(String[] values, String fieldName) {
		final int idx = this.indices.get(fieldName);
		return values[idx];
	}

	public String getField(String ticket, String commit, String file, int line, String fieldName) {
		final String key = ticket + ";" + commit + ";" + file;
		final List<String[]> recordsForKey = this.records.get(key);
		if (recordsForKey == null) {
			return null;
		}
		for (final String[] record : recordsForKey) {
			final int lineFrom = Integer.parseInt(this.getVal(record, "lineFrom"));
			if (lineFrom > line) {
				continue;
			}
			final int lineTo = Integer.parseInt(this.getVal(record, "lineTo"));
			if (lineTo < line) {
				continue;
			}
			return this.getVal(record, fieldName);
		}
		return null;
	}

	public boolean isFieldName(String feature) {
		return this.indices.containsKey(feature);
	}

	public Set<String> getFieldNames() {
		return this.indices.keySet();
	}

}
