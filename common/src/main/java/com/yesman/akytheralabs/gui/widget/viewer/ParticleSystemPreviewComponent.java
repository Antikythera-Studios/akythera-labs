package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.client.api.particle.AkytheraParticleSystem;
import com.yesman.akythera.client.api.particle.AkytheraTrailParticle;
import com.yesman.akythera.client.api.particle.ParticleHost;
import com.yesman.akythera.client.api.particle.module.DataDrivenParticleModule;
import com.yesman.akythera.client.api.particle.module.ParticleInitializerModule;
import com.yesman.akythera.client.api.particle.module.ParticleUpdateModule;
import com.yesman.akythera.core.blueprint.particle.ParticleBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleEmitterBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleInitializerBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleInitializerScriptBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleUpdaterBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleUpdaterScriptBlueprint;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.akythera.particle.ParticleBlueprintHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleEmitterHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleInitializerScriptHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleSystemHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleUpdaterScriptHolder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/// Plays the system a [ParticleSystemHolder] builds in [Model3DViewer], with no level, at the world's origin, along a
/// playhead in seconds. The particles play in a [PreviewParticles].
///
/// Nothing plays until [#play], and the playhead stays between zero and [ParticleSystemHolder#getPreviewEnd].
/// [#pause] and [#seek] freeze the particles and remove the orphans: those whose emitter is not in the track, or whose
/// emitter's time span does not cover the playhead. The next [#play] goes on from the playhead. Reaching the end pauses
/// the play and removes the orphans there. Emitters that name no particle are left out. An edit to the emitters restarts a running
/// play, and rebuilds a paused one at its next [#play].
///
/// A frame draws the particles between the system's last two ticks, so while playing [#time] answers that drawn time,
/// one tick behind the last tick, and a paused play draws the last tick itself.
///
/// The pieces and the module node graphs the emitters name come from the open project, else from the loaded registry.
public final class ParticleSystemPreviewComponent implements Model3DViewerComponent {
    /// Two times closer than this, in seconds, are the same tick.
    private static final float TIME_EPSILON = 1.0E-4F;

    private final ParticleSystemHolder holder;
    private final PreviewParticles particles = new PreviewParticles(ParticleSystemPreviewComponent::piece);

    /// The system of the current play. Null before the first play and after [#stop].
    private @Nullable AkytheraParticleSystem system;

    /// What [#system] was built from.
    private @Nullable Source source;

    private boolean playing;

    /// Whether the last play ran to the preview's end.
    private boolean finished;

    /// Whether the playhead moved since the particles last ticked.
    private boolean seeked;

    /// The playhead, in seconds: the system's time at its last tick, or where it was seeked to.
    private float time;

    /// The system's time at its next tick, in seconds.
    private float nextTick;

    /// The playhead the running play started from, in seconds.
    private float playStart;

    /// Ticks not played yet, below one. Also the partial tick frames are drawn at.
    private float pendingTicks;

    public ParticleSystemPreviewComponent(final ParticleSystemHolder holder) {
        this.holder = holder;
    }

    /// Goes on from the playhead, building the system again when there is none, it ended, or it was edited. Starts over
    /// from zero after a finished play. Nothing plays when the holder builds no valid system.
    public void play() {
        if (finished) stop();

        Source current = Source.of(holder);

        if (current == null) {
            AkytheraLabs.LOGGER.warn("The particle system can't play: an emitter does not build");
            return;
        }

        ParticleSystemBlueprint blueprint = new ParticleSystemBlueprint(
            current.emitters(), holder.getVariables().stream().map(BlueprintScriptData.Variable::toInfo).toList()
        );
        Optional<String> error = blueprint.validate().error().map(result -> result.message());

        if (error.isPresent()) {
            AkytheraLabs.LOGGER.warn("The particle system can't play: {}", error.get());
            return;
        }

        if (system == null || !system.isAlive() || !current.equals(source)) {
            if (system != null) particles.dropSystem(system);

            system = new AkytheraParticleSystem(null, blueprint, Transform.IDENTITY, null, particles);
            system.seek(time);
            particles.addSystem(system);
            source = current;
            nextTick = time;
        } else if (seeked) {
            system.seek(time);
            nextTick = time;
        }

        seeked = false;
        playing = true;
        playStart = time;
    }

