package de.unihannover.reviews.mining.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class NondominatedResultsTest {

    @Test
    public void testAdd() {
        final NondominatedResults<And> nd = new NondominatedResults<>();
        assertEquals(Collections.emptyList(), nd.getItems());
        assertTrue(nd.add(new ValuedResult<>(new And(), 1, 0, 0, 0, 0, 0.0, 0.0)));
        assertEquals(Arrays.asList(new ValuedResult<>(new And(), 1, 0, 0, 0, 0, 0.0, 0.0)), nd.getItems());
        assertTrue(nd.add(new ValuedResult<>(new And(), 0, 1, 0, 0, 0, 0.0, 0.0)));
        assertEquals(Arrays.asList(
                        new ValuedResult<>(Collections.singleton(new And()), 0, 1, 0, 0, 0, 0.0, 0.0),
                        new ValuedResult<>(Collections.singleton(new And()), 1, 0, 0, 0, 0, 0.0, 0.0)),
                    nd.getItemsSorted());
        assertTrue(nd.add(new ValuedResult<>(new And(), 0, 0, 0, 0, 0, 0.0, 0.0)));
        assertEquals(Arrays.asList(new ValuedResult<>(new And(), 0, 0, 0, 0, 0, 0.0, 0.0)), nd.getItems());
    }

    @Test
    public void testAddItemTwice() {
        final NondominatedResults<And> nd = new NondominatedResults<>();
        assertTrue(nd.add(new ValuedResult<>(new And(), 0, 1, 0, 0, 0, 0.0, 0.0)));
        assertFalse(nd.add(new ValuedResult<>(new And(), 0, 1, 0, 0, 0, 0.0, 0.0)));
        assertEquals(Arrays.asList(new ValuedResult<>(new And(), 0, 1, 0, 0, 0, 0.0, 0.0)), nd.getItems());
    }
}
