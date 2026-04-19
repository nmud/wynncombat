package io.github.nmud.wynncombat.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nmud.wynncombat.WynnCombat;
import io.github.nmud.wynncombat.client.debug.CombatDebug;
import io.github.nmud.wynncombat.client.debug.FocusedEntityTracker;
import io.github.nmud.wynncombat.client.gui.WynnCombatScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class WynnCombatClient implements ClientModInitializer {
	public static KeyMapping openMenuKey;
	public static KeyMapping toggleDebugKey;

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

		CombatDebug.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMenuKey.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new WynnCombatScreen());
				}
			}

			while (toggleDebugKey.consumeClick()) {
				CombatDebug.toggle();
			}

			FocusedEntityTracker.tick(client);
		});
	}
}
