package io.github.nmud.wynncombat.client.mixin;

import io.github.nmud.wynncombat.client.debug.DebugLogger;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catches custom-payload queries that arrive during the LOGIN protocol
 * phase, before the world is loaded. Some servers (BungeeCord forwarding,
 * Velocity, Geyser, certain proxies) negotiate features here. Wynncraft
 * doesn't <em>appear</em> to use this phase but we have no proof either
 * way -- this mixin makes that a one-line confirmation.
 *
 * <p>The CONFIGURATION phase counterpart is {@link
 * ConfigCustomPayloadMixin}, and the PLAY phase counterpart is the
 * existing {@link CustomPayloadPacketMixin}.
 */
@Mixin(ClientHandshakePacketListenerImpl.class)
public class LoginCustomQueryMixin {
	@Inject(method = "handleCustomQuery", at = @At("HEAD"))
	private void wynncombat$logLoginQuery(ClientboundCustomQueryPacket packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;
		if (packet == null) return;

		CustomQueryPayload payload = packet.payload();
		String channel = (payload != null && payload.id() != null) ? payload.id().toString() : "null";
		String cls = payload == null ? "null" : payload.getClass().getName();

		debug.log(
			"LOGIN_QUERY",
			"tx=" + packet.transactionId() + " id=" + channel + " class=" + cls
		);
	}
}
