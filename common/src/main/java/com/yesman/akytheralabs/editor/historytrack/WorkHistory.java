package com.yesman.akytheralabs.editor.historytrack;

import java.util.Stack;

/// One undoable editor change, managed by [HistoryManager].
public interface WorkHistory {
    /// Reverts the change.
    void undo();

    /// Replays the change after an [#undo].
    void redo();

    /// Inspects the last entry on [HistoryManager#toUndo] and decides whether to merge with it or
    /// abort the push. This is what pairs the two halves of a drag — its start and its end.
    default boolean verifyLastHistory(final Stack<WorkHistory> toUndo) { return true; }

    /// Whether this entry, the last on [HistoryManager#toUndo], stays there once `next` is pushed over it.
    default boolean staysUnder(final WorkHistory next) { return true; }

    /// Whether the change is one a save writes. An entry that is not, such as a preview setting, leaves
    /// [HistoryManager#currentSavePoint] where it was.
    default boolean changesData() {
        return true;
    }

    /// A gate on [HistoryManager#undo]: a destructive change such as a file deletion confirms
    /// through a modal first. The default runs `undo` straight away.
    default void requestUndoConfirmation(Runnable proceed) {
        proceed.run();
    }

    /// The [#requestUndoConfirmation] gate for [HistoryManager#redo].
    default void requestRedoConfirmation(Runnable proceed) {
        proceed.run();
    }
}
