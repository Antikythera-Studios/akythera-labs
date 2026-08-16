package com.yesman.akytheralabs.gui.widget.script;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.datafixers.util.Pair;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.animation.driver.FSMAnimationState;
import com.yesman.akythera.api.animation.impl.AkytheraAnimationState;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.animation.ScriptCanvasKeys;
import com.yesman.akytheralabs.gui.widget.ContextMenuContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// The canvas of one animation state machine (UE's state-machine tab): a hardcoded **Entry** node
/// plus user-added **state** nodes, joined by directed **transition** arrows.
///
/// Interaction mirrors UE: a left-drag starting on a node's **inner** body moves it; a left-drag
/// starting on its **outer** edge band draws a transition to whatever node it is released over (a
/// new transition is created only if that exact `from → to` direction does not already exist, and
/// never onto itself). Right-click empty canvas adds a state; right-drag pans; `Delete` removes the
/// selected state (and the transitions touching it). The Entry node can never be deleted.
///
/// This widget owns the topology — the positions, the names and the transition set — and
/// round-trips it through [StateMachineData]. The state pose and transition condition sub-graphs
/// are authored in their own `{state}` / `{from} to {to}` sub-tabs.
public final class StateMachineGraphWidget extends AbstractContainerWidget implements AnchoredWidget {
    private static final int GRID = 24;
    private static final int MAJOR_EVERY = 4;
    /// Outer edge band (graph posX) of a node where a press starts a transition instead of a move.
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

    private static final int COLOR_BG = 0xFF161616;
    private static final int COLOR_GRID_MINOR = 0xFF222222;
    private static final int COLOR_GRID_MAJOR = 0xFF2E2E2E;
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

    /// The sentinel "from" name of an Entry transition (Entry has no [StateNode] name of its own).
    private static final String ENTRY = ScriptCanvasKeys.ENTRY;

    private final Font font;

    /// Entry is a node like any other for hit-testing / moving, but it is non-deletable and is the
    /// source of the Entry transitions (the machine's candidate initial states).
    private final StateNode entry;
    private final List<StateNode> states = new ArrayList<>();
    private final List<Transition> transitions = new ArrayList<>();

    /// The selected states (drawn with the orange frame), subjects of group move / delete. A plain
    /// click selects just one; Shift+click toggles one; a marquee selects everything it covers.
    private final List<StateNode> selectedNodes = new ArrayList<>();

    /// The selected transition (its line drawn highlighted; `Delete` removes it). Mutually exclusive
    /// with [#selectedNodes].
    @Nullable
    private Transition selectedTransition;

    /// Fired when a state node is double-clicked (open its `{state}` pose sub-tab) — wired in 3b.
    @Nullable
    private Consumer<StateNode> onStateActivated;

    /// Fired when a transition is double-clicked (open its `{from} to {to}` condition sub-tab) — 3b.
    @Nullable
    private BiConsumer<String, String> onTransitionActivated;

    /// Fired when the single-state selection changes, with the newly selected state — or null when
    /// the selection is empty, is Entry, or covers several states.
    @Nullable
    private Consumer<StateNode> onStateSelected;

    /// The state last handed to [#onStateSelected], so repeated notifications for an unchanged
    /// selection are swallowed (the handler rebuilds the Details panel, so it must only fire on a
    /// genuine change).
    @Nullable
    private StateNode lastNotifiedSelection;

    private float panX = 40.0F;
    private float panY = 40.0F;
    /// Scroll-wheel zoom; nodes / transitions are drawn under a `translate(pan) + scale(zoom)` pose.
    private float zoom = 1.0F;

    // Pan (right-drag) tracking.
    private boolean rightDown;
    private double rightPressX;
    private double rightPressY;

    // Node move tracking. `dragging` is the grabbed anchor; the whole selection moves with it, each
    // node offset from its own captured start position so the relative layout is preserved.
    @Nullable private StateNode dragging;
    private int grabDx;
    private int grabDy;
    private int dragAnchorStartX;
    private int dragAnchorStartY;
    private final List<StateNode> dragNodes = new ArrayList<>();
    private final List<Pair<Integer, Integer>> dragStartPositions = new ArrayList<>();

