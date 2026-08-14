package com.yesman.akytheralabs.gui.widget.animation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/// Draws the playhead — a vertical green line spanning the panel and a small
/// orange drag handle near the top — and routes mouse events for scrubbing.
public final class CurrentFrameIndicator extends AbstractWidget {
    private static final int ORANGE_BOX_W = 7;
    private static final int ORANGE_BOX_H = 12;
    private static final int ORANGE_BOX_TOP_OFFSET = 12;

    private final AnimationTrackEditorPanel parent;
    private boolean draggingBox;

    public CurrentFrameIndicator(AnimationTrackEditorPanel parent) {
        super(0, 0, 0, 0, Component.empty());

        this.parent = parent;
    }

    /// Resize callback: the panel relays its own bounds so the indicator can
    /// hit-test mouse events anywhere in the panel.
    public void setBounds(int x, int y, int w, int h) {
        setX(x);
        setY(y);
        setWidth(w);
        setHeight(h);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (!parent.isCurrentFrameInScope()) {
            return;
        }

        int x = parent.frameToX(parent.currentFrame());

        // Green vertical line through the entire panel
        g.fill(x, getY() + ORANGE_BOX_TOP_OFFSET, x + 1, getY() + getHeight(), 0xFF44CC44);

        // Orange drag box near the top of the panel
        int boxX = x - ORANGE_BOX_W / 2;
        int boxY = getY() + ORANGE_BOX_TOP_OFFSET;
        g.fill(boxX, boxY, boxX + ORANGE_BOX_W, boxY + ORANGE_BOX_H, 0xFFEE8822);

        String label = String.format("%.1f", parent.currentFrame());
        Font font = Minecraft.getInstance().font;
        g.centeredText(font, label, x + 1, getY() + 2, 0xFFEEEEEE);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0 || parent.isDebugDriven()) return false;

        int x = parent.frameToX(parent.currentFrame());
        int boxX = x - ORANGE_BOX_W / 2;
        int boxY = getY() + ORANGE_BOX_TOP_OFFSET;

        if (
            event.x() >= boxX &&
            event.x() < boxX + ORANGE_BOX_W &&
            event.y() >= boxY &&
            event.y() < boxY + ORANGE_BOX_H
        ) {
            draggingBox = true;

            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (draggingBox) {
            parent.setCurrentFrame(parent.xToFrame(event.x()));
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        boolean wasDragging = draggingBox;
        draggingBox = false;

        return wasDragging;
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        // Only consume mouse events that hit the orange drag box; let the rest
        // fall through to the track list / sub-widgets.
        if (!visible || !active) return false;
        int x = parent.frameToX(parent.currentFrame());
        int boxX = x - ORANGE_BOX_W / 2;
        int boxY = getY() + ORANGE_BOX_TOP_OFFSET;
        return mx >= boxX && mx < boxX + ORANGE_BOX_W && my >= boxY && my < boxY + ORANGE_BOX_H;
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput o) {
    }
}