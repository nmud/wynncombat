package io.github.nmud.wynncombat.client.recorder;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Bottom-right HUD indicator shown only while a recording is active. Renders
 * a blinking red "● DPS Recording MM:SS" pill so the user has a clear visual
 * cue that their keypress actually started a session - the overlay is the
 * only feedback when the recorder is toggled via keybind from outside any
 * settings screen.
 *
 * <p>Whole-text blink (not just the dot) because at small HUD scale a 1-pixel
 * dot toggling on and off is very easy to miss in a busy combat scene.
 */
public final class DpsRecorderOverlay implements HudElement {
	private static final int BG_COLOR = 0x80000000;
	private static final int TEXT_COLOR_ON = 0xFFFF3030;
	private static final int TEXT_COLOR_OFF = 0xFF8A1A1A;
	private static final int DOT_COLOR_ON = 0xFFFF3030;

	private static final int MARGIN_X = 4;
	private static final int MARGIN_Y = 4;
	private static final int PAD_X = 5;
	private static final int PAD_Y = 3;
	private static final int DOT_W = 5;
	private static final int DOT_GAP = 4;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		DpsRecorder rec = DpsRecorder.get();
		if (!rec.isRecording()) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui) return;
		Font font = mc.font;

		long elapsed = rec.elapsedMs();
		String time = formatMmSs(elapsed);
		String full = "DPS Recording " + time;
		int textW = font.width(full);

		int contentW = DOT_W + DOT_GAP + textW;
		int boxW = contentW + PAD_X * 2;
		int boxH = font.lineHeight + PAD_Y * 2;

		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();
		int boxX0 = screenW - MARGIN_X - boxW;
		int boxY0 = screenH - MARGIN_Y - boxH;

		graphics.fill(boxX0, boxY0, boxX0 + boxW, boxY0 + boxH, BG_COLOR);

		// ~1 Hz blink. Both the dot and the text dim together so the cue is
		// hard to miss; the dim phase still keeps the text readable.
		boolean on = ((elapsed / 500L) & 1L) == 0L;
		int textColor = on ? TEXT_COLOR_ON : TEXT_COLOR_OFF;

		if (on) {
			int dotX = boxX0 + PAD_X;
			int dotY = boxY0 + PAD_Y + (font.lineHeight - DOT_W) / 2;
			graphics.fill(dotX, dotY, dotX + DOT_W, dotY + DOT_W, DOT_COLOR_ON);
		}

		int textX = boxX0 + PAD_X + DOT_W + DOT_GAP;
		int textY = boxY0 + PAD_Y;
		graphics.text(font, Component.literal(full), textX, textY, textColor, true);
	}

	private static String formatMmSs(long ms) {
		long totalSec = ms / 1000L;
		long m = totalSec / 60L;
		long s = totalSec % 60L;
		return String.format("%02d:%02d", m, s);
	}
}
