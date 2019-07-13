package de.unihannover.reviews.mining.common;

import java.util.function.ToDoubleFunction;

public class TargetFunction implements ToDoubleFunction<ValuedResult<?>> {

    private final String id;
    private final ToDoubleFunction<ValuedResult<?>> function;
    private final String tooltip;

    public TargetFunction(String id, ToDoubleFunction<ValuedResult<?>> function, String tooltip) {
        this.id = id;
        this.function = function;
        this.tooltip = tooltip;
    }

    @Override
	public double applyAsDouble(ValuedResult<?> c2) {
        return this.function.applyAsDouble(c2);
    }

    public String getId() {
        return this.id;
    }

	public String getTooltip() {
		return this.tooltip;
	}
}
