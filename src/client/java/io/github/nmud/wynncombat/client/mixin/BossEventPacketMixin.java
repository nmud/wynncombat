package io.github.nmud.wynncombat.client.mixin;

import io.github.nmud.wynncombat.client.debug.DebugLogger;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Captures every boss-bar update. Wynncraft uses the boss bar to surface
 * focused-mob health, world-event progress, raid intermission, and a few
 * status effects. Wynntils' {@code CombatModel} reads the same packets to
 * know which mob the player is aimed at and how much HP it has left, which
 * is the cleanest way to attribute floating damage labels to a target.
 *
 * <p>The packet uses a visitor pattern: we dispatch through a private
 * {@link Handler} and log whichever method gets called.
 */
@Mixin(ClientPacketListener.class)
public class BossEventPacketMixin {
	@Inject(method = "handleBossUpdate", at = @At("HEAD"))
	private void wynncombat$logBossEvent(ClientboundBossEventPacket packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;

		packet.dispatch(new ClientboundBossEventPacket.Handler() {
			@Override
			public void add(UUID id, Component name, float progress, BossEvent.BossBarColor color,
							BossEvent.BossBarOverlay overlay, boolean darkenScreen, boolean playMusic,
							boolean createWorldFog) {
				debug.log(
					"BOSSBAR",
					"op=add id=" + id
						+ " name=\"" + DebugLogger.escape(DebugLogger.styled(name)) + "\""
						+ " progress=" + progress
						+ " color=" + color
						+ " overlay=" + overlay
				);
			}

			@Override
			public void remove(UUID id) {
				debug.log("BOSSBAR", "op=remove id=" + id);
			}

			@Override
			public void updateProgress(UUID id, float progress) {
				debug.log("BOSSBAR", "op=progress id=" + id + " progress=" + progress);
			}

			@Override
			public void updateName(UUID id, Component name) {
				debug.log(
					"BOSSBAR",
					"op=name id=" + id
						+ " name=\"" + DebugLogger.escape(DebugLogger.styled(name)) + "\""
				);
			}

			@Override
			public void updateStyle(UUID id, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
				debug.log("BOSSBAR", "op=style id=" + id + " color=" + color + " overlay=" + overlay);
			}

			@Override
			public void updateProperties(UUID id, boolean darkenScreen, boolean playMusic, boolean createWorldFog) {
				debug.log("BOSSBAR", "op=props id=" + id
					+ " darken=" + darkenScreen
					+ " music=" + playMusic
					+ " fog=" + createWorldFog);
			}
		});
	}
}
