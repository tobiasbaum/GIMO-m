package de.unihannover.reviews.mining.common;

public class Util {

	public static double determineSplitPointWithFewDigits(double lower, double upper) {
		final double middle = (lower + upper) / 2.0;
		final double bl = (2.0 * lower + upper) / 3.0;
		final double bu = (lower + 2.0 * upper) / 3.0;

		for (int exp = -10; exp < 10; exp++) {
			final double r = roundToExp(middle, exp);
			if (r > bl && r < bu) {
				return r;
			}
		}
		return middle;
	}

	private static double roundToExp(double middle, int exp) {
		final double factor = Math.pow(10, exp);
		return Math.round(middle * factor) / factor;
	}

}
