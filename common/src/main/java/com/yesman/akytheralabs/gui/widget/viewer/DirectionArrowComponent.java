package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.editor.historytrack.DraggingDataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.PendingDataChange;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// A unit direction drawn as an arrow standing at [#ORIGIN], turned by the gizmo in
/// [GizmoComponent.Mode#ROTATE] only. Raised by an [EditorEditable#direction] row; it leaves the viewer
/// as soon as it loses focus.
public final class DirectionArrowComponent implements LocatableComponent {
    /// Where the arrow stands, in viewer world units.
    private static final Vec3 ORIGIN = new Vec3(0.0D, 1.0D, 0.0D);

    /// The way the arrow points before it is turned.
    private static final Vector3d FORWARD = new Vector3d(0.0D, 0.0D, 1.0D);

    private static final float LENGTH = 0.75F; // viewer world units
    private static final float HEAD_LENGTH = 0.12F; // viewer world units
    private static final int COLOR = 0xFFFFC845;

    private final Supplier<Vec3> direction;

    /// Writes a unit direction back, and refreshes whatever shows it.
    private final Consumer<Vec3> write;

    /// The turn last shown, so the arrow keeps its roll from one drag to the next.
    private final Quaterniond rotation = new Quaterniond();

    private boolean dragging;

    public DirectionArrowComponent(final Supplier<Vec3> direction, final Consumer<Vec3> write) {
        this.direction = direction;
        this.write = write;
    }

    // ***************************************************************
    // [LocatableComponent]
    // ***************************************************************

    @Override
    public Transform parentTransform() {
        return Transform.fromTranslation(ORIGIN);
    }

    /// [#rotation], turned afresh onto the direction when it was typed in elsewhere.
    @Override
    public Transform localTransform() {
        Vec3 value = direction.get();

        if (value.lengthSqr() > 1.0E-12D) {
            Vector3d wanted = new Vector3d(value.x, value.y, value.z).normalize();

            if (rotation.transform(new Vector3d(FORWARD)).dot(wanted) < 1.0D - 1.0E-9D) {
                rotation.rotationTo(FORWARD, wanted);
            }
        }

        return Transform.fromRotation(new Quaterniond(rotation));
    }

    /// Keeps only the rotation of `transform`: the arrow points where it turns [#FORWARD].
    @Override
    public void applyLocalTransform(final Transform transform, final boolean live) {
        rotation.set(transform.rot());

        Vector3d turned = rotation.transform(new Vector3d(FORWARD));
        Vec3 value = new Vec3(turned.x, turned.y, turned.z).normalize();

        if (live) {
            // One pending baseline per drag — the committed entry's verifyLastHistory pops exactly one.
            if (!dragging) {
                dragging = true;
                HistoryManager.getInstance().pushHistory(new PendingDataChange<>(this, direction.get(), write));
            }

            write.accept(value);
            return;
        }

        dragging = false;
        HistoryManager.getInstance().pushHistory(new DraggingDataChange<>(this, value, write));
        write.accept(value);
    }

    @Override
    public Set<GizmoComponent.Mode> gizmoModes() {
        return EnumSet.of(GizmoComponent.Mode.ROTATE);
    }

    // ***************************************************************
    // [Model3DViewerComponent]
    // ***************************************************************

    @Override
    public void onReleased(final Model3DViewer owner, final @Nullable Model3DViewerComponent newFocus) {
        LocatableComponent.super.onReleased(owner, newFocus);

        owner.removeComponent(this);
    }

    /// A shaft from [#ORIGIN] and a four-line head, drawn over the scene.
    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer viewer, PoseStack poseStack,
        ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        Quaterniond turn = localTransform().rot();
        Vector3d forward = turn.transform(new Vector3d(FORWARD));
        Vector3d right = turn.transform(new Vector3d(1.0D, 0.0D, 0.0D));
        Vector3d up = turn.transform(new Vector3d(0.0D, 1.0D, 0.0D));
        Vector3f origin = ORIGIN.toVector3f();
        Vector3f tip = new Vector3f(origin).add(new Vector3f(forward).mul(LENGTH));
        Vector3f headBase = new Vector3f(tip).sub(new Vector3f(forward).mul(HEAD_LENGTH));
        Vector3f[] headSides = {
            new Vector3f(right).mul(HEAD_LENGTH * 0.5F), new Vector3f(right).mul(-HEAD_LENGTH * 0.5F),
            new Vector3f(up).mul(HEAD_LENGTH * 0.5F), new Vector3f(up).mul(-HEAD_LENGTH * 0.5F)
        };

        // The arrow stands inside the preview model, so it reads over the scene
        viewer.clearDepth();

        RenderType renderType = RenderTypes.lines();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());
        PoseStack.Pose pose = poseStack.last();

        ClothMeshGeometry.emitLine(builder, pose, origin, COLOR, tip, COLOR);

        for (Vector3f side : headSides) {
            ClothMeshGeometry.emitLine(builder, pose, tip, COLOR, new Vector3f(headBase).add(side), COLOR);
        }

        MeshData meshData = builder.build();

        if (meshData != null) RenderUtils.drawImmediate(renderType, meshData);
    }

    @Override
    public int drawOrder() {
        return 40; // after the cloth tools (20), under the gizmo (OVERLAY_DRAW_ORDER)
    }
}
