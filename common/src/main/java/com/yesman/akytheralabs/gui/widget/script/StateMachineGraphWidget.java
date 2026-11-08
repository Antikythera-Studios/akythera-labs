package com.yesman.akytheralabs.gui.widget.script;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.datafixers.util.Pair;
import com.yesman.akythera.client.api.gui.ContextMenuContainer;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.math.model.FSMState;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.animation.ScriptCanvasKeys;
import com.yesman.akytheralabs.editor.debug.AnimatorProbe;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.gui.widget.CanvasGrid;
import com.yesman.akytheralabs.mixinaccessor.GuiGraphicsExtractorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// The canvas of one state machine of a [StateMachineData.Kind] (UE's state-machine tab): a hardcoded **Entry** node
/// plus user-added **state** and **group** nodes, joined by directed **transition** arrows.
///
/// Interaction mirrors UE: a left-drag starting on a box's **inner** body moves it; a left-drag
/// starting on its **outer** edge band draws a transition to whatever state it is released over (a
/// new transition is created only if that exact `from → to` direction does not already exist, and
/// never onto itself). Right-click empty canvas adds a state or a group node; right-drag pans;
/// `Delete` removes the selected state / group / transition. The Entry node can never be deleted.
///
/// A state declares the names of the groups it belongs to, shared by the states of this machine only.
/// A group node is the transition source its members share, and never a target.
///
/// This widget owns the topology — the positions, the names, the group declarations and the
/// transition set — and round-trips it through [StateMachineData]. What a state runs and the transition
/// condition sub-graphs are authored in their own `{state}` / `{from} to {to}` sub-tabs.
public final class StateMachineGraphWidget extends AbstractContainerWidget implements AnchoredWidget, CutSource {
    /// Outer edge band (graph posX) of a box where a press starts a transition instead of a move.
    private static final int EDGE_BAND = 6;
    private static final int NODE_H = 28;
    private static final int NODE_PAD_X = 14;
    private static final int NODE_MIN_W = 64;
    /// Grid step (graph posX) a state snaps to while dragging / on create — matches the Event Graph.
    private static final int DRAG_SNAP = 5;
    /// Scroll-wheel zoom bounds / step — matches the Event Graph ([ScriptNodeGraphWidget]).
    private static final float MIN_ZOOM = 0.30F;
    private static final float MAX_ZOOM = 2.50F;
    private static final float ZOOM_STEP = 1.10F;

    // A node is drawn as two regions (UE-style): a darker **outer frame** — the band a press grabs to
    // start a transition — around a brighter **inner body** — the part a press grabs to move it.
    private static final int COLOR_OUTER = 0xFF2C2C2C;
    /// The outer frame brightened while the cursor is over it, advertising the transition-drag band.
    private static final int COLOR_OUTER_HOVER = 0xFF8A8A8A;
    /// The outer frame when the node is selected (UE's orange state frame).
    private static final int COLOR_OUTER_SELECTED = 0xFFC88A2A;
    private static final int COLOR_ENTRY_BODY = 0xFF4A4A4A;
    private static final int COLOR_STATE_BODY = 0xFF5A5A5A;
    private static final int COLOR_OUTLINE = 0xFF101010;
    private static final int COLOR_LABEL = 0xFFE0E0E0;
    private static final int COLOR_GROUP_BODY = 0xFF3F5770;
    private static final int COLOR_TRANSITION = 0xFFBFBFBF;
    /// The transition line / arrow brightened while the cursor hovers it.
    private static final int COLOR_TRANSITION_HOVER = 0xFFFFFFFF;
    /// The transition line / arrow when it is the selected one (UE's orange).
    private static final int COLOR_TRANSITION_SELECTED = 0xFFC88A2A;
    /// Arrowhead triangle size (screen posX): length back from the tip, half-width across the base.
    private static final float ARROW_LEN = 9.0F;
    private static final float ARROW_HALF_W = 4.5F;
    /// Rubber-band (marquee) selection box — a faint fill and a dashed border (mirrors the Event Graph).
    private static final int COLOR_MARQUEE_FILL = 0x22FFFFFF;
    private static final int COLOR_MARQUEE_BORDER = 0xFFCFCFCF;

    /// The state a possessed entity is in. Distinct from [#COLOR_OUTER_SELECTED] so a debug highlight
    /// never reads as a selection.
    private static final int COLOR_ACTIVE_STATE = 0xFF3ACF6A;
    private static final int ACTIVE_STATE_RING = 2;

    /// Alpha a state waiting for its cut's paste draws at, and the transitions on it.
    private static final float CUT_ALPHA = 0.5F;
    /// Graph-space shift between one paste and the next at the same press. A multiple of [#DRAG_SNAP].
    private static final int PASTE_STEP = 25;

    /// Name of a pasted state whose signature this machine already carries. No block carries it, so the
    /// state reads as unresolved until a signature is picked for it.
    private static final String UNSELECTED = AkytheraLabs.MODID + ":unselected";

    /// The sentinel "from" name of an Entry transition (Entry has no [StateNode] name of its own).
    private static final String ENTRY = ScriptCanvasKeys.ENTRY;

    private final Font font;

    /// The kind of machine this canvas edits: its signatures and the parts of its states and transitions.
    private final StateMachineData.Kind<?> kind;

    /// Entry is a node like any other for hit-testing / moving, but it is non-deletable and is the
    /// source of the Entry transitions (the machine's candidate initial states).
    private final StateNode entry;
    private final List<StateNode> states = new ArrayList<>();
    private final List<GroupNode> groups = new ArrayList<>();
    private final List<Transition> transitions = new ArrayList<>();

    /// The selected states (drawn with the orange frame), subjects of group move / delete. A plain
    /// click selects just one; Shift+click toggles one; a marquee selects everything it covers.
    private final List<StateNode> selectedNodes = new ArrayList<>();

    /// The states waiting for their cut's paste.
    private final List<StateNode> cutNodes = new ArrayList<>();

    /// Where the next paste lands, in graph space: the last press on the canvas.
    private int pasteX;
    private int pasteY;

    /// The selected group (drawn with the orange frame; `Delete` removes it, leaving its members).
    /// Mutually exclusive with [#selectedNodes] and [#selectedTransition].
    @Nullable
    private GroupNode selectedGroup;

    /// The selected transition (its line drawn highlighted; `Delete` removes it). Mutually exclusive
    /// with [#selectedNodes].
    @Nullable
    private Transition selectedTransition;

    /// Fired when a state node is double-clicked (open its `{state}` pose sub-tab).
    @Nullable
    private Consumer<StateNode> onStateActivated;

    /// Fired when a state / Entry transition is double-clicked, with its source name ([#ENTRY] for
    /// Entry) and its target state (open the `{from} to {to}` condition sub-tab).
    @Nullable
    private BiConsumer<String, String> onTransitionActivated;

    /// Fired when a group transition is double-clicked, with the group's name and the target state
    /// (open the `{group} to {to}` condition sub-tab).
    @Nullable
    private BiConsumer<String, String> onGroupTransitionActivated;

    /// Fired when the single selection changes, with the newly selected state, group or transition,
    /// [NoDetails#INSTANCE] when the selection is Entry alone or covers several states, or null when it is
    /// empty.
    @Nullable
    private Consumer<Selection> onSelectionChanged;

    /// The selection last handed to [#onSelectionChanged], so repeated notifications for an unchanged
    /// selection are swallowed (the handler rebuilds the Details panel, so it must only fire on a
    /// genuine change).
    @Nullable
    private Selection lastNotifiedSelection;

    private float panX = 40.0F;
    private float panY = 40.0F;
    /// Scroll-wheel zoom; boxes / transitions are drawn under a `translate(pan) + scale(zoom)` pose.
    private float zoom = 1.0F;

    // Pan (right-drag) tracking.
    private boolean rightDown;
    private double rightPressX;
    private double rightPressY;

    // Box move tracking. `dragging` is the grabbed anchor; the nodes it carries move with it, each
    // offset from its own captured start position so the relative layout is preserved.
    @Nullable private Box dragging;
    private int grabDx;
    private int grabDy;
    private int dragAnchorStartX;
    private int dragAnchorStartY;
    private final List<Box> dragNodes = new ArrayList<>();
    private final List<Pair<Integer, Integer>> dragStartPositions = new ArrayList<>();

    /// Records the box move in flight once it ends, see [#beginTopologyChange]. Null when no box is dragged.
    @Nullable private Runnable dragCommit;

