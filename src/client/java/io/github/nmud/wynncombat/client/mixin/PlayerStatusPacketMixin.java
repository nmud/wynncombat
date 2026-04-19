package io.github.nmud.wynncombat.client.mixin;

import io.github.nmud.wynncombat.client.debug.DebugLogger;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.world.effect.MobEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player-state packets that the server only ever sends to the local
 * player (i.e. unambiguously "mine"). Closes out the recon set:
 *
 * <ul>
 *   <li>{@link ClientboundSetHealthPacket} — local player HP, food,
 *       saturation. The single best signal for "I just took damage" or
 *       "I just healed", and feeds a survival HUD trivially.</li>
 *   <li>{@link ClientboundUpdateMobEffectPacket} /
 *       {@link ClientboundRemoveMobEffectPacket} — vanilla potion effects
 *       applied to the local player. Wynncraft drives several class
 *       buffs/debuffs through these (War Scream → Strength on self,
 *       Vanish → Invisibility, Heal Echo → Regeneration). When the
 *       affected entity id matches the player and the effect is a
 *       known self-buff, this is a definitive cast confirmation.</li>
 *   <li>{@link ClientboundPlayerCombatKillPacket} — vanilla "you died"
 *       packet. Possibly used by Wynncraft for the death screen; the
 *       log will tell us either way.</li>
 * </ul>
 */
@Mixin(ClientPacketListener.class)
public class PlayerStatusPacketMixin {
	@Inject(method = "handleSetHealth", at = @At("HEAD"))
	private void wynncombat$logSetHealth(ClientboundSetHealthPacket packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;

		debug.log(
			"SET_HEALTH",
			"hp=" + packet.getHealth()
				+ " food=" + packet.getFood()
				+ " sat=" + packet.getSaturation()
		);
	}

	@Inject(method = "handleUpdateMobEffect", at = @At("HEAD"))
	private void wynncombat$logUpdateMobEffect(ClientboundUpdateMobEffectPacket packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;

		debug.log(
			"MOB_FX_ADD",
			"entity=" + packet.getEntityId()
				+ " effect=" + effectId(packet.getEffect())
				+ " amp=" + packet.getEffectAmplifier()
				+ " dur=" + packet.getEffectDurationTicks()
				+ " ambient=" + packet.isEffectAmbient()
				+ " visible=" + packet.isEffectVisible()
		);
	}

	@Inject(method = "handleRemoveMobEffect", at = @At("HEAD"))
	private void wynncombat$logRemoveMobEffect(ClientboundRemoveMobEffectPacket packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;

		debug.log(
			"MOB_FX_REM",
			"entity=" + packet.entityId()
				+ " effect=" + effectId(packet.effect())
		);
	}

	@Inject(method = "handlePlayerCombatKill", at = @At("HEAD"))
	private void wynncombat$logCombatKill(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;

		String msg;
		try {
			msg = packet.message().getString();
		} catch (Throwable t) {
			msg = "<message failed>";
		}

		debug.log(
			"COMBAT_KILL",
			"victim=" + packet.playerId() + " message=\"" + DebugLogger.escape(msg) + "\""
		);
	}

	private static String effectId(Holder<MobEffect> holder) {
		if (holder == null) return "null";
		try {
			return holder.unwrapKey().map(Object::toString).orElse("inline");
		} catch (Throwable ignored) {
			return holder.toString();
		}
	}
}
