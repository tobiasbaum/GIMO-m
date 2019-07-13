package de.unihannover.reviews.mining.common;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import de.unihannover.reviews.miningInputCreation.RemarkTriggerMap;

public class MissedTriggerCounterTest {

    private static final double EPSILON = 0.00000001;

	private static ChangePartId cpid(String ticket, String file, String commit) {
        return new ChangePartId(ticket, file, commit);
    }

    private static ChangePartId cpid(String ticket, String file, String commit, int lineFrom, int lineTo) {
        return new ChangePartId(ticket, file, commit, lineFrom, lineTo);
    }

    @Test
    public void testNoRemarksWithActive() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleActive(cpid("TIC-123", "A.java", "cafe"));
        assertEquals(0, c.countRemarksWithoutTriggers());
    }

    @Test
    public void testNoRemarksWithInactive() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleInactive(cpid("TIC-123", "A.java", "cafe"));
        assertEquals(0, c.countRemarksWithoutTriggers());
    }

    @Test
    public void testOneRemarkCapturedByActiveTrigger() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.add("cafe,A.java;TIC-123;acef,A.java");
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleActive(cpid("TIC-123", "A.java", "acef"));
        assertEquals(0, c.countRemarksWithoutTriggers());
    }

    @Test
    public void testOneRemarkNotCapturedByInactiveTrigger() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.add("cafe,A.java;TIC-123;acef,A.java");
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleInactive(cpid("TIC-123", "A.java", "acef"));
        assertEquals(1, c.countRemarksWithoutTriggers());
    }

    @Test
    public void testOneRemarkMissedByTrigger() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.add("cafe,A.java;TIC-123;acef,A.java");
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleActive(cpid("TIC-123", "B.java", "acef"));
        assertEquals(1, c.countRemarksWithoutTriggers());
    }

    @Test
    public void testWholeTicketWithOneActive() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.add("cafe,A.java;TIC-123;WT");
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleInactive(cpid("TIC-123", "A.java", "acef"));
        c.handleActive(cpid("TIC-123", "B.java", "acef"));
        assertEquals(0, c.countRemarksWithoutTriggers());
    }

    @Test
    public void testWholeTicketWithTwoActives() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.add("cafe,A.java;TIC-123;WT");
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleActive(cpid("TIC-123", "A.java", "acef"));
        c.handleActive(cpid("TIC-123", "B.java", "acef"));
        assertEquals(0, c.countRemarksWithoutTriggers());
    }

    @Test
    public void testWholeTicketWithNoActive() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.add("cafe,A.java;TIC-123;WT");
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleInactive(cpid("TIC-123", "A.java", "acef"));
        c.handleInactive(cpid("TIC-123", "B.java", "acef"));
        assertEquals(1, c.countRemarksWithoutTriggers());
    }

    @Test
    public void testTwoTickets() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.add("cafe,A.java;TIC-123;WT");
        m.add("fefe,B.java;TIC-456;WT");
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleInactive(cpid("TIC-123", "X.java", "acef"));
        c.handleInactive(cpid("TIC-456", "Y.java", "efef"));
        c.handleInactive(cpid("TIC-456", "Z.java", "efef"));
        assertEquals(2, c.countRemarksWithoutTriggers());
    }

    @Test
    public void testLineGranularity1() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.add("cafe,A.java,99;TIC-123;acef,A.java,99");
        m.add("cafe,A.java,99;TIC-123;fefe,A.java,99");
        m.add("cafe,B.java;TIC-123;fefe,A.java,99");
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleInactive(cpid("TIC-123", "A.java", "acef"));
        c.handleInactive(cpid("TIC-123", "B.java", "acef"));
        c.handleInactive(cpid("TIC-123", "A.java", "fefe"));
        assertEquals(2, c.countRemarksWithoutTriggers());
    }

    @Test
    public void testLineGranularity2() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.add("cafe,A.java,99;TIC-123;acef,A.java,99");
        m.add("cafe,A.java,99;TIC-123;fefe,A.java,99");
        m.add("cafe,B.java;TIC-123;fefe,A.java,99");
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleActive(cpid("TIC-123", "A.java", "acef"));
        c.handleInactive(cpid("TIC-123", "B.java", "acef"));
        c.handleInactive(cpid("TIC-123", "A.java", "fefe"));
        assertEquals(1, c.countRemarksWithoutTriggers());
    }

    @Test
    public void testLineGranularity3() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.add("cafe,A.java,99;TIC-123;acef,A.java,99");
        m.add("cafe,A.java,99;TIC-123;fefe,A.java,99");
        m.add("cafe,B.java;TIC-123;fefe,A.java,99");
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleInactive(cpid("TIC-123", "A.java", "acef"));
        c.handleActive(cpid("TIC-123", "B.java", "acef"));
        c.handleInactive(cpid("TIC-123", "A.java", "fefe"));
        assertEquals(2, c.countRemarksWithoutTriggers());
    }

    @Test
    public void testLineGranularity4() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.add("cafe,A.java,99;TIC-123;acef,A.java,99");
        m.add("cafe,A.java,99;TIC-123;fefe,A.java,99");
        m.add("cafe,B.java;TIC-123;fefe,A.java,99");
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleInactive(cpid("TIC-123", "A.java", "acef"));
        c.handleInactive(cpid("TIC-123", "B.java", "acef"));
        c.handleActive(cpid("TIC-123", "A.java", "fefe"));
        assertEquals(0, c.countRemarksWithoutTriggers());
    }

    @Test
    public void testLineGranularityInRequest1() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.add("cafe,A.java,77;TIC-123;acef,A.java,78");
        m.add("cafe,A.java,99;TIC-123;acef,A.java,98");
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleActive(cpid("TIC-123", "A.java", "acef", 79, 97));
        assertEquals(2, c.countRemarksWithoutTriggers());
    }

    @Test
    public void testLineGranularityInRequest2() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.add("cafe,A.java,77;TIC-123;acef,A.java,78");
        m.add("cafe,A.java,99;TIC-123;acef,A.java,98");
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleActive(cpid("TIC-123", "A.java", "acef", 78, 97));
        assertEquals(1, c.countRemarksWithoutTriggers());
    }

    @Test
    public void testLineGranularityInRequest3() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.add("cafe,A.java,77;TIC-123;acef,A.java,78");
        m.add("cafe,A.java,99;TIC-123;acef,A.java,98");
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleActive(cpid("TIC-123", "A.java", "acef", 79, 98));
        assertEquals(1, c.countRemarksWithoutTriggers());
    }

    @Test
    public void testLineGranularityInRequest4() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.add("cafe,A.java,77;TIC-123;acef,A.java,78");
        m.add("cafe,A.java,99;TIC-123;acef,A.java,98");
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleActive(cpid("TIC-123", "A.java", "acef", 78, 98));
        assertEquals(0, c.countRemarksWithoutTriggers());
    }

    @Test
    public void testGetTicketsWithMisses() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.add("cafe,A.java,77;TIC-123;acef,A.java,78");
        m.add("dafe,A.java,88;TIC-124;bcef,A.java,84");
        m.add("eafe,A.java,99;TIC-125;ccef,A.java,98");
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleInactive(cpid("TIC-123", "A.java", "acef"));
        c.handleActive(cpid("TIC-124", "A.java", "bcef"));
        c.handleInactive(cpid("TIC-125", "A.java", "ccef"));
        assertEquals(Arrays.asList("TIC-123", "TIC-125"), c.getTicketsWithMisses());
    }

    @Test
    public void testGetTrimmedMeansForTicket() {
        final RemarkTriggerMap m = new RemarkTriggerMap();
        m.add("cafe,A.java,77;TIC-123;acef,A.java,78");
        m.add("dafe,A.java,88;TIC-124;bcef,A.java,84");
        m.finishCreation();
        final MissedTriggerCounter c = new MissedTriggerCounter(m);
        c.handleInactive(cpid("TIC-123", "A.java", "acef"));
        c.handleActive(cpid("TIC-124", "A.java", "bcef"));
        c.handleInactive(cpid("TIC-125", "A.java", "ccef"));
        assertEquals(Math.log(2.0), c.getRemarkWithoutTriggerLog(), EPSILON);
        assertEquals(-2.0/3.0, c.getSavedHunkTrimmedMean(), EPSILON);
    }

    @Test
    public void testTrimmedMean() {
    	assertEquals(0.0, MissedTriggerCounter.trimmedMean(Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0, 42, -23)), EPSILON);
    	assertEquals(0.5, MissedTriggerCounter.trimmedMean(Arrays.asList(4, 0, 0, 0, 0, 0, 0, 0, 42, -23)), EPSILON);
    }

}
