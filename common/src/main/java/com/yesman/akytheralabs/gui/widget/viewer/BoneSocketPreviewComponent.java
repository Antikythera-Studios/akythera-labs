package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.client.gui.widget.viewer.EntityModelComponent;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewer;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.gui.widget.viewer.Ray;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.akythera.entitypatch.component.BoneSocketHolder;
import com.yesman.akytheralabs.editor.historytrack.DraggingDataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.PendingDataChange;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/// The 3D marker for one [BoneSocketHolder]: a screen-facing dot on the bone the socket rides,
/// offset by the socket's local transform.
///
/// The gizmo is a *sibling* in the viewer, not a child of this component — [Model3DViewer#focus]
/// raises and binds one. This component holds no gizmo reference; it reports its two transforms and
/// takes the drag results back.
public final class BoneSocketPreviewComponent implements LocatableComponent {
    private static final int COLOR = 0xFFFFAA33;
    private static final int COLOR_FOCUSED = 0xFFFFDD88;

    /// On-screen radius of the marker, in widget pixels. Also its click radius.
    private static final float MARKER_PIXELS = 5.0F;

    private final BoneSocketHolder socket;
    private final EntityModelComponent model;

    /// Rebuilds the Details rows once a drag commits. Not per drag step: re-filling the row table
    /// reallocates every widget in it.
    private final Runnable onCommitted;

    /// Reports the focus to the Components tree, so picking a marker in the 3D view moves the row
    /// selection with it.
    private final Consumer<BoneSocketHolder> onPicked;

    private final Consumer<BoneSocketHolder> optionsOpener;

    private boolean focused;
    private boolean dragging;

    public BoneSocketPreviewComponent(
        BoneSocketHolder socket,
        EntityModelComponent model,
        Runnable onCommitted,
        Consumer<BoneSocketHolder> onPicked,
        Consumer<BoneSocketHolder> optionsOpener
    ) {
        this.socket = socket;
        this.model = model;
        this.onCommitted = onCommitted;
        this.onPicked = onPicked;
        this.optionsOpener = optionsOpener;
    }

    public BoneSocketHolder getSocket() {
        return socket;
    }

    /// ***************************************************************
    /// [LocatableComponent]
    /// ***************************************************************

    /// The bound bone at the animator's current pose. Resolved per call — the socket holds only its
    /// local offset.
    ///
    /// The armature's model space is already [Model3DViewer]'s world space: both stand the model
    /// upright with its feet on `y = 0`, and every model path converts into it as it draws.
    @Override
    public Transform parentTransform() {
        return model.getArmatureComponent().getBoneModelTransform(socket.getBoneName());
    }

    @Override
    public Transform localTransform() {
        return socket.getLocalTransform().makeObject();
    }

    @Override
    public void applyLocalTransform(Transform transform, boolean live) {
        if (live) {
            // One pending baseline per drag — the committed entry's verifyLastHistory pops exactly one.
            if (!dragging) {
                dragging = true;
                HistoryManager.getInstance().pushHistory(
                    new PendingDataChange<>(socket, localTransform(), this::writeTransform));
            }

            writeTransform(transform);
            return;
        }

        dragging = false;
        HistoryManager.getInstance().pushHistory(
            new DraggingDataChange<>(socket, transform, this::writeTransform));
        writeTransform(transform);
        onCommitted.run();
    }

    private void writeTransform(@Nullable Transform transform) {
        if (transform == null) return;

        socket.getLocalTransform().readFrom(transform);
    }

    /// ***************************************************************
    /// [Model3DViewerComponent]
    /// ***************************************************************

    @Override
    public boolean canBeFocused(final Model3DViewer owner) {
        return true;
    }

    /// Hit test against the marker's on-screen radius, returning the camera distance so the nearest
    /// socket wins an overlap.
    @Override
    public float rayTrace(final Model3DViewer owner, final Ray ray) {
        Vector3f world = worldOrigin();
        Vector3f toMarker = new Vector3f(world).sub(ray.origin());
        float along = toMarker.dot(ray.direction());

        if (along <= 0.0F) return -1.0F;

        float radius = markerRadius(owner, world);

        if (radius <= 0.0F) return -1.0F;

        return ray.at(along).distance(world) <= radius ? along : -1.0F;
    }

    @Override
    public void onFocused(final Model3DViewer owner) {
        LocatableComponent.super.onFocused(owner);

        focused = true;
        onPicked.accept(socket);
    }

    @Override
    public void onReleased(final Model3DViewer owner, final @Nullable Model3DViewerComponent newFocus) {
        LocatableComponent.super.onReleased(owner, newFocus);

        focused = false;
    }

    @Override
    public void showOptions(final Model3DViewer owner) {
        optionsOpener.accept(socket);
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer viewer, PoseStack poseStack,
        ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        Vector3f world = worldOrigin();
        float half = markerRadius(viewer, world);

        if (half <= 0.0F) return;

        Vector3f right = new Vector3f(viewer.getCameraRight()).mul(half);
        Vector3f up = new Vector3f(viewer.getCameraUp()).mul(half);

        RenderType renderType = RenderTypes.debugQuads();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());
        PoseStack.Pose pose = poseStack.last();
        int color = focused ? COLOR_FOCUSED : COLOR;

        addVertex(builder, pose, new Vector3f(world).sub(right).sub(up), color);
        addVertex(builder, pose, new Vector3f(world).add(right).sub(up), color);
        addVertex(builder, pose, new Vector3f(world).add(right).add(up), color);
        addVertex(builder, pose, new Vector3f(world).sub(right).add(up), color);

        MeshData meshData = builder.build();

        if (meshData != null) RenderUtils.drawImmediate(renderType, meshData);
    }

    private static void addVertex(BufferBuilder builder, PoseStack.Pose pose, Vector3f v, int color) {
        builder.addVertex(pose, v.x, v.y, v.z).setColor(color);
    }

    private Vector3f worldOrigin() {
        Transform world = worldTransform();
        return new Vector3f((float) world.loc().x, (float) world.loc().y, (float) world.loc().z);
    }

    /// [#MARKER_PIXELS] converted to world units at the marker's depth.
    private float markerRadius(Model3DViewer viewer, Vector3f world) {
        Vector3f origin = viewer.projectToWidget(world);
        Vector3f probe = viewer.projectToWidget(new Vector3f(world).add(viewer.getCameraRight()));

        if (origin == null || probe == null) return 0.0F;

        float pixelsPerUnit = (float) Math.hypot(probe.x - origin.x, probe.y - origin.y);

        return pixelsPerUnit < 1.0e-3F ? 0.0F : MARKER_PIXELS / pixelsPerUnit;
    }

    @Override
    public int drawOrder() {
        return 30; // after grid (1), model (10) and root-motion trajectory (20)
    }
}