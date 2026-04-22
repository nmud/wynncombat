package io.github.nmud.wynncombat.client.gui;

import io.github.nmud.wynncombat.client.damage.DpsConfig;
import io.github.nmud.wynncombat.client.damage.DpsOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Settings + position editor for the {@link DpsOverlay}. Mirrors the
 * structure of {@link AbilityLogScreen}: a central panel in
 * {@link Mode#SETTINGS} mode; the panel disappears and the overlay becomes
 * draggable/resizable in {@link Mode#EDIT_POSITION} mode with a single
 * "Done" button at the bottom.
 *
 * <p>The settings panel exposes colors, background, shadow, font family,
 * font size, display mode (all/cycle), color-tier toggle, and a list of
 * DPS windows you can enable/disable/remove plus an input box to add new
 * custom durations.
 */
public class DpsScreen extends Screen {
	public enum Mode { SETTINGS, EDIT_POSITION }

	private static final int PANEL_WIDTH = 360;
	private static final int PANEL_HEIGHT = 260;
	private static final int BACKGROUND_COLOR = 0xCC101010;
	private static final int BORDER_COLOR = 0xFFFFFFFF;
	private static final int TITLE_COLOR = 0xFFFFFFFF;
	private static final int LABEL_COLOR = 0xFFAAAAAA;

	private static final int ROW_H = 22;
	private static final int BTN_W = 140;

	private static final int MAX_WINDOWS = 8;

	private final Screen parent;
	private Mode mode = Mode.SETTINGS;

	private boolean draggingMove = false;
	private boolean draggingResize = false;
	private int dragOffsetX = 0;
	private int dragOffsetY = 0;

	private EditBox addWindowBox;

	public DpsScreen(Screen parent) {
		super(Component.literal("DPS Counter"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();
		DpsOverlay.setEditing(true);

		if (mode == Mode.EDIT_POSITION) {
			buildEditPositionUi();
		} else {
			buildSettingsUi();
		}
	}

	private void buildSettingsUi() {
		DpsConfig cfg = DpsConfig.get();

		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;

		int leftX = panelX + 12;
		int rightX = panelX + PANEL_WIDTH - BTN_W - 12;
		int contentY = panelY + 30;

		// --- Left column: styling -----------------------------------------
		this.addRenderableWidget(Button.builder(Component.literal("Enabled: " + onOff(cfg.enabled)), b -> {
				cfg.enabled = !cfg.enabled;
				b.setMessage(Component.literal("Enabled: " + onOff(cfg.enabled)));
			})
			.bounds(leftX, contentY, BTN_W, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Anchor: " + cfg.anchor.name()), b -> {
				cfg.anchor = cfg.anchor.next();
				cfg.offsetX = 5;
				cfg.offsetY = 5;
				b.setMessage(Component.literal("Anchor: " + cfg.anchor.name()));
			})
			.bounds(leftX, contentY + ROW_H, BTN_W, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Text: " + cfg.textColor.label), b -> {
				cfg.textColor = cfg.textColor.next();
				b.setMessage(Component.literal("Text: " + cfg.textColor.label));
			})
			.bounds(leftX, contentY + ROW_H * 2, BTN_W, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("BG: " + cfg.background.label), b -> {
				cfg.background = cfg.background.next();
				b.setMessage(Component.literal("BG: " + cfg.background.label));
			})
			.bounds(leftX, contentY + ROW_H * 3, BTN_W, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Shadow: " + onOff(cfg.shadow)), b -> {
				cfg.shadow = !cfg.shadow;
				b.setMessage(Component.literal("Shadow: " + onOff(cfg.shadow)));
			})
			.bounds(leftX, contentY + ROW_H * 4, BTN_W, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Font: " + cfg.fontFamily.label), b -> {
				cfg.fontFamily = cfg.fontFamily.next();
				b.setMessage(Component.literal("Font: " + cfg.fontFamily.label));
			})
			.bounds(leftX, contentY + ROW_H * 5, BTN_W, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Font Size: " + cfg.fontSize.level), b -> {
				cfg.fontSize = cfg.fontSize.next();
				b.setMessage(Component.literal("Font Size: " + cfg.fontSize.level));
			})
			.bounds(leftX, contentY + ROW_H * 6, BTN_W, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Display: " + cfg.displayMode.label), b -> {
				cfg.displayMode = cfg.displayMode.next();
				b.setMessage(Component.literal("Display: " + cfg.displayMode.label));
			})
			.bounds(leftX, contentY + ROW_H * 7, BTN_W, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Color Tiers: " + onOff(cfg.colorTiers)), b -> {
				cfg.colorTiers = !cfg.colorTiers;
				b.setMessage(Component.literal("Color Tiers: " + onOff(cfg.colorTiers)));
			})
			.bounds(leftX, contentY + ROW_H * 8, BTN_W, 20)
			.build());

		// --- Right column: windows list -----------------------------------
		int y = contentY;
		for (int i = 0; i < cfg.windows.size(); i++) {
			final int idx = i;
			DpsConfig.DpsWindow w = cfg.windows.get(i);

			int toggleW = 90;
			int removeW = 36;
			int gap = 4;

			this.addRenderableWidget(Button.builder(
					Component.literal(w.label() + ": " + onOff(w.enabled)),
					b -> {
						w.enabled = !w.enabled;
						b.setMessage(Component.literal(w.label() + ": " + onOff(w.enabled)));
					})
				.bounds(rightX, y, toggleW, 20)
				.build());

			this.addRenderableWidget(Button.builder(Component.literal("X"), b -> {
					cfg.windows.remove(idx);
					cfg.cycleIndex = cfg.clampCycleIndex();
					rebuild();
				})
				.bounds(rightX + toggleW + gap, y, removeW, 20)
				.build());

			y += ROW_H;
			if (y > panelY + PANEL_HEIGHT - 80) break;
		}

		// "Add window" input + button (only if under cap)
		int addY = panelY + PANEL_HEIGHT - 58;
		if (cfg.windows.size() < MAX_WINDOWS) {
			int boxW = 56;
			int btnAddW = 64;
			int gap = 4;

			addWindowBox = new EditBox(this.font, rightX, addY, boxW, 20, Component.literal("sec"));
			addWindowBox.setHint(Component.literal("sec"));
			addWindowBox.setMaxLength(4);
			this.addRenderableWidget(addWindowBox);

			this.addRenderableWidget(Button.builder(Component.literal("Add"), b -> {
					String s = addWindowBox == null ? "" : addWindowBox.getValue().trim();
					if (s.isEmpty()) return;
					try {
						int seconds = Integer.parseInt(s);
						if (seconds < 1 || seconds > 600) return;
						cfg.windows.add(new DpsConfig.DpsWindow(seconds, true));
						rebuild();
					} catch (NumberFormatException ignored) {
						// Filter above makes this impossible; swallow defensively.
					}
				})
				.bounds(rightX + boxW + gap, addY, btnAddW, 20)
				.build());
		} else {
			// No room for more windows; hint in place of the input.
			addWindowBox = null;
		}

		// --- Bottom row ---------------------------------------------------
		int bottomY = panelY + PANEL_HEIGHT - 30;

		this.addRenderableWidget(Button.builder(Component.literal("Edit Position/Size"), b -> {
				mode = Mode.EDIT_POSITION;
				rebuild();
			})
			.bounds(leftX, bottomY, BTN_W, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
				cfg.resetToDefaults();
				rebuild();
			})
			.bounds(panelX + PANEL_WIDTH / 2 - 30, bottomY, 60, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
			.bounds(panelX + PANEL_WIDTH - 70 - 12, bottomY, 70, 20)
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
		// Skip the default blur + dim in edit-position mode so the DPS
		// overlay stays crisp while being dragged/resized.
		if (mode == Mode.EDIT_POSITION) return;
		super.extractBackground(graphics, mouseX, mouseY, delta);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (mode == Mode.SETTINGS) {
			int panelX = (this.width - PANEL_WIDTH) / 2;
			int panelY = (this.height - PANEL_HEIGHT) / 2;

			graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, BACKGROUND_COLOR);
			drawBorder(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, BORDER_COLOR);

			Component title = Component.literal("DPS Counter");
			int titleX = (this.width - this.font.width(title)) / 2;
			graphics.text(this.font, title, titleX, panelY + 10, TITLE_COLOR, false);

			Component hint = Component.literal("Enter seconds then Add for a custom window.");
			int hintX = panelX + PANEL_WIDTH - this.font.width(hint) - 12;
			graphics.text(this.font, hint, hintX, panelY + PANEL_HEIGHT - 76, LABEL_COLOR, false);
		} else {
			Component hint = Component.literal("Drag the cyan box to move. Drag the white corner to resize.");
			int hintX = (this.width - this.font.width(hint)) / 2;
			graphics.text(this.font, hint, hintX, 16, LABEL_COLOR, true);
		}

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
		if (mode != Mode.EDIT_POSITION) return false;
		if (event.button() != 0) return false;

		double mx = event.x();
		double my = event.y();
		DpsConfig cfg = DpsConfig.get();
		int boxX = DpsOverlay.getBoxX();
		int boxY = DpsOverlay.getBoxY();

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

		DpsConfig cfg = DpsConfig.get();
		double mx = event.x();
		double my = event.y();
		int screenW = DpsOverlay.getScreenW();
		int screenH = DpsOverlay.getScreenH();
		if (screenW <= 0 || screenH <= 0) return false;

		if (draggingResize) {
			int boxX = DpsOverlay.getBoxX();
			int boxY = DpsOverlay.getBoxY();
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

	private static boolean insideBox(double mx, double my, int boxX, int boxY, DpsConfig cfg) {
		return mx >= boxX && mx <= boxX + cfg.width
			&& my >= boxY && my <= boxY + cfg.height;
	}

	private static boolean insideResizeHandle(double mx, double my, int boxX, int boxY, DpsConfig cfg) {
		int handle = DpsOverlay.RESIZE_HANDLE;
		return mx >= boxX + cfg.width - handle && mx <= boxX + cfg.width
			&& my >= boxY + cfg.height - handle && my <= boxY + cfg.height;
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	@Override
	public void onClose() {
		DpsOverlay.setEditing(false);
		DpsConfig.save();
		if (this.minecraft != null) {
			this.minecraft.setScreen(parent);
		} else {
			super.onClose();
		}
	}

	@Override
	public void removed() {
		DpsOverlay.setEditing(false);
		DpsConfig.save();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
