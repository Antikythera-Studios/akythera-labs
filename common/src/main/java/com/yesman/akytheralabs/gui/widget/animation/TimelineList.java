package com.yesman.akytheralabs.gui.widget.animation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.NonNull;

import java.util.List;

/// Generic vertically-scrolling list of [TimelineTrackEntry] rows. It knows nothing about
/// notifies / sections / segments — the host panel's row builder supplies whatever rows
/// to show via [#rebuild]. Each row owns its own editable child widgets.
public final class TimelineList extends ContainerObjectSelectionList<@NonNull TimelineTrackEntry> {
    public static final int ROW_H = 27;
    public static final int NAME_BLOCK_W = 140;
    private static final int SCROLL_BAR_WIDTH = 5;

    /// Whether the press in progress holds the scroll bar.
    private boolean scrollGrabbed;

    public TimelineList() {
        super(Minecraft.getInstance(), 0, 0, 0, ROW_H);
    }

    /// Whether `x` / `y` lies on the scroll bar, while it shows.
    public boolean isOnScrollbar(double x, double y) {
        return scrollable() && isOverScrollbar(x, y);
    }

    /// A press on the scroll bar only scrolls, leaving the rows under it untouched.
    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        scrollGrabbed = updateScrolling(event);

        return scrollGrabbed || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        return super.mouseDragged(event, dx, dy) || scrollGrabbed;
    }

    @Override
    public void onRelease(@NonNull MouseButtonEvent event) {
        super.onRelease(event);
        scrollGrabbed = false;
    }

    /// Replace every row with the caller-built list.
    public void rebuild(List<TimelineTrackEntry> rows) {
        clearEntries();
        for (TimelineTrackEntry r : rows) addEntry(r);
    }

    @Override
    public int getRowWidth() {
        return getWidth();
    }

    @Override
    public int scrollbarWidth() {
        return SCROLL_BAR_WIDTH;
    }

    @Override
    protected int scrollBarX() {
        return getRight() - SCROLL_BAR_WIDTH;
    }

    @Override
    protected void extractListBackground(@NonNull GuiGraphicsExtractor g) {
        // Entries paint their own backgrounds.
    }

    @Override
    protected void extractListSeparators(@NonNull GuiGraphicsExtractor g) {
    }

    @Override
    protected void extractScrollbar(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!scrollable()) return;
        int x = scrollBarX();
        int scrollerY = scrollBarY();
        int scrollerH = scrollerHeight();

        g.fill(x, getY(), x + SCROLL_BAR_WIDTH, getBottom(), 0xFF1A1A1A);
        boolean hovering = mouseX >= x && mouseX < x + SCROLL_BAR_WIDTH && mouseY >= scrollerY && mouseY < scrollerY + scrollerH;
        g.fill(x + 1, scrollerY, x + SCROLL_BAR_WIDTH - 1, scrollerY + scrollerH, hovering ? 0xFF6A6A6A : 0xFF505050);
    }

    @Override
    protected boolean entriesCanBeSelected() {
        return false;
    }

    @Override
    public void updateWidgetNarration(@NonNull NarrationElementOutput o) {
    }
}