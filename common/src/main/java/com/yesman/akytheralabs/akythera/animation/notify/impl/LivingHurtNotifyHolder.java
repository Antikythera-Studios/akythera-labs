package com.yesman.akytheralabs.akythera.animation.notify.impl;

import com.yesman.akythera.api.animation.notify.impl.LivingHurtNotify;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

import java.util.Map;

/// Editor holder for a [LivingHurtNotify].
public final class LivingHurtNotifyHolder extends AnimationNotifyHolder<LivingHurtNotify> {
    /// Length of the view ray, in blocks.
    @EditorEditable(label = LangKeys.GUI_NOTIFY_TRACKS_FIELD_RANGE, order = 0, min = 0.0F)
    private double range = 3.0D;

    @Override
    public void readFrom(final LivingHurtNotify object) {
        range = object.range();
    }

    @Override
    public LivingHurtNotify makeObject() {
        return new LivingHurtNotify(range);
    }

    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.SERVER; // LivingHurtNotify hard-codes it
    }

    @Override
    public String notifyName() {
        return LivingHurtNotify.class.getSimpleName();
    }

    @Override
    public void simulate(final Model3DViewer previewer) {
    }

    @Override
    public String sourceTemplate() {
        return "notify/living_hurt.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        return Map.of("range", range);
    }
}
