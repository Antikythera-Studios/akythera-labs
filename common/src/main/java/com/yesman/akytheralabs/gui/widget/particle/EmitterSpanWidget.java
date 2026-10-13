package com.yesman.akytheralabs.gui.widget.particle;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.yesman.akytheralabs.akythera.particle.ParticleEmitterHolder;
import com.yesman.akytheralabs.editor.historytrack.DraggingDataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.PendingDataChange;
import com.yesman.akytheralabs.gui.widget.animation.AnimationTrackEditorPanel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// One emitter's time on the emitter track: a bar from its start over its time span, or a marker at its start for an
/// instant emitter. Dragging the body moves the start, keys and all; dragging an edge of a time span resizes it, the
/// left edge keeping its end and keys where they are. An edge stops at the outermost key. A looping span trails a faint
/// tail to the track's end. One drag is one undo step.
public final class EmitterSpanWidget extends AbstractWidget {
    public static final int BAR_H = 14;

    /// The width of an instant emitter's marker, in pixels.
    private static final int MARKER_W = 6;

    private static final int SPAN_COLOR = 0xFF4A86C8;
    private static final int INSTANT_COLOR = 0xFFD9A441;
    private static final int LOOP_TAIL_COLOR = 0x444A86C8;
    private static final int SELECTED_COLOR = 0xFFFFEE55;

    private enum DragMode { NONE, LEFT_EDGE, RIGHT_EDGE, BODY }

    private final EmitterTrackPanel parent;
    private final ParticleEmitterHolder emitter;

    private DragMode dragMode = DragMode.NONE;

    /// Where the drag last grabbed the bar, in seconds.
    private float grabTime;

    public EmitterSpanWidget(final EmitterTrackPanel parent, final ParticleEmitterHolder emitter) {
        super(0, 0, 0, BAR_H, Component.empty());

        this.parent = parent;
        this.emitter = emitter;
    }

    /// Recomputes the bounds from the emitter's start and span and the row's current top.
    public void updatePosition(final int rowTop, final int rowHeight) {
        int startX = parent.frameToX(emitter.getStart() * 60.0F);
        boolean instant = emitter.getSpawnType() == ParticleEmitterHolder.SpawnType.INSTANT;

        setX(instant ? startX - MARKER_W / 2 : startX);
        setY(rowTop + (rowHeight - BAR_H) / 2);
        setWidth(instant ? MARKER_W : Math.max(2, parent.frameToX((emitter.getStart() + emitter.getSpan()) * 60.0F) - startX));
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x1 = getX();
        int x2 = x1 + getWidth();
        int y = getY();
        boolean instant = emitter.getSpawnType() == ParticleEmitterHolder.SpawnType.INSTANT;

        if (emitter.loops()) g.fill(x2, y + 3, parent.getRight(), y + BAR_H - 3, LOOP_TAIL_COLOR);

        g.fill(x1, y, x2, y + BAR_H, instant ? INSTANT_COLOR : SPAN_COLOR);

        if (!instant) {
            g.fill(x1, y, x1 + 2, y + BAR_H, 0xFF000000);
            g.fill(x2 - 2, y, x2, y + BAR_H, 0xFF000000);
        }

        if (parent.getSelected() == emitter) {
            g.fill(x1 - 1, y - 1, x2 + 1, y, SELECTED_COLOR);
            g.fill(x1 - 1, y + BAR_H, x2 + 1, y + BAR_H + 1, SELECTED_COLOR);
            g.fill(x1 - 1, y, x1, y + BAR_H, SELECTED_COLOR);
            g.fill(x2, y, x2 + 1, y + BAR_H, SELECTED_COLOR);
        }

        if (!instant && (edgeAt(mouseX, mouseY) != DragMode.NONE || dragMode == DragMode.LEFT_EDGE || dragMode == DragMode.RIGHT_EDGE)) {
            g.requestCursor(CursorTypes.RESIZE_EW);
        }
    }

    /// The edge of a time span `mouseX` / `mouseY` is on, or [DragMode#NONE].
    private DragMode edgeAt(final double mouseX, final double mouseY) {
        if (emitter.getSpawnType() == ParticleEmitterHolder.SpawnType.INSTANT || mouseY < getY() || mouseY >= getY() + BAR_H) return DragMode.NONE;

        int threshold = AnimationTrackEditorPanel.EDGE_DRAG_THRESHOLD;

        if (Math.abs(mouseX - getX()) <= threshold) return DragMode.LEFT_EDGE;
        if (Math.abs(mouseX - (getX() + getWidth())) <= threshold) return DragMode.RIGHT_EDGE;

        return DragMode.NONE;
    }

    @Override
    public boolean isMouseOver(final double mouseX, final double mouseY) {
        return super.isMouseOver(mouseX, mouseY) || edgeAt(mouseX, mouseY) != DragMode.NONE;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;

        DragMode edge = edgeAt(event.x(), event.y());

        dragMode = edge != DragMode.NONE ? edge : DragMode.BODY;
        grabTime = parent.xToFrame(event.x()) / 60.0F;
        parent.setSelected(emitter);
        HistoryManager.getInstance().pushHistory(new PendingDataChange<>(emitter, emitter.getTimeline(), this::writeTimeline));

        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        float time = Math.max(0.0F, parent.xToFrame(event.x()) / 60.0F);

        switch (dragMode) {
            case LEFT_EDGE -> emitter.setStartKeepingEnd(time);
            case RIGHT_EDGE -> emitter.setDuration(time - emitter.getStart());
            case BODY -> {
                emitter.setStart(emitter.getStart() + time - grabTime);
                grabTime = time;
            }
            case NONE -> {
                return false;
            }
        }

        parent.refreshSelectionView();

        return true;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (dragMode == DragMode.NONE) return false;

        dragMode = DragMode.NONE;
        HistoryManager.getInstance().pushHistory(new DraggingDataChange<>(emitter, emitter.getTimeline(), this::writeTimeline));

        return false;
    }

    /// Writes a timeline back, as an undo or redo does.
    private void writeTimeline(final ParticleEmitterHolder.@Nullable Timeline timeline) {
        if (timeline == null) return;

        emitter.setTimeline(timeline);
        parent.refreshSelectionView();
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput o) {
    }
}
