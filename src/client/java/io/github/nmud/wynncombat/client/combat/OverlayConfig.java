package io.github.nmud.wynncombat.client.combat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.nmud.wynncombat.WynnCombat;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * User-facing settings for the {@link AbilityLogOverlay}. Persisted to
 * {@code config/wynncombat.json} in the game directory.
 *
 * <p>Position is anchor-relative so the overlay stays glued to the same
 * corner when window resolution / GUI scale changes. {@link #offsetX} and
 * {@link #offsetY} are in scaled-GUI pixels measured from the chosen anchor
 * edge to the matching edge of the overlay box (e.g. for a
 * {@link Anchor#BOTTOM_RIGHT} anchor, {@code offsetX} is the gap between the
 * box's right edge and the screen's right edge).
 *
 * <p>All "live" state -- whether the user is currently editing position,
 * dragging, etc. -- belongs on the overlay, not here. This class is pure
 * persisted preferences.
 */
public final class OverlayConfig {
	public enum Anchor {
		TOP_LEFT(false, false),
		TOP_RIGHT(true, false),
		BOTTOM_LEFT(false, true),
		BOTTOM_RIGHT(true, true);

		public final boolean right;
		public final boolean bottom;

		Anchor(boolean right, boolean bottom) {
			this.right = right;
			this.bottom = bottom;
		}

		public Anchor next() {
			Anchor[] all = values();
			return all[(ordinal() + 1) % all.length];
		}
	}

	public enum Background {
		NONE(0x00000000, "Off"),
		FAINT(0x40000000, "Faint"),
		DARK(0x80000000, "Dark"),
		SOLID(0xC0000000, "Solid");

		public final int color;
		public final String label;

		Background(int color, String label) {
			this.color = color;
			this.label = label;
		}

		public Background next() {
			Background[] all = values();
			return all[(ordinal() + 1) % all.length];
		}
	}

	public enum TextColor {
		WHITE(0xFFFFFFFF, "White"),
		YELLOW(0xFFFFFF55, "Yellow"),
		AQUA(0xFF55FFFF, "Aqua"),
		GREEN(0xFF55FF55, "Green"),
		PINK(0xFFFF55FF, "Pink"),
		ORANGE(0xFFFFAA00, "Orange"),
		GRAY(0xFFAAAAAA, "Gray");

		public final int color;
		public final String label;

		TextColor(int color, String label) {
			this.color = color;
			this.label = label;
		}

		public TextColor next() {
			TextColor[] all = values();
			return all[(ordinal() + 1) % all.length];
		}
	}

	public Anchor anchor = Anchor.BOTTOM_RIGHT;
	public int offsetX = 5;
	public int offsetY = 50;
	public int width = 180;
	public int height = 90;
	public TextColor textColor = TextColor.WHITE;
	public Background background = Background.DARK;
	public boolean shadow = true;
	public boolean showIcons = false;
	public long entryLifetimeMs = 5000;
	public long fadeMs = 800;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static volatile OverlayConfig instance;

	public static OverlayConfig get() {
		OverlayConfig local = instance;
		if (local == null) {
			synchronized (OverlayConfig.class) {
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
			WynnCombat.LOGGER.error("Failed to save overlay config", e);
		}
	}

	public void resetToDefaults() {
		OverlayConfig fresh = new OverlayConfig();
		this.anchor = fresh.anchor;
		this.offsetX = fresh.offsetX;
		this.offsetY = fresh.offsetY;
		this.width = fresh.width;
		this.height = fresh.height;
		this.textColor = fresh.textColor;
		this.background = fresh.background;
		this.shadow = fresh.shadow;
		this.showIcons = fresh.showIcons;
		this.entryLifetimeMs = fresh.entryLifetimeMs;
		this.fadeMs = fresh.fadeMs;
	}

	private static OverlayConfig loadOrDefault() {
		try {
			Path path = configPath();
			if (Files.exists(path)) {
				String json = Files.readString(path, StandardCharsets.UTF_8);
				OverlayConfig cfg = GSON.fromJson(json, OverlayConfig.class);
				if (cfg != null) return cfg.normalized();
			}
		} catch (IOException | RuntimeException e) {
			WynnCombat.LOGGER.warn("Failed to load overlay config, using defaults", e);
		}
		return new OverlayConfig();
	}

	/** Repair anything that ended up null after Gson deserialisation. */
	private OverlayConfig normalized() {
		if (anchor == null) anchor = Anchor.BOTTOM_RIGHT;
		if (background == null) background = Background.DARK;
		if (textColor == null) textColor = TextColor.WHITE;
		if (entryLifetimeMs < 500) entryLifetimeMs = 500;
		if (entryLifetimeMs > 60_000) entryLifetimeMs = 60_000;
		if (fadeMs < 0) fadeMs = 0;
		if (fadeMs > entryLifetimeMs) fadeMs = entryLifetimeMs;
		if (width < 60) width = 60;
		if (width > 600) width = 600;
		if (height < 20) height = 20;
		if (height > 400) height = 400;
		return this;
	}

	private static Path configPath() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("config/wynncombat.json");
	}
}
