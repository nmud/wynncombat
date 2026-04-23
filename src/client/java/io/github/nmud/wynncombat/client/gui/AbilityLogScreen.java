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
 * Settings + position editor for the {@link AbilityLogOverlay}.
 *
 * <p>Two visual modes:
 * <ul>
 *   <li>{@link Mode#SETTINGS} - shows a tabbed options panel. The overlay
 *       still renders in "editing" mode so the user can see the box they're
 *       configuring, but it is <em>not</em> draggable/resizable here.</li>
 *   <li>{@link Mode#EDIT_POSITION} - hides the options panel entirely, lets
 *       the user drag/resize the overlay freely, and shows a single "Done"
 *       button in the bottom center to return to {@code SETTINGS}.</li>
 * </ul>
 *
 * <p>Options are grouped into {@link Tab tabs} so no single column grows
 * taller than the screen. Changes persist to {@code config/wynncombat.json}
 * on close.
 */
public class AbilityLogScreen extends Screen {
	public enum Mode { SETTINGS, EDIT_POSITION }

	/** Top-level tab selector inside {@link Mode#SETTINGS}. */
	public enum Tab {
		APPEARANCE("Appearance"),
		BEHAVIOR("Behavior");

		public final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private static final int PANEL_WIDTH = 300;
	private static final int PANEL_HEIGHT = 250;
	private static final int BACKGROUND_COLOR = 0xCC101010;
	private static final int BORDER_COLOR = 0xFFFFFFFF;
	private static final int TITLE_COLOR = 0xFFFFFFFF;
	private static final int LABEL_COLOR = 0xFFAAAAAA;
	private static final int TAB_SELECTED_COLOR = 0xFF3E5E9B;
	private static final int TAB_UNSELECTED_COLOR = 0xFF2A2A2A;

	private static final int ROW_H = 22;
	private static final int TITLE_Y = 10;
	private static final int TAB_ROW_Y = 26;
	private static final int TAB_H = 18;
	private static final int CONTENT_Y = 52;

	private final Screen parent;
	private Mode mode = Mode.SETTINGS;
	private Tab tab = Tab.APPEARANCE;

	private boolean draggingMove = false;
	private boolean draggingResize = false;
	private int dragOffsetX = 0;
	private int dragOffsetY = 0;

	public AbilityLogScreen(Screen parent) {
		super(Component.literal("Ability Log Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();
		AbilityLogOverlay.setEditing(true);

		if (mode == Mode.EDIT_POSITION) {
			buildEditPositionUi();
		} else {
			buildSettingsUi();
		}
	}

	private void buildSettingsUi() {
		OverlayConfig cfg = OverlayConfig.get();

		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;

		buildTabRow(panelX, panelY);

		int rowW = PANEL_WIDTH - 24;
		int rowX = panelX + 12;
		int y = panelY + CONTENT_Y;

		switch (tab) {
			case APPEARANCE -> buildAppearanceTab(cfg, rowX, y, rowW);
			case BEHAVIOR -> buildBehaviorTab(cfg, rowX, y, rowW);
		}

		buildBottomRow(panelX, panelY, cfg);
	}

	private void buildTabRow(int panelX, int panelY) {
		Tab[] tabs = Tab.values();
		int rowW = PANEL_WIDTH - 24;
		int gap = 4;
		int tabW = (rowW - gap * (tabs.length - 1)) / tabs.length;
		int startX = panelX + 12;
		int y = panelY + TAB_ROW_Y;
		for (int i = 0; i < tabs.length; i++) {
			Tab t = tabs[i];
			int x = startX + i * (tabW + gap);
			this.addRenderableWidget(Button.builder(Component.literal(t.label), b -> {
					tab = t;
					rebuild();
				})
				.bounds(x, y, tabW, TAB_H)
				.build());
		}
	}

	private void buildAppearanceTab(OverlayConfig cfg, int rowX, int y, int rowW) {
		this.addRenderableWidget(Button.builder(Component.literal("Enabled: " + onOff(cfg.enabled)), b -> {
				cfg.enabled = !cfg.enabled;
				b.setMessage(Component.literal("Enabled: " + onOff(cfg.enabled)));
			})
			.bounds(rowX, y, rowW, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Anchor: " + cfg.anchor.name()), b -> {
				cfg.anchor = cfg.anchor.next();
				cfg.offsetX = 5;
				cfg.offsetY = 50;
				b.setMessage(Component.literal("Anchor: " + cfg.anchor.name()));
			})
			.bounds(rowX, y + ROW_H, rowW, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Text: " + cfg.textColor.label), b -> {
				cfg.textColor = cfg.textColor.next();
				b.setMessage(Component.literal("Text: " + cfg.textColor.label));
			})
			.bounds(rowX, y + ROW_H * 2, rowW, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("BG: " + cfg.background.label), b -> {
				cfg.background = cfg.background.next();
				b.setMessage(Component.literal("BG: " + cfg.background.label));
			})
			.bounds(rowX, y + ROW_H * 3, rowW, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Shadow: " + onOff(cfg.shadow)), b -> {
				cfg.shadow = !cfg.shadow;
				b.setMessage(Component.literal("Shadow: " + onOff(cfg.shadow)));
			})
			.bounds(rowX, y + ROW_H * 4, rowW, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Labels: " + onOff(cfg.showManaLabel)), b -> {
				cfg.showManaLabel = !cfg.showManaLabel;
				b.setMessage(Component.literal("Labels: " + onOff(cfg.showManaLabel)));
			})
			.bounds(rowX, y + ROW_H * 5, rowW, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Font Size: " + cfg.fontSize.level), b -> {
				cfg.fontSize = cfg.fontSize.next();
				b.setMessage(Component.literal("Font Size: " + cfg.fontSize.level));
			})
			.bounds(rowX, y + ROW_H * 6, rowW, 20)
			.build());
	}

	private void buildBehaviorTab(OverlayConfig cfg, int rowX, int y, int rowW) {
		this.addRenderableWidget(Button.builder(Component.literal("Stack: " + onOff(cfg.stackAbilities)), b -> {
				cfg.stackAbilities = !cfg.stackAbilities;
				b.setMessage(Component.literal("Stack: " + onOff(cfg.stackAbilities)));
			})
			.bounds(rowX, y, rowW, 20)
			.build());

		this.addRenderableWidget(new SecondsSlider(
			rowX, y + ROW_H, rowW, 20,
			"Lifetime", cfg.entryLifetimeMs, 1000, 30_000,
			v -> cfg.entryLifetimeMs = v
		));

		this.addRenderableWidget(new SecondsSlider(
			rowX, y + ROW_H * 2, rowW, 20,
			"Fade", cfg.fadeMs, 0, 5000,
			v -> cfg.fadeMs = v
		));
	}

	private void buildBottomRow(int panelX, int panelY, OverlayConfig cfg) {
		int bottomY = panelY + PANEL_HEIGHT - 28;

		int editW = 120;
		int resetW = 60;
		int backW = 60;
		int gap = 6;

		int total = editW + gap + resetW + gap + backW;
		int x = panelX + (PANEL_WIDTH - total) / 2;

		this.addRenderableWidget(Button.builder(Component.literal("Edit Position/Size"), b -> {
				mode = Mode.EDIT_POSITION;
				rebuild();
			})
			.bounds(x, bottomY, editW, 20)
			.build());
		x += editW + gap;

		this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
				cfg.resetToDefaults();
				rebuild();
			})
			.bounds(x, bottomY, resetW, 20)
			.build());
		x += resetW + gap;

		this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
			.bounds(x, bottomY, backW, 20)
			.build());
	}

	private void buildEditPositionUi() {
		int btnW = 80;
		int btnH = 20;
		int x = (this.width - btnW) / 2;
		int y = this.height - btnH - 16;

		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
				mode = Mode.SETTINGS;
				rebuild();
			})
			.bounds(x, y, btnW, btnH)
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
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// Skip the default blur + dim in both modes so the overlay
		// (which rendered during the preceding HUD pass with editing=true)
		// stays crisp and highlighted. The settings panel draws its own
		// solid dark background on top, which keeps it readable.
		return;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (mode == Mode.SETTINGS) {
			int panelX = (this.width - PANEL_WIDTH) / 2;
			int panelY = (this.height - PANEL_HEIGHT) / 2;

			graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, BACKGROUND_COLOR);
			drawBorder(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, BORDER_COLOR);

			// Paint behind the active tab button so the selection is obvious
			// even before the user hovers. Button text stays visible because
			// vanilla draws widgets after extractRenderState returns (via
			// super.extractRenderState below).
			paintActiveTabStripe(graphics, panelX, panelY);

			Component title = Component.literal("Ability Log");
			int titleX = (this.width - this.font.width(title)) / 2;
			graphics.text(this.font, title, titleX, panelY + TITLE_Y, TITLE_COLOR, false);
		} else {
			Component hint = Component.literal("Drag the cyan box to move. Drag the white corner to resize.");
			int hintX = (this.width - this.font.width(hint)) / 2;
			graphics.text(this.font, hint, hintX, 16, LABEL_COLOR, true);
		}

		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void paintActiveTabStripe(GuiGraphicsExtractor graphics, int panelX, int panelY) {
		Tab[] tabs = Tab.values();
		int rowW = PANEL_WIDTH - 24;
		int gap = 4;
		int tabW = (rowW - gap * (tabs.length - 1)) / tabs.length;
		int startX = panelX + 12;
		int y = panelY + TAB_ROW_Y;
		for (int i = 0; i < tabs.length; i++) {
			int x = startX + i * (tabW + gap);
			int color = tabs[i] == tab ? TAB_SELECTED_COLOR : TAB_UNSELECTED_COLOR;
			graphics.fill(x, y + TAB_H, x + tabW, y + TAB_H + 2, color);
		}
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
		if (mode != Mode.EDIT_POSITION) return false;
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
		if (mode != Mode.EDIT_POSITION) return false;
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
		if (this.minecraft != null) {
			this.minecraft.setScreen(parent);
		} else {
			super.onClose();
		}
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
