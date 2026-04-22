package io.github.nmud.wynncombat.client.damage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.nmud.wynncombat.WynnCombat;
import io.github.nmud.wynncombat.client.combat.OverlayConfig;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * User-facing settings for the {@link DpsOverlay}. Persisted to
 * {@code config/wynncombat-dps.json} in the game directory.
 *
 * <p>Deliberately kept separate from {@link OverlayConfig} so the DPS and
 * Ability Log modules stay independent -- each is its own on-disk file, each
 * has its own enable/disable toggle, keybind, and edit-position screen.
 */
public final class DpsConfig {
	/**
	 * A configurable DPS time window. Duration is in seconds so values fit
	 * nicely in the UI and JSON. Displayed label is derived from the duration
	 * ({@code "1s"}, {@code "30s"}, {@code "2m"}, etc.).
	 */
	public static final class DpsWindow {
		public int seconds;
		public boolean enabled;

		public DpsWindow() {
			this(1, true);
		}

		public DpsWindow(int seconds, boolean enabled) {
			this.seconds = seconds;
			this.enabled = enabled;
		}

		public String label() {
			if (seconds < 60) return seconds + "s";
			if (seconds % 60 == 0) return (seconds / 60) + "m";
			return (seconds / 60) + "m" + (seconds % 60) + "s";
		}
	}

	public enum DisplayMode {
		ALL("All"),
		CYCLE("Cycle");

		public final String label;

		DisplayMode(String label) {
			this.label = label;
		}

		public DisplayMode next() {
			DisplayMode[] all = values();
			return all[(ordinal() + 1) % all.length];
		}
	}

	/**
	 * Font families exposed via Minecraft's built-in font assets
	 * ({@code default}, {@code alt}, {@code uniform}, {@code illageralt}).
	 * We don't ship custom font atlases -- doing so is a much bigger effort
	 * and the built-ins are enough to give the overlay visually distinct
	 * "styles" the user asked for.
	 */
	public enum FontFamily {
		DEFAULT("Default", "default"),
		UNIFORM("Uniform", "uniform"),
		ALT("Alt", "alt"),
		ILLAGER("Illager", "illageralt");

		public final String label;
		public final String identifierPath;

		FontFamily(String label, String identifierPath) {
			this.label = label;
			this.identifierPath = identifierPath;
		}

		public FontFamily next() {
			FontFamily[] all = values();
			return all[(ordinal() + 1) % all.length];
		}
	}

	/** Master toggle. If {@code false} the overlay does not render at all. */
	public boolean enabled = true;

	public OverlayConfig.Anchor anchor = OverlayConfig.Anchor.TOP_LEFT;
	public int offsetX = 5;
	public int offsetY = 5;
	public int width = 130;
	public int height = 64;

	public OverlayConfig.TextColor textColor = OverlayConfig.TextColor.WHITE;
	public OverlayConfig.Background background = OverlayConfig.Background.DARK;
	public boolean shadow = true;
	public OverlayConfig.FontSize fontSize = OverlayConfig.FontSize.THREE;
	public FontFamily fontFamily = FontFamily.DEFAULT;

	public DisplayMode displayMode = DisplayMode.ALL;
	public int cycleIndex = 0;

	/**
	 * Color the value by tier. Default cutoffs are hard-coded so the settings
	 * screen stays short -- someone who wants different numbers can edit the
	 * JSON directly. Under the cutoff uses {@link #textColor}; crossing each
	 * threshold upgrades the color.
	 */
	public boolean colorTiers = false;
	public int tierMidDps = 1_000;
	public int tierHighDps = 5_000;
	public int tierExtremeDps = 20_000;

	public List<DpsWindow> windows = defaultWindows();

