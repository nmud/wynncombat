package io.github.nmud.wynncombat.client.mixin;

import io.github.nmud.wynncombat.client.debug.DebugLogger;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures explosion packets. Wynncraft uses real client-side explosions for
 * a handful of high-impact spells (Mage Meteor impact, Warrior Bash AOE,
 * Necromancer death bursts) so the player feels the screen-shake and hears
 * the boom. Each explosion carries:
 *
 * <ul>
 *   <li>Center position — match this to the impact damage label entity</li>
 *   <li>Player knockback vector (if the player is in radius)</li>
 *   <li>Explosion sound holder — distinguishes "small spell pop" from
 *       "boss-level boom"</li>
 * </ul>
 */
@Mixin(ClientPacketListener.class)
public class ExplodePacketMixin {
	@Inject(method = "handleExplosion", at = @At("HEAD"))
	private void wynncombat$logExplode(ClientboundExplodePacket packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;

		Vec3 center = packet.center();
		StringBuilder sb = new StringBuilder();
		if (center != null) {
			sb.append("pos=").append(fmt(center.x)).append(",").append(fmt(center.y)).append(",").append(fmt(center.z));
		} else {
			sb.append("pos=null");
		}

		packet.playerKnockback().ifPresent(obj -> {
			if (obj instanceof Vec3 v) {
				sb.append(" knockback=").append(fmt(v.x)).append(",").append(fmt(v.y)).append(",").append(fmt(v.z));
			}
		});

		sb.append(" sound=").append(soundId(packet.explosionSound()));

		debug.log("EXPLODE", sb.toString());
	}

	private static String soundId(Holder<SoundEvent> holder) {
		if (holder == null) return "null";
		SoundEvent event = holder.value();
		if (event == null) return "null";
		Object loc = event.location();
		return loc == null ? "null" : loc.toString();
	}

	private static String fmt(double d) {
		return String.format("%.1f", d);
	}
}
