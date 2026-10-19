package com.yesman.akytheralabs.akythera.animation.notify.impl;

import com.yesman.akythera.api.animation.notify.impl.CancelOnMoveNotifyState;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyStateHolder;

import java.util.Map;

public final class CancelOnMoveNotifyStateHolder extends AnimationNotifyStateHolder<CancelOnMoveNotifyState> {
    @Override
    public void readFrom(CancelOnMoveNotifyState object) {
    }

    @Override
    public CancelOnMoveNotifyState makeObject() {
        return new CancelOnMoveNotifyState();
    }

    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.BOTH;
    }

    @Override
    public String notifyName() {
        return CancelOnMoveNotifyState.class.getSimpleName();
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
        return "notify_state/cancel_on_move.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        return Map.of();
    }
}
