package de.unihannover.reviews.mining.common;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import de.unihannover.reviews.miningInputCreation.TriggerClassification;

public final class Record {
    private final ChangePartId id;
    private final double[] numericValues;
    private final String[] stringValues;
    private final TriggerClassification classification;

    public Record(ChangePartId id, List<Double> numericValues,
                    List<String> stringValues, TriggerClassification classification) {
        this.id = id;
        this.numericValues = toArray(numericValues);
        this.stringValues = stringValues.toArray(new String[stringValues.size()]);
        this.classification = classification;
    }

    private Record(ChangePartId id, double[] numericValues,
    		String[] stringValues, TriggerClassification classification) {
    	this.id = id;
    	this.numericValues = numericValues;
    	this.stringValues = stringValues;
    	this.classification = classification;
    }

    static double[] toArray(Collection<Double> compl) {
        final double[] ret = new double[compl.size()];
        final Iterator<Double> iter = compl.iterator();
        for (int i = 0; i < compl.size(); i++) {
            ret[i] = iter.next();
        }
        return ret;
    }

    public String getValueStr(int stringColumnIndex) {
        return this.stringValues[stringColumnIndex];
    }

    public double getValueDbl(int numericColumnIndex) {
        return this.numericValues[numericColumnIndex];
    }

    public ChangePartId getId() {
        return this.id;
    }

    public TriggerClassification getClassification() {
        return this.classification;
    }

    public boolean containsValueForColumn(RecordScheme scheme, int absoluteColumnIndex) {
        if (scheme.isNumeric(absoluteColumnIndex)) {
            return !Double.isNaN(this.getValueDbl(scheme.toNumericIndex(absoluteColumnIndex)));
        } else {
            return this.getValueStr(scheme.toStringIndex(absoluteColumnIndex)) != null;
        }
    }

    /**
     * Returns a record with the data of the current one and the given classification.
     * Creates a copy unless the classification does not change.
     */
	public Record withClassification(TriggerClassification newClassification) {
		if (this.classification == newClassification) {
			return this;
		}
		return new Record(this.id, this.numericValues, this.stringValues, newClassification);
	}

}
