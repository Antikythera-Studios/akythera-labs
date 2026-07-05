package com.yesman.akytheralabs.editor.historytrack;

import java.util.Stack;

/// A unit of editor history per making changes
///
/// Managed by [HistoryManager]
public interface WorkHistory {
    /// Revert the change
    void undo();

    /// Recover [#undo]
    void redo();

    /// Inspect the last work history in [HistoryManager#toUndo] stack
    /// and determine whether to merge or abort the pushed history.
    ///
    /// Used in two-steps to change histories like mouse dragging start/end.
    default boolean verifyLastHistory(final Stack<WorkHistory> toUndo) { return true; }

    /// A gate to confirm [HistoryManager#undo]. Critical changes like file
    /// deletion will require users to confirm the undo task by a modal.
    ///
    /// Default action: just proceed the `undo` task given as the parameter
    default void requestUndoConfirmation(Runnable proceed) {
        proceed.run();
    }

    /// Same goes with [#requestUndoConfirmation], but [HistoryManager#redo].
    default void requestRedoConfirmation(Runnable proceed) {
        proceed.run();
    }
}
