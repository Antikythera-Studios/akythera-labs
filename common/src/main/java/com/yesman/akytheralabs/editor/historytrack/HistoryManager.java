package com.yesman.akytheralabs.editor.historytrack;

import org.jspecify.annotations.Nullable;

import java.util.Stack;

/// The editor's undo / redo stacks.
public final class HistoryManager {
    private static final HistoryManager INSTANCE = new HistoryManager();

    /// Cap on the undo stack. Past it the oldest entry is dropped, so the *N*th-from-last change
    /// stops being undoable. The redo stack needs no cap of its own: it only gains entries by undoing
    /// and is cleared on every new push.
    private static final int MAX_UNDO_HISTORY = 64;

    public static HistoryManager getInstance() {
        return INSTANCE;
    }

    /// The recent changes, most recent last, so [#undo] takes the last one first.
    private final Stack<WorkHistory> toUndo = new Stack<>();

    /// The entries [#undo] popped, for [#redo] to replay.
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

        // Drop the oldest entries once over the cap.
        while (toUndo.size() > MAX_UNDO_HISTORY) {
            toUndo.removeFirst();
        }

        // A new change makes the redo stack unreachable.
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

    /// Marks the next user push to be preceded by a [ScreenSavePoint], scoping everything above it
    /// to the screen or screen-like modal about to open.
    public void requestSavePointOnNextAction() {
        needsSavePoint = true;
    }

    /// The current top of the undo stack, or `null` when it is empty.
    ///
    /// Editor screens snapshot this reference as their last save point and drive the `*` unsaved
    /// marker off it: a screen is dirty while this no longer equals the snapshot, and clean again once
    /// a save — or an undo back to that point — restores it. The comparison is by identity.
    public @Nullable WorkHistory currentSavePoint() {
        return toUndo.isEmpty() ? null : toUndo.peek();
    }

    /// Pops the undo stack down to, and including, the most recent [ScreenSavePoint] — the scope of
    /// the screen or screen-like modal now closing.
    public void popScreen() {
        // A pending save point means nothing was pushed under it, so there is nothing to pop.
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

    /// Drops both stacks whole, and resets [#needsSavePoint] so a request from a just-wiped scope
    /// does not carry over. The screen and modal lifecycle uses [#popScreen] instead.
    public void removeAllHistory() {
        toUndo.clear();
        toRedo.clear();
        needsSavePoint = false;
    }
}