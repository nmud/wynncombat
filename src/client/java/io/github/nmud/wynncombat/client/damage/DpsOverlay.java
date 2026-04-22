package io.github.nmud.wynncombat.client.damage;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD element that prints rolling DPS numbers for every enabled
 * {@link DpsConfig.DpsWindow}. In {@link DpsConfig.DisplayMode#ALL} each
 * enabled window is its own line; in {@link DpsConfig.DisplayMode#CYCLE}
 * only the active window (controlled by the prev/next keybinds) is rendered.
 *
 * <p>Positioning, sizing, colors, background, shadow, font family, and
 * font scale all come from {@link DpsConfig}. Editing mode (set by
 * {@code DpsScreen}) makes the overlay always visible with a visible border
 * and sample data, so it can be dragged / resized.
 */
public final class DpsOverlay implements HudElement {
	private static final int LINE_GAP = 1;
	private static final int BG_PAD_X = 3;
	private static final int BG_PAD_Y = 2;

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

		DpsConfig cfg = DpsConfig.get();
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

		List<Line> lines = collectLines(cfg);

		if (lines.isEmpty() && !editing) return;

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

		float scale = cfg.fontSize.multiplier;
		int scaledLineH = Math.max(1, Math.round((font.lineHeight + LINE_GAP) * scale));
		int scaledGlyphH = Math.max(1, Math.round(font.lineHeight * scale));
		int maxLines = Math.max(1, (cfg.height - 2 * BG_PAD_Y) / scaledLineH);
		int startIdx = Math.max(0, lines.size() - maxLines);

		Matrix3x2fStack pose = graphics.pose();
		int rowFromTop = 0;
		for (int i = startIdx; i < lines.size(); i++) {
			Line line = lines.get(i);

			int textW = font.width(line.component);
			int scaledTextW = Math.round(textW * scale);
			int x = cfg.anchor.right
				? boxX + cfg.width - BG_PAD_X - scaledTextW
				: boxX + BG_PAD_X;
			int y = boxY + BG_PAD_Y + rowFromTop * scaledLineH;
			// Keep the visual baseline tight against the top regardless of the
			// cap/ascent of the chosen font. Uniform has taller metrics than
			// default, which is why we floor to the font's line height.
			int yBaseline = Math.min(y, boxY + cfg.height - BG_PAD_Y - scaledGlyphH);

			int textColor = 0xFF000000 | (line.rgb & 0x00FFFFFF);

			pose.pushMatrix();
			pose.translate((float) x, (float) yBaseline);
			pose.scale(scale, scale);
			graphics.text(font, line.component, 0, 0, textColor, cfg.shadow);
			pose.popMatrix();

			rowFromTop++;
		}
	}

	private static List<Line> collectLines(DpsConfig cfg) {
		List<Line> out = new ArrayList<>();
		boolean showSamples = editing && DamageTracker.get().lastDamageTimestamp() == 0L;

		if (cfg.displayMode == DpsConfig.DisplayMode.CYCLE) {
			DpsConfig.DpsWindow active = pickActiveCycleWindow(cfg);
			if (active != null) {
				long value = showSamples ? sampleValue(active.seconds) : DamageTracker.get().dpsOver(active.seconds * 1000L);
				out.add(renderRow(active, value, cfg));
			} else if (editing) {
				out.add(new Line(
					Component.literal("(no enabled windows)").withStyle(applyFont(cfg)),
					dimColor(cfg)
				));
			}
			return out;
		}

		for (DpsConfig.DpsWindow w : cfg.windows) {
			if (w == null || !w.enabled) continue;
			long value = showSamples ? sampleValue(w.seconds) : DamageTracker.get().dpsOver(w.seconds * 1000L);
			out.add(renderRow(w, value, cfg));
		}

		if (out.isEmpty() && editing) {
			out.add(new Line(
				Component.literal("(no enabled windows)").withStyle(applyFont(cfg)),
				dimColor(cfg)
			));
		}
		return out;
	}

	private static DpsConfig.DpsWindow pickActiveCycleWindow(DpsConfig cfg) {
		if (cfg.windows == null || cfg.windows.isEmpty()) return null;
		int idx = cfg.clampCycleIndex();
		DpsConfig.DpsWindow candidate = cfg.windows.get(idx);
		if (candidate != null && candidate.enabled) return candidate;
		// Fall back to any enabled window.
		for (DpsConfig.DpsWindow w : cfg.windows) {
			if (w != null && w.enabled) return w;
		}
		return null;
	}

	private static Line renderRow(DpsConfig.DpsWindow w, long value, DpsConfig cfg) {
		String text = w.label() + ": " + formatDps(value);
		Style style = applyFont(cfg);
		MutableComponent comp = Component.literal(text).withStyle(style);
		int rgb = cfg.colorTiers ? tierColor(value, cfg) : (cfg.textColor.color & 0x00FFFFFF);
		return new Line(comp, rgb);
	}

	private static String formatDps(long value) {
		if (value < 10_000) return Long.toString(value);
		if (value < 1_000_000) return String.format("%.1fk", value / 1_000.0);
		if (value < 1_000_000_000) return String.format("%.1fm", value / 1_000_000.0);
		return String.format("%.1fb", value / 1_000_000_000.0);
	}

	private static int tierColor(long value, DpsConfig cfg) {
		if (value >= cfg.tierExtremeDps) return 0xFF5555;   // red
		if (value >= cfg.tierHighDps) return 0xFFAA00;       // orange
		if (value >= cfg.tierMidDps) return 0xFFFF55;        // yellow
		return cfg.textColor.color & 0x00FFFFFF;
	}

	private static int dimColor(DpsConfig cfg) {
		// Half-saturation grey, readable against both dark and light BG in edit mode.
		return 0xAAAAAA;
	}

	private static Style applyFont(DpsConfig cfg) {
		if (cfg.fontFamily == DpsConfig.FontFamily.DEFAULT) return Style.EMPTY;
		return Style.EMPTY.withFont(new FontDescription.Resource(
			Identifier.fromNamespaceAndPath("minecraft", cfg.fontFamily.identifierPath)
		));
	}

	private static long sampleValue(int seconds) {
		// Pretend values so the user can see the overlay layout in edit mode
		// before any real fight has happened.
		return switch (seconds) {
			case 1 -> 4200;
			case 10 -> 3850;
			case 30 -> 3100;
			default -> Math.max(100L, 3500L - (long) seconds * 10L);
		};
	}

	private static void drawBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	private record Line(Component component, int rgb) {}
}
