package com.yesman.akytheralabs.akythera.animation.notify.impl;

import com.yesman.akythera.api.animation.notify.impl.TrailParticleNotifyState;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.blueprint.particle.ParticleAttachment;
import com.yesman.akythera.core.blueprint.particle.ParticleSystemBlueprint;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyStateHolder;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.viewer.NotifyParticlePreviewComponent;
import net.minecraft.resources.ResourceKey;

import java.util.Map;

/// Editor holder for a [TrailParticleNotifyState]. The previewer plays it through its [NotifyParticlePreviewComponent].
public final class TrailParticleNotifyStateHolder extends AnimationNotifyStateHolder<TrailParticleNotifyState> {
    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_PARTICLE_SYSTEM, order = 0)
    private ResourceKey<ParticleSystemBlueprint> particleSystem = ResourceKey.create(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY, DataHolder.EMPTY_REFERENCE);

    /// A [ParticleAttachment#socketPath], blank for the entity's root.
    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_SOCKET, order = 1, setOnCommit = true)
    private String socket = "";

    public String getSocket() {
        return socket;
    }

    @Override
    public void readFrom(final TrailParticleNotifyState object) {
        particleSystem = object.particleSystem();
        socket = object.socket();
    }

    @Override
    public TrailParticleNotifyState makeObject() {
        return new TrailParticleNotifyState(particleSystem, socket);
    }

    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.CLIENT; // TrailParticleNotifyState hard-codes it
    }

    @Override
    public String notifyName() {
        return TrailParticleNotifyState.class.getSimpleName();
    }

    @Override
    public void simulateBegin(final Model3DViewer previewer, final TimeWindow window) {
        NotifyParticlePreviewComponent particles = previewer.getComponent(NotifyParticlePreviewComponent.class);

        if (particles != null) particles.play(previewer, particleSystem, socket, window);
    }

    @Override
    public void simulateTick(final Model3DViewer previewer, final float prevElapsedTime, final float currentElapsedTime) {
    }

    @Override
    public void simulateEnd(final Model3DViewer previewer) {
    }

    @Override
    public String sourceTemplate() {
        return "notify_state/trail_particle.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        return Map.of("particleSystem", particleSystem.identifier().toString(), "socket", socket);
    }
}
