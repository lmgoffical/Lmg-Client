package dev.lmg.legacy.ui;

/** One line on a settings page: a switch, a < choice >, a slider or a button. */
public final class Row {
	public interface Bool { boolean get(); }
	public interface Num { double get(); }
	public interface NumSet { void set(double v); }
	public interface Text { String get(); }
	public interface Fmt { String of(double v); }

	public enum Kind { TOGGLE, CHOICE, SLIDER, BUTTON, NOTE }

	public final Kind kind;
	public final String label;
	public String hint = "";
	Bool bool;
	Runnable flip, next, prev, action;
	Text value;
	Num num;
	NumSet set;
	double min, max, step;
	Fmt fmt;

	private Row(Kind kind, String label) {
		this.kind = kind;
		this.label = label;
	}

	public static Row toggle(String label, Bool get, Runnable flip) {
		Row r = new Row(Kind.TOGGLE, label);
		r.bool = get;
		r.flip = flip;
		return r;
	}

	public static Row choice(String label, Text value, Runnable next, Runnable prev) {
		Row r = new Row(Kind.CHOICE, label);
		r.value = value;
		r.next = next;
		r.prev = prev;
		return r;
	}

	public static Row slider(String label, Num get, NumSet set, double min, double max, double step, Fmt fmt) {
		Row r = new Row(Kind.SLIDER, label);
		r.num = get;
		r.set = set;
		r.min = min;
		r.max = max;
		r.step = step;
		r.fmt = fmt;
		return r;
	}

	public static Row button(String label, String buttonText, Runnable action) {
		Row r = new Row(Kind.BUTTON, label);
		r.value = () -> buttonText;
		r.action = action;
		return r;
	}

	public static Row note(String text) {
		return new Row(Kind.NOTE, text);
	}

	public Row hint(String h) {
		this.hint = h;
		return this;
	}

	void slideTo(double frac) {
		double v = min + Math.max(0, Math.min(1, frac)) * (max - min);
		v = Math.round(v / step) * step;
		set.set(Math.max(min, Math.min(max, v)));
	}

	void nudge(int dir) {
		set.set(Math.max(min, Math.min(max, num.get() + dir * step)));
	}
}
