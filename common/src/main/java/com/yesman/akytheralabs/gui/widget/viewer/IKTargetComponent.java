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

/// The target of one IK chain in the IK preview, as a wireframe octahedron the gizmo moves. Moving it holds the chain's
/// tip there through [InverseKinematicsSimulation#setTargetOverride]. The preview places the armature at the world's
/// origin, so its space is the viewer's world space.
public final class IKTargetComponent implements LocatableComponent {
    /// From the octahedron's center to each of its tips, in blocks.
    private static final double TIP = 0.06D;

    /// How far from the marker's center a click still hits it, in blocks.
    private static final float PICK_RADIUS = 0.1F;

    private static final int COLOR = 0xFFFFAA33;
    private static final int FOCUSED_COLOR = 0xFFFFEE99;

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
        Vec3 center = worldTransform().loc();
        RenderType renderType = RenderTypes.lines();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());
        Vec3[] tips = {
            center.add(TIP, 0.0D, 0.0D), center.add(-TIP, 0.0D, 0.0D),
            center.add(0.0D, TIP, 0.0D), center.add(0.0D, -TIP, 0.0D),
            center.add(0.0D, 0.0D, TIP), center.add(0.0D, 0.0D, -TIP)
        };

        // Tips 2i and 2i + 1 are opposite; every other pair is an edge
        for (int i = 0; i < tips.length; i++) {
            for (int j = i + 1; j < tips.length; j++) {
                if (j != (i ^ 1)) ColliderWireframe.line(builder, poseStack.last(), tips[i], tips[j], focused ? FOCUSED_COLOR : COLOR);
            }
        }

        MeshData meshData = builder.build();

        if (meshData != null) RenderUtils.drawImmediate(renderType, meshData);
    }

    @Override
    public int drawOrder() {
        return 40; // after the model (10) and the collider previews (35)
    }
}
