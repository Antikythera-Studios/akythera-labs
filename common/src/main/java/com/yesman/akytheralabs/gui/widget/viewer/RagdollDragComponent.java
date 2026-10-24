package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Ray;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.math.physics.ragdoll.RagdollSimulation;
import com.yesman.akytheralabs.mixinaccessor.RagdollSimulationAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/// Drags a running [RagdollSimulation] by hand. Bones are drawn and picked by the [ArmatureViewComponent] it wraps,
/// and the picked bone carries a translate [GizmoComponent]: while the gizmo is dragged,
/// [RagdollSimulationAccessor#akytheralabs$grab] holds that bone's body at it, and letting go releases it.
public final class RagdollDragComponent implements LocatableComponent {
    private final ArmatureViewComponent view;

    /// The simulation being dragged, null once it stopped.
    private final Supplier<@Nullable RagdollSimulation> simulation;

    /// A bone's simulated transform in viewer world space, by bone name.
    private final Function<String, Transform> boneTransform;

    /// `view` should draw the simulated pose, see [ArmatureViewComponent#setPoseSource].
    public RagdollDragComponent(
        final ArmatureViewComponent view,
        final Supplier<@Nullable RagdollSimulation> simulation,
        final Function<String, Transform> boneTransform
    ) {
        this.view = view;
        this.simulation = simulation;
        this.boneTransform = boneTransform;
    }

    // ***************************************************************
    // [LocatableComponent]
    // ***************************************************************

    @Override
    public Transform parentTransform() {
        return Transform.IDENTITY;
    }

    /// The picked bone's head, without its rotation: the gizmo's handles run along the viewer's axes.
    @Override
    public Transform localTransform() {
        Bone bone = view.getSelectedBone();

        return bone == null ? Transform.IDENTITY : Transform.fromTranslation(boneTransform.apply(bone.getName()).loc());
    }

    @Override
    public void applyLocalTransform(final Transform transform, final boolean live) {
        RagdollSimulation running = simulation.get();
        Bone bone = view.getSelectedBone();

        if (running == null || bone == null) return;

        if (live) {
            ((RagdollSimulationAccessor) (Object) running).akytheralabs$grab(bone.getName(), transform.loc());
        } else {
            ((RagdollSimulationAccessor) (Object) running).akytheralabs$release();
        }
    }

    @Override
    public Set<GizmoComponent.Mode> gizmoModes() {
        return EnumSet.of(GizmoComponent.Mode.TRANSLATE);
    }

    // ***************************************************************
    // [Model3DViewerComponent]
    // ***************************************************************

    @Override
    public boolean canBeFocused(final Model3DViewer owner) {
        return true;
    }

    @Override
    public float rayTrace(final Model3DViewer owner, final Ray ray) {
        return view.rayTrace(owner, ray);
    }

    @Override
    public void onFocused(final Model3DViewer owner) {
        view.onFocused(owner);
        LocatableComponent.super.onFocused(owner);

        GizmoComponent gizmo = boundGizmo(owner);

        if (gizmo != null) gizmo.setMode(GizmoComponent.Mode.TRANSLATE);
    }

    /// The gizmo takes the press first. One it lets go of re-picks a bone.
    @Override
    public boolean onMouseDown(final Model3DViewer owner, final MouseButtonEvent event) {
        return LocatableComponent.super.onMouseDown(owner, event) || view.onMouseDown(owner, event);
    }

    @Override
    public void onDragCancelled(final Model3DViewer owner) {
        LocatableComponent.super.onDragCancelled(owner);

        RagdollSimulation running = simulation.get();

        if (running != null) ((RagdollSimulationAccessor) (Object) running).akytheralabs$release();
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer viewer, PoseStack poseStack,
        ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        view.extractComponent(graphics, viewer, poseStack, byteBuffer, mouseX, mouseY, a);
    }

    @Override
    public void extractOverlay(final GuiGraphicsExtractor graphics, final Model3DViewer viewer, final int mouseX, final int mouseY, final float a) {
        view.extractOverlay(graphics, viewer, mouseX, mouseY, a);
    }

    @Override
    public int drawOrder() {
        return view.drawOrder();
    }
}
