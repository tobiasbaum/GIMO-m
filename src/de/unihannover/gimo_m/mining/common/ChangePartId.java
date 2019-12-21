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
package de.unihannover.gimo_m.mining.common;

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
