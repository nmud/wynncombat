package io.github.nmud.wynncombat;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WynnCombat implements ModInitializer {
	public static final String MOD_ID = "wynncombat";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("WynnCombat initialized.");
	}
}
