package com.yesman.akytheralabs.gui.widget.cinema;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.yesman.akytheralabs.akythera.cinema.CinemaSceneHolder;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/// The red line of the cinema track, where the scene stops placing its actors: a grip in the ruler over a line down
/// the rows. Dragging the grip moves [CinemaSceneHolder#getDuration] along the time axis. One drag is one undo step.
/// The widget's bounds are the grip.
public final class CinemaSceneEndMarker extends AbstractWidget {
    private static final int COLOR = 0xFFE04848;
    private static final int GRIP_W = 5;

    private final CinemaTrackPanel parent;
    private final CinemaSceneHolder holder;

    /// Where the line ends, in screen coordinates.
    private int lineBottom;

    private boolean dragging;

    /// The scene's duration when the drag in progress started, in seconds.
    private float dragStart;

    public CinemaSceneEndMarker(final CinemaTrackPanel parent, final CinemaSceneHolder holder) {
        super(0, 0, GRIP_W + 2, 0, Component.empty());

        this.parent = parent;
        this.holder = holder;
    }

    /// Recomputes the bounds from the scene's duration: the grip from `gripTop` to `gripBottom`, over a line down to
    /// `lineBottom`.
    public void updatePosition(final int gripTop, final int gripBottom, final int lineBottom) {
        setX(parent.frameToX(holder.getDuration() * 60.0F) - GRIP_W / 2 - 1);
        setY(gripTop);
        setHeight(gripBottom - gripTop);
        this.lineBottom = lineBottom;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int lineX = getX() + GRIP_W / 2 + 1;

        g.fill(lineX, getBottom(), lineX + 1, lineBottom, COLOR);
        g.fill(getX() + 1, getY(), getRight() - 1, getBottom(), COLOR);

        if (dragging || isMouseOver(mouseX, mouseY)) g.requestCursor(CursorTypes.RESIZE_EW);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;

        dragging = true;
        dragStart = holder.getDuration();

        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (!dragging) return false;

        holder.setDuration(parent.xToFrame(event.x()) / 60.0F);
        parent.refreshSelectionView();

        return true;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (!dragging) return false;

        float from = dragStart;
        float to = holder.getDuration();

        dragging = false;

        if (from != to) {
            HistoryManager.getInstance().pushHistory(new WorkHistory() {
                @Override
                public void undo() {
                    holder.setDuration(from);
                    parent.refreshSelectionView();
                }

                @Override
                public void redo() {
                    holder.setDuration(to);
                    parent.refreshSelectionView();
                }
            });
        }

        return true;
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput o) {
    }
}
