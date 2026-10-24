package com.yesman.akytheralabs.akythera.animation.notify.impl;

import com.yesman.akythera.api.animation.notify.impl.StartRagdollNotify;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

import java.util.Map;

public final class StartRagdollNotifyHolder extends AnimationNotifyHolder<StartRagdollNotify> {
    /// Multiplier on the velocity the bodies take from the animation.
    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_VELOCITY_SCALE, order = 0, min = 0.0F, max = 10.0F)
    public float velocityScale = 1.0F;

    /// How long the rag doll runs, in ticks.
    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_DURATION, order = 1, min = 1.0F)
    public int durationTicks = StartRagdollNotify.DEFAULT_DURATION_TICKS;

    @Override
    public void readFrom(StartRagdollNotify object) {
        this.velocityScale = object.velocityScale();
        this.durationTicks = object.durationTicks();
    }

    @Override
    public StartRagdollNotify makeObject() {
        return new StartRagdollNotify(velocityScale, durationTicks);
    }

    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.BOTH; // StartRagdollNotify hard-codes it
    }

    @Override
    public String notifyName() {
        return StartRagdollNotify.class.getSimpleName();
    }

    /// The previewer has no rag doll to start.
    @Override
    public void simulate(Model3DViewer previewer) {
    }

    @Override
    public String sourceTemplate() {
        return "notify/start_ragdoll.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        return Map.of("velocityScale", velocityScale, "durationTicks", durationTicks);
    }
}
