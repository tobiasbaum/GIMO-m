package de.unihannover.reviews.mining.common;

public class False extends ConstantRule {

    @Override
    public boolean test(Record r) {
        return false;
    }

    @Override
    public int hashCode() {
        return 78654;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof False;
    }

    @Override
    public String toString() {
        return "false";
    }

}