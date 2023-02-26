package de.unihannover.gimo_m.objectives;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.unihannover.gimo_m.mining.common.ObjectiveCalculator;
import de.unihannover.gimo_m.mining.common.ObjectiveStrategy;
import de.unihannover.gimo_m.mining.common.TargetFunction;
import de.unihannover.gimo_m.mining.common.ValuedResult;

/**
 * An objective strategy that creates an objective vector with an entry for the number
 * of misclassifications for each class and some addition target functions.
 */
public class StandardObjectiveStrategy implements ObjectiveStrategy {

	private static final String FEATURE_COUNT = "featureCount";
	private static final String COMPLEXITY = "complexity";
	private static final String WRONG = "wrong_";

	private final List<String> classes;
	private final Map<String, Integer> classIndices;
	private final Map<String, Integer> countsPerClass;

	public StandardObjectiveStrategy(final Map<String, Integer> knownClasses) {
		this.countsPerClass = knownClasses;
		this.classes = new ArrayList<String>(knownClasses.keySet());
		this.classIndices = new HashMap<>();
		for (int i = 0; i < this.classes.size(); i++) {
			this.classIndices.put(this.classes.get(i), i);
		}
	}

	@Override
	public List<String> getObjectiveNames() {
		final List<String> ret = new ArrayList<>();
		for (final String c : this.classes) {
			ret.add(WRONG + c);
		}
		ret.add(COMPLEXITY);
		ret.add(FEATURE_COUNT);
		return ret;
	}

	@Override
	public List<TargetFunction> getTargetFunctions() {
        final List<TargetFunction> ret = new ArrayList<>();
        ret.add(new TargetFunction("avgRelWrong",
                (final ValuedResult<?> r) -> {
                    double sum = 0.0;
                    for (int i = 0; i < this.classes.size(); i++) {
                        sum += this.relWrong(i, r);
                    }
                    return sum / this.classes.size();
                },
                "average relative share of misclassifications"));
        ret.add(new TargetFunction("totalWrong",
                (final ValuedResult<?> r) -> {
                    double sum = 0.0;
                    for (int i = 0; i < this.classes.size(); i++) {
                        sum += r.getValue(i);
                    }
                    return sum;
                },
                "total number of wrong classifications"));
        for (int i = 0; i < this.classes.size(); i++) {
        	final String c = this.classes.get(i);
        	final int idx = i;
            ret.add(new TargetFunction(WRONG + c, (final ValuedResult<?> r) -> r.getValue(idx), "number of misclassifications that should have been " + c));
        }
        for (int i = 0; i < this.classes.size(); i++) {
        	final String c = this.classes.get(i);
        	final int idx = i;
            ret.add(new TargetFunction("relWrong_" + c,
            		(final ValuedResult<?> r) -> this.relWrong(idx, r),
            		"relative share of misclassifications that should have been " + c));
        }
        ret.add(new TargetFunction(COMPLEXITY, (final ValuedResult<?> r) -> r.getValue(this.classes.size()), "complexity of the rule set"));
        ret.add(new TargetFunction(FEATURE_COUNT, (final ValuedResult<?> r) -> r.getValue(this.classes.size() + 1), "number of used features in the rule set"));
		return ret;
	}

	private double relWrong(final int idx, final ValuedResult<?> r) {
		return r.getValue(idx) / this.countsPerClass.get(this.classes.get(idx));
	}

	@Override
	public ObjectiveCalculator createCalculator() {
		return new StandardCalculator(this.classIndices);
	}

	private static final class StandardCalculator implements ObjectiveCalculator {

		private final Map<String, Integer> classIndices;
		private final double[] vector;

		private StandardCalculator(final Map<String, Integer> classIndices) {
			this.classIndices = classIndices;
			this.vector = new double[classIndices.size() + 2];
		}

		@Override
		public void handleInstance(final String correctClass, final String predictedClass) {
			if (!correctClass.equals(predictedClass)) {
				this.vector[this.classIndices.get(correctClass)]++;
			}
		}

		@Override
		public double[] getResult(final double rulesetComplexity, final double rulesetFeatureCount) {
			this.vector[this.classIndices.size()] = rulesetComplexity;
			this.vector[this.classIndices.size() + 1] = rulesetFeatureCount;
			return this.vector;
		}

	}

}
