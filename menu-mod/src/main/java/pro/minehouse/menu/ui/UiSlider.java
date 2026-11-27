package pro.minehouse.menu.ui;

/**
 * Where one slider of a server window (node "slider") stands: min..max in steps. No Minecraft
 * classes, so it is unit tested.
 */
final class UiSlider {
	/** The value the server sent last: a different one moves the slider there. */
	final double server;
	final double min;
	final double max;
	final double step;
	double value;

	UiSlider(double min, double max, double step, double value) {
		this.min = Math.min(min, max);
		this.max = Math.max(min, max);
		this.step = step > 0 ? step : 0;
		this.server = value;
		this.value = snap(value);
	}

	/** The value nearest to v that is in range and on a step. */
	double snap(double v) {
		if (Double.isNaN(v)) v = min;
		if (step > 0) v = min + Math.round((v - min) / step) * step;
		v = Math.max(min, Math.min(max, v));
		// Steps like 0.1 add up to 0.30000000000000004: keep the step's own decimals.
		if (step > 0) {
			int decimals = decimals(step);
			double k = Math.pow(10, decimals);
			v = Math.round(v * k) / k;
		}
		return v;
	}

	/** Where along the track the value is, 0..1. */
	double fraction() {
		return max > min ? (value - min) / (max - min) : 0;
	}

	/** Sets the value from a place along the track, 0..1; true if it changed. */
	boolean setFraction(double f) {
		double v = snap(min + Math.max(0, Math.min(1, f)) * (max - min));
		if (v == value) return false;
		value = v;
		return true;
	}

	/** One step up or down (arrow keys); a hundredth of the range without a step. */
	boolean nudge(int dir) {
		double v = snap(value + dir * (step > 0 ? step : (max - min) / 100));
		if (v == value) return false;
		value = v;
		return true;
	}

	/** The value as shown and sent: no ".0" on whole numbers. */
	String text() {
		return format(value, step);
	}

	static String format(double v, double step) {
		if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
		int d = step > 0 ? Math.max(1, decimals(step)) : 2;
		String s = String.format(java.util.Locale.ROOT, "%." + d + "f", v);
		if (step <= 0) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		return s;
	}

	private static int decimals(double step) {
		String s = java.math.BigDecimal.valueOf(step).stripTrailingZeros().toPlainString();
		int dot = s.indexOf('.');
		return dot < 0 ? 0 : Math.min(6, s.length() - dot - 1);
	}
}
