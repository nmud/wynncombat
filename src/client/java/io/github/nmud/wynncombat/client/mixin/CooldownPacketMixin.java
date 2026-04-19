package io.github.nmud.wynncombat.client.mixin;

import io.github.nmud.wynncombat.client.debug.DebugLogger;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures vanilla item-cooldown packets. Wynncraft is suspected to put the
 * player's class weapon on cooldown when a spell is cast (the same vanilla
 * mechanism vanilla uses for ender pearls / chorus fruit). If so, this is
 * the cleanest possible "spell was just cast" signal -- server-authoritative,
 * named by the cooldown group identifier, no glyph parsing needed.
 *
 * <p>If Wynncraft does not use this packet, we'll see no SPELL_CD lines in
 * the log and we'll fall back to action-bar diffing.
 */
@Mixin(ClientPacketListener.class)
public class CooldownPacketMixin {
	@Inject(method = "handleItemCooldown", at = @At("HEAD"))
	private void wynncombat$logCooldown(ClientboundCooldownPacket packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;

		debug.log(
			"SPELL_CD",
			"group=" + packet.cooldownGroup() + " ticks=" + packet.duration()
		);
	}
}
