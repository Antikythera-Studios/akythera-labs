package com.yesman.akytheralabs.akythera.animation.notify.impl;

import com.yesman.akythera.api.animation.notify.impl.AimNotifyState;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyStateHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

import java.util.Map;

/// Editor holder for an [AimNotifyState].
public final class AimNotifyStateHolder extends AnimationNotifyStateHolder<AimNotifyState> {
    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_BONE, order = 0, setOnCommit = true)
    private String bone = "";

    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_WEIGHT, order = 1)
    private float weight = 1.0F;

    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_MAX_PITCH, order = 2, min = 0.0F, max = 180.0F)
    private float maxPitch = 90.0F;

    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_BLEND_TIME, order = 3, min = 0.0F, max = 10.0F)
    private float blendTime = 0.2F;

    @Override
    public void readFrom(final AimNotifyState object) {
        bone = object.bone();
        weight = object.weight();
        maxPitch = object.maxPitch();
        blendTime = object.blendTime();
    }

    @Override
    public AimNotifyState makeObject() {
        return new AimNotifyState(bone, weight, maxPitch, blendTime);
    }

    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.BOTH;
    }

    @Override
    public String notifyName() {
        return AimNotifyState.class.getSimpleName();
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
        return "notify_state/aim.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        return Map.of("bone", bone, "weight", weight, "maxPitch", maxPitch, "blendTime", blendTime);
    }
}
