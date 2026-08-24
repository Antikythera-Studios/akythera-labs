package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewer;
import com.yesman.akythera.client.gui.widget.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.gui.widget.viewer.Ray;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import com.yesman.akytheralabs.AkytheraLabs;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Draws an armature's bind pose over whatever the viewer already holds: an octahedron per bone,
/// running from its own head to its tail, and a screen-sized marker at every head. Clicking either
/// selects that bone.
///
/// The armature stores no tail — only a local transform per bone — so a bone's tail is taken as the
/// midpoint of its children's heads. That is exact for the single-child case, which is most of a
/// humanoid rig, and averages the split for a bone that forks. A childless bone has nothing to
/// average, so its shape carries on in the direction it arrived from, at [#LEAF_LENGTH_RATIO] of
/// that offset.
public final class ArmatureViewComponent implements Model3DViewerComponent {
    private static final int COLOR_BONE = 0xFFDDDDDD;
    private static final int COLOR_SELECTED = 0xFFFFA028;
    private static final float LINE_WIDTH = 2.0F;
    private static final float SELECTED_LINE_WIDTH = 3.0F;

    /// Where the octahedron's widest ring sits along the bone, and how far it stands off the axis
    /// there. Both as a fraction of the bone's length.
    private static final float RING_ALONG = 0.1F;
    private static final float RING_RADIUS = 0.1F;

    /// On-screen radius of a bone-head marker, in widget pixels. Also its click radius.
    private static final float MARKER_PIXELS = 3.0F;

    /// How far off a bone's projected axis a click still counts as a hit, in widget pixels.
    private static final float PICK_TOLERANCE_PIXELS = 5.0F;

    /// How long a childless bone's shape is drawn, as a fraction of the offset it sits at from its
    /// parent.
    private static final float LEAF_LENGTH_RATIO = 0.5F;

    private final AssetAccessor<? extends Armature> armature;

    /// Bone heads in viewer world space, in hierarchy order. Rebuilt every frame.
    private final Map<Bone, Vector3f> heads = new LinkedHashMap<>();

    /// The octahedron drawn for each bone. One per bone, minus a childless root.
    private final List<BoneSegment> segments = new ArrayList<>();

    @Nullable
    private Bone selected;

    /// Run whenever [#selected] moves to a different bone, so an owner can follow the pick.
    @Nullable
    private Runnable onSelectionChanged;

    private double lastMouseX;
    private double lastMouseY;

    public ArmatureViewComponent(final AssetAccessor<? extends Armature> armature) {
        this.armature = armature;
    }

    public void setOnSelectionChanged(final Runnable onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
    }

    /// The bone the last click landed on, or null while none is picked.
    public @Nullable Bone getSelectedBone() {
        return selected;
    }

    /// Settles on `bone`, notifying the owner only when the pick actually moved.
    private void select(final @Nullable Bone bone) {
        if (Objects.equals(selected, bone)) return;

        selected = bone;

        if (onSelectionChanged != null) onSelectionChanged.run();
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer viewer, PoseStack poseStack,
        ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        Armature arm = armature.getOrHandleException(AkytheraLabs.LOGGER::error);

        if (arm == null) return;

        heads.clear();
        segments.clear();
        collect(arm.getRootBone(), new Matrix4f(), null);

        // The scene drawn so far stays, its depth does not — so the bones read over the mesh while
        // still occluding one another.
        viewer.clearDepth();

        RenderType renderType = RenderTypes.lines();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());
        PoseStack.Pose pose = poseStack.last();

        for (BoneSegment segment : segments) {
            boolean highlighted = segment.bone().equals(selected);

            emitOctahedron(
                builder, pose, segment.head(), segment.tail(),
                highlighted ? COLOR_SELECTED : COLOR_BONE,
                highlighted ? SELECTED_LINE_WIDTH : LINE_WIDTH
            );
        }

        for (Map.Entry<Bone, Vector3f> head : heads.entrySet()) {
            boolean highlighted = head.getKey().equals(selected);

            emitMarker(
                builder, pose, viewer, head.getValue(),
                highlighted ? COLOR_SELECTED : COLOR_BONE,
                highlighted ? SELECTED_LINE_WIDTH : LINE_WIDTH
            );
        }

