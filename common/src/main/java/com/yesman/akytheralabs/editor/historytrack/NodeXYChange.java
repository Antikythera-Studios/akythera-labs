package com.yesman.akytheralabs.editor.historytrack;

import com.yesman.akytheralabs.dataholder.animation.BlendSpace2DHolder;

import java.util.Stack;

/// Committed entry for a node drag on a
/// [BlendSpace2DHolder.MutableAnimNode]. Pairs with [PendingNodeXYChange]
/// by `target` identity.
public final class NodeXYChange implements WorkHistory {
    private final BlendSpace2DHolder.MutableAnimNode target;
    private final float newX;
    private final float newY;
    private float oldX;
    private float oldY;

    public NodeXYChange(BlendSpace2DHolder.MutableAnimNode target, float newX, float newY) {
        this.target = target;
        this.newX = newX;
        this.newY = newY;
    }

    @Override
    public void undo() {
        target.setX(oldX);
        target.setY(oldY);
    }

    @Override
    public void redo() {
        target.setX(newX);
        target.setY(newY);
    }

    @Override
    public boolean verifyLastHistory(final Stack<WorkHistory> toUndo) {
        if (toUndo.isEmpty()) return false;
        if (!(toUndo.peek() instanceof PendingNodeXYChange pending)) return false;
        if (pending.target() != this.target) return false;

        toUndo.pop();
        this.oldX = pending.oldX();
        this.oldY = pending.oldY();
        return Float.compare(oldX, newX) != 0 || Float.compare(oldY, newY) != 0;
    }
}