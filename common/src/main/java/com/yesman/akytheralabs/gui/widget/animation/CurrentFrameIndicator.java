package com.yesman.akytheralabs.gui.widget.animation;

import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/// Draws the playhead — a vertical green line spanning the panel and a small
/// orange drag handle near the top — and routes mouse events for scrubbing. A press
/// anywhere on the handle's row over the track moves the playhead there and drags it. From the press to the release
/// is one [PlayheadMove] undo step.
public final class CurrentFrameIndicator extends AbstractWidget {
    private static final int ORANGE_BOX_W = 7;
    private static final int ORANGE_BOX_H = 12;
    private static final int ORANGE_BOX_TOP_OFFSET = 12;

    private final TrackTimeline parent;
    private boolean draggingBox;

    /// The frame the playhead was at when the press in progress landed.
    private float pressFrame;

    public CurrentFrameIndicator(TrackTimeline parent) {
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

        Font font = Minecraft.getInstance().font;
        g.centeredText(font, parent.playheadLabel(), x + 1, getY() + 2, 0xFFEEEEEE);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0 || parent.isDebugDriven()) return false;

        if (!isMouseOver(event.x(), event.y())) return false;

        int x = parent.frameToX(parent.currentFrame());

        pressFrame = parent.currentFrame();

        if (Math.abs(event.x() - x) > ORANGE_BOX_W / 2) parent.setCurrentFrame(parent.xToFrame(event.x()));

        draggingBox = true;

        return true;
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

        if (wasDragging && parent.currentFrame() != pressFrame) {
            HistoryManager.getInstance().pushHistory(new PlayheadMove(parent, pressFrame, parent.currentFrame()));
        }

        return wasDragging;
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        // Only consume mouse events on the orange box's row over the track; let the rest
        // fall through to the track list / sub-widgets.
        if (!visible || !active) return false;
        int boxY = getY() + ORANGE_BOX_TOP_OFFSET;
        int x1 = parent.frameToX(parent.visualFrameStart()) - ORANGE_BOX_W / 2;
        int x2 = parent.frameToX(parent.visualFrameEnd()) + ORANGE_BOX_W / 2;
        return mx >= x1 && mx < x2 && my >= boxY && my < boxY + ORANGE_BOX_H;
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput o) {
    }
}