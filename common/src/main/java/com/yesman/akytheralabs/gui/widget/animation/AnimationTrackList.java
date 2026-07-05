package com.yesman.akytheralabs.gui.widget.animation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import org.jspecify.annotations.NonNull;

import java.util.List;

/// Generic vertically-scrolling list of [AnimTrackEntry] rows. It knows nothing about
/// notifies / sections / segments — the host panel's row builder supplies whatever rows
/// to show via [#rebuild]. Each row owns its own editable child widgets.
public final class AnimationTrackList extends ContainerObjectSelectionList<@NonNull AnimTrackEntry> {
    static final int ROW_H = 27;
    public static final int NAME_BLOCK_W = 140;
    private static final int SCROLL_BAR_WIDTH = 5;

    public AnimationTrackList() {
        super(Minecraft.getInstance(), 0, 0, 0, ROW_H);
    }

    /// Replace every row with the caller-built list.
    public void rebuild(List<AnimTrackEntry> rows) {
        clearEntries();
        for (AnimTrackEntry r : rows) addEntry(r);
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