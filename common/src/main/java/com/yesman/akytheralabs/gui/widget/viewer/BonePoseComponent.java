package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Ray;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.editor.historytrack.DraggingDataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.PendingDataChange;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import org.joml.Matrix4f;

import java.util.function.Consumer;
import java.util.function.Supplier;

/// Poses an armature by hand, for previewing how a mesh skins to it. Bones are drawn and picked by the
/// [ArmatureViewComponent] it wraps, and the picked bone carries a [GizmoComponent] whose drags write
/// that bone's entry in the pose.
///
/// The picker and the gizmo target are one component because a viewer hands a press only to the
/// focused component: the press has to reach the gizmo first and fall back to picking a bone.
public final class BonePoseComponent implements LocatableComponent {
    private final ArmatureViewComponent view;
    private final AssetAccessor<? extends Armature> armature;

    /// The pose being edited. Read per call, since its owner may swap it for a fresh one.
    private final Supplier<Pose> pose;

    /// The pose drawn from [#pose], such as [#pose] under bone constraints. The gizmo stands and starts its drags on it.
    private final Supplier<Pose> posed;

    /// Whether each drag is recorded in the [HistoryManager] as one undo step.
    private boolean recordsHistory;

    /// Whether a gizmo drag is in flight, so its pending undo baseline is pushed once.
    private boolean dragging;

    /// `view` should draw the same `pose`, see [ArmatureViewComponent#setPoseSource].
    public BonePoseComponent(ArmatureViewComponent view, AssetAccessor<? extends Armature> armature, Supplier<Pose> pose) {
        this(view, armature, pose, pose);
    }

    /// `view` should draw `posed`, see [ArmatureViewComponent#setPoseSource].
    public BonePoseComponent(ArmatureViewComponent view, AssetAccessor<? extends Armature> armature, Supplier<Pose> pose, Supplier<Pose> posed) {
        this.view = view;
        this.armature = armature;
        this.pose = pose;
        this.posed = posed;
    }

    public void setRecordsHistory(final boolean recordsHistory) {
        this.recordsHistory = recordsHistory;
    }

    // ***************************************************************
    // [LocatableComponent]
    // ***************************************************************

    /// The picked bone's rest frame under its posed parent, in viewer world space: its posed model
    /// transform with its own pose entry taken back off. [Pose#boundTransformRecursively] multiplies
    /// that entry on last, so this is the space the entry is expressed in.
    @Override
    public Transform parentTransform() {
        Bone bone = view.getSelectedBone();

        if (bone == null) return Transform.IDENTITY;

        Pose current = posed.get();
        Matrix4f model = current.getForwardBoundTransforms(armature)[bone.getId()];

        return view.getTransform().mul(Transform.fromMatrix(model.mul(current.orElseIdentity(bone.getName()).toMatrix().invert())));
    }

    @Override
    public Transform localTransform() {
        Bone bone = view.getSelectedBone();

        return bone == null ? Transform.IDENTITY : posed.get().orElseIdentity(bone.getName());
    }

    /// Writes the picked bone's entry. With [#recordsHistory], a drag is one undo step that puts back the entry it
    /// started from.
    @Override
    public void applyLocalTransform(Transform transform, boolean live) {
        Bone bone = view.getSelectedBone();

        if (bone == null) return;

        if (!recordsHistory) {
            pose.get().putBoneTransform(bone.getName(), transform);
            return;
        }

        Pose target = pose.get();
        String name = bone.getName();
        Consumer<Transform> write = value -> {
            if (value != null) target.putBoneTransform(name, value);
        };

        if (live) {
            // One pending baseline per drag — the committed entry's verifyLastHistory pops exactly one.
            if (!dragging) {
                dragging = true;
                HistoryManager.getInstance().pushHistory(new PendingDataChange<>(this, target.orElseIdentity(name), write));
            }

            write.accept(transform);
            return;
        }

        dragging = false;
        HistoryManager.getInstance().pushHistory(new DraggingDataChange<>(this, transform, write));
        write.accept(transform);
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

    /// Picks the bone under the cursor and raises the gizmo onto it. The viewer's first gizmo starts in
    /// [GizmoComponent.Mode#ROTATE], which is what posing is done with.
    @Override
    public void onFocused(final Model3DViewer owner) {
        boolean fresh = owner.getComponent(GizmoComponent.class) == null;

        view.onFocused(owner);
        LocatableComponent.super.onFocused(owner);

        GizmoComponent gizmo = boundGizmo(owner);

        if (fresh && gizmo != null) gizmo.setMode(GizmoComponent.Mode.ROTATE);
    }

    /// The gizmo takes the press first. One it lets go of re-picks a bone, and the gizmo follows
    /// because it reads the pick live. A press cycling past this component is left to the viewer.
    @Override
    public boolean onMouseDown(final Model3DViewer owner, final MouseButtonEvent event) {
        return LocatableComponent.super.onMouseDown(owner, event)
            || !owner.cyclesPast(this, event.x(), event.y()) && view.onMouseDown(owner, event);
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
