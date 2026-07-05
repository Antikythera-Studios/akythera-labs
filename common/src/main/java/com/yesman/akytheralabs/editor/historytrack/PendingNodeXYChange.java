package com.yesman.akytheralabs.editor.historytrack;

import com.yesman.akytheralabs.dataholder.animation.BlendSpace2DHolder;

/// Sentinel pushed when a node drag starts on a
/// [BlendSpace2DHolder.MutableAnimNode]. Carries the baseline `(x, y)` so
/// the matching [NodeXYChange] can recover it on commit.
public final class PendingNodeXYChange implements WorkHistory {
    private final BlendSpace2DHolder.MutableAnimNode target;
    private final float oldX;
    private final float oldY;

    public PendingNodeXYChange(BlendSpace2DHolder.MutableAnimNode target, float oldX, float oldY) {
        this.target = target;
        this.oldX = oldX;
        this.oldY = oldY;
    }

    public BlendSpace2DHolder.MutableAnimNode target() {
        return target;
    }

    public float oldX() {
        return oldX;
    }

    public float oldY() {
        return oldY;
    }

    @Override
    public void undo() {
    }

    @Override
    public void redo() {
    }
}