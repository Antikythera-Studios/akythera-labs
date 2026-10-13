package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.api.gui.viewer.Ray;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.akythera.particle.ParticleEmitterHolder;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.historytrack.DraggingDataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.PendingDataChange;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/// A wireframe marker of one emitter in the particle system preview, at a transform the gizmo moves, turns and scales.
/// Drawn and clickable only while [#shown] says so. A click focuses it and picks its emitter. The system plays at the world's origin, so its space is the viewer's world
/// space.
public abstract class EmitterMarkerComponent implements LocatableComponent {
    /// How far from the marker's center a click still hits it, in blocks.
    private static final float PICK_RADIUS = 0.15F;

    protected final ParticleEmitterHolder emitter;

    /// Told the emitter when a click focuses this marker.
    private final Consumer<ParticleEmitterHolder> onPicked;

    /// Runs after a committed gizmo drag, or its undo or redo, writes the transform.
    private final Runnable onTransformWritten;

    /// Whether the marker is drawn and takes clicks.
    private final BooleanSupplier shown;

    private final int color;
    private final int focusedColor;

    private boolean focused;

    /// Whether a gizmo drag is in flight, whose baseline is already on the history stack.
    private boolean dragging;

    protected EmitterMarkerComponent(
        final ParticleEmitterHolder emitter,
        final Consumer<ParticleEmitterHolder> onPicked,
        final Runnable onTransformWritten,
        final BooleanSupplier shown,
        final int color,
        final int focusedColor
    ) {
        this.emitter = emitter;
        this.onPicked = onPicked;
        this.onTransformWritten = onTransformWritten;
        this.shown = shown;
        this.color = color;
        this.focusedColor = focusedColor;
    }

    public ParticleEmitterHolder getEmitter() {
        return emitter;
    }

    /// The transform the gizmo edits, asked on every use since a read of the emitter replaces it.
    protected abstract TransformHolder editedTransform();

    /// Emits the marker's lines. `world` is the marker's transform in world space without its scale.
    protected abstract void emitLines(VertexConsumer consumer, PoseStack.Pose pose, Transform world, int color);

    private void writeTransform(final @Nullable Transform transform) {
        if (transform == null) return;

        editedTransform().readFrom(transform);
        onTransformWritten.run();
    }

    // ***************************************************************
    // LocatableComponent
    // ***************************************************************

    @Override
    public Transform localTransform() {
        return editedTransform().makeObject();
    }

    @Override
    public void applyLocalTransform(final Transform transform, final boolean live) {
        if (live) {
            // One pending baseline per drag, popped by the committed entry
            if (!dragging) {
                dragging = true;
                HistoryManager.getInstance().pushHistory(new PendingDataChange<>(editedTransform(), localTransform(), this::writeTransform));
            }

            editedTransform().readFrom(transform);
            return;
        }

        dragging = false;
        HistoryManager.getInstance().pushHistory(new DraggingDataChange<>(editedTransform(), transform, this::writeTransform));
        writeTransform(transform);
    }

    /// The gizmo restores the transform the drag started at, and no commit follows.
    @Override
    public void onDragCancelled(final Model3DViewer owner) {
        LocatableComponent.super.onDragCancelled(owner);
        dragging = false;
    }

    // ***************************************************************
    // Model3DViewerComponent
    // ***************************************************************

    @Override
    public boolean canBeFocused(final Model3DViewer owner) {
        return shown.getAsBoolean();
    }

    @Override
    public float rayTrace(final Model3DViewer owner, final Ray ray) {
        if (!shown.getAsBoolean()) return -1.0F;

        Vec3 loc = worldTransform().loc();
        Vector3f center = new Vector3f((float)loc.x, (float)loc.y, (float)loc.z);
        float along = new Vector3f(center).sub(ray.origin()).dot(ray.direction());

        return along > 0.0F && ray.at(along).distance(center) <= PICK_RADIUS ? along : -1.0F;
    }

    @Override
    public void onFocused(final Model3DViewer owner) {
        LocatableComponent.super.onFocused(owner);

        focused = true;
        onPicked.accept(emitter);
    }

    @Override
    public void onReleased(final Model3DViewer owner, final @Nullable Model3DViewerComponent newFocus) {
        LocatableComponent.super.onReleased(owner, newFocus);

        focused = false;
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        if (!shown.getAsBoolean()) return;

        Transform world = worldTransform();
        RenderType renderType = RenderTypes.lines();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());

        emitLines(builder, poseStack.last(), Transform.of(world.loc(), world.rot(), new Vec3(1.0D, 1.0D, 1.0D)), focused ? focusedColor : color);

        MeshData meshData = builder.build();

        if (meshData != null) RenderUtils.drawImmediate(renderType, meshData);
    }

    @Override
    public int drawOrder() {
        return 40; // over the particles (30)
    }
}
