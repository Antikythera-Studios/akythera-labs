package com.yesman.akytheralabs.akythera.animation.notify.impl;

import com.yesman.akythera.api.animation.notify.impl.StartRagdollNotifyState;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyStateHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

import java.util.Map;

public final class StartRagdollNotifyStateHolder extends AnimationNotifyStateHolder<StartRagdollNotifyState> {
    /// Multiplier on the velocity the bodies take from the animation.
    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_VELOCITY_SCALE, order = 0, min = 0.0F, max = 10.0F)
    public float velocityScale = 1.0F;

    /// How long the rag doll runs from the window's start, in ticks.
    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_DURATION, order = 1, min = 1.0F)
    public int durationTicks = StartRagdollNotifyState.DEFAULT_DURATION_TICKS;

    @Override
    public void readFrom(StartRagdollNotifyState object) {
        this.velocityScale = object.velocityScale();
        this.durationTicks = object.durationTicks();
    }

    @Override
    public StartRagdollNotifyState makeObject() {
        return new StartRagdollNotifyState(velocityScale, durationTicks);
    }

    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.BOTH; // StartRagdollNotifyState hard-codes it
    }

    @Override
    public String notifyName() {
        return StartRagdollNotifyState.class.getSimpleName();
    }

    /// The previewer has no rag doll to start.
    @Override
    public void simulateBegin(Model3DViewer previewer, TimeWindow window) {
    }

    @Override
    public void simulateTick(Model3DViewer previewer, float prevElapsedTime, float currentElapsedTime) {
    }

    @Override
    public void simulateEnd(Model3DViewer previewer) {
    }

    @Override
    public String sourceTemplate() {
        return "notify_state/start_ragdoll.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        return Map.of("velocityScale", velocityScale, "durationTicks", durationTicks);
    }
}
