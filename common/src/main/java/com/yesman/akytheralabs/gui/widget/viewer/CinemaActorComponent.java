package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.SingletonAccessor;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.api.gui.viewer.Ray;
import com.yesman.akythera.client.core.renderer.RenderUtils;
import com.yesman.akythera.client.core.renderer.collider.ColliderWireframe;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.cinema.CinemaScene;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.QuaternionUtils;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.cinema.CinemaActorHolder;
import com.yesman.akytheralabs.akythera.cinema.CinemaMontagePreview;
import com.yesman.akytheralabs.akythera.entitypatch.EntityPatchBlueprintHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ArmatureComponentHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.TransformComponentHolder;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.dataholder.EntityPreviewModelMapping;
import com.yesman.akytheralabs.editor.historytrack.DraggingDataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.PendingDataChange;
import com.yesman.akytheralabs.gui.panel.main.processors.ResourceProcessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/// One actor of a cinema scene in the preview: an arrow on the floor where the actor stands when the scene starts,
/// pointing where it faces, which the gizmo moves and turns. A click focuses it and picks its actor.
///
/// It installs the model of the actor's preview entity type, else of the montage's, and poses it at the playhead, and
/// the montage's root motion path, as components of their own, see [#removeFrom].
public final class CinemaActorComponent implements LocatableComponent {
    /// How far from the arrow's start a click still hits it, in blocks.
    private static final float PICK_RADIUS = 0.3F;

    /// From the arrow's start to its tip, and how far its head spreads, in blocks.
    private static final double ARROW_LENGTH = 0.7D;
    private static final double ARROW_HEAD = 0.18D;

    private static final int COLOR = 0xFF55AAFF;
    private static final int SELECTED_COLOR = 0xFFFFEE55;

    private final CinemaActorHolder actor;
    private final CinemaScenePreviewComponent preview;

    /// Told the actor when a click focuses this component.
    private final Consumer<CinemaActorHolder> onPicked;

    /// Runs after a committed gizmo drag, or its undo or redo, writes the transform.
    private final Runnable onTransformWritten;

    /// Whether the actor is the selected one.
    private final BooleanSupplier selected;

    /// The model of the preview entity type, or null when there is none to draw.
    private @Nullable EntityModelComponent model;

    /// The entity type [#model] was built for.
    private @Nullable Identifier modelEntityType;

    /// The clip [#model] plays.
    private @Nullable AnimationClip modelClip;

    /// The montage's root motion path, or null for a montage without root motion.
    private @Nullable RootMotionTrajectoryComponent trajectory;

    /// What the last frame read off the actor's montage.
    private @Nullable CinemaMontagePreview montage;

    /// Whether a gizmo drag is in flight, whose baseline is already on the history stack.
    private boolean dragging;

    public CinemaActorComponent(
        final CinemaActorHolder actor,
        final CinemaScenePreviewComponent preview,
        final Consumer<CinemaActorHolder> onPicked,
        final Runnable onTransformWritten,
        final BooleanSupplier selected
    ) {
        this.actor = actor;
        this.preview = preview;
        this.onPicked = onPicked;
        this.onTransformWritten = onTransformWritten;
        this.selected = selected;
    }

    public CinemaActorHolder getActor() {
        return actor;
    }

    /// Removes the model and the root motion path this component installed in `viewer`.
    public void removeFrom(final Model3DViewer viewer) {
        if (model != null) viewer.removeComponent(model);
        if (trajectory != null) viewer.removeComponent(trajectory);

        model = null;
        modelEntityType = null;
        modelClip = null;
        trajectory = null;
    }

    /// Where the previewed entity's armature sits in the entity's model space: the transform of the first armature of
    /// the open project's entity patch for [#modelEntityType].
    private Transform armatureTransform() {
        Identifier entityType = modelEntityType;
        EntityPatchBlueprintHolder patch = entityType == null
            ? null
            : AkytheraProject.getCurrentProject().<EntityPatchBlueprint, EntityPatchBlueprintHolder>getDataHolder(ResourceKey.create(
                AkytheraRegistries.DataPack.ENTITY_PATCH_KEY,
                ResourceProcessor.projectId(AkytheraRegistries.DataPack.ENTITY_PATCH_KEY, entityType)
            )).orElse(null);
        ArmatureComponentHolder armature = patch == null ? null : ComponentAttachableHolder.flattenEnabled(patch)
            .filter(ArmatureComponentHolder.class::isInstance)
            .map(ArmatureComponentHolder.class::cast)
            .findFirst()
            .orElse(null);

        return armature == null
            ? Transform.IDENTITY
            : new TransformComponentHolder.Space(
                holder -> ComponentAttachableHolder.hostOf(patch, holder), _ -> Transform.IDENTITY, () -> Transform.IDENTITY
            ).modelTransform(armature);
    }

    /// Where the actor's entity is at the playhead, in the scene's space: its start moved and turned by the montage's
    /// root motion, as [CinemaScene] places it in game. The scale is one.
    public Transform entityTransform() {
        Transform start = actor.getTransform().makeObject();

        if (montage == null || !montage.rootMotion()) return start;

        Transform from = rootAt(montage.clip(), 0.0F);
        Transform to = rootAt(montage.clip(), montage.playbackTime(preview.time()));
        Vec3 moved = armatureTransform().mulDirection(to.loc().subtract(from.loc()));
        double turned = QuaternionUtils.toSwingTwist(to.rot()).y - QuaternionUtils.toSwingTwist(from.rot()).y;

        return start.mul(new Transform(moved, new Quaterniond().rotationY(turned), Transform.IDENTITY.sca()));
    }

