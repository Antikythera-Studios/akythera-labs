package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Ray;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.akythera.cinema.CinemaActorHolder;
import com.yesman.akytheralabs.editor.historytrack.DraggingDataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.PendingDataChange;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/// The camera of one actor of a cinema scene in the preview: a pyramid from the eye to the view's frame, in the
/// space of the actor's entity at the playhead, or in the scene's space for a camera that does not follow the actor. The gizmo moves and turns [CinemaActorHolder#getCamera]. Drawn and
/// clickable only while [#shown] says so.
public final class CinemaCameraComponent implements LocatableComponent {
    private static final int COLOR = 0xFFFFAA33;

    /// How far ahead of the eye the frame is drawn, and half its height and width, in blocks.
    private static final float FRAME_DEPTH = 0.5F;
    private static final float FRAME_HALF_HEIGHT = 0.2F;
    private static final float FRAME_HALF_WIDTH = 0.35F;

    /// The actor's component, which places the camera's space.
    private final CinemaActorComponent actor;

    /// Runs after a committed gizmo drag, or its undo or redo, writes the transform.
    private final Runnable onTransformWritten;

    /// Whether the camera is drawn and takes clicks.
    private final BooleanSupplier shown;

    /// Whether a gizmo drag is in flight, whose baseline is already on the history stack.
    private boolean dragging;

    public CinemaCameraComponent(final CinemaActorComponent actor, final Runnable onTransformWritten, final BooleanSupplier shown) {
        this.actor = actor;
        this.onTransformWritten = onTransformWritten;
        this.shown = shown;
    }

    public CinemaActorHolder getActor() {
        return actor.getActor();
    }

    private void writeTransform(final @Nullable Transform transform) {
        if (transform == null) return;

        getActor().getCamera().readFrom(transform);
        onTransformWritten.run();
    }

    /// The pyramid's edges, each `{from, to}` in viewer world space: four from the eye, then the frame's four sides.
    private List<Vector3f[]> edges() {
        Transform eye = worldTransform();
        Quaternionf rotation = new Quaternionf(eye.rot());
        Vector3f apex = new Vector3f((float)eye.loc().x, (float)eye.loc().y, (float)eye.loc().z);
        Vector3f[] frame = new Vector3f[4];

        // Top left, top right, bottom right, bottom left
        for (int i = 0; i < frame.length; i++) {
            frame[i] = new Vector3f(
                i == 1 || i == 2 ? FRAME_HALF_WIDTH : -FRAME_HALF_WIDTH, i < 2 ? FRAME_HALF_HEIGHT : -FRAME_HALF_HEIGHT, -FRAME_DEPTH
            ).rotate(rotation).add(apex);
        }

        return List.of(
            new Vector3f[] { apex, frame[0] }, new Vector3f[] { apex, frame[1] },
            new Vector3f[] { apex, frame[2] }, new Vector3f[] { apex, frame[3] },
            new Vector3f[] { frame[0], frame[1] }, new Vector3f[] { frame[1], frame[2] },
            new Vector3f[] { frame[2], frame[3] }, new Vector3f[] { frame[3], frame[0] }
        );
    }

    // ***************************************************************
    // LocatableComponent
    // ***************************************************************

    @Override
    public Transform parentTransform() {
        return getActor().isCameraFollowsActor() ? actor.entityTransform() : Transform.IDENTITY;
    }

    @Override
    public Transform localTransform() {
        return getActor().getCamera().makeObject();
    }

    @Override
    public void applyLocalTransform(final Transform transform, final boolean live) {
        if (live) {
            // One pending baseline per drag, popped by the committed entry
            if (!dragging) {
                dragging = true;
                HistoryManager.getInstance().pushHistory(new PendingDataChange<>(getActor().getCamera(), localTransform(), this::writeTransform));
            }

            getActor().getCamera().readFrom(transform);
            return;
        }

        dragging = false;
        HistoryManager.getInstance().pushHistory(new DraggingDataChange<>(getActor().getCamera(), transform, this::writeTransform));
        writeTransform(transform);
    }

    @Override
    public Set<GizmoComponent.Mode> gizmoModes() {
        return EnumSet.of(GizmoComponent.Mode.TRANSLATE, GizmoComponent.Mode.ROTATE);
    }

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
        return shown.getAsBoolean() ? CameraFrustumComponent.pickSegments(owner, ray, edges()) : -1.0F;
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        if (!shown.getAsBoolean()) return;

        RenderType renderType = RenderTypes.lines();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());

        for (Vector3f[] edge : edges()) {
            CameraFrustumComponent.addSegment(builder, poseStack.last(), edge[0], edge[1], COLOR);
        }

        MeshData meshData = builder.build();

        if (meshData != null) RenderUtils.drawImmediate(renderType, meshData);
    }

    @Override
    public int drawOrder() {
        return 30; // with the focus markers, after grid (1), model (10) and root-motion trajectory (20)
    }
}
