package com.yesman.akytheralabs.editor.historytrack;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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

    /// What each scoped entry on either stack belongs to, see [#pushHistory(Object, WorkHistory)].
    private final Map<WorkHistory, Object> scopes = new IdentityHashMap<>();

    /// What [#currentSavePoint] answered when each screen's [ScreenSavePoint] was pushed, null included.
    private final Map<ScreenSavePoint, WorkHistory> openedOver = new IdentityHashMap<>();

    /// True while a [WorkHistory#redo] / [WorkHistory#undo] is being applied.
    /// Used to ignore re-entrant [#pushHistory] calls fired by widget callbacks
    /// during replay.
    private boolean replaying = false;

    /// Set on screen / screen-like-modal open. The very next user-initiated
    /// push prepends a [ScreenSavePoint] to mark the boundary, then flips
    /// back to `false`.
    private boolean needsSavePoint = false;

    /// What [#pushHistory] hands its entries to while a [#capture] runs, or null.
    private @Nullable List<WorkHistory> captured;

    /// Record a change for future undo.
    public void pushHistory(WorkHistory workHistory) {
        pushHistory(null, workHistory);
    }

    /// Record a change for future undo, under `scope`: the editor it happened in, whose [#removeHistory]
    /// drops it again. A null scope belongs to nothing that goes away.
    public void pushHistory(@Nullable Object scope, WorkHistory workHistory) {
        if (replaying) return;

        if (captured != null) {
            captured.add(workHistory);
            return;
        }

        if (needsSavePoint) {
            ScreenSavePoint savePoint = new ScreenSavePoint();

            openedOver.put(savePoint, currentSavePoint());
            toUndo.push(savePoint);
            needsSavePoint = false;
        }

        if (!toUndo.isEmpty() && !toUndo.peek().staysUnder(workHistory)) scopes.remove(toUndo.pop());

        if (!workHistory.verifyLastHistory(toUndo)) return;

        toUndo.push(workHistory);
        if (scope != null) scopes.put(workHistory, scope);

        // Drop the oldest entries once over the cap.
        while (toUndo.size() > MAX_UNDO_HISTORY) {
            WorkHistory oldest = toUndo.removeFirst();

            scopes.remove(oldest);
            openedOver.remove(oldest);
        }

        // A new change makes the redo stack unreachable.
        toRedo.forEach(scopes::remove);
        toRedo.clear();
    }

    /// Runs `changes` and answers the entries they pushed, in order, instead of recording them.
    public List<WorkHistory> capture(final Runnable changes) {
        List<WorkHistory> entries = new ArrayList<>();

        captured = entries;

        try {
            changes.run();
        } finally {
            captured = null;
        }

        return entries;
    }

    /// Drops `entry` from both stacks. For an entry that changed no data.
    public void discard(final WorkHistory entry) {
        toUndo.remove(entry);
        toRedo.remove(entry);
        scopes.remove(entry);
    }

    /// Drops every entry pushed under `scope` from both stacks: the changes of an editor that is gone,
    /// which could no longer reach what they changed.
    ///
    /// The dropped changes stay in the data, so no state below them may read as saved any more. Every entry
    /// kept below the lowest dropped one is swapped for a copy (see [Renewed]), so a screen's
    /// [#currentSavePoint] taken there no longer matches, and the stack is left standing on a
    /// [ScreenSavePoint], so undoing it empty cannot match a save taken on an empty stack either.
    public void removeHistory(Object scope) {
        toRedo.removeIf(entry -> scopes.get(entry) == scope);

        int lowest = -1;

        for (int i = 0; i < toUndo.size() && lowest < 0; i++) {
            if (scopes.get(toUndo.get(i)) == scope) lowest = i;
        }

        if (lowest >= 0) {
            List<WorkHistory> kept = new ArrayList<>();

            for (int i = 0; i < toUndo.size(); i++) {
                WorkHistory entry = toUndo.get(i);
                Object entryScope = scopes.get(entry);

                if (entryScope == scope) continue;

                if (i < lowest) {
                    WorkHistory renewed = entry instanceof ScreenSavePoint ? new ScreenSavePoint() : new Renewed(entry);

                    if (entryScope != null) scopes.put(renewed, entryScope);
                    entry = renewed;
                }

                kept.add(entry);
            }

            if (kept.isEmpty() || !(kept.getFirst() instanceof ScreenSavePoint)) kept.addFirst(new ScreenSavePoint());

            toUndo.clear();
            toUndo.addAll(kept);
        }

        scopes.values().removeIf(entryScope -> entryScope == scope);
        openedOver.keySet().removeIf(savePoint -> !toUndo.contains(savePoint));
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

    /// The topmost entry of the undo stack that [WorkHistory#changesData], or `null` when there is none. For the
    /// [ScreenSavePoint] a screen's first push put under it, the answer is what it was when that screen opened, so a
    /// screen with every data change undone reads as it did then.
    ///
    /// Editor screens snapshot this reference as their last save point and drive the `*` unsaved
    /// marker off it: a screen is dirty while this no longer equals the snapshot, and clean again once
    /// a save — or an undo back to that point — restores it. The comparison is by identity.
    public @Nullable WorkHistory currentSavePoint() {
        WorkHistory top = toUndo.reversed().stream().filter(WorkHistory::changesData).findFirst().orElse(null);

        return top instanceof ScreenSavePoint && openedOver.containsKey(top) ? openedOver.get(top) : top;
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
            scopes.remove(top);
            openedOver.remove(top);
            if (top instanceof ScreenSavePoint) break;
        }

        toRedo.forEach(scopes::remove);
        toRedo.clear();
    }

    /// Drops both stacks whole, and resets [#needsSavePoint] so a request from a just-wiped scope
    /// does not carry over. The screen and modal lifecycle uses [#popScreen] instead.
    public void removeAllHistory() {
        toUndo.clear();
        toRedo.clear();
        scopes.clear();
        openedOver.clear();
        needsSavePoint = false;
    }

    /// An entry kept by [#removeHistory] under a new identity, doing what the entry does.
    private record Renewed(WorkHistory entry) implements WorkHistory {
        @Override
        public void undo() {
            entry.undo();
        }

        @Override
        public void redo() {
            entry.redo();
        }

        @Override
        public boolean changesData() {
            return entry.changesData();
        }

        @Override
        public void requestUndoConfirmation(Runnable proceed) {
            entry.requestUndoConfirmation(proceed);
        }

        @Override
        public void requestRedoConfirmation(Runnable proceed) {
            entry.requestRedoConfirmation(proceed);
        }
    }
}