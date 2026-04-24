package io.github.nmud.wynncombat.client.combat;

import io.github.nmud.wynncombat.client.recorder.DpsRecorder;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded ring buffer of recently observed spell casts, fed by the client's
 * action-bar stream.
 *
 * <p><b>Detection:</b> we listen on
 * {@link ClientReceiveMessageEvents#GAME} and run {@link SpellCastDetector} on
 * every overlay (action-bar) message.
 *
 * <p><b>Dedup:</b> Wynncraft re-broadcasts the action-bar text every game tick
 * for the duration the "Cast!" banner is shown (~3 seconds). We adopt
 * Wynntils' approach (see
 * <a href="https://github.com/Wynntils/Wynntils/blob/main/common/src/main/java/com/wynntils/models/spells/SpellModel.java">SpellModel#handleSpellCast</a>):
 * fire a log entry only on the rising edge - i.e. when the current action bar
 * contains a cast banner and the previous one didn't.
 *
 * <p><b>Stacking:</b> Wynntils additionally tracks
 * {@code repeatedSpellCount} - how many times the same spell was cast in a
 * row. We mirror that: if a new cast has the same spell name as the latest
 * live entry (within {@code entryLifetimeMs}), we merge into that entry
 * instead of appending a new row, summing mana/health costs and bumping
 * {@link AbilityLogEntry#stackCount()}. So spamming Bash three times shows
 * up as a single "Bash x3 -63 mana" row rather than three separate lines.
 *
 * <p><b>Stacking toggle:</b> when
 * {@link OverlayConfig#stackAbilities} is {@code false}, merging is disabled
 * entirely - every cast is its own row. In that mode the overlay displays
 * costs as {@code "base (+extra)"}, where "base" is the minimum cost ever
 * observed for that spell name in this session and "extra" is the delta above
 * that base for this particular cast. We therefore track a per-spell
 * min-cost map here so that value is available to the renderer.
 *
 * <p>The log is intentionally process-local: the listener is registered once
 * during client init and runs whenever Minecraft is connected to a server.
 * Filtering to "actually on Wynncraft" is a future concern; today the regex
 * is specific enough to never match vanilla / non-Wynncraft action bars.
 */
public final class AbilityLog {
	private static final AbilityLog INSTANCE = new AbilityLog();
	private static final int MAX_ENTRIES = 32;

	/**
	 * Max gap between consecutive same-spell casts that still counts as a
	 * "repeat" (and therefore merges into the previous entry). Kept small
	 * enough that two genuinely separate casting bursts don't collapse into
	 * one row, but long enough to cover normal back-to-back combos.
	 */
	private static final long STACK_WINDOW_MS = 5_000L;

	private final Object lock = new Object();
	private final Deque<AbilityLogEntry> entries = new ArrayDeque<>(MAX_ENTRIES);
	private final Map<String, Integer> baseManaByName = new HashMap<>();
	private final Map<String, Integer> baseHealthByName = new HashMap<>();
	private volatile boolean spellTextActive = false;

	private AbilityLog() {}

	public static AbilityLog get() {
		return INSTANCE;
	}

	public static void register() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) return;
			if (message == null) return;
			INSTANCE.onActionBar(message.getString());
		});
	}

	private void onActionBar(String actionBarText) {
		SpellCastInfo info = SpellCastDetector.parse(actionBarText);
		if (info == null) {
			spellTextActive = false;
			return;
		}
		if (spellTextActive) return;
		spellTextActive = true;
		record(info);
	}

	private void record(SpellCastInfo info) {
		// Notify the DPS recorder BEFORE any stacking / dedup logic. Users
		// want every individual cast (with its exact timing) in the recorded
		// timeline, not the merged "Bash x3" rollup the overlay shows.
		DpsRecorder.get().onCast(info);

		long now = System.currentTimeMillis();
		boolean stack = OverlayConfig.get().stackAbilities;
		synchronized (lock) {
			updateBaseCost(baseManaByName, info.spellName(), info.manaCost());
			updateBaseCost(baseHealthByName, info.spellName(), info.healthCost());

			if (stack) {
				AbilityLogEntry last = entries.peekLast();
				if (last != null
					&& last.spellName().equals(info.spellName())
					&& (now - last.timestampMs()) <= STACK_WINDOW_MS) {
					entries.removeLast();
					entries.addLast(new AbilityLogEntry(
						last.spellName(),
						last.manaCost() + info.manaCost(),
						last.healthCost() + info.healthCost(),
						now,
						last.stackCount() + 1
					));
					return;
				}
			}
			entries.addLast(new AbilityLogEntry(
				info.spellName(),
				info.manaCost(),
				info.healthCost(),
				now
			));
			while (entries.size() > MAX_ENTRIES) {
				entries.removeFirst();
			}
		}
	}

	private static void updateBaseCost(Map<String, Integer> map, String name, int cost) {
		if (cost <= 0) return;
		Integer existing = map.get(name);
		if (existing == null || cost < existing) map.put(name, cost);
	}

	/** Minimum mana cost observed for {@code spellName} this session, or 0. */
	public int baseManaCost(String spellName) {
		synchronized (lock) {
			Integer v = baseManaByName.get(spellName);
			return v == null ? 0 : v;
		}
	}

	/** Minimum health cost observed for {@code spellName} this session, or 0. */
	public int baseHealthCost(String spellName) {
		synchronized (lock) {
			Integer v = baseHealthByName.get(spellName);
			return v == null ? 0 : v;
		}
	}

	/**
	 * Snapshot of entries newer than {@code (now - maxAgeMs)}, oldest first.
	 * Safe to call from the render thread.
	 */
	public List<AbilityLogEntry> recent(long maxAgeMs) {
		long cutoff = System.currentTimeMillis() - maxAgeMs;
		List<AbilityLogEntry> out;
		synchronized (lock) {
			out = new ArrayList<>(entries.size());
			for (AbilityLogEntry e : entries) {
				if (e.timestampMs() >= cutoff) out.add(e);
			}
		}
		return out;
	}
}