    // Marquee (rubber-band) selection in graph space. `marqueeBase` is the selection kept when Shift
    // makes the drag additive (empty otherwise), unioned each frame with whatever the box covers.
    private boolean marqueeing;
    private final List<StateNode> marqueeBase = new ArrayList<>();
    private int marqueeStartGx;
    private int marqueeStartGy;
    private int marqueeEndGx;
    private int marqueeEndGy;

    // Transition-drag tracking.
    @Nullable private Box transitionFrom;
    private double transitionEndX;
    private double transitionEndY;

    // Pending-transition tracking: after a transition is dropped on empty canvas, the arrow stays
    // pinned to the drop point while the target-state menu is open (mirrors the Event Graph's pending
    // wire). Cleared once that menu leaves the modal stack — whether by a pick or a dismiss.
    @Nullable private Box transitionPendingSource;
    private double transitionPendingX;
    private double transitionPendingY;
    @Nullable private ContextMenuContainer pendingMenu;

    public StateMachineGraphWidget(
        Font font,
        StateMachineData.Kind<?> kind,
        StateMachineData data,
        Unit x1, Unit x2, Unit y1, Unit y2,
        AnchoredWidget.HorizontalAnchorType horizontalAnchorType,
        AnchoredWidget.VerticalAnchorType verticalAnchorType
    ) {
        super(0, 0, 0, 0, Component.empty(), AbstractScrollArea.defaultSettings(0));

        this.font = font;
        this.kind = kind;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchorType;
        this.verticalAnchorType = verticalAnchorType;

        this.entry = new StateNode("Entry", data.entryX(), data.entryY(), kind.defaultStatePart().get());
        importData(data);
    }

    /// Ids of the states the possessed entity's machines are in, as [AnimatorProbe#activeStateIds] reports
    /// them. A state node named by one of these draws the debug ring. Empty when nothing is being debugged.
    private Set<String> activeStateIds = Set.of();

    public void setActiveStateIds(final Set<String> activeStateIds) {
        this.activeStateIds = activeStateIds;
    }

    public void setOnStateActivated(@Nullable Consumer<StateNode> handler) {
        this.onStateActivated = handler;
    }

    public void setOnTransitionActivated(@Nullable BiConsumer<String, String> handler) {
        this.onTransitionActivated = handler;
    }

    public void setOnGroupTransitionActivated(@Nullable BiConsumer<String, String> handler) {
        this.onGroupTransitionActivated = handler;
    }

    public void setOnSelectionChanged(@Nullable Consumer<Selection> handler) {
        this.onSelectionChanged = handler;
    }

    /// Fires [#onSelectionChanged] when — and only when — the single selection has actually changed
    /// since the last notification. The reported selection is the selected transition, the selected
    /// group, or the sole selected state (never Entry); Entry alone or several states report
    /// [NoDetails#INSTANCE], and an empty selection null.
    /// Called after every gesture that can move the selection.
    private void notifySelection() {
        Selection single = selectedTransition != null ? selectedTransition
            : selectedGroup != null ? selectedGroup
            : selectedNodes.isEmpty() ? null
            : selectedNodes.size() == 1 && selectedNodes.get(0) != entry ? selectedNodes.get(0)
            : NoDetails.INSTANCE;
        if (single == lastNotifiedSelection) return;
        lastNotifiedSelection = single;
        if (onSelectionChanged != null) onSelectionChanged.accept(single);
    }

    /// The signature (id string) of every state currently on this machine — the
    /// set the host's Details combo strips from its options so a state can never be re-signed to a
    /// signature another state already carries (no duplicate states on one machine).
    public Set<String> stateNames() {
        Set<String> names = new HashSet<>();
        for (StateNode s : states) names.add(s.name);
        return names;
    }

    /// Re-signs `node` to the signature `newName`, retargeting every transition that pointed
    /// at its old signature so the topology (and each transition's condition sub-graph) survives the
    /// change; the state's own part travels with the node, and a transition *leaving* it
    /// holds the node itself so it needs no retargeting. Returns false — rejecting the edit — when
    /// `newName` is blank or already carried by another state on this machine.
    public boolean changeStateSignature(StateNode node, String newName) {
        String oldName = node.name;
        if (newName.equals(oldName)) return true;
        if (newName.isEmpty()) return false;
        if (findState(newName) != null) return false; // already on this machine — no duplicates

        for (Transition t : transitions) {
            if (t.to.equals(oldName)) t.to = newName;
        }
        node.name = newName;
        if (node == lastNotifiedSelection) lastNotifiedSelection = null; // force a fresh Details rebuild
        return true;
    }

    /// Renames a group node and every state declaration naming it. Returns false — rejecting the edit —
    /// when blank or taken by another group node.
    public boolean changeGroupName(GroupNode group, String newName) {
        String oldName = group.name;

        if (newName.equals(oldName)) return true;
        if (newName.isEmpty()) return false;
        if (findGroup(newName) != null) return false;

        for (StateNode s : states) {
            s.groups = s.groups.stream().map(name -> name.equals(oldName) ? newName : name).distinct().toList();
        }

        group.name = newName;
        if (group == lastNotifiedSelection) lastNotifiedSelection = null; // force a fresh Details rebuild
        return true;
    }

    // ***************************************************************
    // Persistence
    // ***************************************************************
    private void importData(StateMachineData data) {
        states.clear();
        groups.clear();
        transitions.clear();
        selectedNodes.clear();
        selectedGroup = null;
        selectedTransition = null;

        for (StateMachineData.GroupData g : data.groups()) {
            groups.add(new GroupNode(g.name(), g.x(), g.y()));
        }

        for (StateMachineData.StateData s : data.states()) {
            StateNode node = new StateNode(nameOf(s.state()), s.x(), s.y(), s.part());
            node.groups = s.groups();
            states.add(node);
        }

        // A transition points at the box it leaves, so every state and group has to exist first.
        for (StateMachineData.StateData s : data.states()) {
            StateNode from = findState(nameOf(s.state()));
            if (from == null) continue;
            for (StateMachineData.TransitionData t : s.transitions()) addImported(from, t);
        }

        for (StateMachineData.GroupData g : data.groups()) {
            GroupNode from = findGroup(g.name());
            if (from == null) continue;
            for (StateMachineData.TransitionData t : g.transitions()) addImported(from, t);
        }

        for (StateMachineData.TransitionData t : data.entryTransitions()) addImported(entry, t);
    }

    private void addImported(Box from, StateMachineData.TransitionData data) {
        Transition transition = new Transition(from, nameOf(data.to()), data.part());
        transition.conditionGraph = data.conditionGraph();
        transition.transitionPriority = data.transitionPriority();
        transitions.add(transition);
    }

    /// Display/identity name of a state holder — its key id, which exists even for a dangling
    /// (unresolved) holder; only a keyless engine direct holder reads the live value.
    private static String nameOf(Holder<? extends FSMState> holder) {
        return ScriptCanvasKeys.stateName(holder);
    }

    /// Flattens the live topology — each state's part, each transition's condition sub-graph
    /// and the group declarations — back to a builder-shaped [StateMachineData]: each transition nested
    /// under the box it leaves, Entry's outgoing edges collected into `entryTransitions`. State names
    /// round-trip through their signature; an unresolved signature persists as a
    /// dangling holder (the red MISSING state round-trips instead of being silently deleted) — only an
    /// unparseable name (cannot happen for names created from ids) is dropped.
    public StateMachineData exportData() {
        List<StateMachineData.StateData> stateData = new ArrayList<>();
        for (StateNode s : states) {
            Holder<? extends FSMState> signature = holderOf(s.name);
            if (signature == null) continue;

            stateData.add(new StateMachineData.StateData(
                signature, s.x, s.y, s.part, outgoingData(s), s.groups));
        }

        List<StateMachineData.GroupData> groupData = new ArrayList<>();
        for (GroupNode g : groups) {
            groupData.add(new StateMachineData.GroupData(g.name, g.x, g.y, outgoingData(g)));
        }

        return new StateMachineData(entry.x, entry.y, outgoingData(entry), stateData, groupData);
    }

    /// The persisted transitions leaving `from`, their targets resolved back to signature
    /// holders (an unresolved signature persists as a dangling holder; only an unparseable name is
    /// dropped).
    private List<StateMachineData.TransitionData> outgoingData(Box from) {
        List<StateMachineData.TransitionData> out = new ArrayList<>();

        for (Transition t : transitions) {
            if (t.from != from) continue;
            Holder<? extends FSMState> to = holderOf(t.to);
            if (to != null) {
                out.add(new StateMachineData.TransitionData(to, t.conditionGraph, t.transitionPriority, t.part));
            }
        }

        return out;
    }

