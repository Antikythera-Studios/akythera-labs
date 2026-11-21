package com.yesman.akytheralabs.editor.historytrack;

import org.jspecify.annotations.Nullable;

/// Marker on the undo stack that says "everything above me belongs to the
/// screen (or screen-like modal) that was opened just before the first
/// action on it."
///
/// `undo()` / `redo()` are no-ops; the marker exists only for
/// [HistoryManager] to find. The manager guards against popping past it via
/// Ctrl+Z (a save point is opaque to the user) and uses it as the truncation
/// boundary on [HistoryManager#popScreen].
public final class ScreenSavePoint implements WorkHistory {
    /// The screen or screen-like modal the entries above this marker belong to, or null.
    @Nullable Object owner;

    ScreenSavePoint(final @Nullable Object owner) {
        this.owner = owner;
    }

    @Override
    public void undo() {}

    @Override
    public void redo() {}
}