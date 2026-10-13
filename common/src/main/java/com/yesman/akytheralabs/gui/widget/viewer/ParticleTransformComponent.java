package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.akythera.particle.ParticleBlueprintHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.editor.historytrack.DraggingDataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.PendingDataChange;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/// The mesh particle offset a [ParticleBlueprintHolder] holds, moved, turned and scaled by the gizmo from the
/// preview's spawn point. Focused by the Transform rows, see [EditorEditable#viewerFocus]; draws nothing of its own.
public final class ParticleTransformComponent implements LocatableComponent {
    private final ParticleBlueprintHolder holder;

    /// Runs after a committed gizmo drag, or its undo or redo, writes the offset.
    private final Runnable onTransformWritten;

    /// Whether a gizmo drag is in flight, whose baseline is already on the history stack.
    private boolean dragging;

    public ParticleTransformComponent(final ParticleBlueprintHolder holder, final Runnable onTransformWritten) {
        this.holder = holder;
        this.onTransformWritten = onTransformWritten;
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
    }

    @Override
    public int drawOrder() {
        return 0;
    }

    /// The gizmo restores the offset the drag started at, and no commit follows.
    @Override
    public void onDragCancelled(final Model3DViewer owner) {
        LocatableComponent.super.onDragCancelled(owner);
        dragging = false;
    }

    private void writeTransform(final @Nullable Transform transform) {
        if (transform == null) return;

        holder.getTransform().readFrom(transform);
        onTransformWritten.run();
    }

    // ***************************************************************
    // LocatableComponent
    // ***************************************************************

    @Override
    public Transform parentTransform() {
        return Transform.fromTranslation(ParticlePreviewComponent.ORIGIN);
    }

    @Override
    public Transform localTransform() {
        return holder.getTransform().makeObject();
    }

    @Override
    public void applyLocalTransform(final Transform transform, final boolean live) {
        if (live) {
            // One pending baseline per drag, popped by the committed entry
            if (!dragging) {
                dragging = true;
                HistoryManager.getInstance().pushHistory(new PendingDataChange<>(holder.getTransform(), localTransform(), this::writeTransform));
            }

            holder.getTransform().readFrom(transform);
            return;
        }

        dragging = false;
        HistoryManager.getInstance().pushHistory(new DraggingDataChange<>(holder.getTransform(), transform, this::writeTransform));
        writeTransform(transform);
    }
}
