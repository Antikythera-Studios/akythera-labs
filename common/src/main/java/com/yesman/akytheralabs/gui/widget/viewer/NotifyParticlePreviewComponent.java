package com.yesman.akytheralabs.gui.widget.viewer;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.animation.notify.impl.TrailParticleNotifyState;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewerComponent;
import com.yesman.akythera.client.api.particle.AkytheraParticleSystem;
import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.math.Transform;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.animation.NotifyTrackHolder;
import com.yesman.akytheralabs.akythera.animation.notify.impl.TrailParticleNotifyStateHolder;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.akythera.particle.ParticleSystemHolder;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/// Plays the particle systems of an animation's [TrailParticleNotifyState]s in an animation editor's [Model3DViewer],
/// with no level, each following its socket on the previewed model. The particles play in a [PreviewParticles].
///
/// A socket is resolved by the viewer's [PreviewPatchComponent]. Each trail particle notify state naming a socket it
/// can't resolve raises a debug message.
public final class NotifyParticlePreviewComponent implements Model3DViewerComponent {
    private final Supplier<NotifyTrackHolder> notifyTracks;
    private final PreviewParticles particles = new PreviewParticles(ParticleSystemPreviewComponent::piece);

    /// The socket path each playing system follows.
    private final Map<AkytheraParticleSystem, String> sockets = new IdentityHashMap<>();

    /// Ticks not played yet, below one. Also the partial tick frames are drawn at.
    private float pendingTicks;

    public NotifyParticlePreviewComponent(final Supplier<NotifyTrackHolder> notifyTracks) {
        this.notifyTracks = notifyTracks;
    }

    /// Plays `particleSystem` on `socket` with every emitter's time span reshaped to `window`, as
    /// [TrailParticleNotifyState#onBegin] does. Nothing plays when the system or the socket can't be found.
    public void play(final Model3DViewer viewer, final ResourceKey<ParticleSystemBlueprint> particleSystem, final String socket, final TimeWindow window) {
        ParticleSystemBlueprint blueprint = blueprint(particleSystem);
        Transform transform = socketTransform(viewer, socket);

        if (blueprint == null || transform == null) return;

        float span = window.to() - window.from();
        AkytheraParticleSystem system = new AkytheraParticleSystem(
            null,
            new ParticleSystemBlueprint(blueprint.emitters().stream().map(emitter -> emitter.withTimeSpan(span)).toList(), blueprint.variables()),
            transform,
            null,
            particles
        );

        particles.addSystem(system);
        sockets.put(system, socket);
    }

    @Override
    public void extractComponent(
        GuiGraphicsExtractor graphics, Model3DViewer model3DViewer, PoseStack poseStack, ByteBufferBuilder byteBuffer, int mouseX, int mouseY, float a
    ) {
        model3DViewer.setDebugMessages(this, model3DViewer.getComponent(EntityModelComponent.class) == null
            ? List.of()
            : notifyTracks.get().getTracks().stream()
                .flatMap(track -> track.notifyStates.stream())
                .<Component>mapMulti((state, messages) -> {
                    if (state.holder instanceof TrailParticleNotifyStateHolder trail && socketTransform(model3DViewer, trail.getSocket()) == null) {
                        messages.accept(Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_SOCKET_NOT_FOUND, trail.getSocket(), trail.notifyName()));
                    }
                })
                .toList());

        if (particles.isEmpty()) {
            pendingTicks = 0.0F;
            return;
        }

        pendingTicks += a;

        // A half-configured piece may throw while playing
        try {
            for (; pendingTicks >= 1.0F; pendingTicks -= 1.0F) {
                sockets.forEach((system, socket) -> {
                    Transform transform = socketTransform(model3DViewer, socket);

                    if (transform != null) system.moveTo(transform);
                });

                particles.tick();
            }

            particles.frameUpdate(a * 0.05F, pendingTicks);
        } catch (RuntimeException e) {
            AkytheraLabs.LOGGER.warn("The notify particle preview stops its particles", e);
            particles.clear();
            sockets.clear();
            return;
        }

        sockets.keySet().removeIf(system -> !system.isAlive());
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

    /// Removes every particle, the systems included.
    public void clear() {
        particles.clear();
        sockets.clear();
        pendingTicks = 0.0F;
    }

    @Override
    public void destroy() {
        clear();
        particles.releaseTextures();
    }
}
