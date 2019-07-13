package de.unihannover.reviews.mining.common;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

import de.unihannover.reviews.miningInputCreation.TriggerClassification;

public class PurgeSelectionAlgorithmTest {

	private static Record record(String string, String string2) {
		return new Record(new ChangePartId("TIC-123", "X", "c"), Collections.emptyList(), Arrays.asList(string, string2), TriggerClassification.NO_TRIGGER);
	}

	private static ValuedResult<RuleSet> rule(RecordScheme scheme, int column, String value) {
		final RuleSet r = RuleSet.SKIP_NONE
				.include(new And(new Equals(scheme, column, value)));
		return new ValuedResult<RuleSet>(r, 0, 0, 0, 0, 0, 0, 0);
	}

	@Test
	public void testWithoutTargetFunctions() {
		final RecordScheme scheme = new RecordScheme(Collections.emptyList(), Arrays.asList("s1", "s2"));
		final List<Record> records = Arrays.asList(
				record("a", "A"),
				record("b", "B"),
				record("c", "C"),
				record("d", "D"),
				record("e", "E"),
				record("f", "F"),
				record("g", "G")
		);
		final NondominatedResults<RuleSet> paretoFront = new NondominatedResults<>();
		paretoFront.add(rule(scheme, 0, "a"));
		paretoFront.add(rule(scheme, 0, "b"));
		paretoFront.add(rule(scheme, 0, "c"));
		paretoFront.add(rule(scheme, 0, "d"));
		paretoFront.add(rule(scheme, 1, "A"));
		paretoFront.add(rule(scheme, 1, "B"));
		paretoFront.add(rule(scheme, 1, "C"));
		paretoFront.add(rule(scheme, 1, "D"));
		final Set<ValuedResult<RuleSet>> actual = PurgeSelectionAlgorithm.determineRulesToKeep(
				paretoFront, new NavigationLimits(), 4, Collections.emptyList(), records, new Random(123));

		//the exact result depends on the randomness, but for each letter there must not be more than one rule
		assertEquals(new LinkedHashSet<>(Arrays.asList(
					rule(scheme, 1, "A"),
					rule(scheme, 1, "B"),
					rule(scheme, 0, "c"),
					rule(scheme, 1, "D")
				)),
				actual);
	}

}
