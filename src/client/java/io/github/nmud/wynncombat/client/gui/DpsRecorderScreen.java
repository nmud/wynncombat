package io.github.nmud.wynncombat.client.gui;

import io.github.nmud.wynncombat.client.recorder.DpsRecorder;
import io.github.nmud.wynncombat.client.recorder.DpsRecording;
import io.github.nmud.wynncombat.client.recorder.WynnClass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Entry screen for the DPS Recorder feature. Shows live recording status
 * up top (with a big Start/Stop button) and a scrollable list of saved
 * recordings below. Clicking a recording opens the
 * {@link RecordingViewerScreen}.
 *
 * <p>The screen rebuilds its widgets when the recording state flips
 * (start / stop) so the Start/Stop button label, status text, and list
 * contents stay current.
 */
public class DpsRecorderScreen extends Screen {
	private static final int PANEL_WIDTH = 440;
	private static final int PANEL_HEIGHT = 320;
	private static final int BACKGROUND_COLOR = 0xCC101010;
	private static final int BORDER_COLOR = 0xFFFFFFFF;
	private static final int TITLE_COLOR = 0xFFFFFFFF;
	private static final int LABEL_COLOR = 0xFFAAAAAA;
	private static final int STATUS_REC_COLOR = 0xFFFF6060;

	private static final int ROW_H = 20;
	private static final int ROW_GAP = 2;
	private static final int LIST_TOP_Y_OFFSET = 82;
	private static final int LIST_BOTTOM_MARGIN = 48;

	private static final DateTimeFormatter STARTED_FMT = DateTimeFormatter.ofPattern("MMM dd HH:mm");

	private final Screen parent;
	/** Tick of the last repaint-cycle recorder state so we rebuild on flip. */
	private boolean lastRecordingState = false;
	/** Vertical pixel scroll applied to the recordings list. */
	private int scrollY = 0;

