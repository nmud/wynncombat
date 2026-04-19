package io.github.nmud.wynncombat.client.mixin;

import io.github.nmud.wynncombat.client.debug.DebugLogger;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Raw netty-pipeline observer for every inbound packet. Sits at the very
 * bottom of {@link Connection}'s read path -- after the wire decoder but
 * before any listener dispatch -- so we see <em>every</em> packet type the
 * server ever sends us, including ones the existing per-method mixins
 * don't cover.
 *
 * <p>Logging every inbound packet would write thousands of lines per
 * second (chunk batches, entity ticks, time-of-day, ...). To stay
 * useful, we deduplicate by class name and only emit one
 * {@code IN_NEW} line per packet class per session. Anything that
 * appears here that we don't already have a dedicated mixin for is
 * worth investigating.
 */
@Mixin(Connection.class)
public class InboundPacketMixin {
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

	@Inject(
		method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
		at = @At("HEAD")
	)
	private void wynncombat$logInbound(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;

		Connection self = (Connection) (Object) this;
		if (self.getReceiving() != PacketFlow.CLIENTBOUND) return;
		if (packet == null) return;

		String name = packet.getClass().getSimpleName();
		if (!SEEN.add(name)) return;

		debug.log("IN_NEW", "class=" + name);
	}
}
