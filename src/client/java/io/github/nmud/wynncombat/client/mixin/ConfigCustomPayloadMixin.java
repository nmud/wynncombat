package io.github.nmud.wynncombat.client.mixin;

import io.github.nmud.wynncombat.client.debug.DebugLogger;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CONFIGURATION-phase custom-payload observer. This phase runs after LOGIN
 * but before the player joins the world; some servers use it to push
 * resource packs, brand info, registry overrides, or feature flags. Mirrors
 * {@link CustomPayloadPacketMixin} which covers the PLAY phase, and
 * {@link LoginCustomQueryMixin} which covers LOGIN.
 *
 * <p>If Wynncraft sends anything here we want to know about it, since
 * everything visible during configuration is implicitly server -> client
 * (no player input has happened yet) and is the cleanest place to look
 * for a "session key" or capability negotiation we could later use.
 */
@Mixin(ClientConfigurationPacketListenerImpl.class)
public class ConfigCustomPayloadMixin {
	@Inject(method = "handleCustomPayload", at = @At("HEAD"))
	private void wynncombat$logConfigPayload(CustomPacketPayload payload, CallbackInfo ci) {
		log("CFG_PAYLOAD", payload);
	}

	@Inject(method = "handleUnknownCustomPayload", at = @At("HEAD"))
	private void wynncombat$logConfigUnknown(CustomPacketPayload payload, CallbackInfo ci) {
		log("CFG_PAYLOAD_UNK", payload);
	}

	private static void log(String category, CustomPacketPayload payload) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;
		if (payload == null) {
			debug.log(category, "id=null payload=null");
			return;
		}

		String channel = payload.type() != null && payload.type().id() != null
			? payload.type().id().toString()
			: "null";

		debug.log(
			category,
			"id=" + channel + " class=" + payload.getClass().getName()
		);
	}
}
