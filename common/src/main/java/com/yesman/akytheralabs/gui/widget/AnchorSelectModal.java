package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.api.gui.ComboBox;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.Modal;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.blueprint.widget.WidgetLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/// The dropdown of an anchors [ComboBox]: every pair of [WidgetLayout.Horizontal] and [WidgetLayout.Vertical] as a
/// 4x4 grid of cells, columns left / center / right / stretch, rows top / center / bottom / stretch. Each cell draws
/// where a widget anchored that way sits in its parent. A press on a cell picks it and closes the modal.
public final class AnchorSelectModal extends AbstractContainerWidget implements Modal, AnchoredWidget {
    private static final int CELL = 28;
    private static final int GAP = 2;
    private static final int PAD = 4;
    private static final int GRID = CELL * 4 + GAP * 3;
    private static final int SIZE = GRID + PAD * 2;

    /// Inset of the parent frame inside a cell, and the anchored box's side across its axis, in pixels.
    private static final int FRAME_INSET = 5;
    private static final int BOX = 5;

    private static final int COLOR_BACKGROUND = 0xFF1E1E1E;
    private static final int COLOR_BORDER = 0xFF3A3A3A;
    private static final int COLOR_CELL = 0xFF262626;
    private static final int COLOR_CELL_HOVERED = 0xFF3A3A3A;
    private static final int COLOR_SELECTED = 0xFFFFCC00;
    private static final int COLOR_FRAME = 0xFF6A6A6A;
    private static final int COLOR_FRAME_GRID = 0xFF303030;
    private static final int COLOR_BOX = 0xFFDDDDDD;
    private static final int COLOR_STRETCH_LINE = 0xFF4FC3F7; // between the stretch row / column and the rest

    private final ComboBox<Anchors> combo;
    private final List<AnchorCell> cells = new ArrayList<>();

    public AnchorSelectModal(final ComboBox<Anchors> combo) {
        super(0, 0, SIZE, SIZE, Component.empty(), AbstractScrollArea.defaultSettings(0));

        this.combo = combo;
        this.x2 = AnchoredWidget.pixel(SIZE);
        this.y2 = AnchoredWidget.pixel(SIZE);

        for (WidgetLayout.Vertical vertical : WidgetLayout.Vertical.values()) {
            for (WidgetLayout.Horizontal horizontal : WidgetLayout.Horizontal.values()) {
                cells.add(new AnchorCell(new Anchors(horizontal, vertical)));
            }
        }
    }

    /// Places the cells in a grid centered across the modal, which a combo wider than the grid widens.
    @Override
    public void relocateChildren() {
        int left = getX() + (getWidth() - GRID) / 2;
        int top = getY() + PAD;

        for (AnchorCell cell : cells) {
            int column = cell.anchors.horizontal().ordinal();
            int row = cell.anchors.vertical().ordinal();

            cell.setRectangle(CELL, CELL, left + gridOffset(column), top + gridOffset(row));
        }
    }

