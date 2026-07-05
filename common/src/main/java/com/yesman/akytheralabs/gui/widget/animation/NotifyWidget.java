package com.yesman.akytheralabs.gui.widget.animation;

import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.dataholder.animation.NotifyTrackHolder;
import com.yesman.akytheralabs.editor.historytrack.DraggingDataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.PendingDataChange;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/// Point-in-time mutableNotify draggable child. The owning [NotifyRow] sets
/// our X/Y just before each render pass via [#updatePosition]; we don't compute
/// our own row Y because only the row knows its current top.
public final class NotifyWidget extends AbstractWidget {
    public static final int FLAG_W = 8;
    public static final int FLAG_H = 14;

    private final AnimationTrackEditorPanel parent;
    private final NotifyTrackHolder.Track track;
    private final NotifyTrackHolder.MutableNotify model;

    public NotifyWidget(AnimationTrackEditorPanel parent, NotifyTrackHolder.Track track, NotifyTrackHolder.MutableNotify model) {
        super(0, 0, FLAG_W, FLAG_H, Component.empty());
        this.parent = parent;
        this.track = track;
        this.model = model;
    }

    public NotifyTrackHolder.MutableNotify model() {
        return model;
    }

    /// Called by the entry every render to align with the row's current top
    /// and the latest zoomed `frameToX` mapping.
    public void updatePosition(int rowTop, int rowHeight) {
        setX(parent.frameToX(model.time));
        setY(rowTop + (rowHeight - FLAG_H) / 2);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int color = colorFor(model.holder.logicalSide());
        int x = getX();
        int y = getY();

        // Pole
        g.fill(x, y, x + FLAG_W / 2 - 1, y + FLAG_H, color);
        // Flag tab
        g.fill(x, y, x + FLAG_W, y + FLAG_H / 2, color);

        // Selection outline
        if (parent.getSelected() instanceof NotifyTrackHolder.NotifyRef.Point p && p.mutableNotify() == model) {
            g.outline(x - 1, y - 1, FLAG_W + 1, FLAG_H + 1, 0xFFFFEE55);
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            parent.setSelected(new NotifyTrackHolder.NotifyRef.Point(track, model));

            HistoryManager.getInstance().pushHistory(new PendingDataChange<>(
                model, model.time, v -> model.time = v
            ));

            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (event.button() == 0) {
            float total = parent.totalFrames() * 60.0F;
            float newTime = parent.xToFrame(event.x());
            model.time = Math.clamp(newTime, 0F, total);

            parent.refreshSelectionView();

            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (event.button() == 0) {
            HistoryManager.getInstance().pushHistory(new DraggingDataChange<>(
                model, model.time, v -> model.time = v
            ));
        }

        return super.mouseReleased(event);
    }

    static int colorFor(LogicalSide side) {
        return switch (side) {
            case CLIENT -> 0xFF4488EE;
            case SERVER -> 0xFFEE4444;
            case BOTH -> 0xFFAA66EE;
        };
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput out) {
    }
}