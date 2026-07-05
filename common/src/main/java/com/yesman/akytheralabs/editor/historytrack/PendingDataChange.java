package com.yesman.akytheralabs.editor.historytrack;

import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/// Sentinel pushed at drag-start or edit-start. Carries the baseline value so
/// the matching [DraggingDataChange] can recover it without the widget keeping
/// a `dragStartValue` field.
///
/// `undo` / `redo` are no-ops — a pending entry should not survive a real
/// Ctrl+Z. If the user Ctrl+Z's mid-drag, this is popped quietly and the
/// subsequent committed push aborts via `verifyLastHistory`.
public final class PendingDataChange<T> implements WorkHistory {
    private final Object pairKey;
    private final @Nullable T oldValue;
    private final Consumer<T> setData;

    public PendingDataChange(Object pairKey, @Nullable T oldValue, Consumer<T> setData) {
        this.pairKey = pairKey;
        this.oldValue = oldValue;
        this.setData = setData;
    }

    public Object pairKey() {
        return pairKey;
    }

    public @Nullable T oldValue() {
        return oldValue;
    }

    public Consumer<T> setData() {
        return setData;
    }

    @Override
    public void undo() {
    }

    @Override
    public void redo() {
    }
}