    /// Replaces the whole topology with `data`, as one undoable unit.
    public void replaceData(StateMachineData data) {
        recordTopology(() -> importData(data));
        notifySelection();
    }

    // ***************************************************************
    // Copy / paste
    // ***************************************************************

    /// Snapshots the selected states for the clipboard in the same builder shape the machine persists:
    /// each state keeps its position, its group names and its part — what it runs — and carries the transitions leaving it **to another selected state**, so a
    /// paste never lands an edge with no source or target. Entry is skipped: a machine has exactly one
    /// and it is hardcoded. Returns null when no state is selected.
    public @Nullable List<StateMachineData.StateData> copySelection() {
        List<StateNode> copied = selectedNodes.stream().filter(n -> n != entry).toList();
        if (copied.isEmpty()) return null;

        Set<String> names = new HashSet<>();
        for (StateNode node : copied) names.add(node.name);

        List<StateMachineData.StateData> out = new ArrayList<>();

        for (StateNode node : copied) {
            Holder<? extends FSMState> signature = holderOf(node.name);
            if (signature == null) continue;

            List<StateMachineData.TransitionData> outgoing = new ArrayList<>();
            for (Transition t : transitions) {
                if (t.from != node || !names.contains(t.to)) continue;
                Holder<? extends FSMState> to = holderOf(t.to);
                if (to != null) {
                    outgoing.add(new StateMachineData.TransitionData(to, t.conditionGraph, t.transitionPriority, t.part));
                }
            }

            out.add(new StateMachineData.StateData(
                signature, node.x, node.y, node.part, outgoing, node.groups));
        }

        return out.isEmpty() ? null : out;
    }

    /// Marks the states [#copySelection] takes as cut, and answers what marks the same states again.
    public Runnable markSelectionCut() {
        List<StateNode> cut = selectedNodes.stream().filter(n -> n != entry).toList();

        Runnable mark = () -> {
            cutNodes.clear();
            cutNodes.addAll(cut);
        };

        mark.run();

        return mark;
    }

    /// Places clipboard states on this machine as one undoable unit, the top-left corner of them at the last
    /// press on the canvas. A state whose signature this machine already carries lands
    /// under an [#UNSELECTED] name instead — a signature is the state's identity and a machine holds it once.
    /// A transition whose target is not among the pasted states lands on the state of this machine carrying
    /// that signature, when there is one. Group nodes are not on the clipboard. The placed states become the
    /// selection.
    public void pasteStates(List<StateMachineData.StateData> pasted) {
        int dx = pasteX - pasted.stream().mapToInt(StateMachineData.StateData::x).min().orElse(pasteX);
        int dy = pasteY - pasted.stream().mapToInt(StateMachineData.StateData::y).min().orElse(pasteY);

        List<StateNode> created = new ArrayList<>();

        // The name each pasted state landed under, by the name it was copied under
        Map<String, String> landedNames = new HashMap<>();

        recordTopology(() -> {
            for (StateMachineData.StateData s : pasted) {
                String name = nameOf(s.state());

                for (int suffix = 2; findState(name) != null; suffix++) {
                    name = suffix == 2 ? UNSELECTED : UNSELECTED + "_" + (suffix - 1);
                }

                StateNode node = new StateNode(name, snap(s.x() + dx), snap(s.y() + dy), s.part());
                node.groups = s.groups();
                states.add(node);
                created.add(node);
                landedNames.put(nameOf(s.state()), name);
            }

            for (int i = 0; i < pasted.size(); i++) {
                StateNode from = created.get(i);

                for (StateMachineData.TransitionData t : pasted.get(i).transitions()) {
                    String to = landedNames.getOrDefault(nameOf(t.to()), nameOf(t.to()));
                    if (findState(to) == null || findTransitionFrom(from, to) != null) continue;

                    Transition transition = new Transition(from, to, t.part());
                    transition.conditionGraph = t.conditionGraph();
                    transition.transitionPriority = t.transitionPriority();
                    transitions.add(transition);
                }
            }
        });

        pasteX += PASTE_STEP;
        pasteY += PASTE_STEP;

        selectedTransition = null;
        clearSelection();
        for (StateNode node : created) addToSelection(node);
        notifySelection();
    }

    /// Runs `change` and records what it did to the topology as one undoable unit, see [#beginTopologyChange].
    private void recordTopology(Runnable change) {
        Runnable commit = beginTopologyChange();

        change.run();
        commit.run();
    }

    /// Starts a change of the topology: the states, groups and transitions on the canvas, and where each node
    /// sits. Answers what records the change as one undoable unit, to run once the change is made.
    private Runnable beginTopologyChange() {
        List<StateNode> statesBefore = List.copyOf(states);
        List<GroupNode> groupsBefore = List.copyOf(groups);
        List<Transition> transitionsBefore = List.copyOf(transitions);
        List<Placement> placementsBefore = new ArrayList<>();

        placementsBefore.add(new Placement(entry));
        for (StateNode state : states) placementsBefore.add(new Placement(state));
        for (GroupNode group : groups) placementsBefore.add(new Placement(group));

        return () -> {
            List<Placement> restored = placementsBefore.stream()
                .filter(placement -> !placement.equals(new Placement(placement.node())))
                .toList();
            List<Placement> applied = restored.stream().map(placement -> new Placement(placement.node())).toList();

            Topology added = new Topology(
                without(states, statesBefore), without(groups, groupsBefore), without(transitions, transitionsBefore), applied);
            Topology removed = new Topology(
                without(statesBefore, states), without(groupsBefore, groups), without(transitionsBefore, transitions), restored);

            if (added.isEmpty() && removed.isEmpty()) return;

            HistoryManager.getInstance().pushHistory(this, new WorkHistory() {
                @Override
                public void undo() {
                    replaceTopology(added, removed);
                }

                @Override
                public void redo() {
                    replaceTopology(removed, added);
                }
            });
        };
    }

    /// Takes `out` off the canvas and puts `in` on it. Nothing stays selected.
    private void replaceTopology(Topology out, Topology in) {
        states.removeAll(out.states());
        groups.removeAll(out.groups());
        transitions.removeAll(out.transitions());

        states.addAll(in.states());
        groups.addAll(in.groups());
        transitions.addAll(in.transitions());

        for (Placement placement : in.placements()) {
            placement.node().x = placement.x();
            placement.node().y = placement.y();
        }

        selectedTransition = null;
        clearSelection();
        notifySelection();
    }

    /// The elements of `from` that `other` does not hold.
    private static <T> List<T> without(List<T> from, List<T> other) {
        return from.stream().filter(element -> !other.contains(element)).toList();
    }

    /// Where a node sits, in graph space.
    private record Placement(Box node, int x, int y) {
        Placement(Box node) {
            this(node, node.x, node.y);
        }
    }

    /// What a change put on the canvas or took off it, and the placements it gave or took away.
    private record Topology(
        List<StateNode> states, List<GroupNode> groups, List<Transition> transitions, List<Placement> placements
    ) {
        boolean isEmpty() {
            return states.isEmpty() && groups.isEmpty() && transitions.isEmpty() && placements.isEmpty();
        }
    }

    // ***************************************************************
    // [CutSource]
    // ***************************************************************

    @Override
    public void clearCut() {
        cutNodes.clear();
    }

    @Override
    public void deleteCut() {
        recordTopology(() -> {
            for (StateNode node : cutNodes) {
                if (states.contains(node)) deleteState(node);
            }
        });

        selectedNodes.removeAll(cutNodes);
        notifySelection();
    }

    /// Deletes the current selection as one undoable unit: the selected transition, the selected group node, or
    /// every selected state and the transitions touching it. Entry is never deleted.
    public void deleteSelection() {
        recordTopology(() -> {
            if (selectedTransition != null) {
                transitions.remove(selectedTransition);
                selectedTransition = null;
                return;
            }

            if (selectedGroup != null) {
                deleteGroup(selectedGroup);
                return;
            }

            for (StateNode n : List.copyOf(selectedNodes)) {
                if (n != entry) deleteState(n);
            }

            clearSelection();
        });

        notifySelection();
    }

