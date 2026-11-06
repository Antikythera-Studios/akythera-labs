package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.api.gui.viewer.Ray;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.client.core.renderer.collider.ColliderWireframe;
import com.yesman.akythera.core.animation.ik.InverseKinematicsSimulation;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/// One IK chain in the IK preview: a red square where its tip is placed now, which the gizmo moves, and a smaller blue
/// square where the animation puts it. Moving the red square holds the chain's tip there through
/// [InverseKinematicsSimulation#setTargetOverride].
public final class IKTargetComponent implements LocatableComponent {
    /// Half the side of the current position's square, in blocks.
    private static final double CURRENT_HALF = 0.09D;

    /// Half the side of the animated position's square, in blocks.
    private static final double ANIMATED_HALF = 0.06D;

    /// How far the animated position's square is drawn over the current one's, in blocks.
    private static final double ANIMATED_LIFT = 0.002D;

    /// How far from the current position a click still hits it, in blocks.
    private static final float PICK_RADIUS = 0.1F;

    private static final int CURRENT_COLOR = 0xFFFF2222;
    private static final int FOCUSED_COLOR = 0xFFFF9999;
    private static final int ANIMATED_COLOR = 0xFF2244FF;

    private final InverseKinematicsSimulation simulation;

    /// The tip bone of the chain this marker places.
    private final String tipBone;

    private boolean focused;

    public IKTargetComponent(final InverseKinematicsSimulation simulation, final String tipBone) {
        this.simulation = simulation;
        this.tipBone = tipBone;
    }

    // ***************************************************************
    // LocatableComponent
    // ***************************************************************

    @Override
    public Transform parentTransform() {
        return Transform.IDENTITY;
    }

    /// Where the chain's tip is placed now, at the origin before the first placement.
    @Override
    public Transform localTransform() {
        Vec3 placed = simulation.getPlacedTarget(tipBone);

        return Transform.fromTranslation(placed == null ? Vec3.ZERO : placed);
    }

    @Override
    public void applyLocalTransform(final Transform transform, final boolean live) {
        simulation.setTargetOverride(tipBone, transform.loc());
    }

    @Override
    public Set<GizmoComponent.Mode> gizmoModes() {
        return EnumSet.of(GizmoComponent.Mode.TRANSLATE);
    }

    // ***************************************************************
    // Model3DViewerComponent
    // ***************************************************************

    @Override
    public boolean canBeFocused(final Model3DViewer owner) {
        return true;
    }

    @Override
    public float rayTrace(final Model3DViewer owner, final Ray ray) {
        Vec3 loc = worldTransform().loc();
        Vector3f center = new Vector3f((float)loc.x, (float)loc.y, (float)loc.z);
        float along = new Vector3f(center).sub(ray.origin()).dot(ray.direction());

        return along > 0.0F && ray.at(along).distance(center) <= PICK_RADIUS ? along : -1.0F;
    }

    @Override
    public void onFocused(final Model3DViewer owner) {
        LocatableComponent.super.onFocused(owner);
        focused = true;
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
        RenderType renderType = RenderTypes.lines();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());
        Vec3 animated = simulation.getAnimatedTarget(tipBone);

        square(builder, poseStack, worldTransform().loc(), CURRENT_HALF, focused ? FOCUSED_COLOR : CURRENT_COLOR);

        if (animated != null) square(builder, poseStack, animated.add(0.0D, ANIMATED_LIFT, 0.0D), ANIMATED_HALF, ANIMATED_COLOR);

        MeshData meshData = builder.build();

        if (meshData != null) RenderUtils.drawImmediate(renderType, meshData);
    }

    @Override
    public int drawOrder() {
        return 40; // after the model (10) and the collider previews (35)
    }

    /// A flat square around `center`, `half` blocks from its center to each side, with its diagonals.
    private static void square(final BufferBuilder builder, final PoseStack poseStack, final Vec3 center, final double half, final int color) {
        Vec3[] corners = {
            center.add(-half, 0.0D, -half), center.add(half, 0.0D, -half), center.add(half, 0.0D, half), center.add(-half, 0.0D, half)
        };

        for (int i = 0; i < corners.length; i++) {
            ColliderWireframe.line(builder, poseStack.last(), corners[i], corners[(i + 1) % corners.length], color);
        }

        ColliderWireframe.line(builder, poseStack.last(), corners[0], corners[2], color);
        ColliderWireframe.line(builder, poseStack.last(), corners[1], corners[3], color);
    }
}
