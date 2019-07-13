package de.unihannover.reviews.mining.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;

import java.util.Arrays;

import org.junit.Test;

public class NavigationLimitsTest {

	private static final double EPSILON = 0.00000000001;

	@Test
	public void testSetLimit() {
		final NavigationLimits l = new NavigationLimits();
		final TargetFunction f1 = new TargetFunction("f1", r -> r.getFeatureCount(), "");
		final TargetFunction f2 = new TargetFunction("f2", r -> r.getMissedRemarkCount(), "");

		assertFalse(Double.isFinite(l.getLimit(f1)));
		assertFalse(Double.isFinite(l.getLimit(f2)));

		l.setLimit(f1, 3.0);
		assertEquals(3.0, l.getLimit(f1), EPSILON);
		assertFalse(Double.isFinite(l.getLimit(f2)));

		l.setLimit(f2, 2.0);
		assertEquals(3.0, l.getLimit(f1), EPSILON);
		assertEquals(2.0, l.getLimit(f2), EPSILON);
	}

	@Test
	public void testReplaceLimit() {
		final NavigationLimits l = new NavigationLimits();
		final TargetFunction f1 = new TargetFunction("f1", r -> r.getFeatureCount(), "");

		l.setLimit(f1, 3.0);
		l.setLimit(f1, 4.0);
		assertEquals(4.0, l.getLimit(f1), EPSILON);
	}

	@Test
	public void testRemoveLimit() {
		final NavigationLimits l = new NavigationLimits();
		final TargetFunction f1 = new TargetFunction("f1", r -> r.getFeatureCount(), "");

		l.setLimit(f1, 1.5);
		assertEquals(1.5, l.getLimit(f1), EPSILON);
		l.removeLimit(f1);
		assertFalse(Double.isFinite(l.getLimit(f1)));
	}

	@Test
	public void testFilter() {
		final NavigationLimits l = new NavigationLimits();
		final TargetFunction f1 = new TargetFunction("f1", r -> r.getFeatureCount(), "");
		final TargetFunction f2 = new TargetFunction("f2", r -> r.getMissedRemarkCount(), "");

		l.setLimit(f1, 10.0);
		l.setLimit(f2, 15.0);

		final NondominatedResults<String> s = new NondominatedResults<>();
		s.add(new ValuedResult<String>("a", 15, 4, 0, 10, 0, 0.0, 0.0));
		s.add(new ValuedResult<String>("b", 14, 5, 0, 9, 0, 0.0, 0.0));
		s.add(new ValuedResult<String>("c", 16, 3, 0, 10, 0, 0.0, 0.0));
		s.add(new ValuedResult<String>("d", 15, 2, 0, 11, 0, 0.0, 0.0));
		s.add(new ValuedResult<String>("e", 16, 1, 0, 11, 0, 0.0, 0.0));
		assertEquals(5, s.getItems().size());

		final NondominatedResults<String> filtered = l.filter(s);
		assertNotSame(s, filtered);
		assertEquals(Arrays.asList(
				new ValuedResult<String>("a", 15, 4, 0, 10, 0, 0.0, 0.0),
				new ValuedResult<String>("b", 14, 5, 0, 9, 0, 0.0, 0.0)),
				filtered.getItems());
	}

}