    /// The live [Holder] for a state name (its `identifier().toString()`), resolved through
    /// project &cup; manager (so editor-created blocks resolve); an id in neither mints a dangling
    /// holder so the unresolved state/transition still round-trips to disk. Only an unparseable
    /// name (cannot happen for names created from ids) yields null, now logged.
    private @Nullable Holder<? extends FSMState> holderOf(String name) {
        Identifier id = Identifier.tryParse(name);
        if (id == null) {
            AkytheraLabs.LOGGER.warn("Dropping state-machine element with unparseable name {}", name);
            return null;
        }
        return AkytheraProject.getCurrentProject().getDataBlockHolderOrDangling(kind.manager(), id);
    }

    public StateMachineData.Kind<?> getKind() {
        return kind;
    }

    /// What state `name` runs (the kind's default if unknown), and its setter — the host's `{state}` sub-tab
    /// reads / writes these.
    public StateMachineData.StatePart getStatePart(String name) {
        StateNode s = findState(name);
        return s == null ? kind.defaultStatePart().get() : s.part;
    }

    public void setStatePart(String name, StateMachineData.StatePart part) {
        StateNode s = findState(name);
        if (s != null) s.part = part;
    }

    /// The condition sub-graph stored for the state / Entry transition `from → to`
    /// ([ScriptGraphData#EMPTY] if unset / unknown), and its setter — the host's `{from} to {to}`
    /// condition sub-tab reads / writes these.
    public ScriptGraphData getTransitionCondition(String from, String to) {
        Transition t = findTransition(from, to);
        return t == null ? ScriptGraphData.EMPTY : t.conditionGraph;
    }

    public void setTransitionCondition(String from, String to, ScriptGraphData graph) {
        Transition t = findTransition(from, to);
        if (t != null) t.conditionGraph = graph;
    }

    /// The condition sub-graph stored for the group transition `group → to`, and its setter — the
    /// host's `{group} to {to}` condition sub-tab reads / writes these. One graph, expanded onto every
    /// member of the group at compile.
    public ScriptGraphData getGroupTransitionCondition(String group, String to) {
        Transition t = findGroupTransition(group, to);
        return t == null ? ScriptGraphData.EMPTY : t.conditionGraph;
    }

    public void setGroupTransitionCondition(String group, String to, ScriptGraphData graph) {
        Transition t = findGroupTransition(group, to);
        if (t != null) t.conditionGraph = graph;
    }

    /// How `transition` is taken, and its setter — the Details panel's rows of the kind read and write these.
    public StateMachineData.TransitionPart getTransitionPart(Transition transition) {
        return transition.part;
    }

    public void setTransitionPart(Transition transition, StateMachineData.TransitionPart part) {
        transition.part = part;
    }

    /// The priority `transition` is taken at, and its setter — the Details panel's Transition Priority
    /// row reads and writes these.
    public int getTransitionPriority(Transition transition) {
        return transition.transitionPriority;
    }

    public void setTransitionPriority(Transition transition, int transitionPriority) {
        transition.transitionPriority = transitionPriority;
    }

    /// The names of the groups `state` belongs to, and its setter — the Details panel's State Group rows
    /// read and write these.
    public List<String> getStateGroups(StateNode state) {
        return state.groups;
    }

    public void setStateGroups(StateNode state, List<String> groups) {
        state.groups = groups;
    }

    private @Nullable Transition findTransition(String from, String to) {
        for (Transition t : transitions) {
            if (t.from instanceof StateNode s && canvasFrom(s).equals(from) && t.to.equals(to)) return t;
        }
        return null;
    }

    private @Nullable Transition findGroupTransition(String group, String to) {
        for (Transition t : transitions) {
            if (t.from instanceof GroupNode g && g.name.equals(group) && t.to.equals(to)) return t;
        }
        return null;
    }

    private @Nullable Transition findTransitionFrom(Box from, String to) {
        for (Transition t : transitions) {
            if (t.from == from && t.to.equals(to)) return t;
        }
        return null;
    }

    /// The name a transition leaving `source` reports to the host — the state's signature, or [#ENTRY]
    /// for the Entry node, which has no signature of its own.
    private String canvasFrom(StateNode source) {
        return source == entry ? ENTRY : source.name;
    }

    // ***************************************************************
    // Geometry
    // ***************************************************************
    private int nodeWidth(Box n) {
        return Math.max(NODE_MIN_W, font.width(n.name) + NODE_PAD_X * 2);
    }

    private int screenToGraphX(double sx) { return (int) ((sx - getX() - panX) / zoom); }
    private int screenToGraphY(double sy) { return (int) ((sy - getY() - panY) / zoom); }

    /// The graph-space rectangle `{x, y, width, height}` of a box.
    private int[] boundsOf(Box box) {
        return new int[]{box.x, box.y, nodeWidth(box), NODE_H};
    }

    private boolean inBox(Box box, int gx, int gy) {
        int[] b = boundsOf(box);
        return gx >= b[0] && gx < b[0] + b[2] && gy >= b[1] && gy < b[1] + b[3];
    }

    /// Whether `(gx, gy)` is in the box's outer edge band (a transition-drag start) vs. its inner
    /// body (a move).
    private boolean inEdgeBand(Box box, int gx, int gy) {
        if (!inBox(box, gx, gy)) return false;
        int[] b = boundsOf(box);
        return gx < b[0] + EDGE_BAND || gx >= b[0] + b[2] - EDGE_BAND
            || gy < b[1] + EDGE_BAND || gy >= b[1] + b[3] - EDGE_BAND;
    }

    /// The topmost state box under a graph point (states above Entry), or null.
    private @Nullable StateNode stateNodeAt(int gx, int gy) {
        for (int i = states.size() - 1; i >= 0; i--) {
            if (inBox(states.get(i), gx, gy)) return states.get(i);
        }
        return inBox(entry, gx, gy) ? entry : null;
    }

    /// The topmost group node under a graph point, or null.
    private @Nullable GroupNode groupAt(int gx, int gy) {
        for (int i = groups.size() - 1; i >= 0; i--) {
            if (inBox(groups.get(i), gx, gy)) return groups.get(i);
        }
        return null;
    }

    /// Whether a press at this graph point lands on a state or a group node, ahead of any transition.
    private boolean boxClaims(int gx, int gy) {
        return stateNodeAt(gx, gy) != null || groupAt(gx, gy) != null;
    }

    // ***************************************************************
    // Rendering
    // ***************************************************************
    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        CanvasGrid.extract(g, getX(), getY(), getRight(), getBottom(), getX() + panX, getY() + panY, zoom);
        g.enableScissor(getX(), getY(), getRight(), getBottom());

        // The pinned arrow lives only while its target-state menu is open; drop it once that menu is
        // gone (a pick spawned + wired the state, a dismiss left the topology unchanged).
        if (transitionPendingSource != null && !menuStillOpen()) clearTransitionPending();


        // Graph-space cursor for hover highlighting — off-canvas (no box) when the mouse is outside
        // this widget, so a box / transition never falsely highlights.
        boolean over = isMouseOver(mouseX, mouseY);
        int gmx = over ? screenToGraphX(mouseX) : Integer.MIN_VALUE;
        int gmy = over ? screenToGraphY(mouseY) : Integer.MIN_VALUE;

        // The hovered transition (only when idle and nothing claims the point).
        Transition hovered = (over && dragging == null && transitionFrom == null && !marqueeing && !boxClaims(gmx, gmy))
            ? transitionAt(gmx, gmy) : null;

        // Boxes / transitions live in graph space; a translate + scale pose draws them at their graph
        // coordinates so they pan and zoom together (mirrors the Event Graph). The grid is screen-space.
        g.pose().pushMatrix();
        g.pose().translate(getX() + panX, getY() + panY);
        g.pose().scale(zoom, zoom);

        for (Transition t : transitions) {
            int color = t == selectedTransition ? COLOR_TRANSITION_SELECTED
                : (t == hovered ? COLOR_TRANSITION_HOVER : COLOR_TRANSITION);
            boolean cut = cutNodes.contains(t.from) || cutNodes.contains(findState(t.to));

            if (cut) ((GuiGraphicsExtractorAccessor) g).akytheralabs$setAlpha(CUT_ALPHA);
            drawTransition(g, t, color);
            if (cut) ((GuiGraphicsExtractorAccessor) g).akytheralabs$setAlpha(1.0F);
        }

        // The in-flight transition arrow: tracking the cursor while dragging, then pinned to the drop
        // point while the target-state menu is open.
        if (transitionFrom != null) {
            drawTransitionPreview(g, transitionFrom, transitionEndX, transitionEndY);
        } else if (transitionPendingSource != null) {
            drawTransitionPreview(g, transitionPendingSource, transitionPendingX, transitionPendingY);
        }

        drawNode(g, entry, COLOR_ENTRY_BODY, gmx, gmy);

