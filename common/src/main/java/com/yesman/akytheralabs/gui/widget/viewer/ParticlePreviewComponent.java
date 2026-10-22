package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Either;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.api.gui.viewer.Ray;
import com.yesman.akythera.client.api.particle.AkytheraParticleSystem;
import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
import com.yesman.akythera.core.blueprint.particle.EmitterSpawnRule;
import com.yesman.akythera.core.blueprint.particle.InstantSpawnRule;
import com.yesman.akythera.core.blueprint.particle.KeyframeTrack;
import com.yesman.akythera.core.blueprint.particle.ParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleEmitterBlueprint;
import com.yesman.akythera.core.blueprint.particle.TrailParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.TrailSpawnRule;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.akythera.particle.ParticleBlueprintHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.editor.historytrack.DraggingDataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.PendingDataChange;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// Plays the piece a [ParticleBlueprintHolder] builds in [Model3DViewer], with no level: a system with one
/// [InstantSpawnRule] emitter spawns it at [#ORIGIN], and a new system starts [#REPLAY_DELAY] after the particle is
/// gone. A trail spawns by a [TrailSpawnRule] instead, and its system turns once about the Z axis over [#SWEEP_TICKS],
/// so the trail sweeps a disc standing on the floor. The particles play in a [PreviewParticles].
///
/// Focused, by a press on a particle or by a row that names it in [EditorEditable#viewerFocus], it outlines each
/// particle's bounding box, and a scale gizmo on the first one resizes the box: X and Z both set its width.
///
/// The piece is read per frame. A different one restarts the play.
public final class ParticlePreviewComponent implements LocatableComponent {
    /// Where the particle spawns, in the viewer's world space.
    public static final Vec3 ORIGIN = new Vec3(0.0D, 1.0D, 0.0D);

    /// Ticks a trail's system takes to turn once, the time span of its emitter.
    private static final int SWEEP_TICKS = 20;

    private static final int BOUNDING_BOX_COLOR = 0xFFFFCC00;

    /// Ticks between the end of one play and the start of the next.
    private static final int REPLAY_DELAY = 20;

    /// How far a pick ray reaches, in blocks.
    private static final double PICK_REACH = 1000.0D;

    /// The id the emitter names the previewed piece by.
    private static final ResourceKey<ParticleBlueprint> PREVIEW_KEY =
        ResourceKey.create(AkytheraRegistries.DataPack.PARTICLE_PIECE_KEY, AkytheraLabs.identifier("preview"));

    private static final ParticleSystemBlueprint SYSTEM = previewSystem(new InstantSpawnRule(1));

    private static final ParticleSystemBlueprint TRAIL_SYSTEM = previewSystem(new TrailSpawnRule(SWEEP_TICKS / 20.0F));

    private final ParticleBlueprintHolder holder;

    /// Runs after a committed gizmo drag, or its undo or redo, writes the bounding box.
    private final Runnable onBoundingBoxWritten;

    private final PreviewParticles particles = new PreviewParticles(key -> key.equals(PREVIEW_KEY) ? this.playing : null);

    /// The piece the running play was started with, or null when the holder builds none.
    private @Nullable ParticleBlueprint playing;

    /// The system of the running play, kept after it is removed, since a trail still follows its transform.
    private @Nullable AkytheraParticleSystem system;

    /// Ticks since [#system] started.
    private int playTicks;

    /// Ticks not played yet, below one. Also the partial tick frames are drawn at.
    private float pendingTicks;

    /// Ticks since the last play ended. Starts at [#REPLAY_DELAY], so a new piece plays at once.
    private int idleTicks = REPLAY_DELAY;

    /// Whether [#playing] threw while it played, which stops the preview until the piece changes.
    private boolean failed;

    /// What the last frame drew.
    private PreviewParticles.Drawn drawn = PreviewParticles.Drawn.NOTHING;

    /// Whether the viewer focuses this component, which outlines the bounding boxes.
    private boolean focused;

    /// The bounding box size a gizmo drag in flight started from, X the width and Y the height, already on the history
    /// stack as its baseline. Null while no drag is in flight.
    private @Nullable Vec3 dragStart;

    public ParticlePreviewComponent(final ParticleBlueprintHolder holder, final Runnable onBoundingBoxWritten) {
        this.holder = holder;
        this.onBoundingBoxWritten = onBoundingBoxWritten;
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        ParticleBlueprint current;

        try {
            current = holder.makeObject();
        } catch (RuntimeException e) {
            current = null;
        }

        if (!Objects.equals(current, playing)) {
            particles.clear();
            system = null;
            playing = current;
            idleTicks = REPLAY_DELAY;
            failed = false;
        }

        if (playing == null || failed) {
            drawn = PreviewParticles.Drawn.NOTHING;
            return;
        }

        pendingTicks += a;

        // A piece the editor builds half-configured, such as a skinned mesh with no armature, may throw while playing
        try {
            for (; pendingTicks >= 1.0F; pendingTicks -= 1.0F) {
                if (particles.isEmpty() && idleTicks++ >= REPLAY_DELAY) {
                    system = new AkytheraParticleSystem(null, playing instanceof TrailParticleBlueprint ? TRAIL_SYSTEM : SYSTEM, systemTransformAt(0), null, particles);
                    particles.addSystem(system);
                    playTicks = 0;
                    idleTicks = 0;
                } else if (system != null) {
                    system.moveTo(systemTransformAt(++playTicks));
                }

                particles.tick();
            }

            // The system as this frame draws it
            if (system != null) system.moveTo(systemTransformAt(playTicks + pendingTicks));

            particles.frameUpdate(a * 0.05F, pendingTicks);
        } catch (RuntimeException e) {
            AkytheraLabs.LOGGER.warn("The particle preview stops until the piece changes", e);
            particles.clear();
            failed = true;
            return;
        }

        drawn = particles.draw(model3DViewer, poseStack, byteBuffer, pendingTicks);

        if (focused) {
            float halfWidth = holder.getBbWidth() * 0.5F;
            float height = holder.getBbHeight();

            for (Vec3 bottom : drawn.bottoms().isEmpty() ? List.of(ORIGIN) : drawn.bottoms()) {
                WireframeCuboidComponent.drawBox(
                    byteBuffer,
                    poseStack.last(),
                    new AABB(bottom.x - halfWidth, bottom.y, bottom.z - halfWidth, bottom.x + halfWidth, bottom.y + height, bottom.z + halfWidth),
                    BOUNDING_BOX_COLOR
                );
            }
        }
    }

