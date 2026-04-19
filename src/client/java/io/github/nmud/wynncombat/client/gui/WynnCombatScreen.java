package io.github.nmud.wynncombat.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WynnCombatScreen extends Screen {
	private static final int PANEL_WIDTH = 280;
	private static final int PANEL_HEIGHT = 180;
	private static final int BORDER_COLOR = 0xFFFFFFFF;
	private static final int BACKGROUND_COLOR = 0xCC101010;
	private static final int TITLE_COLOR = 0xFFFFFFFF;
	private static final int BODY_COLOR = 0xFFCCCCCC;

	public WynnCombatScreen() {
		super(Component.literal("WynnCombat"));
	}

	@Override
	protected void init() {
		super.init();

		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;

		int buttonWidth = 80;
		int buttonHeight = 20;
		int buttonX = panelX + (PANEL_WIDTH - buttonWidth) / 2;
		int buttonY = panelY + PANEL_HEIGHT - buttonHeight - 10;

		// TODO: Ensure this isn't effected by the semi transparent background
		this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
			.bounds(buttonX, buttonY, buttonWidth, buttonHeight)
			.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;

		graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, BACKGROUND_COLOR);
		graphics.outline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, BORDER_COLOR);

		Component title = Component.literal("WynnCombat");
		int titleX = (this.width - this.font.width(title)) / 2;
		graphics.text(this.font, title, titleX, panelY + 12, TITLE_COLOR, false);

		Component body = Component.literal("Welcome to WynnCombat.");
		int bodyX = (this.width - this.font.width(body)) / 2;
		int bodyY = (this.height - this.font.lineHeight) / 2;
		graphics.text(this.font, body, bodyX, bodyY, BODY_COLOR, false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