    // Marquee (rubber-band) selection in graph space. `marqueeBase` is the selection kept when Shift
    // makes the drag additive (empty otherwise), unioned each frame with whatever the box covers.
    private boolean marqueeing;
    private final List<StateNode> marqueeBase = new ArrayList<>();
    private int marqueeStartGx;
    private int marqueeStartGy;
    private int marqueeEndGx;
    private int marqueeEndGy;

    // Transition-drag tracking.
    @Nullable private StateNode transitionFrom;
    private double transitionEndX;
    private double transitionEndY;

    // Pending-transition tracking: after a transition is dropped on empty canvas, the arrow stays
    // pinned to the drop point while the target-state menu is open (mirrors the Event Graph's pending
    // wire). Cleared once that menu leaves the modal stack — whether by a pick or a dismiss.
    @Nullable private StateNode transitionPendingSource;
    private double transitionPendingX;
    private double transitionPendingY;
    @Nullable private ContextMenuContainer pendingMenu;

    public StateMachineGraphWidget(
        Font font,
        StateMachineData data,
        Unit x1, Unit x2, Unit y1, Unit y2,
        AnchoredWidget.HorizontalAnchorType horizontalAnchorType,
        AnchoredWidget.VerticalAnchorType verticalAnchorType
    ) {
        super(0, 0, 0, 0, Component.empty(), AbstractScrollArea.defaultSettings(0));

        this.font = font;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchorType;
        this.verticalAnchorType = verticalAnchorType;

        this.entry = new StateNode("Entry", data.entryX(), data.entryY());
        importData(data);
    }

    /// Ids of the states the possessed entity's machines are in, as
    /// [com.yesman.akytheralabs.editor.debug.AnimatorProbe#activeStateIds] reports them. A state node
    /// named by one of these draws the debug ring. Empty when nothing is being debugged.
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

    public void setOnStateSelected(@Nullable Consumer<StateNode> handler) {
        this.onStateSelected = handler;
    }

    /// Fires [#onStateSelected] when — and only when — the single-state selection has actually changed
    /// since the last notification. The reported state is the sole selected node (never Entry); any
    /// other selection shape (empty, multi, a transition) reports null. Called after every gesture that
    /// can move the selection.
    private void notifySelection() {
        StateNode single = selectedNodes.size() == 1 && selectedNodes.get(0) != entry
            ? selectedNodes.get(0) : null;
        if (single == lastNotifiedSelection) return;
        lastNotifiedSelection = single;
        if (onStateSelected != null) onStateSelected.accept(single);
    }

    /// The [FSMAnimationState] signature (id string) of every state currently on this machine — the
    /// set the host's Details combo strips from its options so a state can never be re-signed to a
    /// signature another state already carries (no duplicate states on one machine).
    public Set<String> stateNames() {
        Set<String> names = new java.util.HashSet<>();
        for (StateNode s : states) names.add(s.name);
        return names;
    }

    /// Re-signs `node` to the [FSMAnimationState] `newName`, retargeting every transition that touched
    /// its old signature so the topology (and each transition's condition sub-graph) survives the
    /// change; the state's own pose sub-graph travels with the node. Returns false — rejecting the edit
    /// — when `newName` is blank or already carried by another state on this machine.
    public boolean changeStateSignature(StateNode node, String newName) {
        String oldName = node.name;
        if (newName.equals(oldName)) return true;
        if (newName.isEmpty()) return false;
        if (findState(newName) != null) return false; // already on this machine — no duplicates

        for (Transition t : transitions) {
            if (t.from.equals(oldName)) t.from = newName;
            if (t.to.equals(oldName)) t.to = newName;
        }
        node.name = newName;
        if (node == lastNotifiedSelection) lastNotifiedSelection = null; // force a fresh Details rebuild
        return true;
    }

