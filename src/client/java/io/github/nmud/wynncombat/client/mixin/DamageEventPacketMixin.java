package io.github.nmud.wynncombat.client.mixin;

import io.github.nmud.wynncombat.client.debug.DebugLogger;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures vanilla damage events. Whenever any entity takes damage from a
 * registered Minecraft damage source, the server fires this packet with the
 * target entity, the damage type holder, the cause/direct source entity ids,
 * and an optional impact position.
 *
 * <p>This is the most authoritative "X hit Y with damage type Z" signal in
 * the protocol. Wynncraft renders most spell damage as floating text labels
 * (which bypass this packet), but this still fires for environmental damage,
 * mob-on-mob hits, and player-taken damage. If Wynncraft also routes any
 * spell damage through vanilla {@code DamageSource} we get free attribution.
 */
@Mixin(ClientPacketListener.class)
public class DamageEventPacketMixin {
	@Inject(method = "handleDamageEvent", at = @At("HEAD"))
	private void wynncombat$logDamageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;

		StringBuilder sb = new StringBuilder();
		sb.append("target=").append(packet.entityId());
		sb.append(" type=").append(damageTypeId(packet.sourceType()));
		sb.append(" cause=").append(packet.sourceCauseId());
		sb.append(" direct=").append(packet.sourceDirectId());

		packet.sourcePosition().ifPresent(obj -> {
			if (obj instanceof Vec3 v) {
				sb.append(" pos=").append(fmt(v.x)).append(",").append(fmt(v.y)).append(",").append(fmt(v.z));
			}
		});

		debug.log("DAMAGE_EVT", sb.toString());
	}

	private static String damageTypeId(Holder<DamageType> holder) {
		if (holder == null) return "null";
		return holder.unwrapKey().map(Object::toString).orElse("inline");
	}

	private static String fmt(double d) {
		return String.format("%.1f", d);
	}
}