    /// Where the system stands `ticks` into a play: at [#ORIGIN], or for a trail, turned `ticks / SWEEP_TICKS` of the way
    /// about the Z axis through a point one trail length above the floor.
    private Transform systemTransformAt(final float ticks) {
        if (!(playing instanceof TrailParticleBlueprint trail)) return Transform.fromTranslation(ORIGIN);

        double turns = (double)ticks / SWEEP_TICKS;

        return Transform.of(new Vec3(0.0D, trail.length() + 0.05D, 0.0D), new Quaterniond().rotateZ(turns * Mth.TWO_PI), new Vec3(1.0D, 1.0D, 1.0D));
    }

    /// A system of one emitter that spawns the previewed piece by `spawn`.
    private static ParticleSystemBlueprint previewSystem(final EmitterSpawnRule spawn) {
        return new ParticleSystemBlueprint(
            List.of(new ParticleEmitterBlueprint(
                "preview",
                Either.left(PREVIEW_KEY),
                0.0F,
                spawn,
                false,
                Transform.IDENTITY,
                Transform.IDENTITY,
                KeyframeTrack.empty(),
                KeyframeTrack.empty(),
                List.of(),
                List.of()
            )),
            List.of()
        );
    }

    /// Writes a bounding box size: X the width, Y the height.
    private void writeBoundingBox(final @Nullable Vec3 size) {
        if (size == null) return;

        holder.setBoundingBox((float)size.x, (float)size.y);
        onBoundingBoxWritten.run();
    }

    @Override
    public int drawOrder() {
        return 30; // translucent, after grid (1) and models (10)
    }

    @Override
    public boolean canBeFocused(final Model3DViewer owner) {
        return true;
    }

    /// The nearest sprite quad `ray` passes within the half extent of, or bounding box of another particle it enters.
    @Override
    public float rayTrace(final Model3DViewer owner, final Ray ray) {
        float nearest = -1.0F;

        for (PreviewParticles.Sprite frame : drawn.sprites()) {
            Vec3 position = frame.frame().position();
            Vector3f toCenter = new Vector3f((float)position.x, (float)position.y, (float)position.z).sub(ray.origin());
            float along = toCenter.dot(ray.direction());
            float missSquared = toCenter.lengthSquared() - along * along;

            if (along >= 0.0F && missSquared <= frame.frame().size() * frame.frame().size() && (nearest < 0.0F || along < nearest)) {
                nearest = along;
            }
        }

        Vec3 from = new Vec3(ray.origin());
        Vec3 to = new Vec3(ray.at((float)PICK_REACH));

        for (AABB box : drawn.boxes()) {
            float along = box.clip(from, to).map(hit -> (float)hit.distanceTo(from)).orElse(-1.0F);

            if (along >= 0.0F && (nearest < 0.0F || along < nearest)) nearest = along;
        }

        return nearest;
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

    /// The gizmo restores the size the drag started at, and no commit follows.
    @Override
    public void onDragCancelled(final Model3DViewer owner) {
        LocatableComponent.super.onDragCancelled(owner);
        dragStart = null;
    }

    @Override
    public void destroy() {
        particles.clear();
        system = null;
        particles.releaseTextures();
    }

    // ***************************************************************
    // LocatableComponent
    // ***************************************************************

    @Override
    public Transform parentTransform() {
        return Transform.IDENTITY;
    }

    /// The first particle's bounding box: its bottom center, and its width, height and width as the scale.
    @Override
    public Transform localTransform() {
        Vec3 bottom = drawn.bottoms().isEmpty() ? ORIGIN : drawn.bottoms().getFirst();

        return Transform.of(bottom, new Quaterniond(), new Vec3(holder.getBbWidth(), holder.getBbHeight(), holder.getBbWidth()));
    }

    /// Takes the width from whichever of X and Z moved further from the width the drag started at, and the height
    /// from Y.
    @Override
    public void applyLocalTransform(final Transform transform, final boolean live) {
        if (dragStart == null) {
            dragStart = new Vec3(holder.getBbWidth(), holder.getBbHeight(), 0.0D);

            // The baseline the committed entry pops
            if (live) HistoryManager.getInstance().pushHistory(new PendingDataChange<>(holder, dragStart, this::writeBoundingBox));
        }

        double scaleX = transform.sca().x;
        double scaleZ = transform.sca().z;
        Vec3 size = new Vec3(Math.abs(scaleX - dragStart.x) >= Math.abs(scaleZ - dragStart.x) ? scaleX : scaleZ, transform.sca().y, 0.0D);

        if (live) {
            holder.setBoundingBox((float)size.x, (float)size.y);
            return;
        }

        dragStart = null;
        HistoryManager.getInstance().pushHistory(new DraggingDataChange<>(holder, size, this::writeBoundingBox));
        writeBoundingBox(size);
    }

    @Override
    public Set<GizmoComponent.Mode> gizmoModes() {
        return EnumSet.of(GizmoComponent.Mode.SCALE);
    }
}
