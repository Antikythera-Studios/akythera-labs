package com.yesman.akytheralabs.editor;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.client.api.gui.AkytheraEditBox;
import com.yesman.akythera.client.api.gui.modal.ModalAccessibleScreen;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.gui.widget.script.CutSource;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.ScriptNodeGraphWidget;
import com.yesman.akytheralabs.gui.widget.script.StateMachineData;
import com.yesman.akytheralabs.gui.widget.script.StateMachineGraphWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/// The blueprint canvases' copy / cut clipboard, shared by every editor screen. Not the OS clipboard, which
/// carries text.
public final class NodeClipboard {
    private static final NodeClipboard INSTANCE = new NodeClipboard();

    public static NodeClipboard getInstance() {
        return INSTANCE;
    }

    private Content content = new Content(ScriptGraphData.EMPTY, Set.of(), false, List.of(), Map.of());

    /// The cut waiting for the paste that moves its nodes, or null.
    @Nullable
    private PendingCut pendingCut;

    public ScriptGraphData graph() {
        return content.graph();
    }

    public List<StateMachineData.StateData> states() {
        return content.states();
    }

    public Map<String, StateMachineData> machines() {
        return content.machines();
    }

    public void setMachines(final Map<String, StateMachineData> machines) {
        content = new Content(content.graph(), content.usage(), content.dataFlowOnly(), content.states(), Map.copyOf(machines));
    }

    /// Copies the selected nodes of `canvas`. False when none of them can be copied.
    public boolean copy(final ScriptNodeGraphWidget canvas) {
        ScriptGraphData copied = canvas.copySelection();
        if (copied == null) return false;

        cancelCut();
        content = new Content(copied, Set.copyOf(canvas.usage()), canvas.isDataFlowOnly(), List.of(), Map.of());

        return true;
    }

    /// Copies the selected states of `canvas`. False when none is selected.
    public boolean copy(final StateMachineGraphWidget canvas) {
        List<StateMachineData.StateData> copied = canvas.copySelection();
        if (copied == null) return false;

        cancelCut();
        content = new Content(ScriptGraphData.EMPTY, Set.of(), false, List.copyOf(copied), Map.of());

        return true;
    }

    /// [#copy], keeping the copied nodes on `canvas`, marked as cut, until a paste moves them or the cut is
    /// cancelled.
    public boolean cut(final ScriptNodeGraphWidget canvas) {
        if (!copy(canvas)) return false;

        holdCut(canvas, canvas, canvas.markSelectionCut());

        return true;
    }

    /// [#copy], keeping the copied states on `canvas`, marked as cut, until a paste moves them or the cut is
    /// cancelled.
    public boolean cut(final StateMachineGraphWidget canvas) {
        if (!copy(canvas)) return false;

        holdCut(canvas, canvas, canvas.markSelectionCut());

        return true;
    }

    /// Records the cut pending on `source`, whose nodes `mark` marks, as an undo entry under `scope`.
    private void holdCut(final CutSource source, final Object scope, final Runnable mark) {
        pendingCut = new PendingCut(source, mark);

        HistoryManager.getInstance().pushHistory(scope, pendingCut);
    }

    /// Pastes [#graph] where `canvas` was last pressed. False when nothing landed: `canvas` is another type of
    /// graph than the one [#graph] was copied off.
    public boolean paste(final ScriptNodeGraphWidget canvas) {
        boolean sameType = canvas.usage().equals(content.usage()) && canvas.isDataFlowOnly() == content.dataFlowOnly();

        if (content.graph().isEmpty() || !sameType) return false;

        paste(canvas, () -> canvas.pasteGraph(content.graph()));

        return true;
    }

    /// Pastes [#states] where `canvas` was last pressed. False when there is none.
    public boolean paste(final StateMachineGraphWidget canvas) {
        if (content.states().isEmpty()) return false;

        paste(canvas, () -> canvas.pasteStates(content.states()));

        return true;
    }

