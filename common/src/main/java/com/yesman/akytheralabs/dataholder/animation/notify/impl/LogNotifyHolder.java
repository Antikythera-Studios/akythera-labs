package com.yesman.akytheralabs.dataholder.animation.notify.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.notify.impl.LogNotify;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.dataholder.animation.notify.AnimationNotifyHolder;
import com.yesman.akytheralabs.editor.EditorEditable;

import java.util.Map;

public final class LogNotifyHolder extends AnimationNotifyHolder<LogNotify> {
    public static final Codec<LogNotifyHolder> EDITOR_CODEC = RecordCodecBuilder.create(inst ->
        inst.group(
            LogicalSide.CODEC.optionalFieldOf("side", LogicalSide.BOTH).forGetter(h -> h.side)
        ).apply(inst, side -> {
            LogNotifyHolder h = new LogNotifyHolder();
            h.side = side;
            return h;
        })
    );

    @EditorEditable(label = "Side", order = 0)
    public LogicalSide side = LogicalSide.BOTH;

    @Override
    public void readFrom(LogNotify object) {
        this.side = object.logicalSide();
    }

    @Override
    public LogNotify makeObject() {
        return new LogNotify(side);
    }

    @Override
    public LogicalSide logicalSide() {
        return side;
    }

    @Override
    public String notifyName() {
        return LogNotify.class.getSimpleName();
    }

    @Override
    public String sourceTemplate() {
        return "notify/log.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        return Map.of("side", side.name());
    }
}