package de.unihannover.reviews.mining.common;

public class True extends ConstantRule {

    @Override
    public boolean test(Record r) {
        return true;
    }

    @Override
    public int hashCode() {
        return 1238076;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof True;
    }

    @Override
    public String toString() {
        return "true";
    }

}