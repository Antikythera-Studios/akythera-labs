package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.animation.notify.impl.TrailParticleNotifyState;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.api.particle.AkytheraParticleSystem;
import com.yesman.akythera.core.animation.keyframe.AnimationClip;
import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.animation.NotifyTrackHolder;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyStateHolder;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.akythera.particle.ParticleSystemHolder;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/// Plays the particle systems of an animation's [TrailParticleNotifyState]s in an animation editor's [Model3DViewer],
/// with no level, each following its socket on the previewed model. The particles play in a [PreviewParticles].
///
/// A socket is resolved by the viewer's [PreviewPatchComponent]. Each notify state with a
/// [AnimationNotifyStateHolder#socketPath] naming a socket it can't resolve raises a debug message, as does an empty
/// one once the notify state is saved.
public final class NotifyParticlePreviewComponent implements Model3DViewerComponent {
    private final Supplier<NotifyTrackHolder> notifyTracks;
    private final PreviewParticles particles = new PreviewParticles(ParticleSystemPreviewComponent::piece);

    /// The plays [#play] asked for, started by the next frame that moves the playhead.
    private final List<Play> pending = new ArrayList<>();

    /// The play of each playing system.
    private final Map<AkytheraParticleSystem, Play> plays = new IdentityHashMap<>();

    /// Ticks not played yet, below one. Also the partial tick frames are drawn at.
    private float pendingTicks;

    /// The playhead at the last frame, NaN before the first.
    private float lastElapsed = Float.NaN;

    public NotifyParticlePreviewComponent(final Supplier<NotifyTrackHolder> notifyTracks) {
        this.notifyTracks = notifyTracks;
    }

    /// Plays `particleSystem` on `socket` as [TrailParticleNotifyState] does, from the next frame that moves the
    /// playhead. Nothing plays when the system or the socket can't be found.
    public void play(final Model3DViewer viewer, final ResourceKey<ParticleSystemBlueprint> particleSystem, final String socket, final TimeWindow window) {
        ParticleSystemBlueprint blueprint = blueprint(particleSystem);

        if (blueprint != null && socketTransform(viewer, socket) != null) pending.add(new Play(blueprint, socket, window));
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        EntityModelComponent model = model3DViewer.getComponent(EntityModelComponent.class);

        model3DViewer.setDebugMessages(this, model == null
            ? List.of()
            : notifyTracks.get().getTracks().stream()
                .flatMap(track -> track.notifyStates.stream())
                .<Component>mapMulti((state, messages) -> {
                    String socket = state.holder.socketPath();

                    if (socket == null) return;

                    if (socket.isEmpty()) {
                        if (state.holder.isSaved()) messages.accept(Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_SOCKET_EMPTY, state.holder.notifyName()));
                    } else if (socketTransform(model3DViewer, socket) == null) {
                        messages.accept(Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_SOCKET_NOT_FOUND, socket, state.holder.notifyName()));
                    }
                })
                .toList());

        ArmatureComponent armature = model == null ? null : model.getArmatureComponent();
        EntityModelComponent.EditorScrubAnimator animator = model != null && model.getAnimator() instanceof EntityModelComponent.EditorScrubAnimator scrub ? scrub : null;
        AnimationClip clip = animator == null || animator.getPreviewAnimation() == null
            ? null
            : animator.getPreviewAnimation().getOrHandleException(AkytheraLabs.LOGGER::error);
        float prev = lastElapsed;
        float cur = animator == null ? Float.NaN : animator.getElapsedTime();

        lastElapsed = cur;

        if (particles.isEmpty() && pending.isEmpty()) {
            pendingTicks = 0.0F;
            return;
        }

        pendingTicks += a;

        // A half-configured piece may throw while playing
        try {
            // The sockets as this frame draws them
            plays.forEach((system, play) -> {
                Transform transform = socketTransform(model3DViewer, play.socket);

                if (transform != null) system.moveTo(transform);
            });

            for (; pendingTicks >= 1.0F; pendingTicks -= 1.0F) {
                particles.tick();
            }

            // NaN compares false, so a frame with no playhead or one that moved back moves nothing
            if (cur > prev && a > 0.0F) {
                float ticksPerSecond = a / (cur - prev);

                for (Play play : pending) {
                    Transform transform = socketTransform(model3DViewer, play.socket);

                    if (transform == null) continue;

                    float span = (play.window.to() - play.window.from()) * ticksPerSecond / 20.0F;
                    AkytheraParticleSystem system = new AkytheraParticleSystem(
                        null,
                        new ParticleSystemBlueprint(play.blueprint.emitters().stream().map(emitter -> emitter.withTimeSpan(span)).toList(), play.blueprint.variables()),
                        transform,
                        null,
                        particles
                    );

                    particles.addSystem(system);
                    plays.put(system, play);
                    system.start(pendingTicks - (cur - play.window.from()) * ticksPerSecond);
                }

                pending.clear();

                if (armature != null && clip != null) {
                    plays.forEach((system, play) -> system.setFrameSampler(TrailParticleNotifyState.frameSampler(
                        armature,
                        clip::getPose,
                        play.window,
                        prev,
                        cur,
                        system.getAge() + pendingTicks,
                        ticksPerSecond,
                        () -> socketTransform(model3DViewer, play.socket)
                    )));
                }
            }

            particles.frameUpdate(a * 0.05F, pendingTicks);
        } catch (RuntimeException e) {
            AkytheraLabs.LOGGER.warn("The notify particle preview stops its particles", e);
            clear();
            return;
        }

        plays.keySet().removeIf(system -> !system.isAlive());
        particles.draw(model3DViewer, poseStack, byteBuffer, pendingTicks);
    }

    /// `socket`'s transform in viewer space, resolved by the viewer's [PreviewPatchComponent]. Null without one.
    private static @Nullable Transform socketTransform(final Model3DViewer viewer, final String socket) {
        PreviewPatchComponent patch = viewer.getComponent(PreviewPatchComponent.class);

        return patch == null ? null : patch.socketTransform(viewer, socket);
    }

    /// The system `key` names: built from the open project's holder as it is now, else the loaded registry's. Null when
    /// neither has one that builds.
    private static @Nullable ParticleSystemBlueprint blueprint(final ResourceKey<ParticleSystemBlueprint> key) {
        ParticleSystemHolder holder = AkytheraProject.getCurrentProject().<ParticleSystemBlueprint, ParticleSystemHolder>getDataHolder(key).orElse(null);

        if (holder == null) return StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY).getValue(key);

        ParticleSystemPreviewComponent.Source source = ParticleSystemPreviewComponent.Source.of(holder);

        return source == null
            ? null
            : new ParticleSystemBlueprint(source.emitters(), holder.getVariables().stream().map(BlueprintScriptData.Variable::toInfo).toList())
                .validate()
                .result()
                .orElse(null);
    }

    @Override
    public int drawOrder() {
        return 30; // translucent, after grid (1) and models (10)
    }

    /// Removes every particle, the systems included, and the plays not started yet.
    public void clear() {
        particles.clear();
        plays.clear();
        pending.clear();
        pendingTicks = 0.0F;
    }

    @Override
    public void destroy() {
        clear();
        particles.releaseTextures();
    }

    /// A system to play from `blueprint`, following `socket` over `window`.
    private record Play(ParticleSystemBlueprint blueprint, String socket, TimeWindow window) {
    }
}