    private static Transform rootAt(final AnimationClip clip, final float time) {
        return clip.getPose(Math.clamp(time, 0.0F, clip.getTotalTime())).orElseIdentity(ArmatureLoader.ROOT_BONE);
    }

    private void writeTransform(final @Nullable Transform transform) {
        if (transform == null) return;

        actor.getTransform().readFrom(transform);
        onTransformWritten.run();
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
        return actor.getTransform().makeObject();
    }

    @Override
    public void applyLocalTransform(final Transform transform, final boolean live) {
        if (live) {
            // One pending baseline per drag, popped by the committed entry
            if (!dragging) {
                dragging = true;
                HistoryManager.getInstance().pushHistory(new PendingDataChange<>(actor.getTransform(), localTransform(), this::writeTransform));
            }

            actor.getTransform().readFrom(transform);
            return;
        }

        dragging = false;
        HistoryManager.getInstance().pushHistory(new DraggingDataChange<>(actor.getTransform(), transform, this::writeTransform));
        writeTransform(transform);
    }

    @Override
    public Set<GizmoComponent.Mode> gizmoModes() {
        return EnumSet.of(GizmoComponent.Mode.TRANSLATE, GizmoComponent.Mode.ROTATE);
    }

    @Override
    public void onDragCancelled(final Model3DViewer owner) {
        LocatableComponent.super.onDragCancelled(owner);
        dragging = false;
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
        Vec3 loc = localTransform().loc();
        Vector3f center = new Vector3f((float)loc.x, (float)loc.y, (float)loc.z);
        float along = new Vector3f(center).sub(ray.origin()).dot(ray.direction());

        return along > 0.0F && ray.at(along).distance(center) <= PICK_RADIUS ? along : -1.0F;
    }

    @Override
    public void onFocused(final Model3DViewer owner) {
        LocatableComponent.super.onFocused(owner);
        onPicked.accept(actor);
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        montage = CinemaMontagePreview.of(actor.getMontage());

        Identifier entityType = actor.getPreviewEntityType() != null
            ? actor.getPreviewEntityType()
            : montage == null ? null : montage.previewEntityType();

        if (!Objects.equals(entityType, modelEntityType)) {
            if (model != null) model3DViewer.removeComponent(model);

            model = EntityPreviewModelMapping.display(model3DViewer, entityType, false, false);
            modelEntityType = entityType;
            modelClip = null;

            if (model != null) model.setTransformSource(() -> actor.getTransform().makeObject().mul(armatureTransform()));
        }

        if (model != null && montage != null) {
            if (montage.clip() != modelClip) {
                modelClip = montage.clip();
                model.setSimplePlayingAnimation(
                    new SingletonAccessor<>(EditorHolderCodecs.entryId(actor.getMontage()), modelClip, AssetAccessor.Type.ANIMATION), false
                );
            }

            if (model.getAnimator() instanceof EntityModelComponent.SimpleHeadlessAnimator animator) {
                animator.setElapsedTime(montage.playbackTime(preview.time()));
            }
        }

        boolean rootMotion = montage != null && montage.rootMotion();

        if (rootMotion && trajectory == null) {
            trajectory = new RootMotionTrajectoryComponent(
                () -> RootMotionTrajectoryComponent.sampleRootPath(montage == null ? null : montage.clip()),
                () -> montage == null ? Vec3.ZERO : RootMotionTrajectoryComponent.rootLocation(montage.clip(), montage.playbackTime(preview.time())),
                BitSet::new,
                () -> actor.getTransform().makeObject().mul(armatureTransform())
            );
            model3DViewer.addComponent(trajectory);
        } else if (!rootMotion && trajectory != null) {
            model3DViewer.removeComponent(trajectory);
            trajectory = null;
        }

        Transform start = localTransform();
        Vec3 tip = start.mul(new Vec3(0.0D, 0.0D, -ARROW_LENGTH));
        int color = selected.getAsBoolean() ? SELECTED_COLOR : COLOR;
        RenderType renderType = RenderTypes.lines();
        BufferBuilder builder = new BufferBuilder(byteBuffer, renderType.primitiveTopology(), renderType.format());

        ColliderWireframe.line(builder, poseStack.last(), start.loc(), tip, color);
        ColliderWireframe.line(builder, poseStack.last(), tip, start.mul(new Vec3(ARROW_HEAD, 0.0D, -ARROW_LENGTH + ARROW_HEAD)), color);
        ColliderWireframe.line(builder, poseStack.last(), tip, start.mul(new Vec3(-ARROW_HEAD, 0.0D, -ARROW_LENGTH + ARROW_HEAD)), color);
        ColliderWireframe.line(builder, poseStack.last(), start.mul(new Vec3(-ARROW_HEAD, 0.0D, 0.0D)), start.mul(new Vec3(ARROW_HEAD, 0.0D, 0.0D)), color);

        MeshData meshData = builder.build();

        if (meshData != null) RenderUtils.drawImmediate(renderType, meshData);
    }

    @Override
    public int drawOrder() {
        return 5; // after the playhead (2), before the model (10) it poses
    }
}
