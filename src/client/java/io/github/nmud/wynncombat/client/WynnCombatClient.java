package io.github.nmud.wynncombat.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nmud.wynncombat.WynnCombat;
import io.github.nmud.wynncombat.client.combat.AbilityLog;
import io.github.nmud.wynncombat.client.combat.AbilityLogOverlay;
import io.github.nmud.wynncombat.client.combat.OverlayConfig;
import io.github.nmud.wynncombat.client.damage.DpsConfig;
import io.github.nmud.wynncombat.client.damage.DpsOverlay;
import io.github.nmud.wynncombat.client.debug.CombatDebug;
import io.github.nmud.wynncombat.client.debug.FocusedEntityTracker;
import io.github.nmud.wynncombat.client.gui.WynnCombatScreen;
import io.github.nmud.wynncombat.client.recorder.DpsRecorder;
import io.github.nmud.wynncombat.client.recorder.DpsRecorderOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class WynnCombatClient implements ClientModInitializer {
	public static KeyMapping openMenuKey;
	public static KeyMapping toggleDebugKey;
	public static KeyMapping toggleAbilityLogKey;
	public static KeyMapping toggleDpsKey;
	public static KeyMapping dpsCyclePrevKey;
	public static KeyMapping dpsCycleNextKey;
	public static KeyMapping toggleRecordingKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(WynnCombat.MOD_ID, "main")
		);

		openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.wynncombat.open_menu",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_N,
			category
		));

		toggleDebugKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.wynncombat.toggle_debug",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_K,
			category
		));

		// Overlay toggles start UNBOUND -- they're sticky state changes users
		// rarely flip during play, so no reason to claim a default keycap.
		toggleAbilityLogKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.wynncombat.toggle_ability_log",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN,
			category
		));

		toggleDpsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.wynncombat.toggle_dps",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN,
			category
		));

		// Cycle bindings are only meaningful when Display Mode = Cycle, so it
		// makes sense to ship sensible defaults.
		dpsCyclePrevKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.wynncombat.dps_cycle_prev",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_LEFT,
			category
		));

		dpsCycleNextKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.wynncombat.dps_cycle_next",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_RIGHT,
			category
		));

		// Recording toggle starts UNBOUND. It's a destructive-ish action
		// (stop finalises and writes a file) so we don't want a random
		// default keycap conflict producing surprise recordings.
		toggleRecordingKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.wynncombat.toggle_recording",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN,
			category
		));

		CombatDebug.register();
		AbilityLog.register();

		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(WynnCombat.MOD_ID, "ability_log"),
			new AbilityLogOverlay()
		);

		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(WynnCombat.MOD_ID, "dps_overlay"),
			new DpsOverlay()
		);

		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(WynnCombat.MOD_ID, "dps_recorder_indicator"),
			new DpsRecorderOverlay()
		);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMenuKey.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new WynnCombatScreen());
				}
			}

			while (toggleDebugKey.consumeClick()) {
				CombatDebug.toggle();
			}

			while (toggleAbilityLogKey.consumeClick()) {
				OverlayConfig cfg = OverlayConfig.get();
				cfg.enabled = !cfg.enabled;
				OverlayConfig.save();
			}

			while (toggleDpsKey.consumeClick()) {
				DpsConfig cfg = DpsConfig.get();
				cfg.enabled = !cfg.enabled;
				DpsConfig.save();
			}

			while (dpsCyclePrevKey.consumeClick()) {
				DpsConfig cfg = DpsConfig.get();
				if (cfg.enabled && cfg.displayMode == DpsConfig.DisplayMode.CYCLE) {
					cfg.cyclePrev();
				}
			}

			while (dpsCycleNextKey.consumeClick()) {
				DpsConfig cfg = DpsConfig.get();
				if (cfg.enabled && cfg.displayMode == DpsConfig.DisplayMode.CYCLE) {
					cfg.cycleNext();
				}
			}

			while (toggleRecordingKey.consumeClick()) {
				DpsRecorder recorder = DpsRecorder.get();
				if (recorder.isRecording()) {
					recorder.stop();
				} else {
					recorder.start();
				}
			}

			// Advance the recorder's per-second bucket boundary every tick
			// (~20 Hz). It only does work while a recording is active.
			DpsRecorder.get().tick();

			FocusedEntityTracker.tick(client);
		});
	}
}
