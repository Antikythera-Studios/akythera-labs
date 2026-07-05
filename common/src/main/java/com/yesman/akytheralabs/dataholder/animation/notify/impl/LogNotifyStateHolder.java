package com.yesman.akytheralabs.dataholder.animation.notify.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.impl.LogNotifyState;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.dataholder.animation.notify.AnimationNotifyStateHolder;
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
    public String sourceTemplate() {
        return "notify_state/log.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        return Map.of("side", side.name());
    }
}