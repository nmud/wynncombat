package io.github.nmud.wynncombat.client.mixin;

import io.github.nmud.wynncombat.client.debug.DebugLogger;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures entity spawns and despawns. Wynncraft uses entity lifecycle for
 * three combat-relevant signals:
 *
 * <ul>
 *   <li><b>Projectile spawn</b> — Most thrown spells (Mage Meteor, Archer
 *       arrows, Smoke Bomb canisters) are backed by an invisible
 *       armor-stand, snowball, egg, or item-display entity. Knowing when
 *       and where it spawned lets us mark it as "Smoke Bomb projectile from
 *       cast at t=N" and attribute every nearby damage label to it for the
 *       projectile's lifetime.</li>
 *   <li><b>Mob death</b> — Removal of a hostile entity within seconds of a
 *       damage label means kill credit, XP gain, and lootrun progress.</li>
 *   <li><b>Floating-label spawn/despawn</b> — Wynncraft's damage labels
 *       are themselves entities (TextDisplay), so we can correlate label
 *       lifetime with the damage packets we already capture.</li>
 * </ul>
 */
@Mixin(ClientPacketListener.class)
public class EntityLifecyclePacketMixin {
	@Inject(method = "handleAddEntity", at = @At("HEAD"))
	private void wynncombat$logAddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;

		debug.log(
			"ENT_ADD",
			"id=" + packet.getId()
				+ " uuid=" + packet.getUUID()
				+ " type=" + entityTypeId(packet.getType())
				+ " pos=" + fmt(packet.getX()) + "," + fmt(packet.getY()) + "," + fmt(packet.getZ())
				+ " yaw=" + String.format("%.0f", packet.getYRot())
				+ " pitch=" + String.format("%.0f", packet.getXRot())
				+ " data=" + packet.getData()
		);
	}

	@Inject(method = "handleRemoveEntities", at = @At("HEAD"))
	private void wynncombat$logRemoveEntities(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;

		IntList ids = packet.getEntityIds();
		if (ids == null || ids.isEmpty()) return;

		debug.log("ENT_REMOVE", "ids=" + ids);
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
