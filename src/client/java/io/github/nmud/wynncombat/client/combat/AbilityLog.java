package io.github.nmud.wynncombat.client.combat;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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
 * contains a cast banner and the previous one didn't. The same spell cast back
 * to back without the banner ever clearing therefore only counts once, which
 * matches Wynntils' behaviour and is acceptable for v1.
 *
 * <p>The log is intentionally process-local: the listener is registered once
 * during client init and runs whenever Minecraft is connected to a server.
 * Filtering to "actually on Wynncraft" is a future concern; today the regex
 * is specific enough to never match vanilla / non-Wynncraft action bars.
 */
public final class AbilityLog {
	private static final AbilityLog INSTANCE = new AbilityLog();
	private static final int MAX_ENTRIES = 32;

	private final Object lock = new Object();
	private final Deque<AbilityLogEntry> entries = new ArrayDeque<>(MAX_ENTRIES);
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
		AbilityLogEntry entry = new AbilityLogEntry(
			info.spellName(),
			info.manaCost(),
			info.healthCost(),
			System.currentTimeMillis()
		);
		synchronized (lock) {
			entries.addLast(entry);
			while (entries.size() > MAX_ENTRIES) {
				entries.removeFirst();
			}
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
