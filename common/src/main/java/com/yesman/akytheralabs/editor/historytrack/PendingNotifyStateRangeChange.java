package com.yesman.akytheralabs.editor.historytrack;

import com.yesman.akytheralabs.akythera.animation.NotifyTrackHolder;

/// Sentinel pushed when a whole-state move drag starts on a
/// [NotifyTrackHolder.MutableNotifyState]. Carries the baseline
/// `(from, to)` range so the matching [NotifyStateRangeChange] can
/// recover it on commit.
public final class PendingNotifyStateRangeChange implements WorkHistory {
    private final NotifyTrackHolder.MutableNotifyState target;
    private final float oldFrom;
    private final float oldTo;

    public PendingNotifyStateRangeChange(NotifyTrackHolder.MutableNotifyState target, float oldFrom, float oldTo) {
        this.target = target;
        this.oldFrom = oldFrom;
        this.oldTo = oldTo;
    }

    public NotifyTrackHolder.MutableNotifyState target() {
        return target;
    }

    public float oldFrom() {
        return oldFrom;
    }

    public float oldTo() {
        return oldTo;
    }

    @Override
    public void undo() {
    }

    @Override
    public void redo() {
    }
}