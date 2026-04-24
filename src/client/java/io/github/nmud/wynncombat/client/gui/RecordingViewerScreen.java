package io.github.nmud.wynncombat.client.gui;

import io.github.nmud.wynncombat.client.recorder.DpsRecorder;
import io.github.nmud.wynncombat.client.recorder.DpsRecording;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Read-only viewer for one {@link DpsRecording}, presented as a single
 * combined timeline (think of a video editor's clip strip):
 *
 * <ul>
 *   <li>The DPS-per-second graph is drawn in the background as columns
 *       scaled to {@link DpsRecording#peakDps}, with the session-average
 *       overlaid as a horizontal dashed line and the peak column tinted.</li>
 *   <li>Each individual cast is a small node in a strip above the bars.
 *       Hovering the node pops a tooltip with the spell name, exact
 *       offset, and mana / hp cost.</li>
 *   <li>Hovering anywhere on the timeline (even between casts) draws a
 *       cyan playhead and a tooltip showing the time + DPS at that
 *       moment.</li>
 *   <li>Mouse-wheel zooms anchored to the cursor, click-drag pans, and
 *       <b>Fit</b> resets to a whole-recording view. A header strip shows
 *       the current view range.</li>
 * </ul>
 *
 * <p>The cast list is intentionally gone - the timeline IS the cast list
 * now. If we ever bring it back it should hang as an optional drawer below
 * the timeline rather than competing for vertical space by default.
 */
public class RecordingViewerScreen extends Screen {
	private static final int PANEL_WIDTH = 600;
	private static final int PANEL_HEIGHT = 380;
	private static final int BACKGROUND_COLOR = 0xCC101010;
	private static final int BORDER_COLOR = 0xFFFFFFFF;
	private static final int TITLE_COLOR = 0xFFFFFFFF;
	private static final int LABEL_COLOR = 0xFFAAAAAA;
	private static final int MUTED_COLOR = 0xFF808080;

	private static final int TIMELINE_BG_COLOR = 0xFF181818;
	private static final int TIMELINE_BORDER_COLOR = 0xFF303030;
	private static final int TIMELINE_NODE_STRIP_COLOR = 0xFF222222;
	private static final int TIMELINE_AXIS_STRIP_COLOR = 0xFF181818;

	private static final int BAR_COLOR = 0xFF5AA9FF;
	private static final int BAR_PEAK_COLOR = 0xFFFF6060;
	private static final int AVG_LINE_COLOR = 0xFFFFD060;
	private static final int NODE_COLOR = 0xFFE0E0E0;
	private static final int NODE_HOVER_COLOR = 0xFF60FFAA;
	private static final int PLAYHEAD_COLOR = 0xFF60D0FF;
	private static final int PLAYHEAD_HALO_COLOR = 0x4060D0FF;
	private static final int TICK_COLOR = 0xFF606060;

	private static final int NODE_STRIP_H = 14;
	private static final int AXIS_STRIP_H = 14;
	private static final int NODE_HALF_W = 3;
	private static final int NODE_HALF_H = 4;
	private static final int NODE_HIT_RADIUS = 5;

	private static final DateTimeFormatter STARTED_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final Screen parent;
	private final DpsRecording recording;

	private boolean confirmingDelete = false;

	// Timeline view state. Persisted across re-init() within one screen
	// instance so opening sub-buttons (Save, Delete) doesn't reset the
	// user's zoom/pan.
	private double pixelsPerSecond = -1.0;
	private double scrollOffsetSec = 0.0;
	private boolean dragging = false;
	private double dragStartX = 0.0;
	private double dragStartScroll = 0.0;

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

		// Header row: editable label on the left, Save on the right.
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

		// Bottom button row: zoom controls on the left, Delete + Back on the right.
		int bottomY = panelY + PANEL_HEIGHT - 28;

		int zBtnW = 26;
		int gap = 4;
		this.addRenderableWidget(Button.builder(Component.literal("-"), b -> zoomBy(0.5))
			.bounds(contentX, bottomY, zBtnW, 20)
			.build());
		this.addRenderableWidget(Button.builder(Component.literal("+"), b -> zoomBy(2.0))
			.bounds(contentX + zBtnW + gap, bottomY, zBtnW, 20)
			.build());
		this.addRenderableWidget(Button.builder(Component.literal("Fit"), b -> resetView())
			.bounds(contentX + (zBtnW + gap) * 2, bottomY, 36, 20)
			.build());

		int backW = 60;
		int delW = 70;
		if (confirmingDelete) {
			int confW = 200;
			int cancelW = 60;
			this.addRenderableWidget(Button.builder(Component.literal("Confirm Delete"), b -> {
					DpsRecorder.get().delete(recording);
					if (this.minecraft != null) this.minecraft.setScreen(parent);
				})
				.bounds(panelX + PANEL_WIDTH - padding - backW - gap - cancelW - gap - confW, bottomY, confW, 20)
				.build());
			this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
					confirmingDelete = false;
					rebuild();
				})
				.bounds(panelX + PANEL_WIDTH - padding - backW - gap - cancelW, bottomY, cancelW, 20)
				.build());
		} else {
			this.addRenderableWidget(Button.builder(Component.literal("Delete"), b -> {
					confirmingDelete = true;
					rebuild();
				})
				.bounds(panelX + PANEL_WIDTH - padding - backW - gap - delW, bottomY, delW, 20)
				.build());
		}

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

		// Timeline rect.
		int timelineX = contentX;
		int timelineY = statsY + 18;
		int timelineW = contentW;
		int timelineH = panelY + PANEL_HEIGHT - 40 - timelineY;

		ensureZoomInitialized(timelineW);
		clampZoom(timelineW);

		drawTimelineHeader(graphics, timelineX, timelineY - 12, timelineW);
		drawTimeline(graphics, timelineX, timelineY, timelineW, timelineH, mouseX, mouseY);

		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void drawTimelineHeader(GuiGraphicsExtractor g, int x, int y, int w) {
		double durSec = recording.durationMs / 1000.0;
		double viewSec = w / pixelsPerSecond;
		double leftSec = scrollOffsetSec;
		double rightSec = Math.min(durSec, leftSec + viewSec);
		String header = String.format("Timeline · %.1fx · view %s → %s of %s · scroll-drag to pan, wheel to zoom",
			pixelsPerSecond / Math.max(0.0001, fitPps(w)),
			formatTime(leftSec),
			formatTime(rightSec),
			formatTime(durSec));
		g.text(this.font, Component.literal(header), x, y, MUTED_COLOR, false);
	}

	private void drawTimeline(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
		// Outer chrome.
		g.fill(x, y, x + w, y + h, TIMELINE_BG_COLOR);
		drawBorder(g, x, y, w, h, TIMELINE_BORDER_COLOR);

		// Stripe layout: top = node strip, middle = bars, bottom = axis.
		int innerX = x + 1;
		int innerW = w - 2;
		int innerY = y + 1;
		int innerH = h - 2;

		int nodeStripY = innerY;
		int barsY = nodeStripY + NODE_STRIP_H;
		int barsH = innerH - NODE_STRIP_H - AXIS_STRIP_H;
		int axisY = barsY + barsH;

		g.fill(innerX, nodeStripY, innerX + innerW, nodeStripY + NODE_STRIP_H, TIMELINE_NODE_STRIP_COLOR);
		g.fill(innerX, axisY, innerX + innerW, axisY + AXIS_STRIP_H, TIMELINE_AXIS_STRIP_COLOR);

		drawBars(g, innerX, barsY, innerW, barsH);
		drawAvgLine(g, innerX, barsY, innerW, barsH);
		int hoveredNode = drawNodes(g, innerX, nodeStripY, innerW, mouseX, mouseY);
		drawAxis(g, innerX, axisY, innerW);

		// Hover playhead + tooltip.
		boolean insideTimeline = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
		if (insideTimeline) {
			drawPlayhead(g, mouseX, innerY, innerH);
			drawHoverTooltip(g, mouseX, mouseY, hoveredNode, innerX);
		}
	}

	private void drawBars(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		List<Long> samples = recording.secondDps;
		if (samples == null || samples.isEmpty()) {
			Component msg = Component.literal("(no damage recorded)");
			int mw = this.font.width(msg);
			g.text(this.font, msg, x + (w - mw) / 2, y + h / 2 - 4, LABEL_COLOR, false);
			return;
		}
		long peak = Math.max(1L, recording.peakDps);
		int n = samples.size();

		int firstSec = Math.max(0, (int) Math.floor(scrollOffsetSec));
		int lastSec = Math.min(n - 1, (int) Math.ceil(scrollOffsetSec + w / pixelsPerSecond) + 1);

		for (int i = firstSec; i <= lastSec; i++) {
			long v = samples.get(i);
			if (v <= 0) continue;
			int x0 = (int) Math.round(x + (i - scrollOffsetSec) * pixelsPerSecond);
			int x1 = (int) Math.round(x + (i + 1 - scrollOffsetSec) * pixelsPerSecond);
			if (x1 <= x0) x1 = x0 + 1;
			if (x1 < x) continue;
			if (x0 > x + w) break;
			x0 = Math.max(x, x0);
			x1 = Math.min(x + w, x1);
			int barH = (int) Math.max(1, Math.round((v / (double) peak) * h));
			int y0 = y + h - barH;
			int color = i == recording.peakDpsSecond ? BAR_PEAK_COLOR : BAR_COLOR;
			g.fill(x0, y0, x1, y + h, color);
		}
	}

	private void drawAvgLine(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		long peak = Math.max(1L, recording.peakDps);
		if (recording.avgDps <= 0 || recording.avgDps > peak) return;
		int avgY = y + h - (int) Math.round((recording.avgDps / (double) peak) * h);
		// Dashed line: 3px on, 3px off.
		for (int px = x; px < x + w; px += 6) {
			g.fill(px, avgY, Math.min(x + w, px + 3), avgY + 1, AVG_LINE_COLOR);
		}
	}

	private int drawNodes(GuiGraphicsExtractor g, int x, int stripY, int w, int mouseX, int mouseY) {
		List<DpsRecording.CastEntry> casts = recording.casts;
		if (casts == null || casts.isEmpty()) return -1;

		int centerY = stripY + NODE_STRIP_H / 2;

		// Pick the closest node to the cursor - within hit radius and
		// roughly in the node strip vertically. We scan all casts so very
		// dense, overlapping nodes still pick the nearest unambiguously.
		int hovered = -1;
		int hoveredDist = Integer.MAX_VALUE;
		boolean cursorInStrip = Math.abs(mouseY - centerY) <= NODE_HIT_RADIUS;

		double leftSec = scrollOffsetSec;
		double rightSec = leftSec + w / pixelsPerSecond;

		for (int i = 0; i < casts.size(); i++) {
			DpsRecording.CastEntry c = casts.get(i);
			double sec = c.offsetMs / 1000.0;
			if (sec < leftSec - 1.0 || sec > rightSec + 1.0) continue;
			int nx = (int) Math.round(x + (sec - leftSec) * pixelsPerSecond);
			if (nx < x - NODE_HALF_W || nx > x + w + NODE_HALF_W) continue;

			if (cursorInStrip) {
				int d = Math.abs(mouseX - nx);
				if (d < NODE_HIT_RADIUS && d < hoveredDist) {
					hovered = i;
					hoveredDist = d;
				}
			}
		}

		// Render nodes (hovered last so it paints on top).
		for (int i = 0; i < casts.size(); i++) {
			if (i == hovered) continue;
			DpsRecording.CastEntry c = casts.get(i);
			double sec = c.offsetMs / 1000.0;
			if (sec < leftSec - 1.0 || sec > rightSec + 1.0) continue;
			int nx = (int) Math.round(x + (sec - leftSec) * pixelsPerSecond);
			drawNode(g, nx, centerY, NODE_COLOR);
		}
		if (hovered >= 0) {
			DpsRecording.CastEntry c = casts.get(hovered);
			double sec = c.offsetMs / 1000.0;
			int nx = (int) Math.round(x + (sec - leftSec) * pixelsPerSecond);
			drawNode(g, nx, centerY, NODE_HOVER_COLOR);
		}

		return hovered;
	}

	private static void drawNode(GuiGraphicsExtractor g, int cx, int cy, int color) {
		// Diamond-ish: small filled square. Cheap and reads clearly at any
		// zoom; circles aren't worth the per-pixel cost for tens of nodes.
		g.fill(cx - NODE_HALF_W, cy - NODE_HALF_H, cx + NODE_HALF_W + 1, cy + NODE_HALF_H + 1, color);
	}

	private void drawAxis(GuiGraphicsExtractor g, int x, int y, int w) {
		double durSec = recording.durationMs / 1000.0;
		if (durSec <= 0) return;

		double tickEverySec = chooseTickInterval(pixelsPerSecond);
		double leftSec = scrollOffsetSec;
		double rightSec = leftSec + w / pixelsPerSecond;

		int firstTickIdx = (int) Math.ceil(leftSec / tickEverySec);
		int lastTickIdx = (int) Math.floor(rightSec / tickEverySec);

		for (int t = firstTickIdx; t <= lastTickIdx; t++) {
			double sec = t * tickEverySec;
			int tx = (int) Math.round(x + (sec - leftSec) * pixelsPerSecond);
			if (tx < x || tx > x + w) continue;
			g.fill(tx, y, tx + 1, y + 4, TICK_COLOR);
			String lbl = formatTime(sec);
			int lblX = tx - this.font.width(lbl) / 2;
			g.text(this.font, Component.literal(lbl), Math.max(x + 1, Math.min(x + w - this.font.width(lbl) - 1, lblX)),
				y + 4, MUTED_COLOR, false);
		}
	}

	private static double chooseTickInterval(double pps) {
		// Aim for ~80px between major ticks. Snap to a "nice" interval so
		// labels stay legible (no "0:13.7s" noise).
		double targetPx = 80.0;
		double rawSec = targetPx / pps;
		double[] choices = {0.5, 1, 2, 5, 10, 15, 30, 60, 120, 300, 600};
		for (double c : choices) {
			if (c >= rawSec) return c;
		}
		return 600;
	}

	private void drawPlayhead(GuiGraphicsExtractor g, int mouseX, int innerY, int innerH) {
		g.fill(mouseX - 1, innerY, mouseX, innerY + innerH, PLAYHEAD_HALO_COLOR);
		g.fill(mouseX, innerY, mouseX + 1, innerY + innerH, PLAYHEAD_COLOR);
	}

	private void drawHoverTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY, int hoveredNode, int innerX) {
		double sec = scrollOffsetSec + (mouseX - innerX) / pixelsPerSecond;
		double durSec = recording.durationMs / 1000.0;
		if (sec < 0 || sec > durSec) return;

		int secIdx = (int) Math.floor(sec);
		long dpsAtMoment = 0L;
		if (recording.secondDps != null && secIdx >= 0 && secIdx < recording.secondDps.size()) {
			dpsAtMoment = recording.secondDps.get(secIdx);
		}

		List<String> lines = new java.util.ArrayList<>();
		lines.add(formatTimeMs(sec) + "  ·  " + formatNumber(dpsAtMoment) + " DPS");
		if (hoveredNode >= 0) {
			DpsRecording.CastEntry c = recording.casts.get(hoveredNode);
			lines.add("──────────────");
			lines.add(c.name == null ? "?" : c.name);
			StringBuilder costs = new StringBuilder();
			if (c.mana > 0) costs.append("-").append(c.mana).append(" mana");
			if (c.hp > 0) {
				if (!costs.isEmpty()) costs.append("  ");
				costs.append("-").append(c.hp).append(" hp");
			}
			if (!costs.isEmpty()) lines.add(costs.toString());
		}

		int padX = 4;
		int padY = 3;
		int lineH = this.font.lineHeight + 1;
		int textH = lines.size() * lineH - 1;
		int textW = 0;
		for (String s : lines) textW = Math.max(textW, this.font.width(s));

		int boxW = textW + padX * 2;
		int boxH = textH + padY * 2;
		int boxX = mouseX + 8;
		int boxY = mouseY + 8;
		// Flip to the left of the cursor if we'd run off the right edge.
		if (boxX + boxW > this.width - 4) boxX = mouseX - 8 - boxW;
		// Flip above the cursor if we'd run off the bottom.
		if (boxY + boxH > this.height - 4) boxY = mouseY - 8 - boxH;
		boxX = Math.max(2, boxX);
		boxY = Math.max(2, boxY);

		g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xE8000000);
		drawBorder(g, boxX, boxY, boxW, boxH, 0xFF606060);
		int ty = boxY + padY;
		for (String s : lines) {
			g.text(this.font, Component.literal(s), boxX + padX, ty, TITLE_COLOR, false);
			ty += lineH;
		}
	}

	// ----------------------------------------------------------- zoom / pan --

	private void ensureZoomInitialized(int timelineW) {
		if (pixelsPerSecond <= 0) {
			pixelsPerSecond = fitPps(timelineW);
			scrollOffsetSec = 0.0;
		}
	}

	private double fitPps(int timelineW) {
		double durSec = Math.max(1.0, recording.durationMs / 1000.0);
		return Math.max(0.0001, timelineW / durSec);
	}

	private void clampZoom(int timelineW) {
		double minPps = fitPps(timelineW);
		double maxPps = Math.max(minPps, 200.0);
		if (pixelsPerSecond < minPps) pixelsPerSecond = minPps;
		if (pixelsPerSecond > maxPps) pixelsPerSecond = maxPps;
		clampScroll(timelineW);
	}

	private void clampScroll(int timelineW) {
		double durSec = recording.durationMs / 1000.0;
		double viewSec = timelineW / pixelsPerSecond;
		double maxOffset = Math.max(0.0, durSec - viewSec);
		if (scrollOffsetSec < 0.0) scrollOffsetSec = 0.0;
		if (scrollOffsetSec > maxOffset) scrollOffsetSec = maxOffset;
	}

	private void zoomBy(double factor) {
		// Anchor zoom to timeline center for keyboard / button driven zoom.
		Rect t = currentTimelineRect();
		if (t == null) return;
		double anchorSec = scrollOffsetSec + (t.w / 2.0) / pixelsPerSecond;
		pixelsPerSecond *= factor;
		clampZoom(t.w);
		scrollOffsetSec = anchorSec - (t.w / 2.0) / pixelsPerSecond;
		clampScroll(t.w);
	}

	private void resetView() {
		Rect t = currentTimelineRect();
		if (t == null) return;
		pixelsPerSecond = fitPps(t.w);
		scrollOffsetSec = 0.0;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent ev, boolean doubleClick) {
		if (ev.button() == 0) {
			Rect t = currentTimelineRect();
			if (t != null && t.contains(ev.x(), ev.y())) {
				dragging = true;
				dragStartX = ev.x();
				dragStartScroll = scrollOffsetSec;
				return true;
			}
		}
		return super.mouseClicked(ev, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent ev) {
		if (ev.button() == 0 && dragging) {
			dragging = false;
			return true;
		}
		return super.mouseReleased(ev);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent ev, double dragX, double dragY) {
		if (dragging) {
			Rect t = currentTimelineRect();
			if (t != null) {
				double pixelDelta = ev.x() - dragStartX;
				scrollOffsetSec = dragStartScroll - pixelDelta / pixelsPerSecond;
				clampScroll(t.w);
			}
			return true;
		}
		return super.mouseDragged(ev, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		Rect t = currentTimelineRect();
		if (t != null && t.contains(mouseX, mouseY)) {
			double anchorSec = scrollOffsetSec + (mouseX - t.x) / pixelsPerSecond;
			double factor = scrollY > 0 ? 1.25 : 0.8;
			pixelsPerSecond *= factor;
			clampZoom(t.w);
			scrollOffsetSec = anchorSec - (mouseX - t.x) / pixelsPerSecond;
			clampScroll(t.w);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private Rect currentTimelineRect() {
		if (this.width <= 0 || this.height <= 0) return null;
		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;
		int padding = 12;
		int contentX = panelX + padding;
		int contentW = PANEL_WIDTH - padding * 2;
		int statsY = panelY + 52;
		int timelineY = statsY + 18;
		int timelineH = panelY + PANEL_HEIGHT - 40 - timelineY;
		return new Rect(contentX, timelineY, contentW, timelineH);
	}

	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx <= x + w && my >= y && my <= y + h;
		}
	}

	// ---------------------------------------------------------- formatters --

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

	private static String formatTime(double sec) {
		long total = (long) Math.floor(sec);
		long m = total / 60L;
		long s = total % 60L;
		return String.format("%d:%02d", m, s);
	}

	private static String formatTimeMs(double sec) {
		long totalMs = Math.round(sec * 1000.0);
		long m = totalMs / 60000L;
		long s = (totalMs / 1000L) % 60L;
		long ms = totalMs % 1000L;
		return String.format("%d:%02d.%03d", m, s, ms);
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
		// Persist label edits implicitly on close so a pure rename flow
		// doesn't require the user to remember the Save button.
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
