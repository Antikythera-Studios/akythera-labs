package com.yesman.akytheralabs.akythera.animation.notify;

import com.google.gson.JsonElement;
import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.core.util.LogicalSide;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/// Placeholder for a notify-state row whose engine [AnimationNotifyState] failed to decode —
/// the windowed counterpart of [FailedAnimationNotifyHolder]. Inert, skipped by lowering /
/// export; the guarded codec re-emits its raw JSON on save so the row round-trips untouched.
public final class FailedAnimationNotifyStateHolder extends AnimationNotifyStateHolder<AnimationNotifyState<?>> {
    @Nullable
    private JsonElement retainedRaw;

    @Override
    public void readFrom(AnimationNotifyState<?> object) {}

    @Override
    public AnimationNotifyState<?> makeObject() {
        throw new IllegalStateException("Failed notify state has no engine object", getDeserializationFailureException());
    }

    @Override
    public LogicalSide logicalSide() {
        return LogicalSide.BOTH;
    }

    @Override
    public String notifyName() {
        return "Unknown notify state";
    }

    @Override
    public String sourceTemplate() {
        throw new IllegalStateException("Failed notify state has no source template");
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