    /// ***************************************************************
    /// Persistence
    /// ***************************************************************
    private void importData(StateMachineData data) {
        states.clear();
        transitions.clear();
        selectedNodes.clear();
        selectedTransition = null;

        for (StateMachineData.StateData s : data.states()) {
            String name = nameOf(s.state());
            StateNode node = new StateNode(name, s.x(), s.y());
            node.poseGraph = s.poseGraph();
            states.add(node);

            for (StateMachineData.TransitionData t : s.transitions()) {
                Transition transition = new Transition(name, nameOf(t.to()));
                transition.conditionGraph = t.conditionGraph();
                transitions.add(transition);
            }
        }

        for (StateMachineData.TransitionData t : data.entryTransitions()) {
            Transition transition = new Transition(ENTRY, nameOf(t.to()));
            transition.conditionGraph = t.conditionGraph();
            transitions.add(transition);
        }
    }

    /// Display/identity name of a state holder — its key id, which exists even for a dangling
    /// (unresolved) holder; only a keyless engine direct holder reads the live value.
    private static String nameOf(Holder<FSMAnimationState> holder) {
        return ScriptCanvasKeys.stateName(holder);
    }

    /// Flattens the live topology — and each state's pose sub-graph / each transition's condition
    /// sub-graph — back to a builder-shaped [StateMachineData]: each transition nested under the state
    /// it leaves, Entry's outgoing edges collected into `entryTransitions`. State names round-trip
    /// through their [FSMAnimationState] signature; an unresolved signature persists as a dangling
    /// holder (the red MISSING state round-trips instead of being silently deleted) — only an
    /// unparseable name (cannot happen for names created from ids) is dropped.
    public StateMachineData exportData() {
        List<StateMachineData.StateData> stateData = new ArrayList<>();
        for (StateNode s : states) {
            Holder<FSMAnimationState> signature = holderOf(s.name);
            if (signature == null) continue;

            List<StateMachineData.TransitionData> outgoing = new ArrayList<>();
            for (Transition t : transitions) {
                if (!t.from.equals(s.name)) continue;
                Holder<FSMAnimationState> to = holderOf(t.to);
                if (to != null) outgoing.add(new StateMachineData.TransitionData(to, t.conditionGraph));
            }
            stateData.add(new StateMachineData.StateData(signature, s.x, s.y, s.poseGraph, outgoing));
        }

        List<StateMachineData.TransitionData> entryTransitions = new ArrayList<>();
        for (Transition t : transitions) {
            if (!t.from.equals(ENTRY)) continue;
            Holder<FSMAnimationState> to = holderOf(t.to);
            if (to != null) entryTransitions.add(new StateMachineData.TransitionData(to, t.conditionGraph));
        }

        return new StateMachineData(entry.x, entry.y, entryTransitions, stateData);
    }

    /// The live [Holder] for a state name (its `identifier().toString()`), resolved through
    /// project &cup; manager (so editor-created blocks resolve); an id in neither mints a dangling
    /// holder so the unresolved state/transition still round-trips to disk. Only an unparseable
    /// name (cannot happen for names created from ids) yields null, now logged.
    private static @Nullable Holder<FSMAnimationState> holderOf(String name) {
        Identifier id = Identifier.tryParse(name);
        if (id == null) {
            AkytheraLabs.LOGGER.warn("Dropping state-machine element with unparseable name {}", name);
            return null;
        }
        return AkytheraProject.getCurrentProject()
            .getDataBlockHolderOrDangling(FSMAnimationState.ENUM_MANAGER, id);
    }

    /// The pose sub-graph stored for state `name` ([ScriptGraphData#EMPTY] if unset / unknown), and
    /// its setter — the host's `{state}` pose sub-tab reads / writes these.
    public ScriptGraphData getStatePoseGraph(String name) {
        StateNode s = findState(name);
        return s == null ? ScriptGraphData.EMPTY : s.poseGraph;
    }

    public void setStatePoseGraph(String name, ScriptGraphData graph) {
        StateNode s = findState(name);
        if (s != null) s.poseGraph = graph;
    }

    /// The condition sub-graph stored for transition `from → to` ([ScriptGraphData#EMPTY] if unset /
    /// unknown), and its setter — the host's `{from} to {to}` condition sub-tab reads / writes these.
    public ScriptGraphData getTransitionCondition(String from, String to) {
        Transition t = findTransition(from, to);
        return t == null ? ScriptGraphData.EMPTY : t.conditionGraph;
    }

