package de.unihannover.gimo_m.mining.common;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

import de.unihannover.gimo_m.util.Multiset;

public class PurgeSelectionAlgorithmTest {

	private static ValuedResult<RuleSet> rule(String... valuesPerRecordId) {
		RuleSet rule = RuleSet.create("");
		for (int i = 0; i < valuesPerRecordId.length; i++) {
			rule = rule.addRule(valuesPerRecordId[i], new And(recordIdMatchesRule(i)));
		}
		return new ValuedResult<RuleSet>(rule);
	}

	private static Rule recordIdMatchesRule(int recordId) {
		return new Rule() {
			@Override
			public double getComplexity(Set<Object> usedValues) {
				return 0;
			}
			@Override
			public boolean test(Record r) {
				return r.getId() == recordId;
			}
			@Override
			public Multiset<String> getUsedFeatures() {
				return new Multiset<String>();
			}
		};
	}

	@Test
	public void test() {
		final List<ValuedResult<RuleSet>> rules = Arrays.asList(
				rule("a", "a", "b", "b"),
				rule("a", "a", "b", "b"),
				rule("x", "x", "y", "y"),
				rule("x", "x", "y", "y"));
		final List<Record> records = Arrays.asList(
				new Record(0, Collections.emptyList(), Collections.emptyList(), ""),
				new Record(1, Collections.emptyList(), Collections.emptyList(), ""),
				new Record(2, Collections.emptyList(), Collections.emptyList(), ""),
				new Record(3, Collections.emptyList(), Collections.emptyList(), ""));

		int goodCount = 0;
		int badCount = 0;
		for (int i = 0; i < 100; i++) {
			final Collection<? extends ValuedResult<RuleSet>> actual =
					PurgeSelectionAlgorithm.getOneRulePerCluster(2, rules, records, new Random(i));

			assertEquals(2, actual.size());
			final boolean goodClustering = set("a", "x").equals(determineValueSet(actual, records.get(0)));
			if (goodClustering) {
				goodCount++;
			} else {
				badCount++;
			}
		}
		assertThat(goodCount, greaterThan(badCount));
	}

	private static Set<String> determineValueSet(Collection<? extends ValuedResult<RuleSet>> actual, Record record) {
		final Set<String> ret = new LinkedHashSet<String>();
		for (final ValuedResult<RuleSet> v : actual) {
			ret.add(v.getItem().apply(record));
		}
		return ret;
	}

	private static Set<String> set(String... strings) {
		return new LinkedHashSet<String>(Arrays.asList(strings));
	}

}
