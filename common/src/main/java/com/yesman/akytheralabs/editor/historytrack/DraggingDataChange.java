package com.yesman.akytheralabs.editor.historytrack;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Stack;
import java.util.function.Consumer;

/// Committed entry pushed at drag-end or edit-end.
///
/// `verifyLastHistory` pops the matching [PendingDataChange] from `toUndo`,
/// absorbs its baseline into `oldValue`, and aborts the push when the value
/// did not actually change.
public final class DraggingDataChange<T> implements WorkHistory {
    private final Object pairKey;
    private final @Nullable T newValue;
    private final Consumer<T> setData;
    private @Nullable T oldValue;

    public DraggingDataChange(Object pairKey, @Nullable T newValue, Consumer<T> setData) {
        this.pairKey = pairKey;
        this.newValue = newValue;
        this.setData = setData;
    }

    @Override
    public void undo() {
        setData.accept(oldValue);
    }

    @Override
    public void redo() {
        setData.accept(newValue);
    }

    @Override
    public boolean verifyLastHistory(final Stack<WorkHistory> toUndo) {
        if (toUndo.isEmpty()) return false;
        if (!(toUndo.peek() instanceof PendingDataChange<?> pending)) return false;
        if (!Objects.equals(pending.pairKey(), this.pairKey)) return false;

        toUndo.pop();
        @SuppressWarnings("unchecked")
        T baseline = (T) pending.oldValue();
        this.oldValue = baseline;
        return !Objects.equals(baseline, newValue);
    }
}