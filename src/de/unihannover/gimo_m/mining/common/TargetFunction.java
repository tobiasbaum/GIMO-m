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
