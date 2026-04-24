package io.github.nmud.wynncombat.client.recorder;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Tiny top-center HUD indicator shown only while a recording is active. It
 * blinks a red dot next to a {@code REC MM:SS} readout so the user has a
 * clear visual cue that their keypress actually started a session (the
 * toggle keybind is unbound by default, but once rebound it fires without
 * any other feedback).
 *
 * <p>Deliberately tiny and fixed position: users don't need to reposition
 * this, and making it configurable would add surface area for a feature
 * that's effectively "is recording on or off".
 */
public final class DpsRecorderOverlay implements HudElement {
	private static final int BG_COLOR = 0x80000000;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int DOT_COLOR = 0xFFFF3030;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		DpsRecorder rec = DpsRecorder.get();
		if (!rec.isRecording()) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui) return;
		Font font = mc.font;

		long elapsed = rec.elapsedMs();
		String time = formatMmSs(elapsed);
		String full = "REC " + time;
		int textW = font.width(full);

		int padX = 5;
		int padY = 3;
		int dotW = 5;
		int dotGap = 4;
		int totalW = dotW + dotGap + textW;

		int screenW = graphics.guiWidth();
		int x = (screenW - totalW) / 2 - padX;
		int y = 2;
		int bgX0 = x;
		int bgY0 = y;
		int bgX1 = bgX0 + totalW + padX * 2;
		int bgY1 = bgY0 + font.lineHeight + padY * 2;

		graphics.fill(bgX0, bgY0, bgX1, bgY1, BG_COLOR);

		// Blink at ~1 Hz so it's obvious vs. a static marker.
		boolean showDot = ((elapsed / 500L) & 1L) == 0L;
		if (showDot) {
			int dotX = bgX0 + padX;
			int dotY = bgY0 + padY + (font.lineHeight - dotW) / 2;
			graphics.fill(dotX, dotY, dotX + dotW, dotY + dotW, DOT_COLOR);
		}

		int textX = bgX0 + padX + dotW + dotGap;
		int textY = bgY0 + padY;
		graphics.text(font, Component.literal(full), textX, textY, TEXT_COLOR, true);
	}

	private static String formatMmSs(long ms) {
		long totalSec = ms / 1000L;
		long m = totalSec / 60L;
		long s = totalSec % 60L;
		return String.format("%02d:%02d", m, s);
	}
}
