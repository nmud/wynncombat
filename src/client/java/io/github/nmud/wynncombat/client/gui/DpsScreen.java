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
 * Settings + position editor for the {@link DpsOverlay}. Mirrors
 * {@link AbilityLogScreen}: a central panel in {@link Mode#SETTINGS} mode;
 * the panel disappears and the overlay becomes draggable/resizable in
 * {@link Mode#EDIT_POSITION} mode with a "Done" button at the bottom.
 *
 * <p>Options are grouped into {@link Tab tabs} (General / Text / Label /
 * Windows) so no single column grows taller than the screen. In edit mode
 * the user can drag the whole box, resize it via the corner handle, or
 * drag individual rendered rows to reposition them within the box (with a
 * "Reset Positions" button to clear those per-row offsets).
 */
public class DpsScreen extends Screen {
	public enum Mode { SETTINGS, EDIT_POSITION }

	public enum Tab {
		GENERAL("General"),
		TEXT("Text"),
		LABEL("Label"),
		WINDOWS("Windows");

		public final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private static final int PANEL_WIDTH = 340;
	private static final int PANEL_HEIGHT = 280;
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

	private static final int MAX_WINDOWS = 8;

	private final Screen parent;
	private Mode mode = Mode.SETTINGS;
	private Tab tab = Tab.GENERAL;

	private boolean draggingMove = false;
	private boolean draggingResize = false;
	private DpsConfig.DpsWindow draggingRow = null;
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

		buildTabRow(panelX, panelY);

		int rowW = PANEL_WIDTH - 24;
		int rowX = panelX + 12;
		int y = panelY + CONTENT_Y;

		switch (tab) {
			case GENERAL -> buildGeneralTab(cfg, rowX, y, rowW);
			case TEXT -> buildTextTab(cfg, rowX, y, rowW);
			case LABEL -> buildLabelTab(cfg, rowX, y, rowW);
			case WINDOWS -> buildWindowsTab(cfg, rowX, y, rowW, panelY);
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

	private void buildGeneralTab(DpsConfig cfg, int rowX, int y, int rowW) {
		this.addRenderableWidget(Button.builder(Component.literal("Enabled: " + onOff(cfg.enabled)), b -> {
				cfg.enabled = !cfg.enabled;
				b.setMessage(Component.literal("Enabled: " + onOff(cfg.enabled)));
			})
			.bounds(rowX, y, rowW, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Anchor: " + cfg.anchor.name()), b -> {
				cfg.anchor = cfg.anchor.next();
				cfg.offsetX = 5;
				cfg.offsetY = 5;
				b.setMessage(Component.literal("Anchor: " + cfg.anchor.name()));
			})
			.bounds(rowX, y + ROW_H, rowW, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Display: " + cfg.displayMode.label), b -> {
				cfg.displayMode = cfg.displayMode.next();
				b.setMessage(Component.literal("Display: " + cfg.displayMode.label));
			})
			.bounds(rowX, y + ROW_H * 2, rowW, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Color Tiers: " + onOff(cfg.colorTiers)), b -> {
				cfg.colorTiers = !cfg.colorTiers;
				b.setMessage(Component.literal("Color Tiers: " + onOff(cfg.colorTiers)));
			})
			.bounds(rowX, y + ROW_H * 3, rowW, 20)
			.build());
	}

	private void buildTextTab(DpsConfig cfg, int rowX, int y, int rowW) {
		this.addRenderableWidget(Button.builder(Component.literal("Text: " + cfg.textColor.label), b -> {
				cfg.textColor = cfg.textColor.next();
				b.setMessage(Component.literal("Text: " + cfg.textColor.label));
			})
			.bounds(rowX, y, rowW, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("BG: " + cfg.background.label), b -> {
				cfg.background = cfg.background.next();
				b.setMessage(Component.literal("BG: " + cfg.background.label));
			})
			.bounds(rowX, y + ROW_H, rowW, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Shadow: " + onOff(cfg.shadow)), b -> {
				cfg.shadow = !cfg.shadow;
				b.setMessage(Component.literal("Shadow: " + onOff(cfg.shadow)));
			})
			.bounds(rowX, y + ROW_H * 2, rowW, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Font Size: " + cfg.fontSize.level), b -> {
				cfg.fontSize = cfg.fontSize.next();
				b.setMessage(Component.literal("Font Size: " + cfg.fontSize.level));
			})
			.bounds(rowX, y + ROW_H * 3, rowW, 20)
			.build());
	}

	private void buildLabelTab(DpsConfig cfg, int rowX, int y, int rowW) {
		this.addRenderableWidget(Button.builder(Component.literal("Label Style: " + cfg.labelStyle.label), b -> {
				cfg.labelStyle = cfg.labelStyle.next();
				b.setMessage(Component.literal("Label Style: " + cfg.labelStyle.label));
			})
			.bounds(rowX, y, rowW, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Uniform Color: " + onOff(cfg.labelUniformColor)), b -> {
				cfg.labelUniformColor = !cfg.labelUniformColor;
				b.setMessage(Component.literal("Uniform Color: " + onOff(cfg.labelUniformColor)));
			})
			.bounds(rowX, y + ROW_H, rowW, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Label Color: " + cfg.labelTextColor.label), b -> {
				cfg.labelTextColor = cfg.labelTextColor.next();
				b.setMessage(Component.literal("Label Color: " + cfg.labelTextColor.label));
			})
			.bounds(rowX, y + ROW_H * 2, rowW, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Label BG: " + cfg.labelBackground.label), b -> {
				cfg.labelBackground = cfg.labelBackground.next();
				b.setMessage(Component.literal("Label BG: " + cfg.labelBackground.label));
			})
			.bounds(rowX, y + ROW_H * 3, rowW, 20)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Label Border: " + cfg.labelBorderColor.label), b -> {
				cfg.labelBorderColor = cfg.labelBorderColor.next();
				b.setMessage(Component.literal("Label Border: " + cfg.labelBorderColor.label));
			})
			.bounds(rowX, y + ROW_H * 4, rowW, 20)
			.build());
	}

	private void buildWindowsTab(DpsConfig cfg, int rowX, int y, int rowW, int panelY) {
		// Per-window rows: [label EditBox] [ON/OFF] [X]. The EditBox carries
		// the custom label; the placeholder shows the auto one ("1s", "30s", ...).
		int labelBoxW = rowW - 40 - 30 - 8;
		int toggleW = 40;
		int removeW = 30;
		int gap = 4;

		int rowY = y;
		for (int i = 0; i < cfg.windows.size(); i++) {
			final int idx = i;
			DpsConfig.DpsWindow w = cfg.windows.get(i);

			EditBox labelBox = new EditBox(this.font, rowX, rowY, labelBoxW, 20,
				Component.literal(w.autoLabel()));
			labelBox.setHint(Component.literal(w.autoLabel()));
			labelBox.setMaxLength(12);
			if (w.customLabel != null) labelBox.setValue(w.customLabel);
			labelBox.setResponder(s -> w.customLabel = (s == null || s.isBlank()) ? null : s);
			this.addRenderableWidget(labelBox);

			this.addRenderableWidget(Button.builder(
					Component.literal(onOff(w.enabled)),
					b -> {
						w.enabled = !w.enabled;
						b.setMessage(Component.literal(onOff(w.enabled)));
					})
				.bounds(rowX + labelBoxW + gap, rowY, toggleW, 20)
				.build());

			this.addRenderableWidget(Button.builder(Component.literal("X"), b -> {
					cfg.windows.remove(idx);
					cfg.cycleIndex = cfg.clampCycleIndex();
					rebuild();
				})
				.bounds(rowX + labelBoxW + gap + toggleW + gap, rowY, removeW, 20)
				.build());

			rowY += ROW_H;
			if (rowY > panelY + PANEL_HEIGHT - 70) break;
		}

		// Add-window row (when under the cap).
		if (cfg.windows.size() < MAX_WINDOWS) {
			int boxW = 60;
			int addBtnW = rowW - boxW - gap;
			addWindowBox = new EditBox(this.font, rowX, rowY, boxW, 20, Component.literal("sec"));
			addWindowBox.setHint(Component.literal("sec"));
			addWindowBox.setMaxLength(4);
			this.addRenderableWidget(addWindowBox);

			this.addRenderableWidget(Button.builder(Component.literal("Add window"), b -> {
					String s = addWindowBox == null ? "" : addWindowBox.getValue().trim();
					if (s.isEmpty()) return;
					try {
						int seconds = Integer.parseInt(s);
						if (seconds < 1 || seconds > 600) return;
						cfg.windows.add(new DpsConfig.DpsWindow(seconds, true));
						rebuild();
					} catch (NumberFormatException ignored) {
						// Non-digit input: ignore quietly.
					}
				})
				.bounds(rowX + boxW + gap, rowY, addBtnW, 20)
				.build());
		} else {
			addWindowBox = null;
		}
	}

	private void buildBottomRow(int panelX, int panelY, DpsConfig cfg) {
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
		int btnW = 120;
		int btnH = 20;
		int gap = 6;
		int totalW = btnW + gap + btnW;
		int startX = (this.width - totalW) / 2;
		int y = this.height - btnH - 16;

		this.addRenderableWidget(Button.builder(Component.literal("Reset Positions"), b -> {
				DpsConfig cfg = DpsConfig.get();
				for (DpsConfig.DpsWindow w : cfg.windows) {
					if (w == null) continue;
					w.customPosition = false;
					w.rowX = 0;
					w.rowY = 0;
				}
			})
			.bounds(startX, y, btnW, btnH)
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
				mode = Mode.SETTINGS;
				rebuild();
			})
			.bounds(startX + btnW + gap, y, btnW, btnH)
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
		// Skip the default blur + dim in both modes so the DPS overlay
		// (which rendered during the preceding HUD pass with editing=true)
		// stays crisp and highlighted.
		return;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (mode == Mode.SETTINGS) {
			int panelX = (this.width - PANEL_WIDTH) / 2;
			int panelY = (this.height - PANEL_HEIGHT) / 2;

			graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, BACKGROUND_COLOR);
			drawBorder(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, BORDER_COLOR);

			paintActiveTabStripe(graphics, panelX, panelY);

			Component title = Component.literal("DPS Counter");
			int titleX = (this.width - this.font.width(title)) / 2;
			graphics.text(this.font, title, titleX, panelY + TITLE_Y, TITLE_COLOR, false);
		} else {
			Component hint = Component.literal("Drag the cyan box to move, the white corner to resize, or a row to reposition it.");
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
		DpsConfig cfg = DpsConfig.get();
		int boxX = DpsOverlay.getBoxX();
		int boxY = DpsOverlay.getBoxY();

		// Priority: resize handle > row drag > box move. The row handles sit
		// inside the box, so we only check them after the corner handle.
		if (insideResizeHandle(mx, my, boxX, boxY, cfg)) {
			draggingResize = true;
			return true;
		}
		DpsOverlay.RowRect hit = DpsOverlay.rowAt((int) mx, (int) my);
		if (hit != null) {
			draggingRow = hit.window();
			dragOffsetX = (int) (mx - hit.x());
			dragOffsetY = (int) (my - hit.y());
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

		if (draggingRow != null) {
			int boxX = DpsOverlay.getBoxX();
			int boxY = DpsOverlay.getBoxY();
			// New row top-left in *screen* coords, clamped inside the box.
			int newRowX = clamp((int) (mx - dragOffsetX), boxX, boxX + cfg.width - 10);
			int newRowY = clamp((int) (my - dragOffsetY), boxY, boxY + cfg.height - 10);
			draggingRow.customPosition = true;
			draggingRow.rowX = newRowX - boxX;
			draggingRow.rowY = newRowY - boxY;
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
		boolean wasDragging = draggingMove || draggingResize || draggingRow != null;
		draggingMove = false;
		draggingResize = false;
		draggingRow = null;
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
