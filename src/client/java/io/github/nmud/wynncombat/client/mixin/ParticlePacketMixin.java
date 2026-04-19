package io.github.nmud.wynncombat.client.mixin;

import io.github.nmud.wynncombat.client.debug.DebugLogger;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures particle spawn packets. Wynncraft renders most spell visuals on
 * the client by spawning vanilla particles at scripted positions:
 *
 * <ul>
 *   <li><i>explosion_emitter</i> + <i>flame</i> at impact → Mage Meteor</li>
 *   <li><i>smoke</i> cloud over time → Smoke Bomb DoT zone</li>
 *   <li><i>sweep_attack</i> at target → wand auto-attack hit</li>
 *   <li><i>crit</i> / <i>enchanted_hit</i> → vanilla crit indicator</li>
 *   <li><i>sonic_boom</i> / <i>flash</i> → various ultimates</li>
 * </ul>
 *
 * <p>Particle position + type fingerprints individual spell impacts and is
 * usually fired a few ticks before the corresponding floating damage label
 * appears, giving us a leading signal for attribution.
 */
@Mixin(ClientPacketListener.class)
public class ParticlePacketMixin {
	@Inject(method = "handleParticleEvent", at = @At("HEAD"))
	private void wynncombat$logParticles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;

		debug.log(
			"PARTICLE",
			"id=" + particleId(packet.getParticle())
				+ " pos=" + fmt(packet.getX()) + "," + fmt(packet.getY()) + "," + fmt(packet.getZ())
				+ " spread=" + fmt(packet.getXDist()) + "," + fmt(packet.getYDist()) + "," + fmt(packet.getZDist())
				+ " speed=" + packet.getMaxSpeed()
				+ " count=" + packet.getCount()
				+ " force=" + packet.isOverrideLimiter()
		);
	}

	private static String particleId(ParticleOptions options) {
		if (options == null) return "null";
		try {
			ParticleType<?> type = options.getType();
			Object key = BuiltInRegistries.PARTICLE_TYPE.getKey(type);
			return key == null ? type.toString() : key.toString();
		} catch (Throwable ignored) {
			return options.toString();
		}
	}

	private static String fmt(double d) {
		return String.format("%.1f", d);
	}
}