    public void setTransitionCondition(String from, String to, ScriptGraphData graph) {
        Transition t = findTransition(from, to);
        if (t != null) t.conditionGraph = graph;
    }

    private @Nullable Transition findTransition(String from, String to) {
        for (Transition t : transitions) if (t.from.equals(from) && t.to.equals(to)) return t;
        return null;
    }

    /// ***************************************************************
    /// Geometry
    /// ***************************************************************
    private int nodeWidth(StateNode n) {
        return Math.max(NODE_MIN_W, font.width(n.name) + NODE_PAD_X * 2);
    }

    private int screenToGraphX(double sx) { return (int) ((sx - getX() - panX) / zoom); }
    private int screenToGraphY(double sy) { return (int) ((sy - getY() - panY) / zoom); }

    private boolean inNode(StateNode n, int gx, int gy) {
        return gx >= n.x && gx < n.x + nodeWidth(n) && gy >= n.y && gy < n.y + NODE_H;
    }

    /// Whether `(gx, gy)` is in the node's outer edge band (a transition-drag start) vs. its inner
    /// body (a move).
    private boolean inEdgeBand(StateNode n, int gx, int gy) {
        if (!inNode(n, gx, gy)) return false;
        int w = nodeWidth(n);
        return gx < n.x + EDGE_BAND || gx >= n.x + w - EDGE_BAND
            || gy < n.y + EDGE_BAND || gy >= n.y + NODE_H - EDGE_BAND;
    }

    /// The topmost node (states above Entry) under a graph point, or null.
    private @Nullable StateNode nodeAt(int gx, int gy) {
        for (int i = states.size() - 1; i >= 0; i--) {
            if (inNode(states.get(i), gx, gy)) return states.get(i);
        }
        return inNode(entry, gx, gy) ? entry : null;
    }

    /// ***************************************************************
    /// Rendering
    /// ***************************************************************
    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(getX(), getY(), getRight(), getBottom(), COLOR_BG);
        g.enableScissor(getX(), getY(), getRight(), getBottom());

        // The pinned arrow lives only while its target-state menu is open; drop it once that menu is
        // gone (a pick spawned + wired the state, a dismiss left the topology unchanged).
        if (transitionPendingSource != null && !menuStillOpen()) clearTransitionPending();

        drawGrid(g);

        // Graph-space cursor for hover highlighting — off-canvas (no node) when the mouse is outside
        // this widget, so a node / transition never falsely highlights.
        boolean over = isMouseOver(mouseX, mouseY);
        int gmx = over ? screenToGraphX(mouseX) : Integer.MIN_VALUE;
        int gmy = over ? screenToGraphY(mouseY) : Integer.MIN_VALUE;

        // The hovered transition (only when idle and the cursor isn't over a node). Hit-tested in
        // graph space against the (already zoom-corrected) graph-space cursor.
        Transition hovered = (over && dragging == null && transitionFrom == null && !marqueeing && nodeAt(gmx, gmy) == null)
            ? transitionAt(gmx, gmy) : null;

        // Nodes / transitions live in graph space; a translate + scale pose draws them at their graph
        // coordinates so they pan and zoom together (mirrors the Event Graph). The grid is screen-space.
        g.pose().pushMatrix();
        g.pose().translate(getX() + panX, getY() + panY);
        g.pose().scale(zoom, zoom);

        for (Transition t : transitions) {
            int color = t == selectedTransition ? COLOR_TRANSITION_SELECTED
                : (t == hovered ? COLOR_TRANSITION_HOVER : COLOR_TRANSITION);
            drawTransition(g, t, color);
        }

        // The in-flight transition arrow: tracking the cursor while dragging, then pinned to the drop
        // point while the target-state menu is open.
        if (transitionFrom != null) {
            drawTransitionPreview(g, transitionFrom, transitionEndX, transitionEndY);
        } else if (transitionPendingSource != null) {
            drawTransitionPreview(g, transitionPendingSource, transitionPendingX, transitionPendingY);
        }

