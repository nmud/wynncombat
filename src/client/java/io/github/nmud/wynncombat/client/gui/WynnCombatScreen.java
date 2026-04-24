package io.github.nmud.wynncombat.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Top-level WynnCombat menu. Each row is a module of the mod; click one to
 * jump into that module's settings screen. Modules navigate back here via
 * {@link Screen#onClose()} with this screen passed as their {@code parent}.
 */
public class WynnCombatScreen extends Screen {
	private static final int PANEL_WIDTH = 240;
	private static final int PANEL_HEIGHT = 190;
	private static final int BACKGROUND_COLOR = 0xCC101010;
	private static final int BORDER_COLOR = 0xFFFFFFFF;
	private static final int TITLE_COLOR = 0xFFFFFFFF;

	private static final int BTN_W = 200;
	private static final int BTN_H = 20;
	private static final int ROW_GAP = 6;

	public WynnCombatScreen() {
		super(Component.literal("WynnCombat"));
	}

	@Override
	protected void init() {
		super.init();

		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;

		int btnX = panelX + (PANEL_WIDTH - BTN_W) / 2;
		int contentY = panelY + 36;

		this.addRenderableWidget(Button.builder(Component.literal("Ability Log"), b -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(new AbilityLogScreen(this));
				}
			})
			.bounds(btnX, contentY, BTN_W, BTN_H)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("DPS Counter"), b -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(new DpsScreen(this));
				}
			})
			.bounds(btnX, contentY + BTN_H + ROW_GAP, BTN_W, BTN_H)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("DPS Recorder"), b -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(new DpsRecorderScreen(this));
				}
			})
			.bounds(btnX, contentY + (BTN_H + ROW_GAP) * 2, BTN_W, BTN_H)
			.build());

		int bottomY = panelY + PANEL_HEIGHT - BTN_H - 12;
		this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
			.bounds(btnX, bottomY, BTN_W, BTN_H)
			.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;

		graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, BACKGROUND_COLOR);
		drawBorder(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, BORDER_COLOR);

		Component title = Component.literal("WynnCombat");
		int titleX = (this.width - this.font.width(title)) / 2;
		graphics.text(this.font, title, titleX, panelY + 12, TITLE_COLOR, false);

		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private static void drawBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
