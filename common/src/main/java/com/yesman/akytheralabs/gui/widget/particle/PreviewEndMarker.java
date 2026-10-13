package com.yesman.akytheralabs.gui.widget.particle;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.yesman.akytheralabs.akythera.particle.ParticleSystemHolder;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/// The red line of the emitter track, where the editor's preview ends: a grip in the ruler over a line down the rows.
/// Dragging the grip moves the end along the time axis, see [ParticleSystemHolder#setPreviewEnd]. One drag is one
/// undo step that does not count as an unsaved change. The widget's bounds are the grip.
public final class PreviewEndMarker extends AbstractWidget {
    private static final int COLOR = 0xFFE04848;
    private static final int GRIP_W = 5;

    private final EmitterTrackPanel parent;
    private final ParticleSystemHolder holder;

    /// Where the line ends, in screen coordinates.
    private int lineBottom;

    private boolean dragging;

    /// The preview's end when the drag in progress started, in seconds.
    private float dragStart;

    public PreviewEndMarker(final EmitterTrackPanel parent, final ParticleSystemHolder holder) {
        super(0, 0, GRIP_W + 2, 0, Component.empty());

        this.parent = parent;
        this.holder = holder;
    }

    /// Recomputes the bounds from the preview's end: the grip from `gripTop` to `gripBottom`, over a line down to
    /// `lineBottom`.
    public void updatePosition(final int gripTop, final int gripBottom, final int lineBottom) {
        setX(parent.frameToX(holder.getPreviewEnd() * 60.0F) - GRIP_W / 2 - 1);
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
        dragStart = holder.getPreviewEnd();

        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (!dragging) return false;

        holder.setPreviewEnd(parent.xToFrame(event.x()) / 60.0F);

        return true;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (!dragging) return false;

        float from = dragStart;
        float to = holder.getPreviewEnd();

        dragging = false;

        if (from != to) {
            HistoryManager.getInstance().pushHistory(new WorkHistory() {
                @Override
                public void undo() {
                    holder.setPreviewEnd(from);
                }

                @Override
                public void redo() {
                    holder.setPreviewEnd(to);
                }

                @Override
                public boolean changesData() {
                    return false; // a preview setting
                }
            });
        }

        return true;
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput o) {
    }
}
