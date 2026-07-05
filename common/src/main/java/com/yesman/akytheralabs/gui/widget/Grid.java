package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/// A scrollable grid widget that automatically scales and positions child widgets
/// in a left-to-right, top-to-bottom layout. The number of columns and padding
/// are computed from the available width using a 10:1 widget-width-to-padding ratio.
public final class Grid<T extends AbstractWidget> extends AbstractContainerWidget implements AnchoredWidget {
    private static final int SCROLLBAR_W = 5;

    private final List<T> children = new ArrayList<>();

    /// Width of a grid cell
    private final Function<ScreenRectangle, Integer> cellWidthCalculator;

    /// Height of a grid cell
    private final Function<ScreenRectangle, Integer> cellHeightCalculator;

    /// Padding space between widgets. Both in horizontal and vertical directions.
    private final Function<ScreenRectangle, Integer> paddingCalculator;

    @Nullable
    private final Comparator<T> comparator;

    /// Necessary to memorize for scrolling
    private int padding;

    /// Memorized grid height
    private int totalHeight;

    public Grid(
        final Unit x1,
        final Unit x2,
        final Unit y1,
        final Unit y2,
        final AnchoredWidget.HorizontalAnchorType horizontalAnchor,
        final AnchoredWidget.VerticalAnchorType verticalAnchor,
        final Function<ScreenRectangle, Integer> cellWidthCalculator,
        final Function<ScreenRectangle, Integer> cellHeightCalculator,
        final Function<ScreenRectangle, Integer> paddingCalculator,
        final @Nullable Comparator<T> comparator
    ) {
        super(0, 0, 0, 0, Component.literal("grid_layout"), AbstractScrollArea.defaultSettings(0)); // Scroll bar setting overwritten by methods

        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;
        this.cellWidthCalculator = cellWidthCalculator;
        this.cellHeightCalculator = cellHeightCalculator;
        this.paddingCalculator = paddingCalculator;
        this.comparator = comparator;
    }

    public void addChild(T child) {
        children.add(child);
    }

    public void removeChild(T child) {
        children.remove(child);
    }

    public void clearChildren() {
        children.clear();
    }

    /// Sorts children in place using the stored comparator and re-runs the layout pass.
    public void sort() {
        if (comparator == null) return;
        children.sort(comparator);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (children.isEmpty()) {
            return;
        }

        int availableWidth = getWidth() - SCROLLBAR_W;
        int contentRight = getX() + availableWidth;

        graphics.enableScissor(getX(), getY(), contentRight, getY() + getHeight());

        int x = getX();
        int y = getY() - (int)scrollAmount();

        for (AbstractWidget cell : children) {
            cell.setY(y);

            if (cell.getBottom() >= getY() && cell.getY() <= getBottom()) { // Checks whether the grid cell in visible area
                cell.extractRenderState(graphics, mouseX, mouseY, a);
            }
            if (x + (cell.getWidth() * 2 + padding) > contentRight) { // Checks the next widget start exceeds the grid right
                // Next row first
                x = padding;
                y += (cell.getHeight() + padding);
            } else {
                // Next column
                x += (cell.getWidth() + padding);
            }
        }

        graphics.disableScissor();

        if (scrollable()) {
            renderGridScrollbar(graphics, mouseX, mouseY);
        }
    }

    private void renderGridScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int scrollBarX = getX() + getWidth() - SCROLLBAR_W;
        int trackTop = getY();
        int trackH = getHeight();
        int maxScroll = maxScrollAmount();

        graphics.fill(scrollBarX, trackTop, scrollBarX + SCROLLBAR_W, trackTop + trackH, 0xFF1A1A1A);

        int thumbH = Math.max(8, trackH * trackH / contentHeight());
        int thumbY = maxScroll > 0
            ? trackTop + (trackH - thumbH) * (int)scrollAmount() / maxScroll
            : trackTop;

        boolean hovering = mouseX >= scrollBarX && mouseX < scrollBarX + SCROLLBAR_W && mouseY >= trackTop && mouseY < trackTop + trackH;

        graphics.fill(scrollBarX + 1, thumbY, scrollBarX + SCROLLBAR_W - 1, thumbY + thumbH, hovering ? 0xFF6A6A6A : 0xFF505050);
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
        Optional<GuiEventListener> child = getChildAt(event.x(), event.y());

        if (child.isEmpty()) {
            return false;
        } else {
            GuiEventListener widget = child.get();

            if (widget.mouseClicked(event, doubleClick) && widget.shouldTakeFocusAfterInteraction()) {
                setFocused(widget);

                if (event.button() == 0) {
                    setDragging(true);
                }
            }

            return true;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!visible || !isMouseOver(mx, my) || children.isEmpty()) {
            return false;
        }

        int rowStride = children.getFirst().getHeight() + padding;
        setScrollAmount(scrollAmount() - scrollY * rowStride);

        return true;
    }

    @Override
    public void setFocused(final @Nullable GuiEventListener focused) {
        // Manual fix: focusing was released as soon as a widget focused if it's the same one as current focus
        if (getFocused() == focused) {
            return;
        }

        super.setFocused(focused);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    @Override
    protected int contentHeight() {
        return totalHeight;
    }

    @Override
    protected int scrollBarX() {
        return getRight() - SCROLLBAR_W;
    }

    @Override
    public int scrollbarWidth() {
        return SCROLLBAR_W;
    }

    @Override
    public @NonNull List<T> children() {
        return children;
    }

    @Override @SuppressWarnings("unchecked")
    public @Nullable T getFocused() {
        return (T)super.getFocused();
    }

    /// For the common anchor implementation, you can copy and paste this code
    /// at the bottom of the class
    /// ***************************************************************
    /// [AnchoredWidget] implementations                              *
    /// ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private final AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
    private final AnchoredWidget.VerticalAnchorType verticalAnchorType;

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

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        double scrollRatio = maxScrollAmount() == 0 ? 0.0D : scrollAmount() / maxScrollAmount();
        AnchoredWidget.super.relocate(screenRectangle);

        ScreenRectangle gridRectangle = getRectangle();
        int cellWidth = cellWidthCalculator.apply(gridRectangle);
        int cellHeight = cellHeightCalculator.apply(gridRectangle);
        // Memorize padding
        padding = paddingCalculator.apply(gridRectangle);

        int availableWidth = getWidth() - SCROLLBAR_W;
        int contentRight = getX() + availableWidth;
        int x = getX() + padding;
        int y = getY() - (int)scrollAmount() + padding;
        int rows = 1;

        for (T cell : children) {
            cell.setPosition(x, y);
            cell.setSize(cellWidth, cellHeight);

            if (x + (cellWidth * 2 + padding) > contentRight) { // Checks the next widget start exceeds the grid right
                // Next row first
                x = getX() + padding;
                y += (cellHeight + padding);
                if (cell != children.getLast()) rows++;
            } else {
                // Next column
                x += (cellWidth + padding);
            }
        }

        // Content
        totalHeight = (cellHeight + padding) * rows - padding;

        // Set a new scroll position keeping the old ratio
        setScrollAmount(maxScrollAmount() * scrollRatio);
    }
}
