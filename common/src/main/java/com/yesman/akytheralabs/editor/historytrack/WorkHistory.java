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
