package com.yesman.akytheralabs.gui.widget.animation;

import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.akythera.animation.NotifyTrackHolder;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/// A draggable point-in-time notify marker. A drag moves an [AnimationTrackEditorPanel.NotifyGhost], and the notify
/// takes its place on release, on whichever notify track it was last over. The owning
/// [NotifyRow] sets its X and Y through [#updatePosition] just before each render pass, the row being what knows its
/// own current top.
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

    /// Aligns this marker with the row's current top and the latest zoomed `frameToX` mapping.
    public void updatePosition(int rowTop, int rowHeight) {
        setX(parent.frameToX(model.time * 60.0F));
        setY(rowTop + (rowHeight - FLAG_H) / 2);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX();
        int y = getY();

        drawFlag(g, x, y, colorFor(model.holder.logicalSide()));

        // Selection outline
        if (parent.getSelected() instanceof NotifyTrackHolder.NotifyRef.Point p && p.mutableNotify() == model) {
            g.outline(x - 1, y - 1, FLAG_W + 1, FLAG_H + 1, 0xFFFFEE55);
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            NotifyTrackHolder.NotifyRef.Point dragged = new NotifyTrackHolder.NotifyRef.Point(track, model);

            parent.setSelected(dragged);
            parent.setGhost(new AnimationTrackEditorPanel.NotifyGhost(dragged, placementIn(track, model.time)));

            return true;
        }

        return false;
    }

    /// Moves the ghost to the cursor's time, on the notify track under it.
    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (event.button() != 0) return false;

        AnimationTrackEditorPanel.NotifyGhost ghost = parent.ghost();

        if (ghost != null && ghost.drags(model)) {
            NotifyTrackHolder.Track hovered = parent.notifyTrackAt(event.x(), event.y());
            float time = Math.clamp(parent.xToFrame(event.x()), 0F, parent.totalFrames() * 60.0F) / 60.0F;

            parent.setGhost(new AnimationTrackEditorPanel.NotifyGhost(
                ghost.dragged(), placementIn(hovered == null ? ghost.placement().track() : hovered, time)
            ));
        }

        return true;
    }

    /// Moves the notify to the ghost, unless a right press cancelled the drag.
    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        AnimationTrackEditorPanel.NotifyGhost ghost = parent.ghost();

        if (event.button() == 0 && ghost != null && ghost.drags(model)) {
            AnimationTrackEditorPanel.NotifyPlacement placed = placementIn(track, model.time);

            parent.setGhost(null);

            if (!ghost.placement().equals(placed)) {
                parent.placeNotify(model, ghost.placement());

                HistoryManager.getInstance().pushHistory(new DataChange<>(
                    placed, ghost.placement(), placement -> parent.placeNotify(model, placement)
                ));
            }
        }

        return super.mouseReleased(event);
    }

    /// The notify at `time` in `target`: at its own index in its own track, else at the end.
    private AnimationTrackEditorPanel.NotifyPlacement placementIn(final NotifyTrackHolder.Track target, final float time) {
        int index = target == track ? track.notifies.indexOf(model) : target.notifies.size();

        return new AnimationTrackEditorPanel.NotifyPlacement(target, index, time, time);
    }

    /// Draws a notify's flag with its top left at `x` / `y`.
    static void drawFlag(final GuiGraphicsExtractor g, final int x, final int y, final int color) {
        // Pole
        g.fill(x, y, x + FLAG_W / 2 - 1, y + FLAG_H, color);
        // Flag tab
        g.fill(x, y, x + FLAG_W, y + FLAG_H / 2, color);
    }

    /// [#colorFor] at half opacity, for a ghost.
    static int ghostColorFor(LogicalSide side) {
        return colorFor(side) & 0x00FFFFFF | 0x80000000;
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