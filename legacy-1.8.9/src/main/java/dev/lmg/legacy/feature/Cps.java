package dev.lmg.legacy.feature;

import java.util.ArrayDeque;

/** Clicks per second, from real mouse presses only (counted while playing, not in menus). */
public final class Cps {
	private static final ArrayDeque<Long> LEFT = new ArrayDeque<Long>(), RIGHT = new ArrayDeque<Long>();

	private Cps() {}

	public static void click(int button) {
		long now = System.currentTimeMillis();
		if (button == 0) LEFT.addLast(now);
		else if (button == 1) RIGHT.addLast(now);
	}

	public static int left() {
		return count(LEFT);
	}

	public static int right() {
		return count(RIGHT);
	}

	private static int count(ArrayDeque<Long> q) {
		long cut = System.currentTimeMillis() - 1000;
		while (!q.isEmpty() && q.peekFirst() < cut) q.pollFirst();
		return q.size();
	}
}
