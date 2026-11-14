package com.yesman.akytheralabs.akythera.animation.notify.impl;

import com.yesman.akythera.api.animation.notify.impl.NoGravityNotifyState;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyStateHolder;

import java.util.Map;

public final class NoGravityNotifyStateHolder extends AnimationNotifyStateHolder<NoGravityNotifyState> {
    @Override
    public void readFrom(NoGravityNotifyState object) {
    }

    @Override
    public NoGravityNotifyState makeObject() {
        return new NoGravityNotifyState();
    }

    /// Only the montage players are read for it.
    @Override
    public boolean montageOnly() {
        return true;
    }

    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.BOTH;
    }

    @Override
    public String notifyName() {
        return NoGravityNotifyState.class.getSimpleName();
    }

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
        return "notify_state/no_gravity.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        return Map.of();
    }
}