    /// Runs `paste`. With a cut pending, its nodes are deleted first and the two changes are recorded under `scope`
    /// as one undo entry.
    private void paste(final Object scope, final Runnable paste) {
        PendingCut pending = pendingCut;

        if (pending == null) {
            paste.run();
            return;
        }

        HistoryManager history = HistoryManager.getInstance();

        List<WorkHistory> steps = history.capture(() -> {
            pending.source.deleteCut();
            paste.run();
        });

        release(pending);
        history.discard(pending);

        history.pushHistory(scope, new WorkHistory() {
            @Override
            public void undo() {
                steps.reversed().forEach(WorkHistory::undo);
            }

            @Override
            public void redo() {
                steps.forEach(WorkHistory::redo);
            }
        });
    }

    /// Releases the nodes of a pending cut where they are. False when no cut is pending.
    public boolean cancelCut() {
        PendingCut pending = pendingCut;
        if (pending == null) return false;

        release(pending);
        HistoryManager.getInstance().discard(pending);

        return true;
    }

    /// [#cancelCut] when the pending cut is on `canvas`.
    public void cancelCut(final AbstractWidget canvas) {
        if (pendingCut != null && pendingCut.source == canvas) cancelCut();
    }

    /// Ends the cut `pending` records, when that cut is still the pending one.
    private void release(final PendingCut pending) {
        if (pendingCut != pending) return;

        pending.source.clearCut();
        pendingCut = null;
    }

    /// Runs the copy, cut and paste keys on `content` when it is a node canvas, and cancels a pending cut on
    /// `Escape`. A modal, a focused edit box, or a node hosting an inline editor keeps the keys.
    public <S extends Screen & ModalAccessibleScreen> boolean keyPressed(final KeyEvent event, final S screen, final AbstractWidget content) {
        if (screen.getFocusOwningModal() != null || AkytheraEditBox.isEditingText(screen)) return false;

        ScriptNodeGraphWidget canvas = content instanceof ScriptNodeGraphWidget graphWidget ? graphWidget : null;

        if (canvas != null && canvas.isInlineEditing()) return false;
        if (event.isEscape()) return cancelCut();
        if (canvas == null) return false;

        if (EditorKeyMappings.COPY.matches(event)) {
            copy(canvas);
            return true;
        }

        if (EditorKeyMappings.CUT.matches(event)) {
            cut(canvas);
            return true;
        }

        if (EditorKeyMappings.PASTE.matches(event)) {
            paste(canvas);
            return true;
        }

        return false;
    }

    /// What a copy holds.
    ///
    /// @param graph        the nodes copied off a node canvas and the wires among them
    /// @param usage        the [ScriptNodeGraphWidget#usage] of the canvas `graph` was copied off
    /// @param dataFlowOnly the [ScriptNodeGraphWidget#isDataFlowOnly] of the canvas `graph` was copied off
    /// @param states       the states copied off a state-machine canvas
    /// @param machines     the topology of every State Machine the copy names, by machine name
    private record Content(
        ScriptGraphData graph,
        Set<ScriptFunction.Usage> usage,
        boolean dataFlowOnly,
        List<StateMachineData.StateData> states,
        Map<String, StateMachineData> machines
    ) {
    }

    /// The undo entry of a cut waiting for its paste. Undoing it, or pushing any other entry over it, cancels the
    /// cut. Redoing it cuts again.
    private final class PendingCut implements WorkHistory {
        /// The canvas the cut nodes wait on.
        private final CutSource source;

        /// Marks the cut nodes on [#source].
        private final Runnable mark;

        /// The [NodeClipboard#content] of the cut, kept from [#undo] for [#redo].
        @Nullable
        private Content cutContent;

        private PendingCut(final CutSource source, final Runnable mark) {
            this.source = source;
            this.mark = mark;
        }

        @Override
        public void undo() {
            if (pendingCut == this) cutContent = content;

            release(this);
        }

        @Override
        public void redo() {
            if (cutContent == null) return;

            content = cutContent;
            pendingCut = this;
            mark.run();
        }

        @Override
        public boolean changesData() {
            return false;
        }

        @Override
        public boolean staysUnder(final WorkHistory next) {
            release(this);
            return false;
        }
    }
}
