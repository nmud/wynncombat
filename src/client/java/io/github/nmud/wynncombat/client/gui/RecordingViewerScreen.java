package io.github.nmud.wynncombat.client.gui;

import io.github.nmud.wynncombat.client.recorder.DpsRecorder;
import io.github.nmud.wynncombat.client.recorder.DpsRecording;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Read-only viewer for a single {@link DpsRecording}. Three panels stacked
 * vertically:
 *
 * <ol>
 *   <li>Header with editable label + quick-hit summary stats
 *       (class, duration, casts, total damage, average & peak DPS).</li>
 *   <li>DPS-over-time mini-graph. Each {@code secondDps} sample is drawn as
 *       a column scaled to {@link DpsRecording#peakDps}; a horizontal
 *       dashed line marks the session average.</li>
 *   <li>Scrollable cast list showing the timing, spell name, mana, and hp
 *       delta for every individual cast.</li>
 * </ol>
 *
 * <p>The viewer also provides Delete (with in-screen confirmation) and
 * Save (writes any label edits back to disk).
 */
public class RecordingViewerScreen extends Screen {
	private static final int PANEL_WIDTH = 500;
	private static final int PANEL_HEIGHT = 380;
	private static final int BACKGROUND_COLOR = 0xCC101010;
	private static final int BORDER_COLOR = 0xFFFFFFFF;
	private static final int TITLE_COLOR = 0xFFFFFFFF;
	private static final int LABEL_COLOR = 0xFFAAAAAA;
	private static final int GRAPH_BG_COLOR = 0xFF202020;
	private static final int GRAPH_AXIS_COLOR = 0xFF606060;
	private static final int GRAPH_BAR_COLOR = 0xFF5AA9FF;
	private static final int GRAPH_PEAK_COLOR = 0xFFFF6060;
	private static final int GRAPH_AVG_COLOR = 0xFFFFD060;

	private static final int GRAPH_H = 90;
	private static final int CAST_LIST_ROW_H = 12;

	private static final DateTimeFormatter STARTED_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final Screen parent;
	private final DpsRecording recording;

	private int castScroll = 0;
	private boolean confirmingDelete = false;

	public RecordingViewerScreen(Screen parent, DpsRecording recording) {
		super(Component.literal("Recording"));
		this.parent = parent;
		this.recording = recording;
	}

	@Override
	protected void init() {
		super.init();
		build();
	}

	private void build() {
		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;

		int padding = 12;
		int contentX = panelX + padding;
		int contentW = PANEL_WIDTH - padding * 2;

		// Header row: label EditBox on the left, Save button on the right.
		int saveW = 50;
		int boxW = contentW - saveW - 6;
		EditBox labelBox = new EditBox(this.font, contentX, panelY + 28, boxW, 18,
			Component.literal(recording.label == null ? "" : recording.label));
		labelBox.setMaxLength(64);
		labelBox.setValue(recording.label == null ? "" : recording.label);
		labelBox.setResponder(s -> recording.label = s == null ? "" : s);
		this.addRenderableWidget(labelBox);

		this.addRenderableWidget(Button.builder(Component.literal("Save"),
				b -> DpsRecorder.get().persist(recording))
			.bounds(contentX + boxW + 6, panelY + 28, saveW, 20)
			.build());

		int bottomY = panelY + PANEL_HEIGHT - 28;

		if (confirmingDelete) {
			int confW = 200;
			int cancelW = 60;
			this.addRenderableWidget(Button.builder(Component.literal("Confirm Delete (click again)"), b -> {
					DpsRecorder.get().delete(recording);
					if (this.minecraft != null) this.minecraft.setScreen(parent);
				})
				.bounds(contentX, bottomY, confW, 20)
				.build());
			this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
					confirmingDelete = false;
					rebuild();
				})
				.bounds(contentX + confW + 6, bottomY, cancelW, 20)
				.build());
		} else {
			int delW = 70;
			this.addRenderableWidget(Button.builder(Component.literal("Delete"), b -> {
					confirmingDelete = true;
					rebuild();
				})
				.bounds(contentX, bottomY, delW, 20)
				.build());
		}

		int backW = 60;
		this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
			.bounds(panelX + PANEL_WIDTH - padding - backW, bottomY, backW, 20)
			.build());
	}

	private void rebuild() {
		this.clearWidgets();
		this.init();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;
		int padding = 12;
		int contentX = panelX + padding;
		int contentW = PANEL_WIDTH - padding * 2;

		graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, BACKGROUND_COLOR);
		drawBorder(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, BORDER_COLOR);

		Component title = Component.literal("Recording · " + recording.wynnClass().displayName);
		int titleX = (this.width - this.font.width(title)) / 2;
		graphics.text(this.font, title, titleX, panelY + 10, TITLE_COLOR, false);

		// Stats strip below the label EditBox (which we leave the widget to draw).
		int statsY = panelY + 52;
		String stats = String.format(
			"%s · %s · %d casts · %s dmg · avg %s/s · peak %s/s @ %ds",
			formatStartedAt(recording.startedAtMs),
			formatDuration(recording.durationMs),
			recording.casts == null ? 0 : recording.casts.size(),
			formatNumber(recording.totalDamage),
			formatNumber(recording.avgDps),
			formatNumber(recording.peakDps),
			recording.peakDpsSecond
		);
		graphics.text(this.font, Component.literal(stats), contentX, statsY, LABEL_COLOR, false);

		// DPS graph.
		int graphX = contentX;
		int graphY = statsY + 16;
		int graphW = contentW;
		drawGraph(graphics, graphX, graphY, graphW, GRAPH_H);

		// Cast list heading + rows.
		int castsY = graphY + GRAPH_H + 12;
		graphics.text(this.font, Component.literal("Casts"), contentX, castsY, LABEL_COLOR, false);

		int castsListY = castsY + 12;
		int castsListH = panelY + PANEL_HEIGHT - 40 - castsListY;
		drawCastList(graphics, contentX, castsListY, contentW, castsListH);

		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void drawGraph(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, GRAPH_BG_COLOR);
		// Axis lines.
		g.fill(x, y + h - 1, x + w, y + h, GRAPH_AXIS_COLOR);
		g.fill(x, y, x + 1, y + h, GRAPH_AXIS_COLOR);

		List<Long> samples = recording.secondDps;
		if (samples == null || samples.isEmpty()) {
			Component msg = Component.literal("(no damage recorded)");
			int mw = this.font.width(msg);
			g.text(this.font, msg, x + (w - mw) / 2, y + h / 2 - 4, LABEL_COLOR, false);
			return;
		}

		long peak = Math.max(1L, recording.peakDps);
		int innerW = w - 2;
		int innerH = h - 2;
		int innerX = x + 1;
		int innerY = y + 1;

		// Column per sample, with at least 1px width.
		int n = samples.size();
		float colW = innerW / (float) n;
		for (int i = 0; i < n; i++) {
			long v = samples.get(i);
			if (v <= 0) continue;
			int barH = (int) Math.max(1, Math.round((v / (double) peak) * innerH));
			int x0 = innerX + Math.round(i * colW);
			int x1 = innerX + Math.max(x0 + 1, Math.round((i + 1) * colW));
			int y0 = innerY + innerH - barH;
			int y1 = innerY + innerH;
			int color = i == recording.peakDpsSecond ? GRAPH_PEAK_COLOR : GRAPH_BAR_COLOR;
			g.fill(x0, y0, x1, y1, color);
		}

		// Average line.
		if (recording.avgDps > 0 && recording.avgDps <= peak) {
			int avgY = innerY + innerH - (int) Math.round((recording.avgDps / (double) peak) * innerH);
			// Dashed: fill 2px, skip 2px to fake a dashed line on the discrete grid.
			for (int px = innerX; px < innerX + innerW; px += 4) {
				g.fill(px, avgY, Math.min(innerX + innerW, px + 2), avgY + 1, GRAPH_AVG_COLOR);
			}
		}
	}

	private void drawCastList(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, 0xFF151515);

		List<DpsRecording.CastEntry> casts = recording.casts;
		if (casts == null || casts.isEmpty()) {
			Component msg = Component.literal("(no casts recorded)");
			g.text(this.font, msg, x + 6, y + h / 2 - 4, LABEL_COLOR, false);
			return;
		}

		int innerPad = 4;
		int rowY = y + innerPad - castScroll;
		int maxY = y + h - innerPad;

		for (int i = 0; i < casts.size(); i++) {
			if (rowY + CAST_LIST_ROW_H < y) {
				rowY += CAST_LIST_ROW_H;
				continue;
			}
			if (rowY > maxY) break;

			DpsRecording.CastEntry c = casts.get(i);
			String line = String.format(
				"%s  %s%s%s",
				formatOffset(c.offsetMs),
				c.name == null ? "?" : c.name,
				c.mana > 0 ? "  -" + c.mana + " mana" : "",
				c.hp > 0 ? "  -" + c.hp + " hp" : ""
			);
			g.text(this.font, Component.literal(line), x + innerPad + 4, rowY, TITLE_COLOR, false);
			rowY += CAST_LIST_ROW_H;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;
		int padding = 12;
		int contentX = panelX + padding;
		int contentW = PANEL_WIDTH - padding * 2;
		int statsY = panelY + 52;
		int graphY = statsY + 16;
		int castsListY = graphY + GRAPH_H + 12 + 12;
		int castsListH = panelY + PANEL_HEIGHT - 40 - castsListY;

		boolean insideCasts = mouseX >= contentX && mouseX <= contentX + contentW
			&& mouseY >= castsListY && mouseY <= castsListY + castsListH;
		if (!insideCasts) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

		int rows = recording.casts == null ? 0 : recording.casts.size();
		int contentHeight = rows * CAST_LIST_ROW_H;
		int maxScroll = Math.max(0, contentHeight - castsListH + 8);
		int delta = (int) (-scrollY * CAST_LIST_ROW_H);
		this.castScroll = Math.max(0, Math.min(maxScroll, this.castScroll + delta));
		return true;
	}

	private static String formatStartedAt(long epochMs) {
		return ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
			.format(STARTED_FMT);
	}

	private static String formatDuration(long ms) {
		long totalSec = ms / 1000L;
		long m = totalSec / 60L;
		long s = totalSec % 60L;
		if (m == 0) return s + "s";
		return m + "m" + String.format("%02d", s) + "s";
	}

	private static String formatOffset(long offsetMs) {
		long totalSec = offsetMs / 1000L;
		long m = totalSec / 60L;
		long s = totalSec % 60L;
		long milli = offsetMs % 1000L;
		return String.format("%02d:%02d.%03d", m, s, milli);
	}

	private static String formatNumber(long v) {
		if (v < 10_000) return Long.toString(v);
		if (v < 1_000_000) return String.format("%.1fk", v / 1_000.0);
		if (v < 1_000_000_000) return String.format("%.1fm", v / 1_000_000.0);
		return String.format("%.1fb", v / 1_000_000_000.0);
	}

	private static void drawBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	@Override
	public void onClose() {
		// Persist any label edits implicitly on close; user doesn't have to
		// hit Save for pure rename flow to feel natural.
		DpsRecorder.get().persist(recording);
		if (this.minecraft != null) {
			this.minecraft.setScreen(parent);
		} else {
			super.onClose();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
