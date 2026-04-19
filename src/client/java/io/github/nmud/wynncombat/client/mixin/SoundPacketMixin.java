package io.github.nmud.wynncombat.client.mixin;

import io.github.nmud.wynncombat.client.debug.DebugLogger;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures every sound packet the server sends. Wynncraft fires distinctive
 * sounds for nearly every spell cast, ability proc, mana absorb, etc., usually
 * piggy-backing on vanilla sounds like {@code entity.experience_orb.pickup} or
 * {@code entity.blaze.shoot}. Each sound carries its {@link SoundSource}
 * channel (PLAYER, HOSTILE, AMBIENT, ...) and a position, so we can correlate
 * "spell cast at player position" with the damage labels that follow.
 *
 * <p>This is a parallel signal to the action-bar spell-input glyphs and is
 * usually easier to parse: the sound id is a stable string the server commits
 * to, while the action-bar HUD layout can drift.
 */
@Mixin(ClientPacketListener.class)
public class SoundPacketMixin {
	@Inject(method = "handleSoundEvent", at = @At("HEAD"))
	private void wynncombat$logSound(ClientboundSoundPacket packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;

		debug.log(
			"SOUND",
			"id=" + soundId(packet.getSound())
				+ " src=" + sourceName(packet.getSource())
				+ " pos=" + fmt(packet.getX()) + "," + fmt(packet.getY()) + "," + fmt(packet.getZ())
				+ " vol=" + packet.getVolume()
				+ " pitch=" + packet.getPitch()
		);
	}

	@Inject(method = "handleSoundEntityEvent", at = @At("HEAD"))
	private void wynncombat$logSoundEntity(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;

		debug.log(
			"SOUND_ENT",
			"id=" + soundId(packet.getSound())
				+ " src=" + sourceName(packet.getSource())
				+ " entity=" + packet.getId()
				+ " vol=" + packet.getVolume()
				+ " pitch=" + packet.getPitch()
		);
	}

	private static String soundId(Holder<SoundEvent> holder) {
		if (holder == null) return "null";
		SoundEvent event = holder.value();
		if (event == null) return "null";
		Object loc = event.location();
		return loc == null ? "null" : loc.toString();
	}

	private static String sourceName(SoundSource src) {
		return src == null ? "null" : src.getName();
	}

	private static String fmt(double d) {
		return String.format("%.1f", d);
	}
}
