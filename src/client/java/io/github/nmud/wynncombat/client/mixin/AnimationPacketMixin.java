package io.github.nmud.wynncombat.client.mixin;

import io.github.nmud.wynncombat.client.debug.DebugLogger;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the two animation packets the server sends to drive client-side
 * combat visuals.
 *
 * <ul>
 *   <li><b>{@link ClientboundAnimatePacket}</b> — entity arm-swing, critical
 *       hit, magic critical hit, wake-up. The action codes are:
 *       <pre>0 = SWING_MAIN_HAND, 2 = WAKE_UP, 3 = SWING_OFF_HAND,
 * 4 = CRITICAL_HIT, 5 = MAGIC_CRITICAL_HIT</pre>
 *       Crit/magic-crit are direct "auto-attack landed and crit on this
 *       entity" signals, useful for confirming wand hits on a target.</li>
 *   <li><b>{@link ClientboundHurtAnimationPacket}</b> — the red flash and
 *       knock-back yaw on a hurt entity. Fires on every damage tick the
 *       client is told to render, so it pairs 1:1 with floating damage
 *       labels and tells us exactly which target the label belongs to.</li>
 * </ul>
 */
@Mixin(ClientPacketListener.class)
public class AnimationPacketMixin {
	@Inject(method = "handleAnimate", at = @At("HEAD"))
	private void wynncombat$logAnimate(ClientboundAnimatePacket packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;

		debug.log(
			"ANIMATE",
			"entity=" + packet.getId()
				+ " action=" + packet.getAction()
				+ " (" + actionName(packet.getAction()) + ")"
		);
	}

	@Inject(method = "handleHurtAnimation", at = @At("HEAD"))
	private void wynncombat$logHurt(ClientboundHurtAnimationPacket packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;

		debug.log(
			"HURT",
			"entity=" + packet.id() + " yaw=" + String.format("%.1f", packet.yaw())
		);
	}

	private static String actionName(int action) {
		return switch (action) {
			case 0 -> "swing_main";
			case 2 -> "wake_up";
			case 3 -> "swing_off";
			case 4 -> "crit";
			case 5 -> "magic_crit";
			default -> "unknown";
		};
	}
}
