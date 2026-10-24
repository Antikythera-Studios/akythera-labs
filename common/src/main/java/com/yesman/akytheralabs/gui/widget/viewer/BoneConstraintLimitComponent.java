package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.core.asset.physics.ArmaturePhysicsData;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.akythera.physics.BoneConstraintHolder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/// The rotation a [BoneConstraintHolder] leaves its bone, as a yellow wireframe at the bone's head: the patch of the
/// sphere the bone's tail sweeps within the X and Z limits, and the arc of the Y twist around the bone.
///
/// Read from the holder every frame, so the wireframe follows the number boxes as they are typed.
public final class BoneConstraintLimitComponent implements Model3DViewerComponent {
    private static final int COLOR = 0xFFFFCC00;
    private static final float LINE_WIDTH = 2.0F;

    /// Segments along each edge of the patch, and along the twist arc.
    private static final int SEGMENTS = 24;

    /// Lines across the patch between its edges, per direction.
    private static final int GRID_LINES = 3;

    /// Radius of the twist arc, as a share of the bone's length.
    private static final float TWIST_RADIUS = 0.35F;

    /// The bone at its rest pose, in armature space.
    private final Transform placement;

    /// The bone's length, from head to tail.
    private final float length;

    private final BoneConstraintHolder constraint;

    public BoneConstraintLimitComponent(final Transform placement, final float length, final BoneConstraintHolder constraint) {
        this.placement = placement;
        this.length = length;
        this.constraint = constraint;
    }

    @Override
    public void extractComponent(GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a) {
        ArmaturePhysicsData.BoneConstraint limits = constraint.makeObject();
        boolean swingLimited = !limits.x().isFree() || !limits.z().isFree();
        boolean twistLimited = !limits.y().isFree();

        if (!swingLimited && !twistLimited) return;

        RenderType renderType = RenderTypes.lines();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());

        poseStack.pushPose();
        poseStack.mulPose(placement.toMatrix());

        PoseStack.Pose pose = poseStack.last();

        if (swingLimited) {
            float minX = limits.x().min() * Mth.DEG_TO_RAD, maxX = limits.x().max() * Mth.DEG_TO_RAD;
            float minZ = limits.z().min() * Mth.DEG_TO_RAD, maxZ = limits.z().max() * Mth.DEG_TO_RAD;

            for (int line = 0; line <= GRID_LINES + 1; line++) {
                float t = line / (float) (GRID_LINES + 1);

                addSwingLine(builder, pose, Mth.lerp(t, minX, maxX), minZ, Mth.lerp(t, minX, maxX), maxZ);
                addSwingLine(builder, pose, minX, Mth.lerp(t, minZ, maxZ), maxX, Mth.lerp(t, minZ, maxZ));
            }

            addSegment(builder, pose, new Vector3f(), swingTip(minX, minZ), COLOR);
            addSegment(builder, pose, new Vector3f(), swingTip(minX, maxZ), COLOR);
            addSegment(builder, pose, new Vector3f(), swingTip(maxX, minZ), COLOR);
            addSegment(builder, pose, new Vector3f(), swingTip(maxX, maxZ), COLOR);
        }

        if (twistLimited) {
            float minY = limits.y().min() * Mth.DEG_TO_RAD, maxY = limits.y().max() * Mth.DEG_TO_RAD;
            float radius = length * TWIST_RADIUS;
            float height = length * 0.5F;
            Vector3f center = new Vector3f(0.0F, height, 0.0F);
            Vector3f previous = null;

            for (int i = 0; i <= SEGMENTS; i++) {
                float angle = Mth.lerp(i / (float) SEGMENTS, minY, maxY);
                Vector3f point = new Vector3f(Mth.sin(angle) * radius, height, Mth.cos(angle) * radius);

                if (previous != null) addSegment(builder, pose, previous, point, COLOR);
                if (i == 0 || i == SEGMENTS) addSegment(builder, pose, center, point, COLOR);

                previous = point;
            }
        }

        poseStack.popPose();

        MeshData meshData = builder.build();

        if (meshData != null) RenderUtils.drawImmediate(renderType, meshData);
    }

    /// The tail's path from swing (`fromX`, `fromZ`) to swing (`toX`, `toZ`), in radians.
    private void addSwingLine(BufferBuilder builder, PoseStack.Pose pose, float fromX, float fromZ, float toX, float toZ) {
        Vector3f previous = swingTip(fromX, fromZ);

        for (int i = 1; i <= SEGMENTS; i++) {
            float t = i / (float) SEGMENTS;
            Vector3f point = swingTip(Mth.lerp(t, fromX, toX), Mth.lerp(t, fromZ, toZ));

            addSegment(builder, pose, previous, point, COLOR);
            previous = point;
        }
    }

    /// Where the tail stands under the swing whose rotation vector is (`x`, 0, `z`), in radians.
    private Vector3f swingTip(final float x, final float z) {
        float angle = Mth.sqrt(x * x + z * z);
        Vector3f tail = new Vector3f(0.0F, length, 0.0F);

        if (angle < 1.0E-6F) return tail;

        return new Quaternionf().rotationAxis(angle, x / angle, 0.0F, z / angle).transform(tail);
    }

    /// [RenderTypes#lines] expects the segment direction in the normal slot and a per-vertex width.
    private static void addSegment(BufferBuilder builder, PoseStack.Pose pose, Vector3f from, Vector3f to, int color) {
        Vector3f direction = new Vector3f(to).sub(from).normalize();

        builder.addVertex(pose, from.x, from.y, from.z)
            .setColor(color)
            .setNormal(pose, direction.x, direction.y, direction.z)
            .setLineWidth(LINE_WIDTH);
        builder.addVertex(pose, to.x, to.y, to.z)
            .setColor(color)
            .setNormal(pose, direction.x, direction.y, direction.z)
            .setLineWidth(LINE_WIDTH);
    }

    @Override
    public int drawOrder() {
        return 36; // after the collider previews (35)
    }
}
