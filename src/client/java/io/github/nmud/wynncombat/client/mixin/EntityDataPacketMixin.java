package io.github.nmud.wynncombat.client.mixin;

import io.github.nmud.wynncombat.client.debug.DebugLogger;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

/**
 * Captures every entity-data update packet so we can see when Wynncraft updates
 * floating damage labels and mob name labels.
 *
 * <p>Wynncraft uses two label formats and both come through this packet:
 * <ul>
 *   <li>Modern: {@code Display.TextDisplay} text field (a raw {@link Component})</li>
 *   <li>Legacy: any entity's custom name (an {@code Optional<Component>})</li>
 * </ul>
 * We log both. See Wynntils {@code LabelHandler} for reference.
 */
@Mixin(ClientPacketListener.class)
public class EntityDataPacketMixin {
	@Inject(method = "handleSetEntityData", at = @At("HEAD"))
	private void wynncombat$logEntityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
		DebugLogger debug = DebugLogger.get();
		if (!debug.isEnabled()) return;

		List<SynchedEntityData.DataValue<?>> items = packet.packedItems();
		if (items == null || items.isEmpty()) return;

		for (SynchedEntityData.DataValue<?> item : items) {
			Component component = extractComponent(item.value());
			if (component == null) continue;

			debug.log(
				"ENTITY",
				"id=" + packet.id()
					+ " field=" + item.id()
					+ " styled=\"" + DebugLogger.escape(DebugLogger.styled(component)) + "\""
			);
		}
	}

	/**
	 * Returns the underlying Component for either a raw Component value
	 * (TextDisplay text) or an {@code Optional<Component>} value (entity custom
	 * names). Null otherwise.
	 */
	private static Component extractComponent(Object value) {
		if (value instanceof Component component) {
			return component;
		}
		if (value instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof Component component) {
			return component;
		}
		return null;
	}
}
