package io.github.nmud.wynncombat.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Minimal stub screen used for WynnCombat modules that aren't implemented
 * yet. Shows the module name and a "Coming Soon" message, with a Back button
 * that returns to {@link WynnCombatScreen}.
 */
public class PlaceholderScreen extends Screen {
	private static final int PANEL_WIDTH = 240;
	private static final int PANEL_HEIGHT = 120;
	private static final int BACKGROUND_COLOR = 0xCC101010;
	private static final int BORDER_COLOR = 0xFFFFFFFF;
	private static final int TITLE_COLOR = 0xFFFFFFFF;
	private static final int LABEL_COLOR = 0xFFAAAAAA;

	private final Screen parent;
	private final String moduleName;

	public PlaceholderScreen(Screen parent, String moduleName) {
		super(Component.literal(moduleName));
		this.parent = parent;
		this.moduleName = moduleName;
	}

	@Override
	protected void init() {
		super.init();

		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;

		int btnW = 120;
		int btnH = 20;
		int btnX = panelX + (PANEL_WIDTH - btnW) / 2;
		int btnY = panelY + PANEL_HEIGHT - btnH - 12;

		this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
			.bounds(btnX, btnY, btnW, btnH)
			.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;

		graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, BACKGROUND_COLOR);
		drawBorder(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, BORDER_COLOR);

		Component title = Component.literal(moduleName);
		int titleX = (this.width - this.font.width(title)) / 2;
		graphics.text(this.font, title, titleX, panelY + 14, TITLE_COLOR, false);

		Component msg = Component.literal("Coming Soon");
		int msgX = (this.width - this.font.width(msg)) / 2;
		graphics.text(this.font, msg, msgX, panelY + 48, LABEL_COLOR, false);

		super.extractRenderState(graphics, mouseX, mouseY, delta);
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
