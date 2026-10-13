package com.yesman.akytheralabs.akythera.animation.notify;

import com.google.gson.JsonElement;
import com.yesman.akythera.api.animation.notify.AnimationNotify;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/// Placeholder for a notify row whose engine [AnimationNotify] failed to decode (unknown type after a
/// refactor, or a broken body). Stores its own failure + raw JSON (it does not extend [AbstractDataHolder]),
/// renders as an inert "Unknown notify" row, and is skipped by `toEngineTrack` / source export; the guarded
/// codec re-emits its raw JSON on save so the row round-trips untouched.
public final class FailedAnimationNotifyHolder extends AnimationNotifyHolder<AnimationNotify<?>> {
    @Nullable
    private JsonElement retainedRaw;

    @Override
    public void readFrom(AnimationNotify<?> object) {}

    @Override
    public AnimationNotify<?> makeObject() {
        throw new IllegalStateException("Failed notify has no engine object", getDeserializationFailureException());
    }

    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.BOTH;
    }

    @Override
    public String notifyName() {
        return "Unknown notify";
    }

    @Override
    public void simulate(Model3DViewer previewer) {
    }

    @Override
    public String sourceTemplate() {
        throw new IllegalStateException("Failed notify has no source template");
    }

    @Override
    public Map<String, Object> getDTO() {
        return Map.of();
    }

    @Override
    public void retainRaw(JsonElement raw) {
        retainedRaw = raw;
    }

    @Override
    public @Nullable JsonElement retainedRaw() {
        return retainedRaw;
    }
}
