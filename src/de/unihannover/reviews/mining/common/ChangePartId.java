package de.unihannover.reviews.mining.common;

public class ChangePartId {

    private final String ticket;
    private final String file;
    private final String commit;
    private final int lineFrom;
    private final int lineTo;

    public ChangePartId(String ticket, String file, String commit) {
        this(ticket, file, commit, -2, -2);
    }

    public ChangePartId(String ticket, String file, String commit, int lineFrom, int lineTo) {
        this.ticket = ticket;
        this.file = file;
        this.commit = commit;
        this.lineFrom = lineFrom;
        this.lineTo = lineTo;
    }

    public String getTicket() {
        return this.ticket;
    }

    public String getFile() {
        return this.file;
    }

    public String getCommit() {
        return this.commit;
    }

    public boolean isLineGranularity() {
        return this.lineFrom > -2;
    }

    public int getLineFrom() {
        return this.lineFrom;
    }

    public int getLineTo() {
        return this.lineTo;
    }

    @Override
	public int hashCode() {
    	return this.commit.hashCode() + this.file.hashCode() + this.lineFrom;
    }

    @Override
	public boolean equals(Object o) {
    	if (!(o instanceof ChangePartId)) {
    		return false;
    	}
    	final ChangePartId c = (ChangePartId) o;
    	return this.commit.equals(c.commit)
			&& this.file.equals(c.file)
			&& this.ticket.equals(c.ticket)
			&& this.lineFrom == c.lineFrom
			&& this.lineTo == c.lineTo;
	}

    @Override
	public String toString() {
    	return this.ticket + "," + this.commit + "," + this.file + "," + this.lineFrom + "," + this.lineTo;
    }

}