	private static List<DpsWindow> defaultWindows() {
		List<DpsWindow> w = new ArrayList<>();
		w.add(new DpsWindow(1, true));
		w.add(new DpsWindow(10, false));
		w.add(new DpsWindow(30, true));
		return w;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static volatile DpsConfig instance;

	public static DpsConfig get() {
		DpsConfig local = instance;
		if (local == null) {
			synchronized (DpsConfig.class) {
				local = instance;
				if (local == null) {
					local = loadOrDefault();
					instance = local;
				}
			}
		}
		return local;
	}

	public static void save() {
		if (instance == null) return;
		try {
			Path path = configPath();
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(instance), StandardCharsets.UTF_8);
		} catch (IOException e) {
			WynnCombat.LOGGER.error("Failed to save DPS config", e);
		}
	}

	public void resetToDefaults() {
		DpsConfig fresh = new DpsConfig();
		this.enabled = fresh.enabled;
		this.anchor = fresh.anchor;
		this.offsetX = fresh.offsetX;
		this.offsetY = fresh.offsetY;
		this.width = fresh.width;
		this.height = fresh.height;
		this.textColor = fresh.textColor;
		this.background = fresh.background;
		this.shadow = fresh.shadow;
		this.fontSize = fresh.fontSize;
		this.fontFamily = fresh.fontFamily;
		this.displayMode = fresh.displayMode;
		this.cycleIndex = fresh.cycleIndex;
		this.colorTiers = fresh.colorTiers;
		this.tierMidDps = fresh.tierMidDps;
		this.tierHighDps = fresh.tierHighDps;
		this.tierExtremeDps = fresh.tierExtremeDps;
		this.windows = defaultWindows();
	}

	/** Cycle {@link #cycleIndex} to the previous enabled window. No-op if none. */
	public void cyclePrev() {
		advanceCycle(-1);
	}

	/** Cycle {@link #cycleIndex} to the next enabled window. No-op if none. */
	public void cycleNext() {
		advanceCycle(+1);
	}

	private void advanceCycle(int step) {
		if (windows == null || windows.isEmpty()) return;
		int size = windows.size();
		int idx = clampCycleIndex();
		for (int i = 0; i < size; i++) {
			idx = Math.floorMod(idx + step, size);
			if (windows.get(idx).enabled) {
				cycleIndex = idx;
				return;
			}
		}
		// None enabled; leave cycleIndex where it was.
	}

	public int clampCycleIndex() {
		if (windows == null || windows.isEmpty()) return 0;
		if (cycleIndex < 0) return 0;
		if (cycleIndex >= windows.size()) return windows.size() - 1;
		return cycleIndex;
	}

	private static DpsConfig loadOrDefault() {
		try {
			Path path = configPath();
			if (Files.exists(path)) {
				String json = Files.readString(path, StandardCharsets.UTF_8);
				DpsConfig cfg = GSON.fromJson(json, DpsConfig.class);
				if (cfg != null) return cfg.normalized();
			}
		} catch (IOException | RuntimeException e) {
			WynnCombat.LOGGER.warn("Failed to load DPS config, using defaults", e);
		}
		return new DpsConfig();
	}

	/** Repair anything null / out-of-range after Gson deserialisation. */
	private DpsConfig normalized() {
		if (anchor == null) anchor = OverlayConfig.Anchor.TOP_LEFT;
		if (background == null) background = OverlayConfig.Background.DARK;
		if (textColor == null) textColor = OverlayConfig.TextColor.WHITE;
		if (fontSize == null) fontSize = OverlayConfig.FontSize.THREE;
		if (fontFamily == null) fontFamily = FontFamily.DEFAULT;
		if (displayMode == null) displayMode = DisplayMode.ALL;
		if (windows == null) windows = defaultWindows();
		for (DpsWindow w : windows) {
			if (w == null) continue;
			if (w.seconds < 1) w.seconds = 1;
			if (w.seconds > 600) w.seconds = 600;
		}
		if (width < 60) width = 60;
		if (width > 600) width = 600;
		if (height < 20) height = 20;
		if (height > 400) height = 400;
		if (tierMidDps < 0) tierMidDps = 0;
		if (tierHighDps < tierMidDps) tierHighDps = tierMidDps;
		if (tierExtremeDps < tierHighDps) tierExtremeDps = tierHighDps;
		cycleIndex = clampCycleIndex();
		return this;
	}

	private static Path configPath() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("config/wynncombat-dps.json");
	}
}
