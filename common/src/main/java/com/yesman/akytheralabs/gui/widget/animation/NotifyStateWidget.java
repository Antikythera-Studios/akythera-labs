package com.yesman.akytheralabs.gui.widget.animation;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.yesman.akytheralabs.dataholder.animation.NotifyTrackHolder;
import com.yesman.akytheralabs.editor.historytrack.DraggingDataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.NotifyStateRangeChange;
import com.yesman.akytheralabs.editor.historytrack.PendingDataChange;
import com.yesman.akytheralabs.editor.historytrack.PendingNotifyStateRangeChange;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/// Windowed mutableNotify-state widget. Three drag modes: drag the left edge, the
/// right edge, or the body to translate.
public final class NotifyStateWidget extends AbstractWidget {
    public static final int BAR_H = 14;

    /// Minimum window width — 3 frames ≈ 0.05s ≈ one Minecraft tick. Anything
    /// shorter is functionally a single-frame trigger and should be authored
    /// as a [AnimationNotify], not a state. Enforced at edge-resize time.
    public static final float MIN_STATE_FRAMES = 3.0F;

    private enum DragMode { NONE, MOVE_LEFT_EDGE, MOVE_RIGHT_EDGE, MOVE_WINDOW }

    private final AnimationTrackEditorPanel parent;
    private final NotifyTrackHolder.Track track;
    private final NotifyTrackHolder.MutableNotifyState model;

    private DragMode dragMode = DragMode.NONE;
    private float dragGrabFrame;

    public NotifyStateWidget(AnimationTrackEditorPanel parent, NotifyTrackHolder.Track track, NotifyTrackHolder.MutableNotifyState model) {
        super(0, 0, 0, BAR_H, Component.empty());
        this.parent = parent;
        this.track = track;
        this.model = model;
    }

    public NotifyTrackHolder.MutableNotifyState model() {
        return model;
    }

    public NotifyTrackHolder.Track track() {
        return track;
    }

    /// Recomputes the widget's bounds from the model's frame window and the row's
    /// current top. Called by the entry every render.
    public void updatePosition(int rowTop, int rowHeight) {
        int x1 = parent.frameToX(model.from);
        int x2 = parent.frameToX(model.to);
        setX(x1);
        setY(rowTop + (rowHeight - BAR_H) / 2);
        setWidth(Math.max(2, x2 - x1));
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int color = NotifyWidget.colorFor(model.holder.logicalSide());
        int x1 = getX();
        int x2 = x1 + getWidth();
        int y = getY();

        g.fill(x1, y, x2, y + BAR_H, color);
        // Edge highlights
        g.fill(x1, y, x1 + 2, y + BAR_H, 0xFF000000);
        g.fill(x2 - 2, y, x2, y + BAR_H, 0xFF000000);

        // Selection outline
        if (parent.getSelected() instanceof NotifyTrackHolder.NotifyRef.State s && s.mutableNotifyState() == model) {
            g.fill(x1 - 1, y - 1, x2 + 1, y, 0xFFFFEE55);
            g.fill(x1 - 1, y + BAR_H, x2 + 1, y + BAR_H + 1, 0xFFFFEE55);
            g.fill(x1 - 1, y, x1, y + BAR_H, 0xFFFFEE55);
            g.fill(x2, y, x2 + 1, y + BAR_H, 0xFFFFEE55);
        }

        // Cursor affordance: resize when hovering an edge, regardless of selection.
        int threshold = AnimationTrackEditorPanel.EDGE_DRAG_THRESHOLD;
        boolean inRow = mouseY >= y && mouseY < y + BAR_H;
        boolean nearLeft = inRow && Math.abs(mouseX - x1) <= threshold;
        boolean nearRight = inRow && Math.abs(mouseX - x2) <= threshold;
        if (nearLeft || nearRight || dragMode == DragMode.MOVE_LEFT_EDGE || dragMode == DragMode.MOVE_RIGHT_EDGE) {
            g.requestCursor(CursorTypes.RESIZE_EW);
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;

        int leftX = parent.frameToX(model.from);
        int rightX = parent.frameToX(model.to);
        int threshold = AnimationTrackEditorPanel.EDGE_DRAG_THRESHOLD;

        if (Math.abs(event.x() - leftX) <= threshold) dragMode = DragMode.MOVE_LEFT_EDGE;
        else if (Math.abs(event.x() - rightX) <= threshold) dragMode = DragMode.MOVE_RIGHT_EDGE;
        else dragMode = DragMode.MOVE_WINDOW;

        dragGrabFrame = parent.xToFrame(event.x());
        parent.setSelected(new NotifyTrackHolder.NotifyRef.State(track, model));

        switch (dragMode) {
            case MOVE_LEFT_EDGE -> HistoryManager.getInstance().pushHistory(new PendingDataChange<>(
                DragMode.MOVE_LEFT_EDGE, model.from, v -> model.from = v
            ));
            case MOVE_RIGHT_EDGE -> HistoryManager.getInstance().pushHistory(new PendingDataChange<>(
                DragMode.MOVE_RIGHT_EDGE, model.to, v -> model.to = v
            ));
            case MOVE_WINDOW -> HistoryManager.getInstance().pushHistory(new PendingNotifyStateRangeChange(
                model, model.from, model.to
            ));
            default -> {}
        }

        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        float frame = Math.max(0F, parent.xToFrame(event.x()));

        switch (dragMode) {
            case MOVE_LEFT_EDGE -> model.from = Math.min(frame, model.to - MIN_STATE_FRAMES);
            case MOVE_RIGHT_EDGE -> model.to = Math.max(frame, model.from + MIN_STATE_FRAMES);
            case MOVE_WINDOW -> {
                float delta = frame - dragGrabFrame;
                float width = model.to - model.from;
                float newFrom = Math.max(0F, model.from + delta);
                model.from = newFrom;
                model.to = newFrom + width;
                dragGrabFrame = frame;
            }
            default -> {
                return false;
            }
        }

        parent.refreshSelectionView();

        return true;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        switch (dragMode) {
            case MOVE_LEFT_EDGE -> HistoryManager.getInstance().pushHistory(new DraggingDataChange<>(
                DragMode.MOVE_LEFT_EDGE, model.from, v -> model.from = v
            ));
            case MOVE_RIGHT_EDGE -> HistoryManager.getInstance().pushHistory(new DraggingDataChange<>(
                DragMode.MOVE_RIGHT_EDGE, model.to, v -> model.to = v
            ));
            case MOVE_WINDOW -> HistoryManager.getInstance().pushHistory(new NotifyStateRangeChange(
                model, model.from, model.to
            ));
            default -> {}
        }

        dragMode = DragMode.NONE;
        return false;
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput o) {}
}