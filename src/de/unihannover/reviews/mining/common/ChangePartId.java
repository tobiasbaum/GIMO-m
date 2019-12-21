package de.unihannover.reviews.mining.common;

public class ChangePartId {

    private final int id;

    public ChangePartId(int id) {
        this.id = id;
    }

    public String getTicket() {
        return Integer.toString(this.id);
    }

    public String getFile() {
        return "file";
    }

    public String getCommit() {
        return "commit";
    }

    public boolean isLineGranularity() {
        return false;
    }

    public int getLineFrom() {
        return -2;
    }

    public int getLineTo() {
        return -2;
    }

    @Override
	public int hashCode() {
    	return this.id;
    }

    @Override
	public boolean equals(Object o) {
    	if (!(o instanceof ChangePartId)) {
    		return false;
    	}
    	final ChangePartId c = (ChangePartId) o;
    	return this.id == c.id;
	}

    @Override
	public String toString() {
    	return Integer.toString(this.id);
    }

    public int getId() {
        return this.id;
    }

}