    /// Builds the system again: at once for a running play, which starts over, else at the next [#play].
    public void rebuild() {
        if (playing) {
            stop();
            play();
        } else {
            source = null;
        }
    }

    /// Freezes the particles and removes the orphans.
    public void pause() {
        playing = false;
        removeOrphans();
    }

    /// Ends the current play, clears its particles, and moves the playhead to zero.
    public void stop() {
        playing = false;
        finished = false;
        seeked = false;
        particles.clear();
        system = null;
        source = null;
        time = 0.0F;
        nextTick = 0.0F;
        pendingTicks = 0.0F;
    }

    /// Freezes the particles, moves the playhead to `seconds` within the preview's end, and removes the orphans and the
    /// trails. A trail whose emitter covers the playhead spawns again there at the next [#play].
    public void seek(final float seconds) {
        playing = false;
        finished = false;
        seeked = true;
        time = Math.clamp(seconds, 0.0F, endTime());
        pendingTicks = 0.0F;
        removeOrphans();
        particles.removeParticles(particle -> particle instanceof AkytheraTrailParticle);
    }

    /// Removes the particles whose emitter is not in the track, or whose emitter's time span does not cover the
    /// playhead. A time span starts at the emitter's start and ends before its end; an instant emitter has none.
    private void removeOrphans() {
        float end = endTime();

        particles.removeParticles(particle -> holder.getEmitters().stream().noneMatch(emitter -> {
            float spanEnd = emitter.loops() ? end : emitter.getStart() + emitter.getSpan();

            return emitter.getName().equals(particle.getEmitter().getBlueprint().name()) && emitter.getStart() <= time && time < spanEnd;
        }));
    }

    /// When the play ends, in seconds, or [Float#MAX_VALUE] when it never does.
    private float endTime() {
        return holder.isEndless() ? Float.MAX_VALUE : holder.getPreviewEnd();
    }

    public boolean isPlaying() {
        return playing;
    }

    /// The playhead, in seconds: the time the particles are drawn at.
    public float time() {
        return playing ? Math.clamp(time + (pendingTicks - 1.0F) / 20.0F, playStart, endTime()) : time;
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        Source edited = playing ? Source.of(holder) : null;

        if (edited != null && !edited.equals(source)) {
            stop();
            play();
        }

        if (playing) {
            float end = endTime();

            pendingTicks += a;

            // A half-configured piece may throw while playing
            try {
                for (; pendingTicks >= 1.0F && nextTick <= end + TIME_EPSILON; pendingTicks -= 1.0F) {
                    particles.tick();
                    time = Math.min(nextTick, end);
                    nextTick += 0.05F;
                }

                particles.frameUpdate(a * 0.05F, pendingTicks);
            } catch (RuntimeException e) {
                AkytheraLabs.LOGGER.warn("The particle system preview stops", e);
                stop();
                return;
            }

            if (nextTick > end + TIME_EPSILON) {
                pause();
                finished = true;
                pendingTicks = 0.0F;
            }
        }

        particles.draw(model3DViewer, poseStack, byteBuffer, playing ? pendingTicks : 1.0F);
    }

    @Override
    public int drawOrder() {
        return 30; // translucent, after grid (1) and models (10)
    }

    @Override
    public void destroy() {
        stop();
        particles.releaseTextures();
    }

