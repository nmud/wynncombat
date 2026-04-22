package io.github.nmud.wynncombat.client.damage;

import io.github.nmud.wynncombat.client.debug.DebugLogger;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side DPS tracker. Modelled on Wynntils' {@code CombatModel}
 * (<a href="https://github.com/Wynntils/Wynntils/blob/main/common/src/main/java/com/wynntils/models/combat/CombatModel.java">source</a>):
 *
 * <ul>
 *   <li>Wynncraft renders damage numbers as floating
 *       {@code Display.TextDisplay} entities above mobs. The text is a
 *       run-on color-coded string like {@code §c1.2k§2500} -- each color
 *       denotes a damage type -- possibly separated by a private-use code
 *       point ({@code U+DB00 U+DC0A}).</li>
 *   <li>That label is the <em>cumulative</em> damage the mob has taken, not
 *       a single hit. So we keep per-entity running totals and record only
 *       the delta when the text changes.</li>
 *   <li>Every delta goes into a rolling time-log; {@link #damageInLast(long)}
 *       sums the bucket for any window the caller asks for.</li>
 * </ul>
 *
 * The mixins feed this via {@link #onEntityText(int, Component)} and
 * {@link #onEntityRemoved(int)}.
 */
public final class DamageTracker {
	private static final DamageTracker INSTANCE = new DamageTracker();

	/** Hard cap on the rolling log so a very long session can't grow unbounded. */
	private static final int MAX_ENTRIES = 4096;
	/** Entries older than this are pruned lazily. 10 minutes covers the longest window a user might configure. */
	private static final long MAX_AGE_MS = 10L * 60L * 1000L;

	private final Object lock = new Object();
	private final Map<Integer, Long> perEntityCumulative = new HashMap<>();
	/** Each entry is a 2-long packed as {timestampMs, amount}. Ordered oldest-first. */
	private final Deque<long[]> log = new ArrayDeque<>();
	private volatile long lastDamageMs = 0L;

	private DamageTracker() {}

	public static DamageTracker get() {
		return INSTANCE;
	}

	/**
	 * Feed a piece of entity text captured by the entity-data packet. Called
	 * for <em>every</em> text-valued field change, so it first must test
	 * whether the text is actually a damage label (non-labels are cheap to
	 * reject via {@link DamageLabelParser#parseTotal(String)} returning
	 * {@code -1}).
	 */
	public void onEntityText(int entityId, Component text) {
		if (text == null) return;
		String legacy = DebugLogger.styled(text);
		long total = DamageLabelParser.parseTotal(legacy);
		if (total < 0) return;

		long now = System.currentTimeMillis();
		long delta;
		synchronized (lock) {
			Long prev = perEntityCumulative.put(entityId, total);
			if (prev == null) {
				delta = total;
			} else if (total >= prev) {
				delta = total - prev;
			} else {
				delta = total;
			}
			if (delta <= 0) return;
			pushLogEntry(now, delta);
		}
		lastDamageMs = now;
	}

	/** Forget per-entity state when the mob / damage label despawns. */
	public void onEntityRemoved(int entityId) {
		synchronized (lock) {
			perEntityCumulative.remove(entityId);
		}
	}

	/** Wipe all tracked state (on world change, disconnect, etc.). */
	public void reset() {
		synchronized (lock) {
			perEntityCumulative.clear();
			log.clear();
		}
		lastDamageMs = 0L;
	}

	/** Total damage dealt in the last {@code windowMs} milliseconds. */
	public long damageInLast(long windowMs) {
		if (windowMs <= 0) return 0L;
		long now = System.currentTimeMillis();
		long cutoff = now - windowMs;
		long sum = 0L;
		synchronized (lock) {
			pruneOlderThan(now - MAX_AGE_MS);
			for (long[] e : log) {
				if (e[0] >= cutoff) sum += e[1];
			}
		}
		return sum;
	}

	/**
	 * Average damage-per-second over the given window. Uses the requested
	 * window length as the denominator regardless of whether we've actually
	 * been collecting that long, matching Wynntils' behaviour.
	 */
	public long dpsOver(long windowMs) {
		if (windowMs <= 0) return 0L;
		long total = damageInLast(windowMs);
		double seconds = windowMs / 1000.0;
		if (seconds <= 0) return total;
		return Math.round(total / seconds);
	}

	public long lastDamageTimestamp() {
		return lastDamageMs;
	}

	private void pushLogEntry(long now, long delta) {
		log.addLast(new long[]{now, delta});
		pruneOlderThan(now - MAX_AGE_MS);
		while (log.size() > MAX_ENTRIES) {
			log.pollFirst();
		}
	}

	private void pruneOlderThan(long cutoffMs) {
		while (!log.isEmpty() && log.peekFirst()[0] < cutoffMs) {
			log.pollFirst();
		}
	}
}
