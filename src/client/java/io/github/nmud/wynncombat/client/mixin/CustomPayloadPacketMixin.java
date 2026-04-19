package io.github.nmud.wynncombat.client.mixin;

import io.github.nmud.wynncombat.client.debug.DebugLogger;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures every plugin / custom-payload message the server sends during
 * PLAY. Wynncraft <em>could</em> be sending structured combat data over a
 * private namespaced channel (their own UI mods do parse some of these).
 * This is a one-shot recon: if the log shows non-vanilla payload ids, we
 * look at what's inside. If everything is vanilla, this is cheap to remove.
 *
 * <p>We log both the recognised path (registered channel, payload class
 * known) and the unknown path (channel id with no client handler) so we see
 * everything regardless of whether Fabric registered a deserializer for it.
 *
 * <p><b>About hex-dumping unknown payloads.</b> In MC 26.1.2 the vanilla
 * fallback type {@code DiscardedPayload} only retains the channel
 * {@code Identifier} -- the {@code FriendlyByteBuf} is read past and then
 * dropped during deserialization. So once we see a payload here, the
 * raw bytes are already gone. Capturing them would require either
 * (a) registering a custom Fabric {@code PayloadTypeRegistry} entry per
 * channel id we're interested in, or (b) intercepting earlier in the
 * netty pipeline. {@link InboundPacketMixin} gives us the latter at the
 * "what packet types exist" level; the former is a follow-up once we
 * actually know the channel ids Wynncraft uses.
 *
 * <p>Sister mixins: {@link LoginCustomQueryMixin} (LOGIN phase),
 * {@link ConfigCustomPayloadMixin} (CONFIGURATION phase).
 */
@Mixin(ClientPacketListener.class)
public class CustomPayloadPacketMixin {
	@Inject(method = "handleCustomPayload", at = @At("HEAD"))
	private void wynncombat$logCustomPayload(CustomPacketPayload payload, CallbackInfo ci) {
		log("PAYLOAD", payload);
	}

	@Inject(method = "handleUnknownCustomPayload", at = @At("HEAD"))
	private void wynncombat$logUnknownCustomPayload(CustomPacketPayload payload, CallbackInfo ci) {
		log("PAYLOAD_UNK", payload);
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
