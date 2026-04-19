package io.github.nmud.wynncombat.client.combat;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * Bottom-right HUD element that prints recent spell casts in a chat-like
 * stack. Newest entry is at the bottom; entries fade out over the last second
 * of their lifetime.
 *
 * <p>Color convention:
 * <ul>
 *   <li>Spell name - white</li>
 *   <li>Mana cost  - aqua, with the Wynncraft mana icon ({@code \uE531})</li>
 *   <li>Health cost - red,  with the Wynncraft health icon ({@code \uE530})</li>
 * </ul>
 * The icons render correctly when Wynncraft's resource pack is loaded (which
 * it always is on the official server).
 */
public final class AbilityLogOverlay implements HudElement {
	private static final long ENTRY_LIFETIME_MS = 5000;
	private static final long FADE_MS = 800;

	private static final int RIGHT_MARGIN = 5;
	private static final int BOTTOM_MARGIN = 50;
	private static final int LINE_GAP = 1;
	private static final int BG_PAD_X = 3;
	private static final int BG_PAD_Y = 1;

	private static final String MANA_ICON = "\uE531";
	private static final String HEALTH_ICON = "\uE530";

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui) return;

		List<AbilityLogEntry> recent = AbilityLog.get().recent(ENTRY_LIFETIME_MS);
		if (recent.isEmpty()) return;

		Font font = mc.font;
		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();
		int lineH = font.lineHeight + LINE_GAP;
		long now = System.currentTimeMillis();

		// Newest at the bottom; iterate from newest (last) upward.
		int rowFromBottom = 0;
		for (int i = recent.size() - 1; i >= 0; i--) {
			AbilityLogEntry entry = recent.get(i);
			long age = now - entry.timestampMs();
			int alpha = computeAlpha(age);
			if (alpha <= 0) {
				rowFromBottom++;
				continue;
			}

			Component line = formatEntry(entry);
			int textW = font.width(line);
			int x = screenW - RIGHT_MARGIN - textW;
			int y = screenH - BOTTOM_MARGIN - lineH * rowFromBottom - font.lineHeight;

			int textColor = ((alpha & 0xFF) << 24) | 0x00FFFFFF;
			int bgColor = ((Math.min(alpha, 0xC0) & 0xFF) << 24);

			graphics.fill(
				x - BG_PAD_X,
				y - BG_PAD_Y,
				x + textW + BG_PAD_X,
				y + font.lineHeight + BG_PAD_Y,
				bgColor
			);
			graphics.text(font, line, x, y, textColor, false);

			rowFromBottom++;
		}
	}

	private static int computeAlpha(long age) {
		if (age <= ENTRY_LIFETIME_MS - FADE_MS) return 0xFF;
		if (age >= ENTRY_LIFETIME_MS) return 0;
		long fadeAge = age - (ENTRY_LIFETIME_MS - FADE_MS);
		double remaining = 1.0 - (double) fadeAge / FADE_MS;
		return (int) (0xFF * remaining);
	}

	private static Component formatEntry(AbilityLogEntry entry) {
		MutableComponent c = Component.literal(entry.spellName()).withStyle(ChatFormatting.WHITE);
		if (entry.manaCost() > 0) {
			c.append(Component.literal("  -" + entry.manaCost() + " " + MANA_ICON)
				.withStyle(ChatFormatting.AQUA));
		}
		if (entry.healthCost() > 0) {
			c.append(Component.literal("  -" + entry.healthCost() + " " + HEALTH_ICON)
				.withStyle(ChatFormatting.RED));
		}
		return c;
	}
}