	public DpsRecorderScreen(Screen parent) {
		super(Component.literal("DPS Recorder"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();
		lastRecordingState = DpsRecorder.get().isRecording();
		build();
	}

	private void build() {
		DpsRecorder recorder = DpsRecorder.get();

		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;

		int contentX = panelX + 12;
		int contentW = PANEL_WIDTH - 24;

		// Start / Stop button, centered.
		int actionW = 140;
		int actionX = panelX + (PANEL_WIDTH - actionW) / 2;
		int actionY = panelY + 52;
		String actionLabel = recorder.isRecording() ? "Stop Recording" : "Start Recording";
		this.addRenderableWidget(Button.builder(Component.literal(actionLabel), b -> {
				if (recorder.isRecording()) {
					recorder.stop();
				} else {
					recorder.start();
				}
				rebuild();
			})
			.bounds(actionX, actionY, actionW, 20)
			.build());

		// Saved-recordings rows.
		int listTop = panelY + LIST_TOP_Y_OFFSET;
		int listBottom = panelY + PANEL_HEIGHT - LIST_BOTTOM_MARGIN;
		List<DpsRecording> all = recorder.savedRecordings();
		int rowX = contentX;
		int rowW = contentW;
		int y = listTop - scrollY;
		for (int i = 0; i < all.size(); i++) {
			DpsRecording r = all.get(i);
			if (y + ROW_H < listTop) {
				y += ROW_H + ROW_GAP;
				continue;
			}
			if (y > listBottom) break;
			buildRow(r, rowX, y, rowW);
			y += ROW_H + ROW_GAP;
		}

		// Bottom action row.
		int bottomY = panelY + PANEL_HEIGHT - 28;
		this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
			.bounds(panelX + PANEL_WIDTH - 12 - 60, bottomY, 60, 20)
			.build());
	}

	private void buildRow(DpsRecording r, int rowX, int rowY, int rowW) {
		int delW = 22;
		int gap = 4;
		int textAreaW = rowW - delW - gap;

		// The button label itself is the row text. Clicking opens the viewer.
		this.addRenderableWidget(Button.builder(Component.literal(formatRow(r)), b -> openViewer(r))
			.bounds(rowX, rowY, textAreaW, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("X"), b -> {
				DpsRecorder.get().delete(r);
				rebuild();
			})
			.bounds(rowX + textAreaW + gap, rowY, delW, 20)
			.build());
	}

	private void openViewer(DpsRecording r) {
		if (this.minecraft == null) return;
		this.minecraft.setScreen(new RecordingViewerScreen(this, r));
	}

	private void rebuild() {
		this.clearWidgets();
		this.init();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// Rebuild if the recorder started/stopped externally (keybind) so
		// the button label flips without needing the user to close/reopen.
		DpsRecorder recorder = DpsRecorder.get();
		if (recorder.isRecording() != lastRecordingState) {
			lastRecordingState = recorder.isRecording();
			rebuild();
		}

		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;

		graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, BACKGROUND_COLOR);
		drawBorder(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, BORDER_COLOR);

		Component title = Component.literal("DPS Recorder");
		int titleX = (this.width - this.font.width(title)) / 2;
		graphics.text(this.font, title, titleX, panelY + 10, TITLE_COLOR, false);

		// Live status string above the Start/Stop button.
		Component status = statusComponent(recorder);
		int statusW = this.font.width(status);
		int statusX = (this.width - statusW) / 2;
		graphics.text(this.font, status, statusX, panelY + 30,
			recorder.isRecording() ? STATUS_REC_COLOR : LABEL_COLOR, false);

		// Column header.
		Component header = Component.literal("Class   Started         Label                  Dur    Avg      Peak");
		graphics.text(this.font, header, panelX + 14, panelY + LIST_TOP_Y_OFFSET - 12, LABEL_COLOR, false);

		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private static Component statusComponent(DpsRecorder recorder) {
		if (recorder.isRecording()) {
			long ms = recorder.elapsedMs();
			return Component.literal(String.format(
				"RECORDING   %s   %d casts   %s damage",
				formatDuration(ms), recorder.liveCastCount(), formatLargeNumber(recorder.liveTotalDamage())
			));
		}
		List<DpsRecording> all = recorder.savedRecordings();
		if (all.isEmpty()) return Component.literal("No saved recordings yet. Press Start to begin.");
		return Component.literal(all.size() + " saved recording" + (all.size() == 1 ? "" : "s"));
	}

	/**
	 * Layout: "[Class]  [Label trimmed to fit]  [Dur]  [Avg]  [Peak]".
	 * We precompute a monospace-ish layout by padding fields to fixed widths
	 * since this uses the default (proportional) font and we want columns
	 * to roughly line up under the header.
	 */
	private static String formatRow(DpsRecording r) {
		String cls = padRight(shortClass(r.wynnClass()), 8);
		String when = padRight(formatStartedAt(r.startedAtMs), 14);
		String label = padRight(truncate(r.label == null ? "Recording" : r.label, 18), 20);
		String dur = padRight(formatDuration(r.durationMs), 7);
		String avg = padRight(formatLargeNumber(r.avgDps) + "/s", 9);
		String peak = formatLargeNumber(r.peakDps) + "/s";
		return cls + when + label + dur + avg + peak;
	}

	private static String shortClass(WynnClass c) {
		return switch (c) {
			case WARRIOR -> "War";
			case MAGE -> "Mag";
			case ARCHER -> "Arc";
			case ASSASSIN -> "Asn";
			case SHAMAN -> "Sha";
			case UNKNOWN -> "???";
		};
	}

	private static String formatStartedAt(long epochMs) {
		return ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
			.format(STARTED_FMT);
	}

	private static String formatDuration(long ms) {
		long totalSec = ms / 1000L;
		long m = totalSec / 60L;
		long s = totalSec % 60L;
		if (m >= 10) return m + "m";
		return String.format("%d:%02d", m, s);
	}

	private static String formatLargeNumber(long v) {
		if (v < 10_000) return Long.toString(v);
		if (v < 1_000_000) return String.format("%.1fk", v / 1_000.0);
		if (v < 1_000_000_000) return String.format("%.1fm", v / 1_000_000.0);
		return String.format("%.1fb", v / 1_000_000_000.0);
	}

	private static String truncate(String s, int max) {
		if (s == null) return "";
		if (s.length() <= max) return s;
		return s.substring(0, Math.max(0, max - 1)) + "…";
	}

	private static String padRight(String s, int width) {
		if (s.length() >= width) return s;
		StringBuilder sb = new StringBuilder(width);
		sb.append(s);
		while (sb.length() < width) sb.append(' ');
		return sb.toString();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;
		int listTop = panelY + LIST_TOP_Y_OFFSET;
		int listBottom = panelY + PANEL_HEIGHT - LIST_BOTTOM_MARGIN;
		if (mouseX < panelX || mouseX > panelX + PANEL_WIDTH || mouseY < listTop || mouseY > listBottom) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		int totalRows = DpsRecorder.get().savedRecordings().size();
		int contentHeight = totalRows * (ROW_H + ROW_GAP);
		int viewHeight = listBottom - listTop;
		int maxScroll = Math.max(0, contentHeight - viewHeight);
		int delta = (int) (-scrollY * (ROW_H + ROW_GAP));
		this.scrollY = Math.max(0, Math.min(maxScroll, this.scrollY + delta));
		rebuild();
		return true;
	}

	private static void drawBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	@Override
	public void onClose() {
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
