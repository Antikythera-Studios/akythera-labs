package com.yesman.akytheralabs.akythera.animation.notify.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.impl.LogNotifyState;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyStateHolder;
import com.yesman.akytheralabs.editor.EditorEditable;

import java.util.Map;

public final class LogNotifyStateHolder extends AnimationNotifyStateHolder<LogNotifyState> {
    public static final Codec<LogNotifyStateHolder> EDITOR_CODEC = RecordCodecBuilder.create(inst ->
        inst.group(
            LogicalSide.CODEC.optionalFieldOf("side", LogicalSide.BOTH).forGetter(h -> h.side)
        ).apply(inst, side -> {
            LogNotifyStateHolder h = new LogNotifyStateHolder();
            h.side = side;
            return h;
        })
    );

    @EditorEditable(label = "Side", order = 0)
    public LogicalSide side = LogicalSide.BOTH;

    @Override
    public void readFrom(LogNotifyState object) {
        this.side = object.logicalSide();
    }

    @Override
    public LogNotifyState makeObject() {
        return new LogNotifyState(side);
    }

    @Override
    public LogicalSide logicalSide() {
        return side;
    }

    @Override
    public String notifyName() {
        return LogNotifyState.class.getSimpleName();
    }

    @Override
    public void simulateBegin(Model3DViewer previewer) {
        AkytheraLabs.LOGGER.info("Debug notify state: begin notified in previewer");
    }

    @Override
    public void simulateTick(Model3DViewer previewer, float prevElapsedTime, float currentElapsedTime) {
        AkytheraLabs.LOGGER.info("Debug notify state: tick notified in previewer at: prev = {}, current = {}", prevElapsedTime, currentElapsedTime);
    }

    @Override
    public void simulateEnd(Model3DViewer previewer) {
        AkytheraLabs.LOGGER.info("Debug notify state: end notified in previewer");
    }

    @Override
    public String sourceTemplate() {
        return "notify_state/log.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        return Map.of("side", side.name());
    }
}