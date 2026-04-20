package io.github.nmud.wynncombat.client.gui;

import io.github.nmud.wynncombat.client.combat.AbilityLogOverlay;
import io.github.nmud.wynncombat.client.combat.OverlayConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Combined settings screen and live editor for the {@link AbilityLogOverlay}.
 *
 * <p>While this screen is open, {@link AbilityLogOverlay#setEditing(boolean)}
 * is on, so the overlay always renders with a visible border / sample
 * content. The user can:
 * <ul>
 *   <li>Drag the box anywhere on screen to move it.</li>
 *   <li>Drag the white square in the bottom-right corner to resize it.</li>
 *   <li>Use the controls in the central panel to change anchor, colors,
 *       background, lifetime, fade, and toggles for shadow / icons.</li>
 * </ul>
 * Changes are persisted to {@code config/wynncombat.json} on
 * {@link #onClose()}.
 */
public class WynnCombatScreen extends Screen {
	private static final int PANEL_WIDTH = 280;
	private static final int PANEL_HEIGHT = 220;
	private static final int BACKGROUND_COLOR = 0xCC101010;
	private static final int BORDER_COLOR = 0xFFFFFFFF;
	private static final int TITLE_COLOR = 0xFFFFFFFF;
	private static final int LABEL_COLOR = 0xFFAAAAAA;

	private static final int ROW_H = 22;
	private static final int BTN_W = 130;

	private boolean draggingMove = false;
	private boolean draggingResize = false;
	private int dragOffsetX = 0;
	private int dragOffsetY = 0;

	private Button anchorButton;
	private Button textColorButton;
	private Button backgroundButton;
	private Button shadowButton;
	private Button iconsButton;

	public WynnCombatScreen() {
		super(Component.literal("WynnCombat"));
	}

	@Override
	protected void init() {
		super.init();
		AbilityLogOverlay.setEditing(true);

		OverlayConfig cfg = OverlayConfig.get();

		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;

		int leftX = panelX + 12;
		int rightX = panelX + PANEL_WIDTH - BTN_W - 12;
		int contentY = panelY + 32;

		anchorButton = Button.builder(Component.literal("Anchor: " + cfg.anchor.name()), b -> {
				cfg.anchor = cfg.anchor.next();
				cfg.offsetX = 5;
				cfg.offsetY = 50;
				b.setMessage(Component.literal("Anchor: " + cfg.anchor.name()));
			})
			.bounds(rightX, contentY, BTN_W, 20)
			.build();
		this.addRenderableWidget(anchorButton);

		textColorButton = Button.builder(Component.literal("Text: " + cfg.textColor.label), b -> {
				cfg.textColor = cfg.textColor.next();
				b.setMessage(Component.literal("Text: " + cfg.textColor.label));
			})
			.bounds(rightX, contentY + ROW_H, BTN_W, 20)
			.build();
		this.addRenderableWidget(textColorButton);

		backgroundButton = Button.builder(Component.literal("BG: " + cfg.background.label), b -> {
				cfg.background = cfg.background.next();
				b.setMessage(Component.literal("BG: " + cfg.background.label));
			})
			.bounds(rightX, contentY + ROW_H * 2, BTN_W, 20)
			.build();
		this.addRenderableWidget(backgroundButton);

		shadowButton = Button.builder(Component.literal("Shadow: " + onOff(cfg.shadow)), b -> {
				cfg.shadow = !cfg.shadow;
				b.setMessage(Component.literal("Shadow: " + onOff(cfg.shadow)));
			})
			.bounds(rightX, contentY + ROW_H * 3, BTN_W, 20)
			.build();
		this.addRenderableWidget(shadowButton);

		iconsButton = Button.builder(Component.literal("Icons: " + onOff(cfg.showIcons)), b -> {
				cfg.showIcons = !cfg.showIcons;
				b.setMessage(Component.literal("Icons: " + onOff(cfg.showIcons)));
			})
			.bounds(rightX, contentY + ROW_H * 4, BTN_W, 20)
			.build();
		this.addRenderableWidget(iconsButton);

		this.addRenderableWidget(new SecondsSlider(
			leftX, contentY, BTN_W - 10, 20,
			"Lifetime", cfg.entryLifetimeMs, 1000, 30_000,
			v -> cfg.entryLifetimeMs = v
		));

		this.addRenderableWidget(new SecondsSlider(
			leftX, contentY + ROW_H, BTN_W - 10, 20,
			"Fade", cfg.fadeMs, 0, 5000,
			v -> cfg.fadeMs = v
		));

		int bottomY = panelY + PANEL_HEIGHT - 30;

		this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
				cfg.resetToDefaults();
				rebuild();
			})
			.bounds(leftX, bottomY, 60, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
			.bounds(panelX + PANEL_WIDTH - 70 - 12, bottomY, 70, 20)
			.build());
	}

	private static String onOff(boolean v) {
		return v ? "ON" : "OFF";
	}

	private void rebuild() {
		this.clearWidgets();
		this.init();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;

		graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, BACKGROUND_COLOR);
		drawBorder(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, BORDER_COLOR);

		Component title = Component.literal("Ability Log Settings");
		int titleX = (this.width - this.font.width(title)) / 2;
		graphics.text(this.font, title, titleX, panelY + 10, TITLE_COLOR, false);

		Component hint = Component.literal("Drag the cyan box to move. Drag corner to resize.");
		int hintX = (this.width - this.font.width(hint)) / 2;
		graphics.text(this.font, hint, hintX, panelY + PANEL_HEIGHT - 50, LABEL_COLOR, false);

		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private static void drawBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		if (event.button() != 0) return false;

		double mx = event.x();
		double my = event.y();
		OverlayConfig cfg = OverlayConfig.get();
		int boxX = AbilityLogOverlay.getBoxX();
		int boxY = AbilityLogOverlay.getBoxY();

		if (insideResizeHandle(mx, my, boxX, boxY, cfg)) {
			draggingResize = true;
			return true;
		}
		if (insideBox(mx, my, boxX, boxY, cfg)) {
			draggingMove = true;
			dragOffsetX = (int) (mx - boxX);
			dragOffsetY = (int) (my - boxY);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (super.mouseDragged(event, dx, dy)) return true;
		if (event.button() != 0) return false;

		OverlayConfig cfg = OverlayConfig.get();
		double mx = event.x();
		double my = event.y();
		int screenW = AbilityLogOverlay.getScreenW();
		int screenH = AbilityLogOverlay.getScreenH();
		if (screenW <= 0 || screenH <= 0) return false;

		if (draggingResize) {
			int boxX = AbilityLogOverlay.getBoxX();
			int boxY = AbilityLogOverlay.getBoxY();
			int newW = clamp((int) mx - boxX, 60, 600);
			int newH = clamp((int) my - boxY, 20, 400);
			if (cfg.anchor.right) {
				cfg.offsetX -= (newW - cfg.width);
			}
			if (cfg.anchor.bottom) {
				cfg.offsetY -= (newH - cfg.height);
			}
			cfg.width = newW;
			cfg.height = newH;
			return true;
		}

		if (draggingMove) {
			int newBoxX = clamp((int) (mx - dragOffsetX), 0, screenW - cfg.width);
			int newBoxY = clamp((int) (my - dragOffsetY), 0, screenH - cfg.height);
			cfg.offsetX = cfg.anchor.right ? screenW - cfg.width - newBoxX : newBoxX;
			cfg.offsetY = cfg.anchor.bottom ? screenH - cfg.height - newBoxY : newBoxY;
			return true;
		}

		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean wasDragging = draggingMove || draggingResize;
		draggingMove = false;
		draggingResize = false;
		if (super.mouseReleased(event)) return true;
		return wasDragging;
	}

	private static boolean insideBox(double mx, double my, int boxX, int boxY, OverlayConfig cfg) {
		return mx >= boxX && mx <= boxX + cfg.width
			&& my >= boxY && my <= boxY + cfg.height;
	}

	private static boolean insideResizeHandle(double mx, double my, int boxX, int boxY, OverlayConfig cfg) {
		int handle = AbilityLogOverlay.RESIZE_HANDLE;
		return mx >= boxX + cfg.width - handle && mx <= boxX + cfg.width
			&& my >= boxY + cfg.height - handle && my <= boxY + cfg.height;
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	@Override
	public void onClose() {
		AbilityLogOverlay.setEditing(false);
		OverlayConfig.save();
		super.onClose();
	}

	@Override
	public void removed() {
		AbilityLogOverlay.setEditing(false);
		OverlayConfig.save();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Slider that exposes a millisecond value as a "X.Xs" label. */
	private static final class SecondsSlider extends AbstractSliderButton {
		private final String label;
		private final long minMs;
		private final long maxMs;
		private final java.util.function.LongConsumer applier;

		SecondsSlider(int x, int y, int width, int height, String label,
		              long currentMs, long minMs, long maxMs,
		              java.util.function.LongConsumer applier) {
			super(x, y, width, height, Component.literal(label), normalize(currentMs, minMs, maxMs));
			this.label = label;
			this.minMs = minMs;
			this.maxMs = maxMs;
			this.applier = applier;
			updateMessage();
		}

		private static double normalize(long current, long min, long max) {
			if (max <= min) return 0.0;
			return Math.max(0.0, Math.min(1.0, (double) (current - min) / (max - min)));
		}

		@Override
		protected void updateMessage() {
			long ms = currentMs();
			setMessage(Component.literal(label + ": " + formatSeconds(ms)));
		}

		@Override
		protected void applyValue() {
			applier.accept(currentMs());
		}

		private long currentMs() {
			return Math.round(minMs + value * (maxMs - minMs));
		}

		private static String formatSeconds(long ms) {
			return String.format("%.1fs", ms / 1000.0);
		}
	}
}
