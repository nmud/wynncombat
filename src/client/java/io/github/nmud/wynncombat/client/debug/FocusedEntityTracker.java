package io.github.nmud.wynncombat.client.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Tracks which entity the local player is currently looking at and emits a
 * {@code FOCUS} log line whenever that target changes. Wynncraft requires
 * the player to face an enemy in order to spell-cast or auto-attack it,
 * which means {@link Minecraft#hitResult} is the strongest <em>local</em>
 * (no packet round-trip) signal of "what the user is about to hit".
 *
 * <p>Combined with the {@code OUT} attack/swing packets and the
 * {@code SPELL_CD} cooldown signal, this gives us a fully client-side
 * answer to "which mob did the player just hit?" without needing the
 * server to tell us.
 *
 * <p>Polled from the client tick (called by {@link
 * io.github.nmud.wynncombat.client.WynnCombatClient}). We only log on
 * change to keep the noise floor low; the focus can update many times a
 * second as the camera sweeps across mobs.
 */
public final class FocusedEntityTracker {
	private static int lastEntityId = Integer.MIN_VALUE;

	private FocusedEntityTracker() {}

	public static void tick(Minecraft mc) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;
		if (mc == null || mc.player == null || mc.level == null) return;

		HitResult hit = mc.hitResult;
		Entity focused = null;
		if (hit instanceof EntityHitResult ehr) {
			focused = ehr.getEntity();
		}

		int currentId = focused == null ? -1 : focused.getId();
		if (currentId == lastEntityId) return;
		lastEntityId = currentId;

		if (focused == null) {
			debug.log("FOCUS", "id=none");
			return;
		}

		Player self = mc.player;
		double dist = self.position().distanceTo(focused.position());
		Vec3 fp = focused.position();

		debug.log(
			"FOCUS",
			"id=" + focused.getId()
				+ " uuid=" + focused.getUUID()
				+ " type=" + entityTypeId(focused.getType())
				+ " name=\"" + DebugLogger.escape(safeName(focused)) + "\""
				+ " dist=" + String.format("%.2f", dist)
				+ " pos=" + fmt(fp.x) + "," + fmt(fp.y) + "," + fmt(fp.z)
		);
	}

	/**
	 * Reset internal state so the next tick re-emits the current focus.
	 * Called when debug logging toggles on, so the first session line
	 * shows what the player happened to be looking at.
	 */
	public static void reset() {
		lastEntityId = Integer.MIN_VALUE;
	}

	private static String safeName(Entity e) {
		try {
			return e.getName().getString();
		} catch (Throwable ignored) {
			return "<name failed>";
		}
	}

	private static String entityTypeId(EntityType<?> type) {
		if (type == null) return "null";
		try {
			Object key = EntityType.getKey(type);
			return key == null ? type.toString() : key.toString();
		} catch (Throwable ignored) {
			return type.toString();
		}
	}

	private static String fmt(double d) {
		return String.format("%.1f", d);
	}
}
