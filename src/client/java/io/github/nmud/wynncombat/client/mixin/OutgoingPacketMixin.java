package io.github.nmud.wynncombat.client.mixin;

import io.github.nmud.wynncombat.client.debug.DebugLogger;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Logs every outgoing packet the client produces. This is the most
 * "user-scoped" signal we have: every entry here was caused by the local
 * player (key press, mouse click, slot change, hotbar swap, plugin channel
 * reply). When we later need to attribute damage to a specific cast, the
 * combination of {@code OUT ServerboundSwingPacket} / {@code OUT
 * ServerboundUseItemPacket} / {@code OUT ServerboundInteractPacket}
 * timestamps gives us a definitive timeline of "what the user just did".
 *
 * <p>Wynncraft also encodes class-detection and a few HUD interactions as
 * outgoing custom payloads, so this hook will surface those too.
 *
 * <p>We only log {@link PacketFlow#SERVERBOUND} so the integrated server's
 * loopback connection (if it ever existed) wouldn't pollute the log.
 */
@Mixin(Connection.class)
public class OutgoingPacketMixin {
	/**
	 * Per-tick / per-frame noise that drowns out the actually interesting
	 * outgoing packets. Identified empirically from the first recon session
	 * (1395 ClientTickEnd / 813 PosRot / 475 Pos / 205 PlayerInput / 33
	 * KeepAlive in ~25 seconds of play). None of these carry combat
	 * intent -- they're either input-state heartbeats or movement deltas.
	 *
	 * <p>If we ever need them back (e.g. correlating cast windows with
	 * exact look angles), we can split this filter into a debug toggle.
	 * For now, dropping them turns the OUT stream into a readable cast
	 * journal.
	 */
	private static final Set<String> SUPPRESSED = Set.of(
		"ServerboundClientTickEndPacket",
		"ServerboundKeepAlivePacket",
		"ServerboundMovePlayerPacket",
		"ServerboundPlayerInputPacket",
		"Pos",
		"PosRot",
		"Rot",
		"StatusOnly"
	);

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
	private void wynncombat$logOutgoing(Packet<?> packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;

		Connection self = (Connection) (Object) this;
		if (self.getSending() != PacketFlow.SERVERBOUND) return;
		if (packet == null) return;

		String name = packet.getClass().getSimpleName();
		if (SUPPRESSED.contains(name)) return;

		String detail;
		try {
			detail = String.valueOf(packet);
		} catch (Throwable t) {
			detail = "<toString failed: " + t.getClass().getSimpleName() + ">";
		}

		debug.log(
			"OUT",
			"class=" + name
				+ " detail=\"" + DebugLogger.escape(detail) + "\""
		);
	}
}
