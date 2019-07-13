package de.unihannover.reviews.mining.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.unihannover.reviews.miningInputCreation.OffsetBitset;
import de.unihannover.reviews.miningInputCreation.RemarkTriggerMap;

public class MissedTriggerCounter {

    private static final class StateForTicket {
        private final String ticketId;
        private final OffsetBitset uncoveredRemarks;

        public StateForTicket(String ticket, RemarkTriggerMap triggerMap) {
            this.ticketId = ticket;
            this.uncoveredRemarks = triggerMap.getAllRemarksFor(this.ticketId);
        }

        public void markRemarksAsCovered(ChangePartId id, RemarkTriggerMap triggerMap) {
            if (this.uncoveredRemarks.isEmpty()) {
                return;
            }
            if (id.isLineGranularity()) {
                this.uncoveredRemarks.removeAll(triggerMap.getCoveredRemarks(id.getTicket(), id.getCommit(), id.getFile(), id.getLineFrom(), id.getLineTo()));
            } else {
                this.uncoveredRemarks.removeAll(triggerMap.getCoveredRemarks(id.getTicket(), id.getCommit(), id.getFile()));
            }
        }

        public int countRemarksWithoutTriggers() {
            return this.uncoveredRemarks.size();
        }

        public Set<Integer> getRemarkIdsWithoutTriggers() {
            return this.uncoveredRemarks.toSet();
        }
    }

    private final RemarkTriggerMap triggerMap;
    private final Set<String> finishedTickets = new HashSet<>();
    private final List<String> ticketsWithMissedRemarks = new ArrayList<>();
    private final List<Integer> savedHunksPerTicket = new ArrayList<>();
    private StateForTicket currentTicket;
    private int countSoFar;
	private int savedHunkCountSoFar;
	private int savedHunkCountCurTicket;
	private double missedRemarkLogSum;

    public MissedTriggerCounter(RemarkTriggerMap m) {
        this.triggerMap = m;
    }

    /**
     * Registers the change part with the given ID as inactive (= will not be reviewed).
     */
    public void handleInactive(ChangePartId id) {
        this.checkForTicketChange(id);
        this.savedHunkCountCurTicket++;
    }

    /**
     * Registers the change part with the given ID as active (= will be reviewed).
     */
    public void handleActive(ChangePartId id) {
        this.checkForTicketChange(id);
        this.currentTicket.markRemarksAsCovered(id, this.triggerMap);
    }

    private void checkForTicketChange(ChangePartId id) {
        if (this.currentTicket == null) {
            this.currentTicket = new StateForTicket(id.getTicket(), this.triggerMap);
        } else if (!this.currentTicket.ticketId.equals(id.getTicket())) {
            if (this.finishedTickets.contains(id.getTicket())) {
                throw new AssertionError("Duplicate ticket ID " + id.getTicket() + ". Records not sorted by ticket?");
            }
            this.finishCurrentTicket();
            this.currentTicket = new StateForTicket(id.getTicket(), this.triggerMap);
        }
    }

	private void finishCurrentTicket() {
		final int remarksWithoutTriggers = this.currentTicket.countRemarksWithoutTriggers();
		if (remarksWithoutTriggers > 0) {
		    this.ticketsWithMissedRemarks.add(this.currentTicket.ticketId);
		}
		this.countSoFar += remarksWithoutTriggers;
		if (remarksWithoutTriggers > 0) {
			this.missedRemarkLogSum += remarksWithoutTriggers * this.logFactorForCurrentTicket();
		}

        this.savedHunksPerTicket.add(this.savedHunkCountCurTicket);
		this.finishedTickets.add(this.currentTicket.ticketId);
		this.savedHunkCountSoFar += this.savedHunkCountCurTicket;
		this.savedHunkCountCurTicket = 0;
	}

    private double logFactorForCurrentTicket() {
    	final int totalRemarkCountForTicket = this.triggerMap.getAllRemarksFor(this.currentTicket.ticketId).size();
    	return Math.log(totalRemarkCountForTicket + 1) / totalRemarkCountForTicket;
	}

	private void finishLastTicket() {
    	if (this.currentTicket != null) {
    		this.finishCurrentTicket();
    		this.currentTicket = null;
    	}
    }

    public int countRemarksWithoutTriggers() {
        return this.countSoFar
                + (this.currentTicket == null ? 0 : this.currentTicket.countRemarksWithoutTriggers());
    }

    public List<String> getTicketsWithMisses() {
		this.finishLastTicket();
        return this.ticketsWithMissedRemarks;
    }

    public Set<Integer> getMissedRemarkIdsForCurrentTicket() {
        return this.currentTicket.getRemarkIdsWithoutTriggers();
    }

	public int getSavedHunkCount() {
		return this.savedHunkCountSoFar
				+ (this.currentTicket == null ? 0 : this.savedHunkCountCurTicket);
	}

	public double getRemarkWithoutTriggerLog() {
		this.finishLastTicket();
		return this.missedRemarkLogSum;
	}

	public double getSavedHunkTrimmedMean() {
		this.finishLastTicket();
		return -trimmedMean(this.savedHunksPerTicket);
	}

	static double trimmedMean(List<Integer> list) {
		Collections.sort(list);
		final int trim = list.size() / 10;
		int sum = 0;
		for (int i = trim; i < list.size() - trim; i++) {
			sum += list.get(i);
		}
		return ((double) sum) / (list.size() - 2 * trim);
	}

}
