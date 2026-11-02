package com.yesman.akytheralabs.akythera.animation.notify.impl;

import com.yesman.akythera.api.animation.notify.impl.LivingHurtNotifyState;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyStateHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

import java.util.Map;

/// Editor holder for a [LivingHurtNotifyState].
public final class LivingHurtNotifyStateHolder extends AnimationNotifyStateHolder<LivingHurtNotifyState> {
    /// A [ComponentAttachable#getComponentAt] path of the host whose colliders strike.
    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_SOCKET, order = 0, setOnCommit = true)
    private String socket = "";

    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_MAX_TARGETS, order = 1, min = 1.0F)
    private int maxTargets = Integer.MAX_VALUE;

    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_SHARED, order = 2)
    private boolean shared;

    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_IGNORE_INVULNERABLE_TIME, order = 3)
    private boolean ignoreInvulnerableTime;

    @Override
    public String socketPath() {
        return socket;
    }

    @Override
    public void readFrom(final LivingHurtNotifyState object) {
        socket = object.socket();
        maxTargets = object.maxTargets();
        shared = object.shared();
        ignoreInvulnerableTime = object.ignoreInvulnerableTime();
    }

    @Override
    public LivingHurtNotifyState makeObject() {
        return new LivingHurtNotifyState(socket, maxTargets, shared, ignoreInvulnerableTime);
    }

    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.SERVER; // LivingHurtNotifyState hard-codes it
    }

    @Override
    public String notifyName() {
        return LivingHurtNotifyState.class.getSimpleName();
    }

    @Override
    public void simulateBegin(final Model3DViewer previewer, final TimeWindow window) {
    }

    @Override
    public void simulateTick(final Model3DViewer previewer, final float prevElapsedTime, final float currentElapsedTime) {
    }

    @Override
    public void simulateEnd(final Model3DViewer previewer) {
    }

    @Override
    public String sourceTemplate() {
        return "notify_state/living_hurt.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        return Map.of("socket", socket, "maxTargets", maxTargets, "shared", shared, "ignoreInvulnerableTime", ignoreInvulnerableTime);
    }
}