        drawNode(g, entry, COLOR_ENTRY_BODY, gmx, gmy);
        for (StateNode s : states) drawNode(g, s, COLOR_STATE_BODY, gmx, gmy);

        if (marqueeing) drawMarquee(g);

        g.pose().popMatrix();

        // Hover cursor when no gesture owns it: the 4-way move cross over a node's inner body, the
        // pointing hand over its outer edge band (the transition-drag grab zone) — mirrors the Event Graph.
        if (over && dragging == null && transitionFrom == null && transitionPendingSource == null
            && !marqueeing && !rightDown) {
            requestHoverCursor(g, gmx, gmy);
        }

        g.disableScissor();
    }

    /// Requests the move (4-directional) cursor over the inner body of the topmost node under
    /// `(gx, gy)`, or the pointing hand over its outer edge band; leaves the default arrow on empty
    /// canvas.
    private void requestHoverCursor(GuiGraphicsExtractor g, int gx, int gy) {
        StateNode node = nodeAt(gx, gy);
        if (node == null) return;
        g.requestCursor(inEdgeBand(node, gx, gy) ? CursorTypes.POINTING_HAND : CursorTypes.RESIZE_ALL);
    }

    /// Draws a node as a two-region box: the outer frame (transition-drag band) around the inner body
    /// (move handle). The frame turns orange when selected and brightens while the cursor hovers it
    /// (but not its inner body), advertising that a drag there starts a transition.
    private void drawNode(GuiGraphicsExtractor g, StateNode n, int bodyColor, int gmx, int gmy) {
        int w = nodeWidth(n);
        int sx = n.x;
        int sy = n.y;

        boolean selected = selectedNodes.contains(n);
        boolean edgeHover = dragging == null && transitionFrom == null && inEdgeBand(n, gmx, gmy);
        // While dragging a transition, the node it would drop onto (any state but the source / Entry)
        // highlights its frame as the valid target.
        boolean dropTarget = transitionFrom != null && n != transitionFrom && n != entry && inNode(n, gmx, gmy);
        int outer = selected ? COLOR_OUTER_SELECTED : (edgeHover || dropTarget ? COLOR_OUTER_HOVER : COLOR_OUTER);

        // A state whose signature no longer resolves gets the shared node error marking: red
        // outline + MISSING banner (checked live, so creating the block clears it next frame).
        boolean unresolved = isUnresolved(n);

        // Outer frame, then the inner body inset by the edge band.
        g.fill(sx, sy, sx + w, sy + NODE_H, outer);
        g.fill(sx + EDGE_BAND, sy + EDGE_BAND, sx + w - EDGE_BAND, sy + NODE_H - EDGE_BAND, bodyColor);
        g.outline(sx, sy, w, NODE_H, unresolved ? AbstractNodeWidget.COLOR_ERROR_OUTLINE : COLOR_OUTLINE);
        if (unresolved) AbstractNodeWidget.drawErrorBanner(g, font, sx, sy + NODE_H, w, "MISSING!");

        // Debug ring, hung outside the frame so it stacks with the selection colour instead of
        // replacing it.
        if (activeStateIds.contains(n.name)) {
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
        Holder<FSMAnimationState> h = holderOf(n.name);
        return h == null || !h.isBound();
    }

    /// Draws the in-flight transition arrow (graph space): from `source`'s center to a graph-space
    /// point converted from the given screen-space end. Shared by the live drag and the pinned
    /// (menu-open) state.
    private void drawTransitionPreview(GuiGraphicsExtractor g, StateNode source, double endScreenX, double endScreenY) {
        float sx = source.x + nodeWidth(source) / 2.0F;
        float sy = source.y + NODE_H / 2.0F;
        float ex = screenToGraphX(endScreenX);
        float ey = screenToGraphY(endScreenY);
        double ang = Math.atan2(ey - sy, ex - sx);
        drawLine(g, sx, sy, ex, ey, COLOR_TRANSITION);
        drawArrowHead(g, ex, ey, ang, COLOR_TRANSITION);
    }

    /// Draws a transition as a line between the two states' **edges** with a filled-triangle arrowhead
    /// sitting on the target state's border (UE-style), in `color`.
    private void drawTransition(GuiGraphicsExtractor g, Transition t, int color) {
        float[] e = transitionEndpoints(t);
        if (e == null) return;

        drawLine(g, e[0], e[1], e[2], e[3], color);
        double ang = Math.atan2(e[3] - e[1], e[2] - e[0]);
        drawArrowHead(g, e[2], e[3], ang, color);
    }

    /// The graph-space endpoints `{fromX, fromY, toX, toY}` of a transition: the points where the
    /// center-to-center line meets each state's rectangle border (so the line touches the boxes rather
    /// than their centers), perpendicular-offset when a reverse transition exists so the twin arrows
    /// don't overlap. Null when an endpoint state is missing. Drawn / hit-tested under the graph-space
    /// pose, so they pan and zoom with the nodes.
    private float @Nullable [] transitionEndpoints(Transition t) {
        StateNode from = t.from.equals(ENTRY) ? entry : findState(t.from);
        StateNode to = findState(t.to);
        if (from == null || to == null) return null;

        float fcx = from.x + nodeWidth(from) / 2.0F;
        float fcy = from.y + NODE_H / 2.0F;
        float tcx = to.x + nodeWidth(to) / 2.0F;
        float tcy = to.y + NODE_H / 2.0F;

        float[] fb = borderPoint(from, tcx, tcy);
        float[] tb = borderPoint(to, fcx, fcy);
        float fx = fb[0], fy = fb[1], tx = tb[0], ty = tb[1];

        boolean hasReverse = transitions.stream().anyMatch(o -> o.from.equals(t.to) && o.to.equals(t.from));
        if (hasReverse) {
            double dx = tx - fx, dy = ty - fy;
            double len = Math.max(1.0, Math.hypot(dx, dy));
            float ox = (float) (-dy / len * 5.0);
            float oy = (float) (dx / len * 5.0);
            fx += ox; fy += oy; tx += ox; ty += oy;
        }
        return new float[]{fx, fy, tx, ty};
    }

    /// The point on `n`'s graph-space rectangle border along the ray from its center toward
    /// `(towardX, towardY)`.
    private float[] borderPoint(StateNode n, float towardX, float towardY) {
        float w = nodeWidth(n);
        float cx = n.x + w / 2.0F;
        float cy = n.y + NODE_H / 2.0F;
        float dx = towardX - cx, dy = towardY - cy;
        if (dx == 0.0F && dy == 0.0F) return new float[]{cx, cy};

        float hw = w / 2.0F, hh = NODE_H / 2.0F;
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

    /// Screen-space grid: line spacing scales with zoom (so cells grow / shrink), line thickness
    /// stays 1px. Skipped once the cells are too dense to read (mirrors the Event Graph).
    private void drawGrid(GuiGraphicsExtractor g) {
        float step = GRID * zoom;
        if (step < 6.0F) return;

        float originX = getX() + panX;
        float originY = getY() + panY;

        int kx = (int) Math.ceil((getX() - originX) / step);
        for (float sx = originX + kx * step; sx < getRight(); sx += step, kx++) {
            g.fill((int) sx, getY(), (int) sx + 1, getBottom(),
                Math.floorMod(kx, MAJOR_EVERY) == 0 ? COLOR_GRID_MAJOR : COLOR_GRID_MINOR);
        }
        int ky = (int) Math.ceil((getY() - originY) / step);
        for (float sy = originY + ky * step; sy < getBottom(); sy += step, ky++) {
            g.fill(getX(), (int) sy, getRight(), (int) sy + 1,
                Math.floorMod(ky, MAJOR_EVERY) == 0 ? COLOR_GRID_MAJOR : COLOR_GRID_MINOR);
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

    /// ***************************************************************
    /// Input
    /// ***************************************************************
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

        if (event.button() == 1) {
            rightDown = true;
            rightPressX = event.x();
            rightPressY = event.y();
            return true;
        }

        if (event.button() != 0) return true;

        StateNode hit = nodeAt(gx, gy);
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
                disconnectAll(hit);
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
                beginNodeDrag(hit, gx, gy);
            }
            return true;
        }

        // Not over a node: a press on a transition selects it; a double-click opens its condition
        // sub-tab (Entry transitions included — their condition gates the initial state).
        Transition t = transitionAt(gx, gy);
        if (t != null) {
            clearSelection();
            selectedTransition = t;
            if (doubleClick && onTransitionActivated != null) onTransitionActivated.accept(t.from, t.to);
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
    }

    private void addToSelection(StateNode node) {
        if (!selectedNodes.contains(node)) selectedNodes.add(node);
    }

    /// Flips `node`'s membership in the selection (Shift+click): drops it when already selected, adds
    /// it otherwise.
    private void toggleSelection(StateNode node) {
        if (!selectedNodes.remove(node)) addToSelection(node);
    }

    private void selectOnly(StateNode node) {
        clearSelection();
        addToSelection(node);
    }

    /// Begins dragging `node`: the whole selection moves with it, each node offset from its own
    /// captured start position so their relative layout is preserved.
    private void beginNodeDrag(StateNode node, int gx, int gy) {
        dragging = node;
        grabDx = gx - node.x;
        grabDy = gy - node.y;
        dragAnchorStartX = node.x;
        dragAnchorStartY = node.y;

        dragNodes.clear();
        dragStartPositions.clear();
        for (StateNode n : selectedNodes) {
            dragNodes.add(n);
            dragStartPositions.add(Pair.of(n.x, n.y));
        }
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

    /// Removes every transition entering or leaving `node` (ALT + click on its outline).
    private void disconnectAll(StateNode node) {
        String name = node == entry ? ENTRY : node.name;
        transitions.removeIf(t -> t.from.equals(name) || t.to.equals(name));
        selectedTransition = null;
    }

    private void activate(StateNode node) {
        if (node == entry) return;
        if (onStateActivated != null) onStateActivated.accept(node);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (rightDown) {
            panX += (float) dx;
            panY += (float) dy;
            return true;
        }
        if (dragging != null) {
            // Re-derive the grabbed node from the cursor (so the grab point stays fixed at any zoom)
            // and snap it, then shift every selected node by the same delta to keep their layout.
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
            return true;
        }

        if (marqueeing) {
            marqueeing = false;
            marqueeBase.clear();
            return true;
        }

        if (transitionFrom != null) {
            StateNode source = transitionFrom;
            transitionFrom = null;

            StateNode target = nodeAt(screenToGraphX(event.x()), screenToGraphY(event.y()));
            if (target != null) {
                tryCreateTransition(source, target);
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
    /// no-duplicate-direction rule.
    private void tryCreateTransition(StateNode from, @Nullable StateNode to) {
        if (to == null || to == entry || from == to) return;

        String fromName = from == entry ? ENTRY : from.name;
        boolean dup = transitions.stream().anyMatch(t -> t.from.equals(fromName) && t.to.equals(to.name));
        if (dup) return;

        transitions.add(new Transition(fromName, to.name));
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

        if (selectedTransition != null) {
            transitions.remove(selectedTransition);
            selectedTransition = null;
            return true;
        }
        if (!selectedNodes.isEmpty()) {
            // Delete every selected state (Entry is non-deletable, so it is skipped) and the
            // transitions touching them.
            for (StateNode n : List.copyOf(selectedNodes)) {
                if (n != entry) deleteState(n);
            }
            clearSelection();
            return true;
        }
        return false;
    }

    private void deleteState(StateNode node) {
        states.remove(node);
        transitions.removeIf(t -> t.from.equals(node.name) || t.to.equals(node.name));
        selectedTransition = null;
    }

    /// Right-click → "Add State": a menu of the available [FSMAnimationState] signatures; the picked
    /// one becomes a new state at the cursor.
    private void openContextMenu(int screenX, int screenY) {
        openStateMenu(screenX, screenY, this::addState);
    }

    /// Drop a transition on empty canvas → the same signature menu, but the picked state is created
    /// **and** wired as the transition's target (UE / Event-Graph "drag a pin to empty space" flow).
    /// The arrow is pinned to the drop point until this menu closes (see [#transitionPendingSource]).
    private void openTransitionTargetMenu(StateNode source, int screenX, int screenY) {
        transitionPendingSource = source;
        transitionPendingX = screenX;
        transitionPendingY = screenY;
        pendingMenu = openStateMenu(screenX, screenY, (name, gx, gy) -> tryCreateTransition(source, addState(name, gx, gy)));
    }

    /// Whether the pinned-arrow's target-state menu is still on the modal stack.
    private boolean menuStillOpen() {
        if (pendingMenu == null) return false;
        for (var modal : AkytheraEditorLayoutManager.getInstance().getModals()) {
            if (modal == pendingMenu) return true;
        }
        return false;
    }

    private void clearTransitionPending() {
        transitionPendingSource = null;
        pendingMenu = null;
    }

    /// A menu of every registered [FSMAnimationState] signature, minus the reserved Entry / Exit and
    /// any already on this machine (no duplicate states). A state is named by its signature and never
    /// renamed, so the picked signature *is* its identity. When every signature is already used the
    /// menu shows a disabled note. `action` places the picked state at the (graph-space) cursor.
    private ContextMenuContainer openStateMenu(int screenX, int screenY, StateMenuAction action) {
        int gx = screenToGraphX(screenX);
        int gy = screenToGraphY(screenY);

        ContextMenuContainer.PageBuilder builder = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(160);
        boolean any = false;

        // Reserved engine states never appear as user-addable.
        Set<Identifier> reserved = Set.of(
            AkytheraAnimationState.ENTRY.identifier(),
            AkytheraAnimationState.EXIT.identifier());

        // Merge engine values with editor-created (labs) states (split registry).
        for (Identifier id : AkytheraProject.getCurrentProject()
                .getMergedDataBlockIdentifiers(FSMAnimationState.ENUM_MANAGER)) {
            if (reserved.contains(id)) continue;
            String name = id.toString();
            if (findState(name) != null) continue; // already on this machine — no duplicates

            any = true;
            builder.entry().message(Component.literal(name)).onPress(_ -> action.place(name, gx, gy)).done();
        }

        if (!any) {
            builder.entry().message(Component.literal("All states added")).onPress(_ -> {}).done();
        }

        ContextMenuContainer menu = builder.build();
        AkytheraEditorLayoutManager.getInstance().openModalWidget(menu, screenX, screenY);
        return menu;
    }

    /// Places a picked signature as a state at a graph-space point (see [#openStateMenu]).
    @FunctionalInterface
    private interface StateMenuAction {
        void place(String name, int gx, int gy);
    }

    private StateNode addState(String name, int gx, int gy) {
        StateNode node = new StateNode(name, snap(gx), snap(gy));
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

    /// A movable state box in graph space (Entry is one too: non-deletable; `name` "Entry"). Owns its
    /// `{state}` output-pose sub-graph, edited in the state's sub-tab.
    public static final class StateNode {
        /// Mutable so a state can be re-signed to a different [FSMAnimationState] from the Details combo
        /// (see [StateMachineGraphWidget#changeStateSignature]); the name is still the state's identity.
        private String name;
        private int x;
        private int y;
        private ScriptGraphData poseGraph = ScriptGraphData.EMPTY;

        StateNode(String name, int x, int y) {
            this.name = name;
            this.x = x;
            this.y = y;
        }

        public String name() { return name; }
    }

    /// A directed transition by state name (`from` is [#ENTRY] for the Entry transition). Owns its
    /// `{from} to {to}` boolean condition sub-graph, edited in the transition's sub-tab.
    private static final class Transition {
        /// Endpoints are mutable so re-signing a state ([StateMachineGraphWidget#changeStateSignature])
        /// can retarget the transitions touching it without rebuilding them (their condition sub-graph
        /// is preserved).
        private String from;
        private String to;
        private ScriptGraphData conditionGraph = ScriptGraphData.EMPTY;

        Transition(String from, String to) {
            this.from = from;
            this.to = to;
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

    /// ***************************************************************
    /// [AnchoredWidget] implementations
    /// ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private final AnchoredWidget.HorizontalAnchorType horizontalAnchorType;
    private final AnchoredWidget.VerticalAnchorType verticalAnchorType;

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
}