    /// The piece `key` names: the open project's, else the loaded registry's. Null when neither has one that builds.
    static @Nullable ParticleBlueprint piece(final ResourceKey<ParticleBlueprint> key) {
        AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();
        ParticleBlueprintHolder projectPiece = project == null ? null : project.<ParticleBlueprint, ParticleBlueprintHolder>getDataHolder(key).orElse(null);

        try {
            return projectPiece != null ? projectPiece.makeObject() : ParticleHost.LEVEL.piece(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /// The part of a holder a play is built from: the emitters that name a particle, with the modules the open project
    /// holds as [ProjectInitializer] and [ProjectUpdater]. Two are equal when they play the same.
    record Source(List<ParticleEmitterBlueprint> emitters) {
        /// Null while an emitter does not build, such as one with a module graph that does not compile.
        static @Nullable Source of(final ParticleSystemHolder holder) {
            AkytheraProject project = AkytheraProject.getCurrentProject();

            try {
                return new Source(
                    holder.getEmitters().stream().filter(ParticleEmitterHolder::hasParticle).map(ParticleEmitterHolder::makeObject)
                        .map(emitter -> new ParticleEmitterBlueprint(
                            emitter.name(), emitter.particle(), emitter.start(), emitter.spawn(), emitter.hideParticlesOnFinish(),
                            emitter.pivot(), emitter.transform(), emitter.pivotKeys(), emitter.transformKeys(),
                            emitter.initializers().stream()
                                .map(initializer -> initializer instanceof ParticleInitializerBlueprint.ByData(var module) && project.getDataHolder(module).isPresent()
                                    ? new ProjectInitializer(module)
                                    : initializer)
                                .toList(),
                            emitter.updaters().stream()
                                .map(updater -> updater instanceof ParticleUpdaterBlueprint.ByData(var module) && project.getDataHolder(module).isPresent()
                                    ? new ProjectUpdater(module)
                                    : updater)
                                .toList()
                        ))
                        .toList()
                );
            } catch (RuntimeException e) {
                return null;
            }
        }
    }

    /// The initializer node graph `module` names in the open project, built from its holder as it is now.
    private record ProjectInitializer(ResourceKey<ParticleInitializerScriptBlueprint> module) implements ParticleInitializerBlueprint {
        @Override
        @SuppressWarnings("unchecked")
        public ParticleInitializerModule instantiate(final AkytheraParticleEmitter emitter) {
            AkytheraProject project = AkytheraProject.getCurrentProject();

            try {
                ParticleInitializerScriptHolder holder = project.<ParticleInitializerScriptBlueprint, ParticleInitializerScriptHolder>getDataHolderOrThrow(module);

                return new DataDrivenParticleModule<>(
                    (Holder.Reference<ParticleInitializerScriptBlueprint>)project.getEntryHolderOrDangling(module), holder.makeObject().body(), emitter, ParticleInitializerModule.class
                );
            } catch (RuntimeException e) {
                throw new IllegalStateException("Particle initializer " + module.identifier() + " does not build: " + e.getMessage(), e);
            }
        }

        @Override
        public Object moduleType() {
            return module;
        }

        @Override
        public MapCodec<? extends ParticleInitializerBlueprint> mapCodec() {
            return ByData.MAP_CODEC;
        }
    }

    /// The updater node graph `module` names in the open project, built from its holder as it is now.
    private record ProjectUpdater(ResourceKey<ParticleUpdaterScriptBlueprint> module) implements ParticleUpdaterBlueprint {
        @Override
        @SuppressWarnings("unchecked")
        public ParticleUpdateModule instantiate(final AkytheraParticleEmitter emitter) {
            AkytheraProject project = AkytheraProject.getCurrentProject();

            try {
                ParticleUpdaterScriptHolder holder = project.<ParticleUpdaterScriptBlueprint, ParticleUpdaterScriptHolder>getDataHolderOrThrow(module);

                return new DataDrivenParticleModule<>(
                    (Holder.Reference<ParticleUpdaterScriptBlueprint>)project.getEntryHolderOrDangling(module), holder.makeObject().body(), emitter, ParticleUpdateModule.class
                );
            } catch (RuntimeException e) {
                throw new IllegalStateException("Particle updater " + module.identifier() + " does not build: " + e.getMessage(), e);
            }
        }

        @Override
        public Object moduleType() {
            return module;
        }

        @Override
        public MapCodec<? extends ParticleUpdaterBlueprint> mapCodec() {
            return ByData.MAP_CODEC;
        }
    }
}
