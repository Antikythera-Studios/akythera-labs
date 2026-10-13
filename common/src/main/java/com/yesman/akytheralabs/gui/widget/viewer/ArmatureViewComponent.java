package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.api.gui.viewer.Ray;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.Matrix4fUtils;
import com.yesman.akytheralabs.AkytheraLabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/// Draws an armature over whatever the viewer already holds: an octahedron per bone, running from its
/// own head to its tail, and a screen-sized marker at every head. Clicking either selects that bone,
/// and shift-clicking reports the pick together with the one it replaces.
///
/// The skeleton stands in the bind pose unless a [#setPoseSource] supplies one, at the viewer's origin unless a
/// [#setTransformSource] places it.
public final class ArmatureViewComponent implements Model3DViewerComponent {
    private static final int COLOR_BONE = 0xFFDDDDDD;
    private static final int COLOR_SELECTED = 0xFFFFA028;
    private static final int COLOR_PARTNER = 0xFF48B0FF; // the mirror partner of the selected bone
    private static final int COLOR_PAIRED = 0xFF58A882;  // paired with something else
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

    private final AssetAccessor<? extends Armature> armature;

    /// Bone heads in viewer world space, in hierarchy order. Rebuilt every frame.
    private final Map<Bone, Vector3f> heads = new LinkedHashMap<>();

    /// The octahedron drawn for each bone.
    private final List<BoneSegment> segments = new ArrayList<>();

    @Nullable
    private Bone selected;

    /// Run whenever [#selected] moves to a different bone, so an owner can follow the pick.
    @Nullable
    private Runnable onSelectionChanged;

    /// The mirror partner's name for a bone, or null while it stands alone. Null — the default —
    /// draws every bone alike.
    @Nullable
    private Function<Bone, String> mirrorPartnerProvider;

    /// The name the pick is labelled by in the viewer's top-right corner, beside its id.
    private Function<Bone, String> boneNameProvider = Bone::getName;

    /// Run on a shift-click that lands on a bone other than the selected one, with the bone that
    /// was selected and the one just hit.
    @Nullable
    private BiConsumer<Bone, Bone> onShiftPicked;

    /// The pose the skeleton is drawn in, read per frame. Null — the default — draws the bind pose.
    @Nullable
    private Supplier<Pose> poseSource;

    /// Where the skeleton's root sits in viewer world space, read per frame. Null — the default — is the origin.
    @Nullable
    private Supplier<Transform> transformSource;

    private double lastMouseX;
    private double lastMouseY;

    public ArmatureViewComponent(final AssetAccessor<? extends Armature> armature) {
        this.armature = armature;
    }

    public void setPoseSource(final @Nullable Supplier<Pose> poseSource) {
        this.poseSource = poseSource;
    }

    public void setTransformSource(final @Nullable Supplier<Transform> transformSource) {
        this.transformSource = transformSource;
    }

    public void setOnSelectionChanged(final Runnable onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
    }

    public void setBoneNameProvider(final Function<Bone, String> boneNameProvider) {
        this.boneNameProvider = boneNameProvider;
    }

    public void setMirrorPartnerProvider(final @Nullable Function<Bone, String> mirrorPartnerProvider) {
        this.mirrorPartnerProvider = mirrorPartnerProvider;
    }

    public void setOnShiftPicked(final BiConsumer<Bone, Bone> onShiftPicked) {
        this.onShiftPicked = onShiftPicked;
    }

    /// The bone the last click landed on, or null while none is picked.
    public @Nullable Bone getSelectedBone() {
        return selected;
    }

    /// Settles on `bone`, notifying the owner only when the pick actually moved. Public because a
    /// picker outside the viewer — a bone combo — settles the same selection.
    public void setSelectedBone(final @Nullable Bone bone) {
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
        collect(
            arm.getRootBone(),
            transformSource == null ? new Matrix4f() : transformSource.get().toMatrix(),
            poseSource == null ? null : poseSource.get()
        );

        // The scene drawn so far stays, its depth does not — so the bones read over the mesh while
        // still occluding one another.
        viewer.clearDepth();

        RenderType renderType = RenderTypes.lines();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());
        PoseStack.Pose pose = poseStack.last();

