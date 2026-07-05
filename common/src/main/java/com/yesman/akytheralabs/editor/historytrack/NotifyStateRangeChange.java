package com.yesman.akytheralabs.editor.historytrack;

import com.yesman.akytheralabs.dataholder.animation.NotifyTrackHolder;

import java.util.Stack;

/// Committed entry for a whole-state move on a
/// [NotifyTrackHolder.MutableNotifyState]. Pairs with
/// [PendingNotifyStateRangeChange] by `target` identity.
public final class NotifyStateRangeChange implements WorkHistory {
    private final NotifyTrackHolder.MutableNotifyState target;
    private final float newFrom;
    private final float newTo;
    private float oldFrom;
    private float oldTo;

    public NotifyStateRangeChange(NotifyTrackHolder.MutableNotifyState target, float newFrom, float newTo) {
        this.target = target;
        this.newFrom = newFrom;
        this.newTo = newTo;
    }

    @Override
    public void undo() {
        target.from = oldFrom;
        target.to = oldTo;
    }

    @Override
    public void redo() {
        target.from = newFrom;
        target.to = newTo;
    }

    @Override
    public boolean verifyLastHistory(final Stack<WorkHistory> toUndo) {
        if (toUndo.isEmpty()) return false;
        if (!(toUndo.peek() instanceof PendingNotifyStateRangeChange pending)) return false;
        if (pending.target() != this.target) return false;

        toUndo.pop();
        this.oldFrom = pending.oldFrom();
        this.oldTo = pending.oldTo();
        return Float.compare(oldFrom, newFrom) != 0 || Float.compare(oldTo, newTo) != 0;
    }
}