    /// Where column or row `index` starts in the grid, as [WidgetLayout] orders the anchor types.
    private static int gridOffset(final int index) {
        return index * (CELL + GAP);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(getX(), getY(), getRight(), getBottom(), COLOR_BACKGROUND);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), COLOR_BORDER);

        for (AnchorCell cell : cells) {
            cell.extractRenderState(graphics, mouseX, mouseY, a);
        }

        int left = getX() + (getWidth() - GRID) / 2;
        int top = getY() + PAD;
        int stretch = gridOffset(3) - GAP / 2 - 1;

        graphics.fill(left + stretch, top, left + stretch + 1, top + GRID, COLOR_STRETCH_LINE);
        graphics.fill(left, top + stretch, left + GRID, top + stretch + 1, COLOR_STRETCH_LINE);
    }

    /// Covers the whole screen, so a press outside reaches [#mouseClicked] and closes the modal.
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return isActive();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.x() < getX() || event.y() < getY() || event.x() >= getRight() || event.y() >= getBottom()) {
            ModalManager.getInstance().closeModal();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isEscape()) {
            ModalManager.getInstance().closeModal();
            return true;
        }

        return false;
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return cells;
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    /// A horizontal and a vertical anchor type, as one pick.
    public record Anchors(WidgetLayout.Horizontal horizontal, WidgetLayout.Vertical vertical) {
    }

    /// One pair of anchor types: the parent as a frame, with a box where a widget anchored this way sits.
    private final class AnchorCell extends AbstractWidget {
        private final Anchors anchors;

        private AnchorCell(final Anchors anchors) {
            super(0, 0, CELL, CELL, Component.empty());

            this.anchors = anchors;
        }

        @Override
        protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            graphics.fill(getX(), getY(), getRight(), getBottom(), isHovered() ? COLOR_CELL_HOVERED : COLOR_CELL);

            int left = getX() + FRAME_INSET;
            int top = getY() + FRAME_INSET;
            int right = getRight() - FRAME_INSET;
            int bottom = getBottom() - FRAME_INSET;

            for (int x = left + 4; x < right; x += 4) graphics.fill(x, top, x + 1, bottom, COLOR_FRAME_GRID);
            for (int y = top + 4; y < bottom; y += 4) graphics.fill(left, y, right, y + 1, COLOR_FRAME_GRID);

            graphics.outline(left, top, right - left, bottom - top, COLOR_FRAME);

            int boxLeft = switch (anchors.horizontal()) {
                case LEFT_WIDTH, LEFT_RIGHT -> left + 2;
                case CENTER_WIDTH -> (left + right - BOX) / 2;
                case RIGHT_WIDTH -> right - 2 - BOX;
            };
            int boxRight = anchors.horizontal() == WidgetLayout.Horizontal.LEFT_RIGHT ? right - 2 : boxLeft + BOX;
            int boxTop = switch (anchors.vertical()) {
                case TOP_HEIGHT, TOP_BOTTOM -> top + 2;
                case CENTER_HEIGHT -> (top + bottom - BOX) / 2;
                case BOTTOM_HEIGHT -> bottom - 2 - BOX;
            };
            int boxBottom = anchors.vertical() == WidgetLayout.Vertical.TOP_BOTTOM ? bottom - 2 : boxTop + BOX;

            graphics.fill(boxLeft, boxTop, boxRight, boxBottom, COLOR_BOX);

            if (anchors.equals(combo.getValue())) graphics.outline(getX(), getY(), getWidth(), getHeight(), COLOR_SELECTED);
        }

        @Override
        public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
            ModalManager.getInstance().closeModal();
            combo.setValue(anchors);
        }

        @Override
        public void playDownSound(@NonNull SoundManager soundManager) {
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
        }
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
    private Unit x1 = AnchoredWidget.pixel(0);
    private Unit x2;
    private Unit y1 = AnchoredWidget.pixel(0);
    private Unit y2;
    private HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
    private VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;

    @Override public Unit getX1() { return x1; }
    @Override public Unit getX2() { return x2; }
    @Override public Unit getY1() { return y1; }
    @Override public Unit getY2() { return y2; }
    @Override public void setX1(Unit unit) { x1 = unit; }
    @Override public void setX2(Unit unit) { x2 = unit; }
    @Override public void setY1(Unit unit) { y1 = unit; }
    @Override public void setY2(Unit unit) { y2 = unit; }
    @Override public HorizontalAnchorType getHorizontalAnchorType() { return horizontalAnchorType; }
    @Override public VerticalAnchorType getVerticalAnchorType() { return verticalAnchorType; }
    @Override public void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType) { this.horizontalAnchorType = horizontalAnchorType; }
    @Override public void setVerticalAnchorType(VerticalAnchorType verticalAnchorType) { this.verticalAnchorType = verticalAnchorType; }
}