        for (GroupNode group : groups) drawNode(g, group, COLOR_GROUP_BODY, gmx, gmy);

        for (StateNode s : states) {
            boolean cut = cutNodes.contains(s);

            if (cut) ((GuiGraphicsExtractorAccessor) g).akytheralabs$setAlpha(CUT_ALPHA);
            drawNode(g, s, COLOR_STATE_BODY, gmx, gmy);
            if (cut) ((GuiGraphicsExtractorAccessor) g).akytheralabs$setAlpha(1.0F);
        }

        if (marqueeing) drawMarquee(g);

        g.pose().popMatrix();

        // Hover cursor when no gesture owns it: the 4-way move cross over a box's inner body, the
        // pointing hand over its outer edge band (the transition-drag grab zone) — mirrors the Event Graph.
        if (over && dragging == null && transitionFrom == null && transitionPendingSource == null
            && !marqueeing && !rightDown) {
            requestHoverCursor(g, gmx, gmy);
        }

        g.disableScissor();
    }

    /// Requests the move (4-directional) cursor over the inner body of the box under `(gx, gy)`, or the
    /// pointing hand over its outer edge band; leaves the default arrow on empty canvas.
    private void requestHoverCursor(GuiGraphicsExtractor g, int gx, int gy) {
        Box box = stateNodeAt(gx, gy);
        if (box == null) box = groupAt(gx, gy);
        if (box == null) return;

        g.requestCursor(inEdgeBand(box, gx, gy) ? CursorTypes.POINTING_HAND : CursorTypes.RESIZE_ALL);
    }

    /// Draws a node as a two-region box: the outer frame (transition-drag band) around the inner body
    /// (move handle). The frame turns orange when selected and brightens while the cursor hovers it
    /// (but not its inner body), advertising that a drag there starts a transition.
    private void drawNode(GuiGraphicsExtractor g, Box n, int bodyColor, int gmx, int gmy) {
        int w = nodeWidth(n);
        int sx = n.x;
        int sy = n.y;

        boolean selected = n == selectedGroup || (n instanceof StateNode s && selectedNodes.contains(s));
        boolean edgeHover = dragging == null && transitionFrom == null && inEdgeBand(n, gmx, gmy);
        // While dragging a transition, the node it would drop onto (any state but the source / Entry)
        // highlights its frame as the valid target.
        boolean dropTarget = transitionFrom != null && n instanceof StateNode && n != transitionFrom && n != entry
            && inBox(n, gmx, gmy);
        int outer = selected ? COLOR_OUTER_SELECTED : (edgeHover || dropTarget ? COLOR_OUTER_HOVER : COLOR_OUTER);

        // The shared node error marking, checked live: a state whose signature no longer resolves, or a
        // group no state declares.
        String error = switch (n) {
            case StateNode s -> isUnresolved(s) ? "MISSING!" : null;
            case GroupNode group -> isUnreachable(group) ? "Unreachable group!" : null;
        };

        // Outer frame, then the inner body inset by the edge band.
        g.fill(sx, sy, sx + w, sy + NODE_H, outer);
        g.fill(sx + EDGE_BAND, sy + EDGE_BAND, sx + w - EDGE_BAND, sy + NODE_H - EDGE_BAND, bodyColor);
        g.outline(sx, sy, w, NODE_H, error != null ? AbstractNodeWidget.COLOR_ERROR_OUTLINE : COLOR_OUTLINE);
        if (error != null) AbstractNodeWidget.drawErrorBanner(g, font, sx, sy + NODE_H, w, error);

        // Debug ring, hung outside the frame so it stacks with the selection colour instead of
        // replacing it.
        if (n instanceof StateNode && activeStateIds.contains(n.name)) {
            for (int ring = 1; ring <= ACTIVE_STATE_RING; ring++) {
                g.outline(sx - ring, sy - ring, w + ring * 2, NODE_H + ring * 2, COLOR_ACTIVE_STATE);
            }
        }

        g.text(font, Component.literal(n.name), sx + NODE_PAD_X, sy + (NODE_H - font.lineHeight) / 2, COLOR_LABEL, false);
    }

    /// A state whose signature resolves in neither the project nor the engine manager. Checked
    /// live each frame (cheap map lookups) so creating the missing block clears the mark without
    /// a reload.
    private boolean isUnresolved(StateNode n) {
        if (n == entry) return false;
        Holder<? extends FSMState> h = holderOf(n.name);
        return h == null || !h.isBound();
    }

    /// A group node no state on this machine declares, so no state inherits its transitions.
    private boolean isUnreachable(GroupNode group) {
        return states.stream().noneMatch(s -> s.groups.contains(group.name));
    }

    /// Draws the in-flight transition arrow (graph space): from `source`'s center to a graph-space
    /// point converted from the given screen-space end. Shared by the live drag and the pinned
    /// (menu-open) state.
    private void drawTransitionPreview(GuiGraphicsExtractor g, Box source, double endScreenX, double endScreenY) {
        int[] b = boundsOf(source);
        float sx = b[0] + b[2] / 2.0F;
        float sy = b[1] + b[3] / 2.0F;
        float ex = screenToGraphX(endScreenX);
        float ey = screenToGraphY(endScreenY);
        double ang = Math.atan2(ey - sy, ex - sx);
        drawLine(g, sx, sy, ex, ey, COLOR_TRANSITION);
        drawArrowHead(g, ex, ey, ang, COLOR_TRANSITION);
    }

    /// Draws a transition as a line between the two boxes' **edges** with a filled-triangle arrowhead
    /// sitting on the target state's border (UE-style), in `color`.
    private void drawTransition(GuiGraphicsExtractor g, Transition t, int color) {
        float[] e = transitionEndpoints(t);
        if (e == null) return;

        drawLine(g, e[0], e[1], e[2], e[3], color);
        double ang = Math.atan2(e[3] - e[1], e[2] - e[0]);
        drawArrowHead(g, e[2], e[3], ang, color);
    }

    /// The graph-space endpoints `{fromX, fromY, toX, toY}` of a transition: the points where the
    /// center-to-center line meets each box's rectangle border (so the line touches the boxes rather
    /// than their centers), perpendicular-offset when a reverse transition exists so the twin arrows
    /// don't overlap. Null when the target state is missing.
    /// Drawn / hit-tested under the graph-space pose, so they pan and zoom with the boxes.
    private float @Nullable [] transitionEndpoints(Transition t) {
        StateNode to = findState(t.to);
        if (to == null) return null;

        int[] fb = boundsOf(t.from);
        int[] tb = boundsOf(to);

        float fcx = fb[0] + fb[2] / 2.0F;
        float fcy = fb[1] + fb[3] / 2.0F;
        float tcx = tb[0] + tb[2] / 2.0F;
        float tcy = tb[1] + tb[3] / 2.0F;

        float[] fp = borderPoint(fb, tcx, tcy);
        float[] tp = borderPoint(tb, fcx, fcy);
        float fx = fp[0], fy = fp[1], tx = tp[0], ty = tp[1];

        if (hasReverse(t, to)) {
            double dx = tx - fx, dy = ty - fy;
            double len = Math.max(1.0, Math.hypot(dx, dy));
            float ox = (float) (-dy / len * 5.0);
            float oy = (float) (dx / len * 5.0);
            fx += ox; fy += oy; tx += ox; ty += oy;
        }
        return new float[]{fx, fy, tx, ty};
    }

    /// Whether the opposite direction of `t` also exists, which offsets both lines sideways. Only a
    /// state-to-state pair can have one: a group is never a target.
    private boolean hasReverse(Transition t, StateNode to) {
        if (!(t.from instanceof StateNode from)) return false;
        return transitions.stream().anyMatch(o -> o.from == to && o.to.equals(from.name));
    }

    /// The point on a graph-space rectangle's border along the ray from its center toward
    /// `(towardX, towardY)`.
    private static float[] borderPoint(int[] bounds, float towardX, float towardY) {
        float cx = bounds[0] + bounds[2] / 2.0F;
        float cy = bounds[1] + bounds[3] / 2.0F;
        float dx = towardX - cx, dy = towardY - cy;
        if (dx == 0.0F && dy == 0.0F) return new float[]{cx, cy};

        float hw = bounds[2] / 2.0F, hh = bounds[3] / 2.0F;
        float adx = Math.abs(dx), ady = Math.abs(dy);
        float scale = adx * hh > ady * hw ? hw / adx : hh / ady;
        return new float[]{cx + dx * scale, cy + dy * scale};
    }

    /// A filled isoceles arrowhead with its tip at `(tipX, tipY)` pointing along `ang`.
    private void drawArrowHead(GuiGraphicsExtractor g, float tipX, float tipY, double ang, int color) {
        float bx = tipX - (float) Math.cos(ang) * ARROW_LEN;
        float by = tipY - (float) Math.sin(ang) * ARROW_LEN;
        float px = (float) -Math.sin(ang) * ARROW_HALF_W;
        float py = (float) Math.cos(ang) * ARROW_HALF_W;
        fillTriangle(g, tipX, tipY, bx + px, by + py, bx - px, by - py, color);
    }

    /// Scanline-fills the triangle `(ax,ay)`,`(bx,by)`,`(cx,cy)` with `color` (the canvas has no
    /// polygon primitive, so each pixel row is a 1px [GuiGraphicsExtractor#fill]).
    private static void fillTriangle(GuiGraphicsExtractor g, float ax, float ay, float bx, float by, float cx, float cy, int color) {
        int minY = (int) Math.floor(Math.min(ay, Math.min(by, cy)));
        int maxY = (int) Math.ceil(Math.max(ay, Math.max(by, cy)));

        for (int y = minY; y <= maxY; y++) {
            float yc = y + 0.5F;
            float[] xs = new float[3];
            int n = 0;
            n = edgeCrossing(xs, n, ax, ay, bx, by, yc);
            n = edgeCrossing(xs, n, bx, by, cx, cy, yc);
            n = edgeCrossing(xs, n, cx, cy, ax, ay, yc);
            if (n < 2) continue;

            float lo = xs[0], hi = xs[0];
            for (int i = 1; i < n; i++) {
                lo = Math.min(lo, xs[i]);
                hi = Math.max(hi, xs[i]);
            }
            int x0 = (int) Math.floor(lo), x1 = (int) Math.ceil(hi);
            if (x1 > x0) g.fill(x0, y, x1, y + 1, color);
        }
    }

    /// Appends the x where edge `(x0,y0)-(x1,y1)` crosses the horizontal line `y = yc`, if it does.
    private static int edgeCrossing(float[] xs, int n, float x0, float y0, float x1, float y1, float yc) {
        if ((y0 <= yc && y1 > yc) || (y1 <= yc && y0 > yc)) {
            xs[n++] = x0 + (yc - y0) / (y1 - y0) * (x1 - x0);
        }
        return n;
    }

    private static void drawLine(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)));
        if (steps <= 0) { g.fill((int) x0, (int) y0, (int) x0 + 1, (int) y0 + 1, color); return; }
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int px = Math.round(x0 + dx * t);
            int py = Math.round(y0 + dy * t);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }


    /// Draws the rubber-band selection box (graph space, under the pose): a faint fill plus a dashed
    /// border, mirroring the Event Graph's marquee.
    private void drawMarquee(GuiGraphicsExtractor g) {
        int x0 = Math.min(marqueeStartGx, marqueeEndGx);
        int y0 = Math.min(marqueeStartGy, marqueeEndGy);
        int x1 = Math.max(marqueeStartGx, marqueeEndGx);
        int y1 = Math.max(marqueeStartGy, marqueeEndGy);

        g.fill(x0, y0, x1, y1, COLOR_MARQUEE_FILL);

        int dash = 4, gap = 3;
        for (int x = x0; x < x1; x += dash + gap) {
            int xe = Math.min(x + dash, x1);
            g.fill(x, y0, xe, y0 + 1, COLOR_MARQUEE_BORDER);
            g.fill(x, y1 - 1, xe, y1, COLOR_MARQUEE_BORDER);
        }
        for (int y = y0; y < y1; y += dash + gap) {
            int ye = Math.min(y + dash, y1);
            g.fill(x0, y, x0 + 1, ye, COLOR_MARQUEE_BORDER);
            g.fill(x1 - 1, y, x1, ye, COLOR_MARQUEE_BORDER);
        }
    }

    // ***************************************************************
    // Input
    // ***************************************************************
    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        boolean handled = handleMouseClicked(event, doubleClick);
        notifySelection();
        return handled;
    }

    private boolean handleMouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver(event.x(), event.y())) return false;

        int gx = screenToGraphX(event.x());
        int gy = screenToGraphY(event.y());

        pasteX = gx;
        pasteY = gy;

        if (event.button() == 1) {
            rightDown = true;
            rightPressX = event.x();
            rightPressY = event.y();
            return true;
        }

        if (event.button() != 0) return true;

        StateNode hit = stateNodeAt(gx, gy);
        if (hit != null) {
            selectedTransition = null;

            // Shift+click toggles this node's membership (priority over move / transition), so states
            // can be picked one by one.
            if ((event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0) {
                toggleSelection(hit);
                return true;
            }

            // ALT + click on the outer edge band severs every transition touching this state.
            if (inEdgeBand(hit, gx, gy) && (event.modifiers() & GLFW.GLFW_MOD_ALT) != 0) {
                selectOnly(hit);
                recordTopology(() -> disconnectAll(hit));
                return true;
            }

            if (doubleClick) {
                selectOnly(hit);
                activate(hit);
                return true;
            }

            // Outer edge → start a transition; inner body → move (the whole selection).
            if (inEdgeBand(hit, gx, gy)) {
                selectOnly(hit);
                transitionFrom = hit;
                transitionEndX = event.x();
                transitionEndY = event.y();
            } else {
                // Clicking a node outside the selection selects just it; clicking one already in a
                // multi-selection keeps the group so it can be dragged as a whole.
                if (!selectedNodes.contains(hit)) selectOnly(hit);
                beginBoxDrag(hit, gx, gy);
            }
            return true;
        }

        GroupNode group = groupAt(gx, gy);
        if (group != null) {
            selectGroup(group);

            if (!inEdgeBand(group, gx, gy)) {
                beginBoxDrag(group, gx, gy);
                return true;
            }

            if ((event.modifiers() & GLFW.GLFW_MOD_ALT) != 0) {
                recordTopology(() -> disconnectAll(group));
                return true;
            }

            transitionFrom = group;
            transitionEndX = event.x();
            transitionEndY = event.y();
            return true;
        }

        // Nothing claims the press: a transition takes it, and a double-click opens its condition
        // sub-tab (Entry transitions included, their condition gates the initial state).
        Transition t = transitionAt(gx, gy);
        if (t != null) {
            clearSelection();
            selectedTransition = t;
            if (doubleClick) activateTransition(t);
            return true;
        }

        // Empty canvas: start a marquee. Shift keeps the current selection (additive); otherwise replace.
        selectedTransition = null;
        marqueeBase.clear();
        if ((event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0) {
            marqueeBase.addAll(selectedNodes);
        } else {
            clearSelection();
        }
        marqueeing = true;
        marqueeStartGx = gx;
        marqueeStartGy = gy;
        marqueeEndGx = gx;
        marqueeEndGy = gy;
        return true;
    }

    private void clearSelection() {
        selectedNodes.clear();
        selectedGroup = null;
    }

    private void addToSelection(StateNode node) {
        if (!selectedNodes.contains(node)) selectedNodes.add(node);
    }

    /// Flips `node`'s membership in the selection (Shift+click): drops it when already selected, adds
    /// it otherwise.
    private void toggleSelection(StateNode node) {
        selectedGroup = null;
        if (!selectedNodes.remove(node)) addToSelection(node);
    }

    private void selectOnly(StateNode node) {
        clearSelection();
        addToSelection(node);
    }

    /// Selects a group on its own — a group and states are never selected together, since `Delete` and
    /// the Details panel mean something different for each.
    private void selectGroup(GroupNode group) {
        selectedNodes.clear();
        selectedTransition = null;
        selectedGroup = group;
    }

    /// Begins dragging `box`: a state carries the whole state selection, a group node itself. Each
    /// carried node is offset from its own captured start position so their relative layout is
    /// preserved.
    private void beginBoxDrag(Box box, int gx, int gy) {
        int[] b = boundsOf(box);

        dragging = box;
        grabDx = gx - b[0];
        grabDy = gy - b[1];
        dragAnchorStartX = b[0];
        dragAnchorStartY = b[1];
        dragCommit = beginTopologyChange();

        dragNodes.clear();
        dragStartPositions.clear();
        for (Box n : dragSubjects(box)) {
            dragNodes.add(n);
            dragStartPositions.add(Pair.of(n.x, n.y));
        }
    }

    /// The nodes a drag on `box` moves: the whole state selection when a state is grabbed, the group
    /// node alone when it is grabbed.
    private List<Box> dragSubjects(Box box) {
        return switch (box) {
            case StateNode _ -> List.copyOf(selectedNodes);
            case GroupNode g -> List.of(g);
        };
    }

    /// Recomputes the marquee selection: the base selection (for a Shift-additive drag) unioned with
    /// every node the box currently covers. Rebuilt each frame so shrinking the box deselects nodes it
    /// no longer encloses.
    private void updateMarqueeSelection() {
        int minX = Math.min(marqueeStartGx, marqueeEndGx);
        int maxX = Math.max(marqueeStartGx, marqueeEndGx);
        int minY = Math.min(marqueeStartGy, marqueeEndGy);
        int maxY = Math.max(marqueeStartGy, marqueeEndGy);

        clearSelection();
        for (StateNode n : marqueeBase) addToSelection(n);
        if (nodeIntersects(entry, minX, minY, maxX, maxY)) addToSelection(entry);
        for (StateNode n : states) {
            if (nodeIntersects(n, minX, minY, maxX, maxY)) addToSelection(n);
        }
    }

    private boolean nodeIntersects(StateNode n, int minX, int minY, int maxX, int maxY) {
        int nx0 = n.x, ny0 = n.y;
        int nx1 = nx0 + nodeWidth(n), ny1 = ny0 + NODE_H;
        return nx0 < maxX && nx1 > minX && ny0 < maxY && ny1 > minY;
    }

    /// Removes every transition entering or leaving `box` (ALT + click on its outer band). A group has
    /// no incoming transitions, so only the ones leaving it go.
    private void disconnectAll(Box box) {
        transitions.removeIf(t -> t.from == box || (box instanceof StateNode s && t.to.equals(s.name)));
        selectedTransition = null;
    }

    private void activate(StateNode node) {
        if (node == entry) return;
        if (onStateActivated != null) onStateActivated.accept(node);
    }

    /// Opens the double-clicked transition's condition sub-tab through the handler its source kind
    /// belongs to — the two are keyed differently, since a group's condition is authored once and
    /// expanded onto its members.
    private void activateTransition(Transition t) {
        switch (t.from) {
            case StateNode s -> {
                if (onTransitionActivated != null) onTransitionActivated.accept(canvasFrom(s), t.to);
            }
            case GroupNode g -> {
                if (onGroupTransitionActivated != null) onGroupTransitionActivated.accept(g.name, t.to);
            }
        }
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (rightDown) {
            panX += (float) dx;
            panY += (float) dy;
            return true;
        }
        if (dragging != null) {
            // Re-derive the grabbed box from the cursor (so the grab point stays fixed at any zoom)
            // and snap it, then shift every carried state by the same delta to keep their layout.
            int targetX = snap(screenToGraphX(event.x()) - grabDx);
            int targetY = snap(screenToGraphY(event.y()) - grabDy);
            int ddx = targetX - dragAnchorStartX;
            int ddy = targetY - dragAnchorStartY;

            for (int i = 0; i < dragNodes.size(); i++) {
                Pair<Integer, Integer> start = dragStartPositions.get(i);
                dragNodes.get(i).x = start.getFirst() + ddx;
                dragNodes.get(i).y = start.getSecond() + ddy;
            }
            return true;
        }
        if (marqueeing) {
            marqueeEndGx = screenToGraphX(event.x());
            marqueeEndGy = screenToGraphY(event.y());
            updateMarqueeSelection();
            return true;
        }
        if (transitionFrom != null) {
            transitionEndX = event.x();
            transitionEndY = event.y();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        boolean handled = handleMouseReleased(event);
        notifySelection();
        return handled;
    }

    private boolean handleMouseReleased(@NonNull MouseButtonEvent event) {
        if (event.button() == 1 && rightDown) {
            boolean moved = Math.abs(event.x() - rightPressX) > 3 || Math.abs(event.y() - rightPressY) > 3;
            rightDown = false;
            if (!moved) openContextMenu((int) event.x(), (int) event.y());
            return true;
        }

        if (dragging != null) {
            dragging = null;
            dragNodes.clear();
            dragStartPositions.clear();

            if (dragCommit != null) dragCommit.run();
            dragCommit = null;

            return true;
        }

        if (marqueeing) {
            marqueeing = false;
            marqueeBase.clear();
            return true;
        }

        if (transitionFrom != null) {
            Box source = transitionFrom;
            transitionFrom = null;

            StateNode target = stateNodeAt(screenToGraphX(event.x()), screenToGraphY(event.y()));
            if (target != null) {
                recordTopology(() -> tryCreateTransition(source, target));
            } else {
                // Dropped on empty canvas → suggest a state to create + connect (UE / Event-Graph style).
                openTransitionTargetMenu(source, (int) event.x(), (int) event.y());
            }
            return true;
        }

        return false;
    }

    /// Adds a `from → to` transition unless it is a self-loop or that exact direction already exists.
    /// Entry behaves like any other source: it may fan out to several states (each Entry transition's
    /// condition gates a candidate initial state — see `PlayerAnimatorScript`), capped only by the
    /// no-duplicate-direction rule. A group edge onto one of its own members is refused for the same
    /// reason a self-loop is: expanded, it would transition that state to itself.
    private void tryCreateTransition(Box from, @Nullable StateNode to) {
        if (to == null || to == entry || to == from) return;
        if (from instanceof GroupNode group && to.groups.contains(group.name)) return;
        if (findTransitionFrom(from, to.name) != null) return;

        transitions.add(new Transition(from, to.name, kind.defaultTransitionPart().get()));
    }

    /// Scroll-wheel zoom, anchored on the graph point under the cursor (mirrors the Event Graph).
    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy == 0.0 || !isMouseOver(mx, my)) return false;

        float oldZoom = zoom;
        float newZoom = Math.clamp(zoom * (sy > 0 ? ZOOM_STEP : 1.0F / ZOOM_STEP), MIN_ZOOM, MAX_ZOOM);
        if (newZoom == oldZoom) return true;

        // Keep the graph point under the cursor fixed on screen across the zoom.
        float graphX = (float) (mx - getX() - panX) / oldZoom;
        float graphY = (float) (my - getY() - panY) / oldZoom;

        panX = (float) (mx - getX()) - graphX * newZoom;
        panY = (float) (my - getY()) - graphY * newZoom;
        zoom = newZoom;

        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        boolean handled = handleKeyPressed(event);
        notifySelection();
        return handled;
    }

    private boolean handleKeyPressed(@NonNull KeyEvent event) {
        if (event.key() != GLFW.GLFW_KEY_DELETE && event.key() != GLFW.GLFW_KEY_BACKSPACE) return false;

        if (selectedTransition == null && selectedGroup == null && selectedNodes.isEmpty()) return false;

        deleteSelection();
        return true;
    }

    private void deleteState(StateNode node) {
        states.remove(node);
        transitions.removeIf(t -> t.from == node || t.to.equals(node.name));
        selectedTransition = null;
    }

    // ***************************************************************
    // Groups
    // ***************************************************************

    private @Nullable GroupNode findGroup(String name) {
        for (GroupNode g : groups) if (g.name.equals(name)) return g;
        return null;
    }

    /// The group names the states of this machine declare, sorted, blanks left out.
    private List<String> declaredGroupNames() {
        return states.stream().flatMap(s -> s.groups.stream()).filter(name -> !name.isBlank()).distinct().sorted().toList();
    }

    /// Places a node for the declared group `name`.
    private void addGroup(String name, int gx, int gy) {
        GroupNode group = new GroupNode(name, snap(gx), snap(gy));
        groups.add(group);
        selectGroup(group);
    }

    /// Drops a group node and the transitions leaving it. The states keep declaring its name.
    private void deleteGroup(GroupNode group) {
        transitions.removeIf(t -> t.from == group);
        groups.remove(group);

        if (selectedGroup == group) selectedGroup = null;
        if (lastNotifiedSelection == group) lastNotifiedSelection = null;
    }

    // ***************************************************************
    // Context menus
    // ***************************************************************

    /// Right-click: **Add State** — a subpage of the signatures this machine can still take — and **Add State
    /// Group** — a subpage of the group names declared on this machine with no node yet. The pick is placed
    /// at the cursor.
    private void openContextMenu(int screenX, int screenY) {
        int gx = screenToGraphX(screenX);
        int gy = screenToGraphY(screenY);

        ContextMenuContainer.PageBuilder page =
            ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(160);

        fillStateEntries(
            page.entry().message(Component.literal("Add State")).subpage().pageWidth(160),
            (name, x, y) -> recordTopology(() -> addState(name, x, y)), gx, gy
        ).done();

        ContextMenuContainer.PageBuilder groupPage =
            page.entry().message(Component.literal("Add State Group")).subpage().pageWidth(160);

        List<String> placeable = declaredGroupNames().stream().filter(name -> findGroup(name) == null).toList();

        for (String name : placeable) {
            groupPage.entry().message(Component.literal(name))
                .onPress(_ -> { recordTopology(() -> addGroup(name, gx, gy)); notifySelection(); }).done();
        }

        if (placeable.isEmpty()) {
            groupPage.entry().message(Component.literal("No state groups declared")).onPress(_ -> {}).done();
        }

        groupPage.done();

        ModalManager.getInstance().openModalWidget(page.build(), screenX, screenY);
    }

    /// Drop a transition on empty canvas → the same signature menu, but the picked state is created
    /// **and** wired as the transition's target (UE / Event-Graph "drag a pin to empty space" flow).
    /// The arrow is pinned to the drop point until this menu closes (see [#transitionPendingSource]).
    private void openTransitionTargetMenu(Box source, int screenX, int screenY) {
        transitionPendingSource = source;
        transitionPendingX = screenX;
        transitionPendingY = screenY;

        ContextMenuContainer.PageBuilder page =
            ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(160);

        fillStateEntries(page, (name, gx, gy) -> recordTopology(() -> tryCreateTransition(source, addState(name, gx, gy))),
            screenToGraphX(screenX), screenToGraphY(screenY));

        ContextMenuContainer menu = page.build();
        ModalManager.getInstance().openModalWidget(menu, screenX, screenY);
        pendingMenu = menu;
    }

    /// Fills `page` with every registered signature of the kind — engine values merged with
    /// editor-created (labs) ones — minus the reserved Entry / Exit and any already on this machine (no
    /// duplicate states). A state is named by its signature and never renamed, so the picked signature
    /// *is* its identity. When every signature is already used the page shows a disabled note.
    /// `action` places the picked state at the given graph-space point.
    private ContextMenuContainer.PageBuilder fillStateEntries(
        ContextMenuContainer.PageBuilder page, StateMenuAction action, int gx, int gy
    ) {
        // Reserved engine states never appear as user-addable.
        Set<Identifier> reserved = Set.of(kind.entry().identifier(), kind.exit().identifier());

        boolean any = false;

        for (Identifier id : AkytheraProject.getCurrentProject().getMergedDataBlockIdentifiers(kind.manager())) {
            if (reserved.contains(id)) continue;
            String name = id.toString();
            if (findState(name) != null) continue; // already on this machine — no duplicates

            any = true;
            page.entry().message(Component.literal(name))
                .onPress(_ -> { action.place(name, gx, gy); notifySelection(); }).done();
        }

        if (!any) {
            page.entry().message(Component.literal("All states added")).onPress(_ -> {}).done();
        }

        return page;
    }

    /// Whether the pinned-arrow's target-state menu is still on the modal stack.
    private boolean menuStillOpen() {
        if (pendingMenu == null) return false;
        for (var modal : ModalManager.getInstance().getModals()) {
            if (modal == pendingMenu) return true;
        }
        return false;
    }

    private void clearTransitionPending() {
        transitionPendingSource = null;
        pendingMenu = null;
    }

    /// Places a picked signature as a state at a graph-space point (see [#fillStateEntries]).
    @FunctionalInterface
    private interface StateMenuAction {
        void place(String name, int gx, int gy);
    }

    /// Places a picked signature as a state.
    private StateNode addState(String name, int gx, int gy) {
        StateNode node = new StateNode(name, snap(gx), snap(gy), kind.defaultStatePart().get());
        states.add(node);
        selectOnly(node);
        return node;
    }

    /// Rounds a graph coordinate to the nearest [#DRAG_SNAP] step (Event-Graph node snapping).
    private static int snap(int graphCoord) {
        return Math.round((float) graphCoord / DRAG_SNAP) * DRAG_SNAP;
    }

    private @Nullable StateNode findState(String name) {
        for (StateNode s : states) if (s.name.equals(name)) return s;
        return null;
    }

    /// The transition whose graph-space line passes within tolerance of the graph-space point, or null.
    private @Nullable Transition transitionAt(int gx, int gy) {
        for (Transition t : transitions) {
            float[] e = transitionEndpoints(t);
            if (e == null) continue;
            if (pointNearSegment(gx, gy, e[0], e[1], e[2], e[3], 5.0)) return t;
        }
        return null;
    }

    private static boolean pointNearSegment(double px, double py, double ax, double ay, double bx, double by, double tol) {
        double dx = bx - ax, dy = by - ay;
        double lenSq = dx * dx + dy * dy;
        double t = lenSq == 0 ? 0 : Math.clamp(((px - ax) * dx + (py - ay) * dy) / lenSq, 0.0, 1.0);
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy)) <= tol;
    }

    /// Anything on the canvas the host builds a Details panel for: a box, or a transition between two
    /// of them.
    public sealed interface Selection permits Box, Transition, NoDetails {}

    /// A selection with no Details rows of its own: Entry alone, or several boxes.
    public enum NoDetails implements Selection {
        INSTANCE
    }

    /// A node on the canvas a transition can leave from and a drag can move: a state (Entry included) or
    /// a group. Public because the host switches on it to build the Details panel.
    public abstract static sealed class Box implements Selection permits StateNode, GroupNode {
        protected String name;
        protected int x;
        protected int y;

        Box(String name, int x, int y) {
            this.name = name;
            this.x = x;
            this.y = y;
        }

        public String name() { return name; }
    }

    /// A state node (Entry is one too: non-deletable; `name` "Entry"). The name is its
    /// signature, re-signed from the Details combo (see
    /// [StateMachineGraphWidget#changeStateSignature]). Owns its part, edited in the state's sub-tab.
    public static final class StateNode extends Box {
        /// What the state runs.
        private StateMachineData.StatePart part;

        /// The names of the groups this state belongs to.
        private List<String> groups = List.of();

        StateNode(String name, int x, int y, StateMachineData.StatePart part) {
            super(name, x, y);
            this.part = part;
        }
    }

    /// A group node, whose outgoing transitions every state declaring its name inherits. The name is
    /// editor-only and unique on the machine: the compile expands the group away, but the name keys its
    /// condition sub-tabs.
    public static final class GroupNode extends Box {
        GroupNode(String name, int x, int y) {
            super(name, x, y);
        }
    }

    /// A directed transition from a box to a state (by name). Owns its `{from} to {to}` boolean
    /// condition sub-graph, edited in the transition's sub-tab; a group's is expanded onto every member
    /// at compile.
    public static final class Transition implements Selection {
        /// The box this transition leaves — held by identity, so re-signing a state
        /// ([StateMachineGraphWidget#changeStateSignature]) or renaming a group leaves it alone.
        private final Box from;

        /// Mutable so re-signing the target state retargets the transition without rebuilding it (its
        /// condition sub-graph is preserved).
        private String to;

        private ScriptGraphData conditionGraph = ScriptGraphData.EMPTY;

        /// The priority this transition is taken at.
        private int transitionPriority = StateMachineData.TransitionData.DEFAULT_TRANSITION_PRIORITY;

        /// How the transition is taken, by the machine's kind.
        private StateMachineData.TransitionPart part;

        Transition(Box from, String to, StateMachineData.TransitionPart part) {
            this.from = from;
            this.to = to;
            this.part = part;
        }
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return Collections.emptyList();
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
    private AnchoredWidget.VerticalAnchorType verticalAnchorType;

    @Override public Unit getX1() { return x1; }
    @Override public Unit getX2() { return x2; }
    @Override public Unit getY1() { return y1; }
    @Override public Unit getY2() { return y2; }
    @Override public void setX1(Unit unit) { x1 = unit; }
    @Override public void setX2(Unit unit) { x2 = unit; }
    @Override public void setY1(Unit unit) { y1 = unit; }
    @Override public void setY2(Unit unit) { y2 = unit; }
    @Override public HorizontalAnchorType getHorizontalAnchorType() { return horizontalAnchorType; }
    @Override public VerticalAnchorType getVerticalAnchorType() { return verticalAnchorType; }
    @Override public void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType) { this.horizontalAnchorType = horizontalAnchorType; }
    @Override public void setVerticalAnchorType(VerticalAnchorType verticalAnchorType) { this.verticalAnchorType = verticalAnchorType; }
}