        for (BoneSegment segment : segments) {
            int color = colorOf(segment.bone());

            emitOctahedron(builder, pose, segment.head(), segment.tail(), color, lineWidthOf(color));
        }

        for (Map.Entry<Bone, Vector3f> head : heads.entrySet()) {
            int color = colorOf(head.getKey());

            emitMarker(builder, pose, viewer, head.getValue(), color, lineWidthOf(color));
        }

        MeshData meshData = builder.build();

        if (meshData != null) RenderUtils.drawImmediate(renderType, meshData);
    }

    /// The color `bone` is drawn in: the pick, the pick's mirror partner, a bone standing in some
    /// other pair, or a plain one.
    private int colorOf(final Bone bone) {
        if (bone.equals(selected)) return COLOR_SELECTED;
        if (mirrorPartnerProvider == null) return COLOR_BONE;
        if (selected != null && bone.getName().equals(mirrorPartnerProvider.apply(selected))) return COLOR_PARTNER;

        return mirrorPartnerProvider.apply(bone) == null ? COLOR_BONE : COLOR_PAIRED;
    }

    /// The pick and its partner are drawn thick, so the two read as one thing.
    private static float lineWidthOf(final int color) {
        return color == COLOR_SELECTED || color == COLOR_PARTNER ? SELECTED_LINE_WIDTH : LINE_WIDTH;
    }

    /// Walks the hierarchy the way [Pose#boundTransformRecursively] does, gathering each bone's head
    /// and the shape running out of it. A null `pose` walks the bind pose.
    private void collect(final Bone bone, final Matrix4f parentTransform, final @Nullable Pose pose) {
        Matrix4f modelTransform = parentTransform.mul(bone.getLocalTransform(), new Matrix4f());

        if (pose != null) modelTransform.mul(pose.orElseIdentity(bone.getName()).toMatrix());

        Vector3f head = Matrix4fUtils.extractTranslation(modelTransform);
        Vector3f tail = modelTransform.transformPosition(new Vector3f(bone.getToTail()));

        heads.put(bone, head);
        segments.add(new BoneSegment(bone, head, tail));

        for (Bone child : bone.getChildBones()) {
            collect(child, modelTransform, pose);
        }
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
        setSelectedBone(hitTest(owner, lastMouseX, lastMouseY));
    }

    /// Re-picks while this component already holds the focus, which is the only way the selection
    /// moves between bones. A press on empty space is left alone so it drives the camera.
    @Override
    public boolean onMouseDown(final Model3DViewer owner, final MouseButtonEvent event) {
        Bone hit = hitTest(owner, event.x(), event.y());

        if (hit == null) return false;

        Bone previous = selected;

        setSelectedBone(hit);

        if (event.hasShiftDown() && onShiftPicked != null && previous != null && !previous.equals(hit)) {
            onShiftPicked.accept(previous, hit);
        }

        return true;
    }

    /// Labels the pick as `name(id)` in the viewer's top-right corner.
    @Override
    public void extractOverlay(final GuiGraphicsExtractor graphics, final Model3DViewer viewer, final int mouseX, final int mouseY, final float a) {
        if (selected == null) return;

        Font font = Minecraft.getInstance().font;
        String label = boneNameProvider.apply(selected) + "(" + selected.getId() + ")";
        int labelX = viewer.getRight() - 6 - font.width(label);
        int labelY = viewer.getY() + 6;

        graphics.fill(labelX - 4, labelY - 3, viewer.getRight() - 2, labelY + font.lineHeight + 3, 0xAA000000);
        graphics.text(font, label, labelX, labelY, COLOR_SELECTED, false);
    }

    @Override
    public int drawOrder() {
        return 20; // after grid (1) and the mesh (10) — the depth clear must land on a drawn scene
    }

    /// One bone's octahedron, from its own head to the tail derived for it.
    private record BoneSegment(Bone bone, Vector3f head, Vector3f tail) {}
}
