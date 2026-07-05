package com.yesman.akytheralabs.editor.historytrack;

import org.jspecify.annotations.Nullable;

import java.util.Stack;

/// A history manager stacks previous works
public final class HistoryManager {
    private static final HistoryManager INSTANCE = new HistoryManager();

    /// Cap on the undo stack so a long editing session can't grow memory without bound. Once
    /// exceeded, the oldest (bottom-most) entry is dropped — making the *N*th-from-last change no
    /// longer undoable, the standard editor trade-off. The redo stack needs no separate cap: it only
    /// gains entries by undoing (bounded by the undo depth) and is cleared on every new push.
    private static final int MAX_UNDO_HISTORY = 64;

    public static HistoryManager getInstance() {
        return INSTANCE;
    }

    /// Stores the recent changes in FIFO stack so that `undo` task
    /// calls the last change first
    private final Stack<WorkHistory> toUndo = new Stack<>();

    /// Stores the recent `undo` calls so when the user requests
    /// `redo`,
    private final Stack<WorkHistory> toRedo = new Stack<>();

    /// True while a [WorkHistory#redo] / [WorkHistory#undo] is being applied.
    /// Used to ignore re-entrant [#pushHistory] calls fired by widget callbacks
    /// during replay.
    private boolean replaying = false;

    /// Set on screen / screen-like-modal open. The very next user-initiated
    /// push prepends a [ScreenSavePoint] to mark the boundary, then flips
    /// back to `false`.
    private boolean needsSavePoint = false;

    /// Record a change for future undo.
    public void pushHistory(WorkHistory workHistory) {
        if (replaying) return;

        if (needsSavePoint) {
            toUndo.push(new ScreenSavePoint());
            needsSavePoint = false;
        }

        if (!workHistory.verifyLastHistory(toUndo)) return;

        toUndo.push(workHistory);

        // Bound the undo depth: drop the oldest entries once over the cap (FIFO eviction from the
        // bottom of the stack) so memory stays bounded across a long session.
        while (toUndo.size() > MAX_UNDO_HISTORY) {
            toUndo.removeFirst();
        }

        // Reset the redo stack when new history comes to
        // prevent history twist
        toRedo.clear();
    }

    /// Undo the most recent change and push the work into `workStack`.
    ///
    /// Pops only after [WorkHistory#requestUndoConfirmation] fires `proceed`,
    /// so an opt-in confirmation popup (e.g. for [FileCreation]) can defer or
    /// reject the revert without leaving the stack out of sync.
    public void undo() {
        if (toUndo.isEmpty()) return;

        // Refuses to cross a `ScreenSavePoint`, the marker of current screen's opening point
        if (toUndo.peek() instanceof ScreenSavePoint) return;

        WorkHistory lastWork = toUndo.peek();

        lastWork.requestUndoConfirmation(() -> {
            // Guard against the stack having shifted under us (a confirmation
            // popup could in theory have allowed something else to run).
            if (toUndo.isEmpty() || toUndo.peek() != lastWork) return;
            toUndo.pop();

            replaying = true;
            try {
                lastWork.undo();
            } finally {
                replaying = false;
            }
            toRedo.push(lastWork);
        });
    }

    /// Redo the most recent `undo`.
    ///
    /// Pops only after [WorkHistory#requestRedoConfirmation] fires `proceed`,
    /// symmetric with [#undo] — so an opt-in confirmation popup (e.g. for
    /// [FileDeletion]) can defer or reject the replay without leaving the
    /// stack out of sync.
    public void redo() {
        if (toRedo.isEmpty()) return;

        WorkHistory lastUndo = toRedo.peek();

        lastUndo.requestRedoConfirmation(() -> {
            // Stack-shift guard mirrors [#undo].
            if (toRedo.isEmpty() || toRedo.peek() != lastUndo) return;
            toRedo.pop();

            replaying = true;
            try {
                lastUndo.redo();
            } finally {
                replaying = false;
            }
            toUndo.push(lastUndo);
        });
    }

    /// Called by the layout manager when a screen (or screen-like modal) is
    /// about to open. The next user push will be preceded by a
    /// [ScreenSavePoint] on the undo stack so its contents stay scoped.
    public void requestSavePointOnNextAction() {
        needsSavePoint = true;
    }

    /// The current top of the undo stack, or `null` when it is empty.
    ///
    /// Editor screens snapshot this reference as their "last save point" to drive the unsaved-changes
    /// marker (the `*` in the title bar): a screen is dirty whenever this no longer equals the snapshot,
    /// and clean again once a save (or an undo back to that point) restores it. Reference identity is
    /// used deliberately, so it survives undo/redo without any per-change bookkeeping.
    public @Nullable WorkHistory currentSavePoint() {
        return toUndo.isEmpty() ? null : toUndo.peek();
    }

    /// Called by the layout manager when a screen (or screen-like modal) is
    /// closing. Pops the undo stack down to (and including) the most recent
    /// [ScreenSavePoint].
    public void popScreen() {
        // If the current state is pending to produce any new history, we don't
        // need to pop out any history stack. Safe to skip.
        if (needsSavePoint) {
            needsSavePoint = false;
            return;
        }

        while (!toUndo.isEmpty()) {
            WorkHistory top = toUndo.pop();
            if (top instanceof ScreenSavePoint) break;
        }

        toRedo.clear();
    }

    /// Hard wipe. Kept around for callers that genuinely need the whole stack
    /// gone (none in normal screen / modal lifecycle anymore — see
    /// [#popScreen]). Also resets [#needsSavePoint] so a stale request from
    /// a just-opened-then-wiped scope doesn't carry over.
    public void removeAllHistory() {
        toUndo.clear();
        toRedo.clear();
        needsSavePoint = false;
    }
}