package com.yesman.akytheralabs.akythera.animation.notify.impl;

import com.yesman.akythera.api.animation.notify.impl.BlockJumpNotifyState;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.math.TimeWindow;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyStateHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

import java.util.Map;

public final class BlockJumpNotifyStateHolder extends AnimationNotifyStateHolder<BlockJumpNotifyState> {
    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_MIN_FALL_HEIGHT, order = 0, min = 0.0F)
    public float minFallHeight;

    @Override
    public void readFrom(BlockJumpNotifyState object) {
        this.minFallHeight = object.minFallHeight();
    }

    @Override
    public BlockJumpNotifyState makeObject() {
        return new BlockJumpNotifyState(minFallHeight);
    }

    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.BOTH;
    }

    @Override
    public String notifyName() {
        return BlockJumpNotifyState.class.getSimpleName();
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
        return "notify_state/block_jump.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        return Map.of("minFallHeight", minFallHeight);
    }
}
