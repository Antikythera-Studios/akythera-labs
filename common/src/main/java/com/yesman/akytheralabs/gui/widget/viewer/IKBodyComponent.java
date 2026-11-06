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
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.util.helper.VectorUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Where the IK preview stands the armature, as a yellow square on its feet the gizmo moves and turns. Like an entity,
/// it keeps only a yaw.
public final class IKBodyComponent implements LocatableComponent {
    /// Half the square's side, in blocks.
    private static final double HALF_SIZE = 0.5D;

    private static final int COLOR = 0xFFFFEE00;
    private static final int FOCUSED_COLOR = 0xFFFFFF99;

    private final Supplier<Transform> body;
    private final Consumer<Transform> setBody;

    private boolean focused;

    public IKBodyComponent(final Supplier<Transform> body, final Consumer<Transform> setBody) {
        this.body = body;
        this.setBody = setBody;
    }

    // ***************************************************************
    // LocatableComponent
    // ***************************************************************

    @Override
    public Transform parentTransform() {
        return Transform.IDENTITY;
    }

    @Override
    public Transform localTransform() {
        return body.get();
    }

    /// Takes the drag's location and the yaw of its rotation.
    @Override
    public void applyLocalTransform(final Transform transform, final boolean live) {
        Vec3 forward = VectorUtils.rotateByQuaternion(transform.rot(), 0.0D, 0.0D, 1.0D);
        double yaw = Math.atan2(forward.x(), forward.z());

        setBody.accept(Transform.fromRotation(new Quaterniond().rotationAxis(yaw, 0.0D, 1.0D, 0.0D)).translate(transform.loc()));
    }

    @Override
    public Set<GizmoComponent.Mode> gizmoModes() {
        return EnumSet.of(GizmoComponent.Mode.TRANSLATE, GizmoComponent.Mode.ROTATE);
    }

    // ***************************************************************
    // Model3DViewerComponent
    // ***************************************************************

    @Override
    public boolean canBeFocused(final Model3DViewer owner) {
        return true;
    }

    /// Hits the square in its own plane.
    @Override
    public float rayTrace(final Model3DViewer owner, final Ray ray) {
        Transform transform = body.get();
        float height = (float)transform.loc().y();

        if (Math.abs(ray.direction().y()) < 1.0E-6F) return -1.0F;

        float along = (height - ray.origin().y()) / ray.direction().y();

        if (along <= 0.0F) return -1.0F;

        Vector3f hit = ray.at(along);
        Vec3 local = transform.inverseMul(new Vec3(hit.x(), hit.y(), hit.z()));

        return Math.abs(local.x()) <= HALF_SIZE && Math.abs(local.z()) <= HALF_SIZE ? along : -1.0F;
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
        Transform transform = body.get();
        RenderType renderType = RenderTypes.lines();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());
        Vec3[] corners = {
            transform.mul(new Vec3(-HALF_SIZE, 0.0D, -HALF_SIZE)), transform.mul(new Vec3(HALF_SIZE, 0.0D, -HALF_SIZE)),
            transform.mul(new Vec3(HALF_SIZE, 0.0D, HALF_SIZE)), transform.mul(new Vec3(-HALF_SIZE, 0.0D, HALF_SIZE))
        };

        for (int i = 0; i < corners.length; i++) {
            ColliderWireframe.line(builder, poseStack.last(), corners[i], corners[(i + 1) % corners.length], focused ? FOCUSED_COLOR : COLOR);
        }

        MeshData meshData = builder.build();

        if (meshData != null) RenderUtils.drawImmediate(renderType, meshData);
    }

    @Override
    public int drawOrder() {
        return 40; // after the model (10) and the collider previews (35)
    }
}
