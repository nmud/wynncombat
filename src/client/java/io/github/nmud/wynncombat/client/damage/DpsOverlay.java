package io.github.nmud.wynncombat.client.damage;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Collections;
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
	/** Horizontal padding inside the label decoration between the edge and the text. */
	private static final int LABEL_INNER_PAD = 4;
	/** Vertical padding of the label decoration above/below the glyph box. */
	private static final int LABEL_DECOR_PAD = 3;
	/** Border thickness (px) for {@link DpsConfig.LabelStyle#BORDER}. */
	private static final int LABEL_BORDER_THICKNESS = 2;

	public static final int RESIZE_HANDLE = 6;

	private static volatile boolean editing = false;
	private static volatile int lastBoxX = 0;
	private static volatile int lastBoxY = 0;
	private static volatile int lastScreenW = 0;
	private static volatile int lastScreenH = 0;
	/**
	 * Screen-space rects of the rows rendered on the last frame, newest
	 * first. The edit-position screen reads this to figure out which row
	 * the mouse is hovering so it can initiate a per-row drag.
	 */
	private static volatile List<RowRect> lastRows = List.of();

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

	/**
	 * Returns the rendered row at the given screen-space pixel, or
	 * {@code null} if none. Iterates the most-recent frame's rows in reverse
	 * so that rows drawn later (which therefore appear on top if the user
	 * has dragged two on top of each other) win the hit-test.
	 */
	public static RowRect rowAt(int x, int y) {
		List<RowRect> snapshot = lastRows;
		for (int i = snapshot.size() - 1; i >= 0; i--) {
			RowRect r = snapshot.get(i);
			if (r.window() == null) continue;
			if (x >= r.x() && x < r.x() + r.w() && y >= r.y() && y < r.y() + r.h()) {
				return r;
			}
		}
		return null;
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
		List<RowRect> rects = new ArrayList<>(lines.size() - startIdx);
		for (int i = startIdx; i < lines.size(); i++) {
			Line line = lines.get(i);

			// Compose in unscaled font pixels; the pose handles scaling.
			int labelW = font.width(line.labelComponent);
			int sepW = font.width(line.separatorComponent);
			int valueW = font.width(line.valueComponent);
			int totalW = labelPadLeft(cfg) + labelW + labelPadRight(cfg) + sepW + valueW;
			int scaledTextW = Math.round(totalW * scale);

			int x;
			int yBaseline;
			boolean hasCustom = line.source != null && line.source.customPosition;
			if (hasCustom) {
				// User dragged this row free of the stack: rowX/rowY are
				// offsets from the box top-left in unscaled pixels. Clamp
				// so the row stays inside the box even if the user resized
				// the box down afterwards.
				int maxRowX = Math.max(0, cfg.width - scaledTextW);
				int maxRowY = Math.max(0, cfg.height - scaledGlyphH);
				int rx = Math.max(0, Math.min(maxRowX, line.source.rowX));
				int ry = Math.max(0, Math.min(maxRowY, line.source.rowY));
				x = boxX + rx;
				yBaseline = boxY + ry;
			} else {
				x = cfg.anchor.right
					? boxX + cfg.width - BG_PAD_X - scaledTextW
					: boxX + BG_PAD_X;
				int y = boxY + BG_PAD_Y + rowFromTop * scaledLineH;
				// Keep the visual baseline tight against the top regardless of
				// the cap/ascent of the font.
				yBaseline = Math.min(y, boxY + cfg.height - BG_PAD_Y - scaledGlyphH);
				rowFromTop++;
			}

			int labelColor = 0xFF000000 | (line.labelRgb & 0x00FFFFFF);
			int valueColor = 0xFF000000 | (line.valueRgb & 0x00FFFFFF);

			pose.pushMatrix();
			pose.translate((float) x, (float) yBaseline);
			pose.scale(scale, scale);

			drawLabelDecoration(graphics, cfg, labelW, font.lineHeight);

			int cursor = labelPadLeft(cfg);
			graphics.text(font, line.labelComponent, cursor, 0, labelColor, cfg.shadow);
			cursor += labelW + labelPadRight(cfg);
			graphics.text(font, line.separatorComponent, cursor, 0, valueColor, cfg.shadow);
			cursor += sepW;
			graphics.text(font, line.valueComponent, cursor, 0, valueColor, cfg.shadow);

			pose.popMatrix();

			rects.add(new RowRect(x, yBaseline, scaledTextW, scaledGlyphH, line.source));
		}

		lastRows = Collections.unmodifiableList(rects);
	}

	/**
	 * Draw the pill background / border around the label. Caller has already
	 * translated the pose so (0,0) is the text start position for this row;
	 * the decoration is extended slightly outside the label width using
	 * {@link #labelPadLeft}/{@link #labelPadRight} so the text doesn't touch
	 * the box edges.
	 */
	private static void drawLabelDecoration(GuiGraphicsExtractor g, DpsConfig cfg, int labelW, int lineH) {
		if (cfg.labelStyle == DpsConfig.LabelStyle.NONE) return;
		int pad = LABEL_DECOR_PAD;
		int x0 = 0;
		int y0 = -pad;
		int x1 = labelPadLeft(cfg) + labelW + labelPadRight(cfg);
		int y1 = lineH + pad;

		if (cfg.labelStyle == DpsConfig.LabelStyle.PILL) {
			g.fill(x0, y0, x1, y1, cfg.labelBackground.color);
		} else if (cfg.labelStyle == DpsConfig.LabelStyle.BORDER) {
			int c = cfg.labelBorderColor.color;
			int t = LABEL_BORDER_THICKNESS;
			g.fill(x0, y0, x1, y0 + t, c);
			g.fill(x0, y1 - t, x1, y1, c);
			g.fill(x0, y0, x0 + t, y1, c);
			g.fill(x1 - t, y0, x1, y1, c);
		}
	}

	private static int labelPadLeft(DpsConfig cfg) {
		return cfg.labelStyle == DpsConfig.LabelStyle.NONE ? 0 : LABEL_INNER_PAD;
	}

	private static int labelPadRight(DpsConfig cfg) {
		return cfg.labelStyle == DpsConfig.LabelStyle.NONE ? 0 : LABEL_INNER_PAD;
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
				out.add(emptyLine(cfg));
			}
			return out;
		}

		for (DpsConfig.DpsWindow w : cfg.windows) {
			if (w == null || !w.enabled) continue;
			long value = showSamples ? sampleValue(w.seconds) : DamageTracker.get().dpsOver(w.seconds * 1000L);
			out.add(renderRow(w, value, cfg));
		}

		if (out.isEmpty() && editing) {
			out.add(emptyLine(cfg));
		}
		return out;
	}

	private static Line emptyLine(DpsConfig cfg) {
		int c = dimColor(cfg);
		return new Line(
			Component.literal("(no windows)"),
			Component.empty(),
			Component.empty(),
			c, c, null
		);
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
		MutableComponent labelComp = Component.literal(w.label());
		MutableComponent sepComp = Component.literal(": ");
		MutableComponent valueComp = Component.literal(formatDps(value));
		int valueRgb = cfg.colorTiers ? tierColor(value, cfg) : (cfg.textColor.color & 0x00FFFFFF);
		int labelRgb = cfg.labelUniformColor ? valueRgb : (cfg.labelTextColor.color & 0x00FFFFFF);
		return new Line(labelComp, sepComp, valueComp, labelRgb, valueRgb, w);
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

	/**
	 * One rendered DPS row split into label / separator / value sub-parts so
	 * the label can be decorated (pill/border) independently of the value
	 * and can be colored separately when {@code labelUniformColor} is off.
	 * {@code source} is the originating window so callers that need to pair
	 * a rendered row with its config entry (e.g. the per-row drag in the
	 * edit-position screen) can do so without re-walking the windows list.
	 */
	private record Line(
		Component labelComponent,
		Component separatorComponent,
		Component valueComponent,
		int labelRgb,
		int valueRgb,
		DpsConfig.DpsWindow source) {}

	/**
	 * Screen-space rect of a rendered row, along with the {@link DpsConfig.DpsWindow}
	 * it originated from. The edit-position screen uses this for hit-testing
	 * when the user clicks a row to drag it.
	 */
	public record RowRect(int x, int y, int w, int h, DpsConfig.DpsWindow window) {}
}
