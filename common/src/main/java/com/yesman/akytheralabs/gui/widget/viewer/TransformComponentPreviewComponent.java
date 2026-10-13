package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.api.gui.viewer.Ray;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.TransformComponentHolder;
import com.yesman.akytheralabs.editor.historytrack.DraggingDataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.PendingDataChange;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/// The 3D marker for one [TransformComponentHolder]: a screen-facing dot at the holder's transform, inside the
/// parent space its screen resolves.
///
/// The gizmo is a *sibling* in the viewer, not a child of this component — [Model3DViewer#focus]
/// raises and binds one. This component holds no gizmo reference; it reports its two transforms and
/// takes the drag results back.
public final class TransformComponentPreviewComponent<H extends ComponentDataHolder<?> & TransformComponentHolder> implements LocatableComponent {
    private static final int COLOR = 0xFFFFAA33;
    private static final int COLOR_FOCUSED = 0xFFFFDD88;

    /// On-screen radius of the marker, in widget pixels. Also its click radius.
    private static final float MARKER_PIXELS = 5.0F;

    private final H holder;

    /// The space [#holder]'s transform is in, resolved per call.
    private final Supplier<Transform> parentTransform;

    /// Rebuilds the Details rows once a drag commits. Not per drag step: re-filling the row table
    /// reallocates every widget in it.
    private final Runnable onCommitted;

    /// Reports the focus to the Components tree, so picking a marker in the 3D view moves the row
    /// selection with it.
    private final Consumer<H> onPicked;

    /// Opens the marker's options menu, null for a marker with none.
    private final @Nullable Consumer<H> optionsOpener;

    private boolean focused;
    private boolean dragging;

    public TransformComponentPreviewComponent(
        H holder,
        Supplier<Transform> parentTransform,
        Runnable onCommitted,
        Consumer<H> onPicked,
        @Nullable Consumer<H> optionsOpener
    ) {
        this.holder = holder;
        this.parentTransform = parentTransform;
        this.onCommitted = onCommitted;
        this.onPicked = onPicked;
        this.optionsOpener = optionsOpener;
    }

    public H getHolder() {
        return holder;
    }

    // ***************************************************************
    // [LocatableComponent]
    // ***************************************************************

    /// The armature's model space is already [Model3DViewer]'s world space: both stand the model
    /// upright with its feet on `y = 0`, and every model path converts into it as it draws.
    @Override
    public Transform parentTransform() {
        return parentTransform.get();
    }

    @Override
    public Transform localTransform() {
        return holder.getLocalTransform().makeObject();
    }

    @Override
    public TransformInheritPolicy transformInheritPolicy() {
        return holder.getTransformInheritPolicy();
    }

    @Override
    public void applyLocalTransform(Transform transform, boolean live) {
        if (live) {
            // One pending baseline per drag — the committed entry's verifyLastHistory pops exactly one.
            if (!dragging) {
                dragging = true;
                HistoryManager.getInstance().pushHistory(
                    new PendingDataChange<>(holder, localTransform(), this::writeTransform));
            }

            writeTransform(transform);
            return;
        }

        dragging = false;
        HistoryManager.getInstance().pushHistory(
            new DraggingDataChange<>(holder, transform, this::writeTransform));
        writeTransform(transform);
        onCommitted.run();
    }

    private void writeTransform(@Nullable Transform transform) {
        if (transform == null) return;

        holder.getLocalTransform().readFrom(transform);
    }

    // ***************************************************************
    // [Model3DViewerComponent]
    // ***************************************************************

    @Override
    public boolean canBeFocused(final Model3DViewer owner) {
        return true;
    }

    /// Hit test against the marker's on-screen radius, returning the camera distance so the nearest
    /// marker wins an overlap.
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
        onPicked.accept(holder);
    }

    @Override
    public void onReleased(final Model3DViewer owner, final @Nullable Model3DViewerComponent newFocus) {
        LocatableComponent.super.onReleased(owner, newFocus);

        focused = false;
    }

    @Override
    public void showOptions(final Model3DViewer owner) {
        if (optionsOpener != null) optionsOpener.accept(holder);
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