package com.yesman.akytheralabs.editor.historytrack;

import java.util.List;

/// Removal of `value` from `target` at `index`. `rebuild` refreshes the
/// presenting widget after undo/redo.
public record ListRemove<T>(List<T> target, int index, T value, Runnable rebuild) implements WorkHistory {
    @Override
    public void undo() {
        target.add(index, value);
        rebuild.run();
    }

    @Override
    public void redo() {
        target.remove(index);
        rebuild.run();
    }
}