package io.github.nmud.wynncombat.client.combat;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * Bottom-right (by default) HUD element that prints recent spell casts in a
 * chat-like stack. Newest entry is at the bottom; entries fade out over the
 * last second of their lifetime.
 *
 * <p>Reads its position, size, colors, and timing from {@link OverlayConfig}.
 * When {@link #setEditing(boolean) editing mode} is enabled (set by the
 * ability-log edit screen), the overlay always renders, draws a visible
 * border, fills with sample entries when empty, and exposes a small resize
 * handle in the bottom-right corner.
 */
public final class AbilityLogOverlay implements HudElement {
	private static final int LINE_GAP = 1;
	private static final int BG_PAD_X = 3;
	private static final int BG_PAD_Y = 1;

	public static final int RESIZE_HANDLE = 6;

	private static volatile boolean editing = false;
	private static volatile int lastBoxX = 0;
	private static volatile int lastBoxY = 0;
	private static volatile int lastScreenW = 0;
	private static volatile int lastScreenH = 0;

	public static void setEditing(boolean value) {
		editing = value;
	}

	public static boolean isEditing() {
		return editing;
	}

	public static int getBoxX() {
		return lastBoxX;
	}

	public static int getBoxY() {
		return lastBoxY;
	}

	public static int getScreenW() {
		return lastScreenW;
	}

	public static int getScreenH() {
		return lastScreenH;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui && !editing) return;

		OverlayConfig cfg = OverlayConfig.get();
		if (!cfg.enabled && !editing) return;
		Font font = mc.font;
		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();

		int boxX = cfg.anchor.right ? screenW - cfg.width - cfg.offsetX : cfg.offsetX;
		int boxY = cfg.anchor.bottom ? screenH - cfg.height - cfg.offsetY : cfg.offsetY;

		lastBoxX = boxX;
		lastBoxY = boxY;
		lastScreenW = screenW;
		lastScreenH = screenH;

		List<AbilityLogEntry> recent = AbilityLog.get().recent(cfg.entryLifetimeMs);
		boolean hasEntries = !recent.isEmpty();

		if (!hasEntries && !editing) return;

		if (cfg.background.color != 0 || editing) {
			int bg = editing ? 0x60000000 : cfg.background.color;
			graphics.fill(boxX, boxY, boxX + cfg.width, boxY + cfg.height, bg);
		}

		if (editing) {
			drawBorder(graphics, boxX, boxY, cfg.width, cfg.height, 0xFF55FFFF);
			graphics.fill(
				boxX + cfg.width - RESIZE_HANDLE,
				boxY + cfg.height - RESIZE_HANDLE,
				boxX + cfg.width,
				boxY + cfg.height,
				0xFFFFFFFF
			);
		}

		List<AbilityLogEntry> render = hasEntries ? recent : sampleEntries();
		long now = System.currentTimeMillis();

		float scale = cfg.fontSize.multiplier;
		int scaledLineH = Math.max(1, Math.round((font.lineHeight + LINE_GAP) * scale));
		int scaledGlyphH = Math.max(1, Math.round(font.lineHeight * scale));
		int maxLines = Math.max(1, (cfg.height - 2 * BG_PAD_Y) / scaledLineH);
		int startIdx = Math.max(0, render.size() - maxLines);

		Matrix3x2fStack pose = graphics.pose();
		int rowFromBottom = 0;
		for (int i = render.size() - 1; i >= startIdx; i--) {
			AbilityLogEntry entry = render.get(i);
			int alpha;
			if (editing && !hasEntries) {
				alpha = 0xFF;
			} else {
				long age = now - entry.timestampMs();
				alpha = computeAlpha(age, cfg.entryLifetimeMs, cfg.fadeMs);
				if (alpha <= 0) {
					rowFromBottom++;
					continue;
				}
			}

			Component line = formatEntry(entry, cfg);
			int textW = font.width(line);
			int scaledTextW = Math.round(textW * scale);
			int x = cfg.anchor.right
				? boxX + cfg.width - BG_PAD_X - scaledTextW
				: boxX + BG_PAD_X;
			int y = boxY + cfg.height - BG_PAD_Y - scaledGlyphH - scaledLineH * rowFromBottom;

			int textColor = ((alpha & 0xFF) << 24) | (cfg.textColor.color & 0x00FFFFFF);

			pose.pushMatrix();
			pose.translate((float) x, (float) y);
			pose.scale(scale, scale);
			graphics.text(font, line, 0, 0, textColor, cfg.shadow);
			pose.popMatrix();

			rowFromBottom++;
		}
	}

	private static List<AbilityLogEntry> sampleEntries() {
		long now = System.currentTimeMillis();
		return List.of(
			new AbilityLogEntry("Bash", 21, 0, now, 1),
			new AbilityLogEntry("War Scream", 35, 0, now, 1),
			new AbilityLogEntry("Charge", 30, 0, now, 3)
		);
	}

	private static void drawBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	private static int computeAlpha(long age, long lifetimeMs, long fadeMs) {
		if (fadeMs <= 0) return age >= lifetimeMs ? 0 : 0xFF;
		long fadeStart = lifetimeMs - fadeMs;
		if (age <= fadeStart) return 0xFF;
		if (age >= lifetimeMs) return 0;
		double remaining = 1.0 - (double) (age - fadeStart) / fadeMs;
		return (int) (0xFF * remaining);
	}

	private static Component formatEntry(AbilityLogEntry entry, OverlayConfig cfg) {
		String name = entry.spellName();
		if (entry.stackCount() > 1) {
			name = name + " x" + entry.stackCount();
		}
		MutableComponent c = Component.literal(name);
		if (entry.manaCost() > 0) {
			String tail = cfg.showManaLabel
				? "  -" + entry.manaCost() + " mana"
				: "  -" + entry.manaCost();
			c.append(Component.literal(tail).withStyle(ChatFormatting.AQUA));
		}
		if (entry.healthCost() > 0) {
			String tail = cfg.showManaLabel
				? "  -" + entry.healthCost() + " hp"
				: "  -" + entry.healthCost();
			c.append(Component.literal(tail).withStyle(ChatFormatting.RED));
		}
		return c;
	}
}
