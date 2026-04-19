package io.github.nmud.wynncombat.client.debug;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

/**
 * Wires up Fabric event listeners that feed the {@link DebugLogger}. Call
 * {@link #register()} once during client init. Listeners are always installed
 * but no-op when logging is disabled, so the runtime cost when off is a single
 * volatile read per event.
 */
public final class CombatDebug {
	private CombatDebug() {}

	/**
	 * Last action-bar payload we logged, used to suppress consecutive duplicates.
	 * The Wynncraft HUD is pushed to the action bar ~20 times per second; most
	 * of those messages carry no new information. By logging only when the
	 * payload differs from the previous one we still surface the signals we
	 * actually want -- spell input glyph changes (R / L / R-L sequences),
	 * mana / HP bar fill changes, the "spell locked" indicator -- without
	 * burying them under thousands of duplicate lines.
	 */
	private static volatile String lastActionBar = null;

	public static void register() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			DebugLogger debug = DebugLogger.get();
			if (!debug.isEnabled()) return;

			String styled = DebugLogger.styled(message);

			if (overlay) {
				if (styled.equals(lastActionBar)) return;
				lastActionBar = styled;
			}

			String category = overlay ? "ACTIONBAR" : "CHAT";
			debug.log(category, "styled=\"" + DebugLogger.escape(styled) + "\"");
		});
	}

	/**
	 * Toggle logging. State change is surfaced to the Minecraft console via the
	 * mod logger (see {@link DebugLogger#toggle()}) and via the start/end
	 * markers written to {@code logs/wynncombat-debug.log}.
	 */
	public static void toggle() {
		DebugLogger.get().toggle();
		lastActionBar = null;
		FocusedEntityTracker.reset();
	}
}
