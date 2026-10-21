package com.yesman.akytheralabs.akythera.animation.notify.impl;

import com.yesman.akythera.api.animation.notify.impl.LockMovementNotifyState;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyStateHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

import java.util.Map;

public final class LockMovementNotifyStateHolder extends AnimationNotifyStateHolder<LockMovementNotifyState> {
    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_ROTATION, order = 0)
    public LockMovementNotifyState.Rotation rotation = LockMovementNotifyState.Rotation.FACE_VIEW;

    @Override
    public void readFrom(LockMovementNotifyState object) {
        this.rotation = object.rotation();
    }

    @Override
    public LockMovementNotifyState makeObject() {
        return new LockMovementNotifyState(rotation);
    }

    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.BOTH;
    }

    @Override
    public String notifyName() {
        return LockMovementNotifyState.class.getSimpleName();
    }

    @Override
    public void simulateBegin(Model3DViewer previewer) {
    }

    @Override
    public void simulateTick(Model3DViewer previewer, float prevElapsedTime, float currentElapsedTime) {
    }

    @Override
    public void simulateEnd(Model3DViewer previewer) {
    }

    @Override
    public String sourceTemplate() {
        return "notify_state/lock_movement.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        return Map.of("rotation", rotation.name());
    }
}
