package com.yesman.akytheralabs.editor.historytrack;

import java.util.List;

/// Insertion of `value` at `index` into `target`. `rebuild` refreshes the
/// presenting widget after undo/redo.
public record ListInsert<T>(List<T> target, int index, T value, Runnable rebuild) implements WorkHistory {
    @Override
    public void undo() {
        target.remove(index);
        rebuild.run();
    }

    @Override
    public void redo() {
        target.add(index, value);
        rebuild.run();
    }
}