        MeshData meshData = builder.build();

        if (meshData != null) RenderUtils.drawImmediate(renderType, meshData);
    }

    /// Walks the bind-pose hierarchy the way [Bone#initOriginTransform] does, gathering each bone's
    /// head and the shape running out of it. Returns `bone`'s own head, which is what its parent
    /// averages into a tail.
    private Vector3f collect(final Bone bone, final Matrix4f parentTransform, final @Nullable Vector3f parentHead) {
        Matrix4f modelTransform = parentTransform.mul(bone.getLocalTransform(), new Matrix4f());
        Vector3f head = Matrix4fUtils.extractTranslation(modelTransform);

        heads.put(bone, head);

        Vector3f tail = new Vector3f();
        int childCount = 0;

        for (Bone child : bone.getChildBones()) {
            tail.add(collect(child, modelTransform, head));
            childCount++;
        }

        if (childCount > 0) {
            tail.div(childCount);
        } else if (parentHead != null) {
            tail.set(head).sub(parentHead).mul(LEAF_LENGTH_RATIO).add(head);
        } else {
            return head;  // a lone bone points nowhere — its marker is the whole of it
        }

        segments.add(new BoneSegment(bone, head, tail));

        return head;
    }

    /// The 12 edges of the bone shape: four from `head` to the ring, the ring itself, four from the
    /// ring to `tail`.
    private static void emitOctahedron(
        BufferBuilder builder, PoseStack.Pose pose,
        Vector3f head, Vector3f tail, int color, float width
    ) {
        Vector3f axis = new Vector3f(tail).sub(head);
        float length = axis.length();

        if (length < 1.0e-4F) return;

        axis.div(length);

        // Any vector off `axis` seeds the ring's plane; the world axis it is least aligned with is
        // the stable pick.
        Vector3f seed = Math.abs(axis.x) < 0.9F ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f u = seed.cross(axis, new Vector3f()).normalize().mul(length * RING_RADIUS);
        Vector3f v = new Vector3f(axis).cross(u).normalize().mul(length * RING_RADIUS);
        Vector3f ringCenter = new Vector3f(axis).mul(length * RING_ALONG).add(head);

        Vector3f[] ring = {
            new Vector3f(ringCenter).add(u),
            new Vector3f(ringCenter).add(v),
            new Vector3f(ringCenter).sub(u),
            new Vector3f(ringCenter).sub(v)
        };

        for (int i = 0; i < ring.length; i++) {
            Vector3f from = ring[i];
            Vector3f to = ring[(i + 1) % ring.length];

            emitLine(builder, pose, head, from, color, width);
            emitLine(builder, pose, from, to, color, width);
            emitLine(builder, pose, from, tail, color, width);
        }
    }

    /// A camera-facing square outline at `at`, held at [#MARKER_PIXELS] on screen.
    private static void emitMarker(
        BufferBuilder builder, PoseStack.Pose pose, Model3DViewer viewer,
        Vector3f at, int color, float width
    ) {
        float radius = worldPerPixel(viewer, at) * MARKER_PIXELS;

        if (radius <= 0.0F) return;

        Vector3f right = new Vector3f(viewer.getCameraRight()).mul(radius);
        Vector3f up = new Vector3f(viewer.getCameraUp()).mul(radius);

        Vector3f[] corners = {
            new Vector3f(at).add(right).add(up),
            new Vector3f(at).add(right).sub(up),
            new Vector3f(at).sub(right).sub(up),
            new Vector3f(at).sub(right).add(up)
        };

        for (int i = 0; i < corners.length; i++) {
            emitLine(builder, pose, corners[i], corners[(i + 1) % corners.length], color, width);
        }
    }

    /// [RenderTypes#lines] expects the segment direction in the normal slot and a per-vertex width.
    private static void emitLine(
        BufferBuilder builder, PoseStack.Pose pose,
        Vector3f from, Vector3f to, int color, float width
    ) {
        Vector3f direction = new Vector3f(to).sub(from);

        if (direction.lengthSquared() < 1.0e-10F) return;

        direction.normalize();

        builder.addVertex(pose, from.x, from.y, from.z)
            .setColor(color)
            .setNormal(pose, direction.x, direction.y, direction.z)
            .setLineWidth(width);
        builder.addVertex(pose, to.x, to.y, to.z)
            .setColor(color)
            .setNormal(pose, direction.x, direction.y, direction.z)
            .setLineWidth(width);
    }

    /// World units one widget pixel spans at `at`'s depth, or 0 when the point does not project. A
    /// right-facing probe is used because it is never foreshortened.
    private static float worldPerPixel(Model3DViewer viewer, Vector3f at) {
        Vector3f projected = viewer.projectToWidget(at);
        Vector3f probe = viewer.projectToWidget(new Vector3f(at).add(viewer.getCameraRight()));

        if (projected == null || probe == null) return 0.0F;

        float pixelsPerUnit = (float) Math.hypot(probe.x - projected.x, probe.y - projected.y);

        return pixelsPerUnit < 1.0e-3F ? 0.0F : 1.0F / pixelsPerUnit;
    }

    /// The bone under `(mouseX, mouseY)` in widget pixels — head markers first, so the joint stays
    /// the precise target where a parent's tail overlaps its child's head. Depth compares under
    /// reversed-Z: greater is nearer.
    private @Nullable Bone hitTest(final Model3DViewer viewer, final double mouseX, final double mouseY) {
        Bone nearest = null;
        float nearestDepth = -Float.MAX_VALUE;

        for (Map.Entry<Bone, Vector3f> head : heads.entrySet()) {
            Vector3f projected = viewer.projectToWidget(head.getValue());

            if (projected == null || projected.z <= nearestDepth) continue;
            if (Math.hypot(projected.x - mouseX, projected.y - mouseY) > MARKER_PIXELS + PICK_TOLERANCE_PIXELS) continue;

            nearest = head.getKey();
            nearestDepth = projected.z;
        }

        if (nearest != null) return nearest;

        for (BoneSegment segment : segments) {
            Vector3f from = viewer.projectToWidget(segment.head());
            Vector3f to = viewer.projectToWidget(segment.tail());

            if (from == null || to == null) continue;

            float depth = Math.max(from.z, to.z);

            if (depth <= nearestDepth) continue;
            if (distanceToSegment(mouseX, mouseY, from, to) > PICK_TOLERANCE_PIXELS) continue;

            nearest = segment.bone();
            nearestDepth = depth;
        }

        return nearest;
    }

    private static double distanceToSegment(double x, double y, Vector3f from, Vector3f to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double lengthSquared = dx * dx + dy * dy;

        if (lengthSquared < 1.0e-6) return Math.hypot(x - from.x, y - from.y);

        double t = Math.clamp(((x - from.x) * dx + (y - from.y) * dy) / lengthSquared, 0.0, 1.0);

        return Math.hypot(x - (from.x + dx * t), y - (from.y + dy * t));
    }

    @Override
    public boolean canBeFocused(final Model3DViewer owner) {
        return true;
    }

    @Override
    public float rayTrace(final Model3DViewer owner, final Ray ray) {
        Bone hit = hitTest(owner, lastMouseX, lastMouseY);

        if (hit == null) return -1.0F;

        Vector3f head = heads.get(hit);

        return head == null ? -1.0F : ray.origin().distance(head);
    }

    @Override
    public void onFocused(final Model3DViewer owner) {
        select(hitTest(owner, lastMouseX, lastMouseY));
    }

    /// Re-picks while this component already holds the focus, which is the only way the selection
    /// moves between bones. A press on empty space is left alone so it drives the camera.
    @Override
    public boolean onMouseDown(final Model3DViewer owner, final MouseButtonEvent event) {
        Bone hit = hitTest(owner, event.x(), event.y());

        if (hit == null) return false;

        select(hit);

        return true;
    }

    @Override
    public int drawOrder() {
        return 20; // after grid (1) and the mesh (10) — the depth clear must land on a drawn scene
    }

    /// One bone's octahedron, from its own head to the tail derived for it.
    private record BoneSegment(Bone bone, Vector3f head, Vector3f tail) {}
}
