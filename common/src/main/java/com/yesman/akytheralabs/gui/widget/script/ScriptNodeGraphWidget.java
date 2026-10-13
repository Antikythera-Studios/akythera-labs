package com.yesman.akytheralabs.gui.widget.script;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.datafixers.util.Pair;
import com.yesman.akythera.api.data.modularize.AkytheraScriptFunctions;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.CallStack;
import com.yesman.akythera.api.data.modularize.node.ExecutionRecord;
import com.yesman.akythera.api.data.modularize.node.FunctionNode;
import com.yesman.akythera.api.data.modularize.node.ScriptFailure;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.client.api.gui.ContextMenuContainer;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akythera.mixinaccessor.client.GuiGraphicsAccessor;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.akythera.animation.ScriptGraphCompiler;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintMembers;
import com.yesman.akytheralabs.config.AkytheraLabsConfig;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.CanvasGrid;
import com.yesman.akytheralabs.gui.widget.EmbeddedWidgetHost;
import com.yesman.akytheralabs.gui.widget.NodeSuggestModal;
import com.yesman.akytheralabs.gui.widget.script.node.FlowFunctionNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.GraphNode;
import com.yesman.akytheralabs.gui.widget.script.node.NodeBuildContext;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.PureFunctionNodeData;
import com.yesman.akytheralabs.mixinaccessor.GuiGraphicsExtractorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/// The blueprint canvas: a grid background and a set of [AbstractNodeWidget]s that
/// pan (left-drag) and zoom (scroll) independently of the editor's outer GUI scale.
///
/// Nodes live in graph space; rendering pushes a `translate(origin) + scale(zoom)`
/// pose so nodes draw at their graph coordinates. The grid is drawn in screen space
/// with its spacing multiplied by `zoom`, so lines stay crisp while the cells grow
/// and shrink with the zoom level (mirroring UE's blueprint grid). Input handed to a node's body
/// widgets runs inside [ModalManager#inWidgetSpace], so a modal they open lands on them.
///
/// Every change the canvas records is scoped to it, and so are its nodes' edits (see
/// [AbstractNodeWidget#historyScope]), so closing the canvas' tab drops them all.
public final class ScriptNodeGraphWidget extends AbstractContainerWidget implements AnchoredWidget, EmbeddedWidgetHost {
    private static final float MIN_ZOOM = 0.30F;
    private static final float MAX_ZOOM = 3.00F;
    private static final float ZOOM_STEP = 1.10F;
    /// How far below a whole zoom a step may land and still stop on it.
    private static final float WHOLE_ZOOM_EPSILON = 0.001F;
    private static final int DRAG_SNAP = 5;
    /// Screen-pixel slop a right-press may move before it counts as a pan instead of a click.
    private static final int PAN_THRESHOLD = 3;
    /// Thinnest half width a wire's solid core draws at, in GUI pixels.
    private static final float WIRE_MIN_HALF_WIDTH = 0.5F;

    private static final int COLOR_WIRE = 0xFFFFFFFF;
    private static final int COLOR_PIN_COMPATIBLE = 0xFF59FF8C;
    private static final int COLOR_MARQUEE_FILL = 0x22FFFFFF;
    private static final int COLOR_MARQUEE_BORDER = 0xFFCFCFCF;

    /// Weight multiplier of an execution wire the captured run went down.
    private static final float EXECUTED_WIRE_WEIGHT = 2.5F;

    /// Written for a recorded pin nothing fed a value into in the captured run.
    private static final String RECORD_NO_VALUE = "—";

    /// Decimal places a recorded floating-point value keeps.
    private static final int RECORD_DECIMALS = 4;

    /// How far [#inputSourceFrame] follows a chain of reroute knots before giving up.
    private static final int REROUTE_HOP_LIMIT = 32;

    /// A wire between an output pin of one node and an input pin of another. Pin
    /// indices are resolved against the live nodes each frame, so a connection stays
    /// attached as its endpoints move.
    private record Connection(
        AbstractNodeWidget fromNode, int fromPin,
        AbstractNodeWidget toNode, int toPin
    ) {}

    private final Font font;
    private final List<AbstractNodeWidget> nodes = new ArrayList<>();
    private final List<Connection> connections = new ArrayList<>();

    /// Supplies the node suggestions shown when a wire is dropped on empty canvas, keyed
    /// on the drag [NodeSuggestContext] so an execution drag offers flow nodes and a data
    /// drag offers value providers. Injected by the host screen, which knows the available
    /// functions / variables. The source graph is passed too, so graph-local suggestions (e.g. the
    /// cached poses defined on *this* canvas) can be offered.
    @Nullable
    private BiFunction<ScriptNodeGraphWidget, NodeSuggestContext, List<NodeSuggestModal.Suggestion<NodeWidgetFactory>>> suggestionProvider;

    /// The script owner type (e.g. the animator) whose available actions are offered when the graph is
    /// right-clicked on empty canvas. Injected by the host screen, which knows the concrete
    /// [DataDrivenScript] being edited.
    @Nullable
    private Class<?> scriptOwnerType;

    /// The [ScriptFunction.Usage]s this canvas accepts — stamped onto every [NodeSuggestContext]
    /// before the suggestion provider runs (UE's per-graph palette). A canvas may accept several: the
    /// Event Graph is `SCRIPT`-only, while the Anim Graph / pose graphs are `{ANIM_GRAPH, SCRIPT}` so
    /// they offer pose nodes **and** script pure functions (e.g. float math feeding a BlendSpace's
    /// X/Y). Defaults to `{SCRIPT}`.
    private Set<ScriptFunction.Usage> usage = EnumSet.of(ScriptFunction.Usage.SCRIPT);

    /// Whether this canvas is pure data-flow (no execution pins) — the Anim Graph and the
    /// state/transition sub-graphs. Suppresses the execution-flow constructs in the suggestion menu.
    private boolean dataFlowOnly;

    /// The members of the blueprint this canvas belongs to, which shape its entry, call, parent and return
    /// nodes as they rebuild.
    private BlueprintMembers members = BlueprintMembers.NONE;

    /// The method or function this canvas implements, or null for a canvas that implements none.
    @Nullable
    private String enclosingFunction;

    /// Invoked when an [AbstractNodeWidget#isActivatable] node is double-clicked (e.g. a
    /// [StateMachineNodeWidget] → open its sub-tab). Injected by the host screen.
    @Nullable
    private Consumer<AbstractNodeWidget> nodeActivationHandler;

    /// Invoked with the selected nodes whenever the selection settles, and again on every press of a node
    /// that was selected already.
    @Nullable
    private Consumer<List<AbstractNodeWidget>> selectionHandler;

    /// Screen-space offset of graph origin (graph 0,0) relative to the widget's top-left.
    private float panX = 40.0F;
    private float panY = 40.0F;
    private float zoom = 1.0F;

    /// Right-button press tracking. The right button now pans (drag) or opens a context
    /// menu (press + release without crossing [#PAN_THRESHOLD]); we defer the decision to
    /// release. `rightDown` is held between press and release, `rightDragged` latches once
    /// the press has moved far enough to count as a pan.
    private boolean rightDown;
    private boolean rightDragged;
    private double rightPressX;
    private double rightPressY;

    /// The currently selected nodes — drawn with the active (orange) outline and the
    /// subjects of group move / delete. Left-click on a node selects just it; a left-drag
    /// on empty canvas marquee-selects everything it covers.
    private final List<AbstractNodeWidget> selectedNodes = new ArrayList<>();

    /// Node-drag state. `draggingNode` is the grabbed node; the whole selection moves with
    /// it, each node offset from its own start position captured in `dragStartPositions`.
    @Nullable
    private AbstractNodeWidget draggingNode;
    private int grabOffsetX;
    private int grabOffsetY;
    private int dragAnchorStartX;
    private int dragAnchorStartY;
    private final List<AbstractNodeWidget> dragNodes = new ArrayList<>();
    private final List<Pair<Integer, Integer>> dragStartPositions = new ArrayList<>();

    /// Marquee (rubber-band) selection state, in graph space. Active while left-dragging
    /// empty canvas. `marqueeBase` is the selection to keep when Shift makes the drag
    /// additive (empty otherwise), unioned each frame with whatever the box covers.
    private boolean marqueeing;
    private final List<AbstractNodeWidget> marqueeBase = new ArrayList<>();
    private int marqueeStartGx;
    private int marqueeStartGy;
    private int marqueeEndGx;
    private int marqueeEndGy;

    /// Wire-drag state. `wiring` is true while the mouse is held after grabbing an
    /// output pin; `wireSourceNode`/`wireSourcePin` identify that pin and are kept set
    /// while a suggestion modal is pending so the spline stays drawn. `wirePending`
    /// marks the after-release window where the spline is pinned to the drop point
    /// and the modal is open.
    private boolean wiring;
    private boolean wirePending;
    @Nullable
    private AbstractNodeWidget wireSourceNode;
    private int wireSourcePin;
    /// Which side the wire was grabbed from: true = an output pin (drag toward an input /
    /// flow suggestion), false = a data input pin (drag toward an output / value suggestion).
    private boolean wireFromOutput;
    private double wireEndScreenX;
    private double wireEndScreenY;
    private int pendingDropGraphX;
    private int pendingDropGraphY;

    /// Frames of the last captured run, keyed by the row that produced them ([#assignIds] numbering).
    /// Empty whenever nothing is being debugged. A node that ran more than once in the captured run
    /// (an uncached pure value read by two consumers) is held by its last frame.
    private Map<Integer, CallStack> debugFrames = Map.of();

    /// Row ids as they stood when [#debugFrames] was captured — the numbering the capture was checked
    /// against, so the highlights keep meaning what they meant at that moment even if the graph is
    /// edited afterwards.
    private Map<AbstractNodeWidget, Integer> debugIds = Map.of();

    /// Whether the last capture handed to [#setDebugRecord] named rows this canvas does not have. A
    /// banner says so over the canvas until the next capture lands.
    private boolean debugStale;

    /// Input pins the user asked to show the value of. Keyed by the live widget rather than a row id,
    /// so a recording survives the edits that renumber the rows.
    private final Set<RecordedPin> recordedPins = new HashSet<>();

    /// One recorded input pin.
    private record RecordedPin(AbstractNodeWidget node, int pin) {}

    public ScriptNodeGraphWidget(
        Font font,
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
    }

    public void addNode(AbstractNodeWidget node) {
        if (scriptOwnerType != null) node.setScriptOwnerType(scriptOwnerType);
        nodes.add(node);
    }

    /// The method entry nodes currently on the canvas, in z-order.
    public List<MethodEntryNodeWidget> methodEntryNodes() {
        return nodes.stream()
            .filter(MethodEntryNodeWidget.class::isInstance)
            .map(MethodEntryNodeWidget.class::cast)
            .toList();
    }

    /// Whether a node of `type` is on the canvas.
    public boolean hasNode(Class<? extends AbstractNodeWidget> type) {
        return nodes.stream().anyMatch(type::isInstance);
    }

    /// Sets the members of the blueprint this canvas belongs to, and the method or function it
    /// implements. Takes effect for the nodes built afterwards.
    public void setBlueprintMembers(BlueprintMembers members, @Nullable String enclosingFunction) {
        this.members = members;
        this.enclosingFunction = enclosingFunction;
    }

    /// The method or function this canvas implements, or null.
    public @Nullable String enclosingFunction() {
        return enclosingFunction;
    }

    /// The unique names of every [StateMachineNodeWidget] on this canvas.
    public List<String> stateMachineNames() {
        return nodes.stream()
            .filter(StateMachineNodeWidget.class::isInstance)
            .map(n -> ((StateMachineNodeWidget) n).machineName())
            .toList();
    }

    /// Sets the provider consulted when a wire is dropped on empty canvas to build
    /// the [NodeSuggestModal] entries for the given [NodeSuggestContext].
    public void setSuggestionProvider(@Nullable BiFunction<ScriptNodeGraphWidget, NodeSuggestContext, List<NodeSuggestModal.Suggestion<NodeWidgetFactory>>> provider) {
        this.suggestionProvider = provider;
    }

    /// The names of every [PoseCachingNodeWidget] on this canvas — the cached poses a
    /// [CachedPoseGetterNodeWidget] here may reference.
    public List<String> cachedPoseNames() {
        return nodes.stream()
            .filter(PoseCachingNodeWidget.class::isInstance)
            .map(n -> ((PoseCachingNodeWidget) n).cacheName())
            .toList();
    }

    /// Sets the script owner type used to build the empty-canvas right-click suggestions
    /// (flow nodes plus pure functions that take the owner). Without it, the right-click
    /// suggest is skipped.
    public void setScriptOwnerType(Class<?> ownerType) {
        this.scriptOwnerType = ownerType;
        for (AbstractNodeWidget node : nodes) node.setScriptOwnerType(ownerType);
    }

    /// Whether `function` is the untyped `akythera:select`, which reloads as the type-detecting
    /// [SelectNodeWidget] rather than a plain [PureFunctionNodeWidget].
    public static boolean isSelect(ScriptFunction function) {
        return function == AkytheraScriptFunctions.SELECT.value();
    }

    /// Re-types the canvas' result root, see [TransitionResultNodeWidget#setResultType].
    public void setResultType(Class<?> resultType) {
        for (AbstractNodeWidget node : nodes) {
            if (node instanceof TransitionResultNodeWidget result) result.setResultType(resultType);
        }
    }

    /// Sets the [ScriptFunction.Usage]s this canvas offers in its node-suggestion menus (Event
    /// Graph → `SCRIPT`, Anim Graph / pose graphs → `ANIM_GRAPH, SCRIPT`).
    public void setUsage(ScriptFunction.Usage... usages) {
        EnumSet<ScriptFunction.Usage> set = EnumSet.noneOf(ScriptFunction.Usage.class);
        Collections.addAll(set, usages);
        this.usage = set;
    }

    /// The [ScriptFunction.Usage]s this canvas offers.
    public Set<ScriptFunction.Usage> usage() {
        return usage;
    }

    /// Marks this canvas as pure data-flow (no execution constructs in its suggestion menu).
    public void setDataFlowOnly(boolean dataFlowOnly) {
        this.dataFlowOnly = dataFlowOnly;
    }

    /// Whether this canvas is pure data-flow, with no execution flow.
    public boolean isDataFlowOnly() {
        return dataFlowOnly;
    }

    /// Sets the handler fired when an [AbstractNodeWidget#isActivatable] node is double-clicked.
    public void setNodeActivationHandler(@Nullable Consumer<AbstractNodeWidget> handler) {
        this.nodeActivationHandler = handler;
    }

    /// Sets the handler fired with the selected nodes, see [#selectionHandler].
    public void setSelectionHandler(@Nullable Consumer<List<AbstractNodeWidget>> handler) {
        this.selectionHandler = handler;
    }

    /// Hands the current selection to [#selectionHandler].
    public void fireSelectionChanged() {
        if (selectionHandler != null) selectionHandler.accept(List.copyOf(selectedNodes));
    }

    /// Shows `node` in the Details panel again after one of its values changed on the canvas, while it is
    /// the only selected node.
    void refreshDetails(AbstractNodeWidget node) {
        if (selectedNodes.size() == 1 && selectedNodes.getFirst() == node) fireSelectionChanged();
    }

    // ***************************************************************
    // Persistence — flatten the live canvas to / from [ScriptGraphData]
    // ***************************************************************

    /// Builds the entry node for a freshly-loaded canvas. Injected by the host screen because the
    /// entry node's title and pin shape are event-specific (the Update event exposes
    /// `frameTime` / `partialTick`, the Constructor event exposes none), which this widget does not
    /// know. Called with the persisted entry position and its output-pin labels, so the screen can
    /// recover *which* event the row is (a row carrying data-output labels is the Update entry).
    @FunctionalInterface
    public interface EntryNodeFactory {
        AbstractNodeWidget create(int graphX, int graphY, List<String> outLabels);
    }

    /// Flattens the live canvas into its persisted form: every node becomes a [GraphNode] row (id =
    /// its index here, so the entry node — always first — is id 0) and every wire a
    /// [ScriptGraphData.WireData] row referencing those ids.
    ///
    /// Reroute knots are persisted like any other node (with their pin shape), so the on-canvas
    /// layout round-trips intact; the compiler is what splices them out when it lowers the graph
    /// to engine nodes (see [ScriptGraphCompiler]).
    public ScriptGraphData exportGraph() {
        Map<AbstractNodeWidget, Integer> idOf = assignIds();
        List<GraphNode> out = new ArrayList<>();
        List<ScriptGraphData.FailedNode> failed = new ArrayList<>();

        for (AbstractNodeWidget node : nodes) {
            if (node instanceof UnresolvedNodeWidget u) {
                failed.add(u.data());
                continue;
            }

            out.add(new GraphNode(idOf.get(node), node.getX(), node.getY(), node.toData(), node.pinLiterals()));
        }

        List<ScriptGraphData.WireData> wires = new ArrayList<>();

        for (Connection c : connections) {
            Integer fromId = idOf.get(c.fromNode());
            Integer toId = idOf.get(c.toNode());
            if (fromId == null || toId == null) continue;

            wires.add(new ScriptGraphData.WireData(fromId, c.fromPin(), toId, c.toPin()));
        }

        // Wires touching an unresolved placeholder never became live Connections — re-emit them
        // under the current ids so the wiring survives load/save cycles. A wire whose endpoint
        // was deleted (or whose placeholder has no usable id) is dropped with its node.
        for (StashedWire sw : unresolvedWires) {
            Integer fromId = idOf.get(sw.from());
            Integer toId = idOf.get(sw.to());
            if (fromId == null || toId == null || fromId < 0 || toId < 0) continue;

            wires.add(new ScriptGraphData.WireData(fromId, sw.fromPin(), toId, sw.toPin()));
        }

        return new ScriptGraphData(out, wires, failed);
    }

    /// The row id every live node would be written under — the one numbering
    /// [#exportGraph] persists and [ScriptNode#nodeId] carries into the pack, so a
    /// [CallStack] recorded from that pack names a node here.
    ///
    /// Unresolved placeholders re-emit their raw rows under their ORIGINAL ids (the raw JSON is
    /// byte-preserved), so live nodes are numbered around those ids to avoid collisions.
    private Map<AbstractNodeWidget, Integer> assignIds() {
        Set<Integer> reservedIds = new HashSet<>();
        for (AbstractNodeWidget node : nodes) {
            if (node instanceof UnresolvedNodeWidget u && u.data().id() >= 0) reservedIds.add(u.data().id());
        }

        Map<AbstractNodeWidget, Integer> idOf = new HashMap<>();
        int nextId = 0;

        for (AbstractNodeWidget node : nodes) {
            if (node instanceof UnresolvedNodeWidget u) {
                idOf.put(node, u.data().id());
                continue;
            }

            while (reservedIds.contains(nextId)) nextId++;
            idOf.put(node, nextId++);
        }

        return idOf;
    }

    // ***************************************************************
    // Debugging
    // ***************************************************************

    /// Shows `records` on the canvas as one run: the wires they ran thicken and every recorded pin
    /// shows the value fed into it. Refuses records whose ids do not describe this graph (see
    /// [#framesDescribeGraph]), puts the stale banner up in their place and answers false.
    public boolean setDebugRecord(final ExecutionRecord... records) {
        Map<Integer, CallStack> frames = new HashMap<>();
        boolean entered = false;

        for (ExecutionRecord record : records) {
            for (CallStack root : record.roots()) indexFrames(root, frames);

            entered |= !record.isEmpty();
        }

        Map<AbstractNodeWidget, Integer> idOf = assignIds();

        // A run that entered nodes yet named none of them came from a pack built without row ids, so
        // there is nothing to map onto this canvas. Caught here because an empty frame map satisfies
        // framesDescribeGraph, which would otherwise accept it as a complete capture of nothing.
        if ((entered && frames.isEmpty()) || !framesDescribeGraph(frames, idOf)) {
            clearDebugRecord();
            debugStale = true;

            return false;
        }

        debugFrames = frames;
        debugIds = idOf;
        debugStale = false;

        return true;
    }

    /// Drops the captured run — nothing thickens and no pin shows a value until the next capture.
    public void clearDebugRecord() {
        debugFrames = Map.of();
        debugIds = Map.of();
        debugStale = false;
    }

    private static void indexFrames(final CallStack frame, final Map<Integer, CallStack> out) {
        if (frame.nodeId() != ScriptNode.NO_NODE_ID) out.put(frame.nodeId(), frame);

        for (CallStack child : frame.children()) indexFrames(child, out);
    }

    /// Whether every frame lands on a row this canvas actually has, and every function call lands on
    /// the row that calls that function.
    ///
    /// Row ids are assigned per export, so a graph edited since the last reload renumbers underneath a
    /// pack built from it. A canvas that cannot vouch for the ids highlights nothing rather than
    /// highlighting the wrong rows.
    private boolean framesDescribeGraph(
        final Map<Integer, CallStack> frames, final Map<AbstractNodeWidget, Integer> idOf
    ) {
        Map<Integer, AbstractNodeWidget> nodeById = new HashMap<>();
        idOf.forEach((node, id) -> nodeById.put(id, node));

        for (Map.Entry<Integer, CallStack> entry : frames.entrySet()) {
            AbstractNodeWidget node = nodeById.get(entry.getKey());

            if (node == null || node instanceof UnresolvedNodeWidget) return false;

            if (!rowRuns(node, entry.getValue().node())) return false;
        }

        return true;
    }

    /// Whether `ran` can be what `node` lowered to: a row calling a function must have run that function.
    private static boolean rowRuns(final AbstractNodeWidget node, final ScriptNode ran) {
        ScriptFunction called = rowFunction(node);

        return called == null || (ran instanceof FunctionNode functionNode && functionNode.function() == called);
    }

    /// Marks the node `failure` left with its throw, on top of the marks already placed, and answers
    /// whether it landed. Refused, like a stale capture, when any open node of the failure names a row
    /// this canvas does not have or runs something else.
    public boolean markRuntimeError(final ScriptFailure failure) {
        Map<Integer, AbstractNodeWidget> nodeById = new HashMap<>();
        assignIds().forEach((node, id) -> nodeById.put(id, node));

        AbstractNodeWidget failed = null;

        for (ScriptNode open : failure.path()) {
            if (open.nodeId() == ScriptNode.NO_NODE_ID) continue;

            AbstractNodeWidget node = nodeById.get(open.nodeId());

            if (node == null || node instanceof UnresolvedNodeWidget || !rowRuns(node, open)) return false;

            failed = node;
        }

        if (failed == null) return false;

        failed.setRuntimeError(failure.cause());

        return true;
    }

    /// Drops every mark [#markRuntimeError] placed.
    public void clearRuntimeErrors() {
        for (AbstractNodeWidget node : nodes) node.setRuntimeError(null);
    }

    /// The [ScriptFunction] a row calls, or null for a row that is not a plain call — the operators,
    /// comparators and pose nodes that lower to a call of their own choosing are left unconstrained.
    private static @Nullable ScriptFunction rowFunction(final AbstractNodeWidget node) {
        return switch (node.toData()) {
            case PureFunctionNodeData data -> data.function();
            case FlowFunctionNodeData data -> data.function();
            default -> null;
        };
    }

    /// Toggles whether input pin `pin` of `node` shows the value fed into it. Dropping a recording
    /// clears the node's values straight away, so nothing lingers once [#pushRecordedValues] stops
    /// rebuilding them.
    private void toggleRecord(final AbstractNodeWidget node, final int pin) {
        RecordedPin recorded = new RecordedPin(node, pin);

        if (recordedPins.remove(recorded)) {
            node.setRecordedInputs(Map.of());
        } else {
            recordedPins.add(recorded);
        }
    }

    /// Hands every recording node the values of its pins for this frame.
    private void pushRecordedValues() {
        if (recordedPins.isEmpty()) {
            return;
        }

        Map<AbstractNodeWidget, Map<Integer, String>> byNode = new HashMap<>();

        for (RecordedPin recorded : recordedPins) {
            CallStack frame = inputSourceFrame(recorded.node(), recorded.pin());

            byNode.computeIfAbsent(recorded.node(), _ -> new HashMap<>())
                .put(recorded.pin(), frame == null ? RECORD_NO_VALUE : formatValue(frame.returnValue()));
        }

        byNode.forEach(AbstractNodeWidget::setRecordedInputs);
    }

    /// The captured frame of whatever fed input pin `pin` of `node`, or null when the pin is unwired
    /// or its source did not run.
    ///
    /// A [RerouteNodeWidget] is transparent to evaluation and produces no frame of its own, so the
    /// search walks through one to the node actually driving the wire. The walk is bounded because a
    /// hand-edited graph file can wire a reroute chain into a cycle.
    private @Nullable CallStack inputSourceFrame(final AbstractNodeWidget node, final int pin) {
        AbstractNodeWidget target = node;
        int targetPin = pin;

        for (int hop = 0; hop < REROUTE_HOP_LIMIT; hop++) {
            Connection wire = connectionInto(target, targetPin);

            if (wire == null) {
                return null;
            }

            if (!(wire.fromNode() instanceof RerouteNodeWidget)) {
                Integer id = debugIds.get(wire.fromNode());

                return id == null ? null : debugFrames.get(id);
            }

            target = wire.fromNode();
            targetPin = 0;
        }

        return null;
    }

    /// The wire ending at input pin `pin` of `node`, or null when that pin is unwired.
    private @Nullable Connection connectionInto(final AbstractNodeWidget node, final int pin) {
        for (Connection c : connections) {
            if (c.toNode() == node && c.toPin() == pin) return c;
        }

        return null;
    }

    /// A captured value as the text written after a pin's name: a floating-point value rounded to
    /// [#RECORD_DECIMALS] places, anything else its `String.valueOf`, shown in full — the node grows
    /// to whatever it takes to read the value.
    ///
    /// Flattened to one line, because a pin row is one line and an embedded newline would run the
    /// rest of the text through the node's border.
    private static String formatValue(final @Nullable Object value) {
        String text = value instanceof Float || value instanceof Double
            ? formatDecimal(((Number) value).doubleValue())
            : String.valueOf(value);

        return text.replace('\n', ' ');
    }

    /// `value` at [#RECORD_DECIMALS] decimal places with the trailing zeros dropped, so 0.30000 reads
    /// "0.3" and 2.0 reads "2". A value that rounds away to nothing loses its sign with its digits.
    private static String formatDecimal(final double value) {
        if (!Double.isFinite(value)) {
            return String.valueOf(value);
        }

        String text = String.format(Locale.ROOT, "%." + RECORD_DECIMALS + "f", value);

        // "%.4f" always writes a decimal point, so trimming zeros here can only eat fraction digits.
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '0') end--;
        if (end > 0 && text.charAt(end - 1) == '.') end--;

        text = text.substring(0, end);

        return text.equals("-0") ? "0" : text;
    }

    /// How heavily a committed wire draws: an execution wire whose two ends both ran in the captured
    /// run is drawn heavy, everything else at its normal weight.
    ///
    /// An execution output feeds exactly one input, so "both ends ran" is the whole test — there is no
    /// other wire that could have carried the flow into the target.
    private float executedWeight(final Connection connection) {
        if (debugFrames.isEmpty() || !connection.fromNode().outputPin(connection.fromPin()).exec()) {
            return 1.0F;
        }

        Integer fromId = debugIds.get(connection.fromNode());
        Integer toId = debugIds.get(connection.toNode());

        boolean executed = fromId != null && toId != null
            && debugFrames.containsKey(fromId) && debugFrames.containsKey(toId);

        return executed ? EXECUTED_WIRE_WEIGHT : 1.0F;
    }

    /// A wire touching at least one [UnresolvedNodeWidget], stashed at import as live widget
    /// references (so [#exportGraph] can re-emit it under the then-current ids). It cannot become
    /// a live [Connection] — placeholders have no pins — but must not be lost across load/save.
    private record StashedWire(AbstractNodeWidget from, int fromPin, AbstractNodeWidget to, int toPin) {}

    private final List<StashedWire> unresolvedWires = new ArrayList<>();

    /// Flattens the current selection into a [ScriptGraphData] for the clipboard — a selection-only
    /// [#exportGraph]. Only [AbstractNodeWidget#isCopyable] nodes are taken — a structural singleton
    /// (an event entry, the Output Pose, a transition's Result) and a node named by a registered
    /// signature (an Anim Layer, an Animation Interface, a Cached Pose definition) are both skipped.
    /// Ids are assigned over the copied nodes, and a wire is kept only when **both** of its endpoints
    /// are in the copied set (a wire leaving the selection is dropped). Returns null when nothing
    /// copyable is selected.
    public @Nullable ScriptGraphData copySelection() {
        // Unresolved placeholders are excluded: they have no describable state (toData() refuses)
        // and duplicating an unreadable raw row would duplicate the failure.
        List<AbstractNodeWidget> copied = selectedNodes.stream()
            .filter(AbstractNodeWidget::isCopyable)
            .filter(n -> !(n instanceof UnresolvedNodeWidget))
            .toList();
        if (copied.isEmpty()) return null;

        Map<AbstractNodeWidget, Integer> idOf = new HashMap<>();
        List<GraphNode> out = new ArrayList<>();

        for (AbstractNodeWidget node : copied) {
            int id = idOf.size();
            idOf.put(node, id);
            out.add(new GraphNode(id, node.getX(), node.getY(), node.toData(), node.pinLiterals()));
        }

        List<ScriptGraphData.WireData> wires = new ArrayList<>();

        for (Connection c : connections) {
            Integer fromId = idOf.get(c.fromNode());
            Integer toId = idOf.get(c.toNode());
            if (fromId == null || toId == null) continue; // wire crosses out of the selection

            wires.add(new ScriptGraphData.WireData(fromId, c.fromPin(), toId, c.toPin()));
        }

        return new ScriptGraphData(out, wires);
    }

    private static Class<?> wrap(Class<?> t) {
        if (!t.isPrimitive()) return t;
        if (t == boolean.class) return Boolean.class;
        if (t == byte.class)    return Byte.class;
        if (t == short.class)   return Short.class;
        if (t == char.class)    return Character.class;
        if (t == int.class)     return Integer.class;
        if (t == long.class)    return Long.class;
        if (t == float.class)   return Float.class;
        if (t == double.class)  return Double.class;
        return t;
    }

    /// Rebuilds the canvas from persisted data: clears the live nodes / wires, lets each
    /// [GraphNode] build its widget (an entry row through `entryFactory`), then re-adds every wire
    /// whose endpoints resolve to in-range pins. A no-op for [ScriptGraphData#EMPTY].
    public void importGraph(ScriptGraphData data, EntryNodeFactory entryFactory) {
        nodes.clear();
        connections.clear();
        selectedNodes.clear();
        unresolvedWires.clear();

        Map<Integer, AbstractNodeWidget> byId = new HashMap<>();
        for (GraphNode row : data.nodes()) {
            AbstractNodeWidget widget = row.data().createWidget(
                new NodeBuildContext(font, entryFactory, members, enclosingFunction, row.x(), row.y()));
            if (widget == null) continue;
            widget.setPinLiterals(row.literals());
            if (scriptOwnerType != null) widget.setScriptOwnerType(scriptOwnerType);
            nodes.add(widget);
            byId.put(row.id(), widget);
        }

        // Failed rows come back as inert, permanently error-marked placeholders — visible, red,
        // movable, deletable; registered under their persisted id so their wires can be stashed.
        for (ScriptGraphData.FailedNode failedNode : data.failedNodes()) {
            UnresolvedNodeWidget widget = new UnresolvedNodeWidget(font, failedNode);
            nodes.add(widget);
            if (failedNode.id() >= 0) byId.putIfAbsent(failedNode.id(), widget);
        }

        for (ScriptGraphData.WireData wd : data.wires()) {
            AbstractNodeWidget from = byId.get(wd.from());
            AbstractNodeWidget to = byId.get(wd.to());
            if (from == null || to == null) continue;

            // A wire touching a placeholder can't be a live connection (no pins) — stash it so
            // exportGraph re-emits it and the wiring survives the round-trip.
            if (from instanceof UnresolvedNodeWidget || to instanceof UnresolvedNodeWidget) {
                unresolvedWires.add(new StashedWire(from, wd.fromPin(), to, wd.toPin()));
                continue;
            }

            if (wd.fromPin() < 0 || wd.fromPin() >= from.outputPinCount()) continue;
            if (wd.toPin() < 0 || wd.toPin() >= to.inputPinCount()) continue;
            connections.add(new Connection(from, wd.fromPin(), to, wd.toPin()));
        }
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        // Nodes are rendered and hit-tested by this widget directly, so none of them takes part in
        // vanilla's child event dispatch.
        return Collections.emptyList();
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        CanvasGrid.extract(g, getX(), getY(), getRight(), getBottom(), getX() + panX, getY() + panY, zoom);

        g.enableScissor(getX(), getY(), getRight(), getBottom());


        // Refresh per-node connected-pin state up front so node layout (type-detected pins,
        // self badges) is settled before either the wires or the nodes are drawn.
        pushConnectionState();

        int graphMouseX = screenToGraphX(mouseX);
        int graphMouseY = screenToGraphY(mouseY);

        // Wires render here, *behind* the nodes: committed connections (graph→screen each frame
        // so they track moving nodes, tinted by the value the wire carries), then any in-flight
        // / pending wire, then the marquee. The nodes themselves are deferred to the frame's
        // tail (see below) so a spline never paints over a node body — matching UE.
        pushRecordedValues();

        for (Connection c : connections) {
            drawConnection(g,
                graphToScreenX(c.fromNode().outputPinCenterX(c.fromPin())),
                graphToScreenY(c.fromNode().outputPinCenterY(c.fromPin())),
                graphToScreenX(c.toNode().inputPinCenterX(c.toPin())),
                graphToScreenY(c.toNode().inputPinCenterY(c.toPin())),
                pinWireColor(c.fromNode().outputPin(c.fromPin())),
                executedWeight(c));
        }

        if (wiring && wireSourceNode != null) {
            renderActiveWire(g, graphMouseX, graphMouseY);
        } else if (wirePending && wireSourceNode != null) {
            // Spline pinned to the drop point while the suggestion modal is open.
            drawConnection(g,
                wireSourceScreenX(),
                wireSourceScreenY(),
                graphToScreenX(pendingDropGraphX),
                graphToScreenY(pendingDropGraphY),
                activeWireColor());
        }

        if (marqueeing) {
            drawMarquee(g);
        }

        // Hover cursor over a node when no gesture owns the cursor: the move cross over the
        // body (a drag anywhere off the pins moves the node) and the pointing hand over a pin
        // marker (a drag there grabs a wire) — matching UE.
        if (!wiring && !wirePending && draggingNode == null && !marqueeing && !rightDown && isMouseOver(mouseX, mouseY)) {
            requestHoverCursor(g, graphMouseX, graphMouseY);
        }

        g.disableScissor();

        // Defer the node widgets (and the header tooltip after them) to the frame's tail so they
        // draw on top of the wires above. The deferred pass runs outside this method's pose /
        // scissor, so both are re-established inside it.
        ((GuiGraphicsAccessor) g).akythera$setDeferredWidgetRenderer(() -> {
            g.enableScissor(getX(), getY(), getRight(), getBottom());

            g.pose().pushMatrix();
            g.pose().translate(getX() + panX, getY() + panY);
            g.pose().scale(zoom, zoom);

            for (AbstractWidget node : nodes) {
                node.extractRenderState(g, graphMouseX, graphMouseY, a);
            }

            g.pose().popMatrix();
            g.disableScissor();

            if (debugStale) {
                Component message = Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_DEBUG_STALE);
                int bannerWidth = font.width(message) + 16;
                int bannerHeight = font.lineHeight + 6;
                int bannerX = getX() + (getWidth() - bannerWidth) / 2;
                int bannerY = getY() + 4;

                g.fill(bannerX, bannerY, bannerX + bannerWidth, bannerY + bannerHeight, AbstractNodeWidget.COLOR_ERROR_BANNER);
                g.outline(bannerX, bannerY, bannerWidth, bannerHeight, AbstractNodeWidget.COLOR_ERROR_OUTLINE);
                g.text(font, message, bannerX + 8, bannerY + 3, 0xFFFFFFFF, false);
            }

            drawHeaderTooltip(g, mouseX, mouseY, graphMouseX, graphMouseY);
        });
    }

    /// Draws the rubber-band selection box (screen space): a faint fill plus a dashed
    /// border, mirroring UE's marquee.
    private void drawMarquee(GuiGraphicsExtractor g) {
        int x0 = (int) graphToScreenX(Math.min(marqueeStartGx, marqueeEndGx));
        int y0 = (int) graphToScreenY(Math.min(marqueeStartGy, marqueeEndGy));
        int x1 = (int) graphToScreenX(Math.max(marqueeStartGx, marqueeEndGx));
        int y1 = (int) graphToScreenY(Math.max(marqueeStartGy, marqueeEndGy));

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

    /// Screen-space X/Y of the wire's source pin, picking the input or output column
    /// from whichever side the wire was grabbed.
    private float wireSourceScreenX() {
        return graphToScreenX(wireFromOutput
            ? wireSourceNode.outputPinCenterX(wireSourcePin)
            : wireSourceNode.inputPinCenterX(wireSourcePin)
        );
    }

    private float wireSourceScreenY() {
        return graphToScreenY(wireFromOutput
            ? wireSourceNode.outputPinCenterY(wireSourcePin)
            : wireSourceNode.inputPinCenterY(wireSourcePin)
        );
    }

    /// Draws the title-bar tooltip of the node under the cursor (screen space, after the
    /// scissor closes so it isn't clipped). Suppressed while panning / dragging / wiring.
    private void drawHeaderTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY, int graphMouseX, int graphMouseY) {
        if (rightDown || marqueeing || draggingNode != null || wiring || wirePending) return;
        if (!isMouseOver(mouseX, mouseY)) return;

        for (int i = nodes.size() - 1; i >= 0; i--) {
            AbstractNodeWidget node = nodes.get(i);
            if (!node.containsGraphPoint(graphMouseX, graphMouseY)) continue;

            // Validation errors take precedence and show anywhere on the node; the node's own
            // header tooltip is appended only while the cursor is over the title bar.
            List<Component> lines = new ArrayList<>();
            if (node.hasErrors()) lines.addAll(node.errorLines());
            if (node.isOverHeader(graphMouseX, graphMouseY)) {
                List<Component> header = node.headerTooltipLines();
                if (header != null) lines.addAll(header);
            }
            if (!lines.isEmpty()) drawTooltipBox(g, lines, mouseX, mouseY);
            return; // topmost node consumes the hover regardless
        }
    }

    /// Requests the hover cursor for the topmost node under `(gx, gy)`: the pointing hand over
    /// a pin marker (output or input), the move cross anywhere else on the node body. Leaves the
    /// cursor untouched (default arrow) when the cursor is over empty canvas.
    private void requestHoverCursor(GuiGraphicsExtractor g, int gx, int gy) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            AbstractNodeWidget node = nodes.get(i);
            if (!node.containsGraphPoint(gx, gy)) continue;

            boolean onPin = node.outputPinIndexAt(gx, gy) >= 0 || node.inputPinIndexAt(gx, gy) >= 0;
            g.requestCursor(onPin ? CursorTypes.POINTING_HAND : CursorTypes.RESIZE_ALL);
            return; // topmost node consumes the hover regardless
        }
    }

    private void drawTooltipBox(GuiGraphicsExtractor g, List<Component> lines, int mouseX, int mouseY) {
        int pad = 4;
        int lineH = font.lineHeight + 1;
        int textW = lines.stream().mapToInt(font::width).max().orElse(0);

        int boxX = mouseX + 12;
        int boxY = mouseY + 12;
        int boxW = textW + pad * 2;
        int boxH = lines.size() * lineH - 1 + pad * 2;

        // Keep the box inside the widget bounds.
        if (boxX + boxW > getRight()) boxX = getRight() - boxW;
        if (boxY + boxH > getBottom()) boxY = getBottom() - boxH;

        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xF0140C24);
        g.outline(boxX, boxY, boxW, boxH, 0xFF3A2A6A);

        for (int i = 0; i < lines.size(); i++) {
            g.text(font, lines.get(i), boxX + pad, boxY + pad + i * lineH, 0xFFE0E0E0, false);
        }
    }

    /// Draws the in-progress wire from the source pin to either the cursor or, when
    /// hovering a valid drop pin, that pin (snapped + highlighted). Drives the cursor
    /// to signal compatibility.
    private void renderActiveWire(GuiGraphicsExtractor g, int graphMouseX, int graphMouseY) {
        float sx = wireSourceScreenX();
        float sy = wireSourceScreenY();
        int wireColor = activeWireColor();

        // From an output pin we snap onto input pins; from an input pin, onto output pins.
        PinTarget target = wireFromOutput
            ? findInputPinTarget(graphMouseX, graphMouseY)
            : findOutputPinTarget(graphMouseX, graphMouseY);

        if (target != null) {
            if (targetCompatible(target)) {
                float tx = graphToScreenX(wireFromOutput
                    ? target.node().inputPinCenterX(target.pin())
                    : target.node().outputPinCenterX(target.pin()));
                float ty = graphToScreenY(wireFromOutput
                    ? target.node().inputPinCenterY(target.pin())
                    : target.node().outputPinCenterY(target.pin()));
                drawConnection(g, sx, sy, tx, ty, wireColor);
                drawPinHighlight(g, tx, ty, COLOR_PIN_COMPATIBLE);
                g.requestCursor(CursorTypes.POINTING_HAND);
                return;
            }

            g.requestCursor(CursorTypes.NOT_ALLOWED);
        }

        drawConnection(g, sx, sy, (float) wireEndScreenX, (float) wireEndScreenY, wireColor);
    }

    /// The draw color of the in-flight wire: a data pin uses its type color so the spline
    /// reads as the value it carries (UE-style), while an execution pin stays the neutral
    /// wire color.
    private int activeWireColor() {
        if (wireSourceNode == null) return COLOR_WIRE;

        AbstractNodeWidget.Pin pin = wireFromOutput
            ? wireSourceNode.outputPin(wireSourcePin)
            : wireSourceNode.inputPin(wireSourcePin);
        return pinWireColor(pin);
    }

    /// The wire color for `pin`: its type color for a data pin, the neutral wire color
    /// for an execution pin.
    private static int pinWireColor(AbstractNodeWidget.Pin pin) {
        return pin.exec() ? COLOR_WIRE : pin.color();
    }

    /// How a dropped wire may join two data pins: a [#DIRECT] wire (same type or an implicit widening
    /// cast), a [#CONVERT] wire (a [TypeConvertNodeWidget] auto-inserted for an explicit narrowing
    /// cast, e.g. `double -> float`), or [#NONE] (incompatible).
    private enum LinkKind { DIRECT, CONVERT, NONE }

    /// Classifies an `output -> input` pin pairing. Execution pins only pair exec↔exec. Typed data pins are
    /// [LinkKind#DIRECT] when the value is assignable (same type, or a widening like `float -> double`,
    /// resolved via the target type's narrowing map) and [LinkKind#CONVERT] when only an explicit narrowing
    /// cast applies (the source type's [ScriptType#implicitlyCastableTypes()] names the target). A null type on
    /// either side is treated as a wildcard ([LinkKind#DIRECT]). Two lists, sets or maps whose elements do not
    /// fit are [LinkKind#NONE], see [AbstractNodeWidget#argumentsCompatible].
    private LinkKind linkKind(AbstractNodeWidget.Pin output, AbstractNodeWidget.Pin input) {
        if (output.exec() || input.exec()) {
            return output.exec() && input.exec() ? LinkKind.DIRECT : LinkKind.NONE;
        }

        if (!AbstractNodeWidget.argumentsCompatible(output.genericType(), input.genericType())) return LinkKind.NONE;

        Class<?> provided = output.type();
        Class<?> expected = input.type();
        if (provided == null || expected == null) return LinkKind.DIRECT;
        if (wrap(expected).isAssignableFrom(wrap(provided))) return LinkKind.DIRECT;

        ScriptType providedType = AbstractNodeWidget.scriptTypeOf(provided);
        if (providedType != null && providedType.implicitlyCastableTypes().containsKey(wrap(expected))) {
            return LinkKind.CONVERT; // explicit narrowing (e.g. double -> float)
        }

        ScriptType expectedType = AbstractNodeWidget.scriptTypeOf(expected);
        if (expectedType != null && expectedType.implicitlyCastableTypes().containsKey(wrap(provided))) {
            return LinkKind.DIRECT; // implicit widening (e.g. float -> double)
        }

        return LinkKind.NONE;
    }

    /// The [LinkKind] of the wire's source pin against the opposite-side `target` pin, oriented so the
    /// output pin is always the source of the value regardless of which end the drag began from.
    private LinkKind targetLinkKind(PinTarget target) {
        AbstractNodeWidget.Pin output = wireFromOutput
            ? wireSourceNode.outputPin(wireSourcePin)
            : target.node().outputPin(target.pin());
        AbstractNodeWidget.Pin input = wireFromOutput
            ? target.node().inputPin(target.pin())
            : wireSourceNode.inputPin(wireSourcePin);
        return linkKind(output, input);
    }

    /// Whether the wire's source pin and the opposite-side `target` pin can join at all (directly or
    /// via an auto-inserted converter), regardless of which end the drag started from.
    private boolean targetCompatible(PinTarget target) {
        return targetLinkKind(target) != LinkKind.NONE;
    }

    /// A square ring around a pin marker (screen space), scaled with zoom.
    private void drawPinHighlight(GuiGraphicsExtractor g, float cx, float cy, int color) {
        int r = (int) (5 * zoom) + 2;
        g.outline((int) cx - r, (int) cy - r, r * 2, r * 2, color);
    }

    /// Draws a wire from an output pin to an input pin (screen space), run by the editor's [WireStyle].
    private void drawConnection(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
        drawConnection(g, x0, y0, x1, y1, color, 1.0F);
    }

    private void drawConnection(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color, float weight) {
        float[] path = AkytheraLabsConfig.getWireStyle().path(x0, y0, x1, y1, zoom);

        // Wire weight grows with the zoom so it reads the same relative to the nodes at any scale.
        float halfWidth = Math.max(WIRE_MIN_HALF_WIDTH, zoom * weight);

        // The fade spans one framebuffer pixel.
        float feather = 1.0F / AkytheraEditorLayoutManager.getInstance().getLayoutGuiScale();

        ScreenRectangle scissor = new ScreenRectangle(getX(), getY(), getWidth(), getHeight()).transformAxisAligned(g.pose());

        ((GuiGraphicsExtractorAccessor) g).akytheralabs$guiRenderState()
            .addGuiElement(new WireRenderState(new Matrix3x2f(g.pose()), path, halfWidth, feather, color, scissor));
    }


    /// A graph-space rectangle as it is drawn on the screen, for [ModalManager#inWidgetSpace].
    private ScreenRectangle graphToScreen(ScreenRectangle graphRect) {
        return new ScreenRectangle(
            Math.round(graphToScreenX(graphRect.left())),
            Math.round(graphToScreenY(graphRect.top())),
            Math.round(graphRect.width() * zoom),
            Math.round(graphRect.height() * zoom)
        );
    }

    private int screenToGraphX(double screenX) {
        return (int) ((screenX - getX() - panX) / zoom);
    }

    private int screenToGraphY(double screenY) {
        return (int) ((screenY - getY() - panY) / zoom);
    }

    private float graphToScreenX(int graphX) {
        return getX() + panX + graphX * zoom;
    }

    private float graphToScreenY(int graphY) {
        return getY() + panY + graphY * zoom;
    }

    /// Rounds a graph coordinate to the nearest [#DRAG_SNAP] step.
    private static int snap(int graphCoord) {
        return Math.round((float) graphCoord / DRAG_SNAP) * DRAG_SNAP;
    }

    /// An input pin resolved as a wire's drop target.
    private record PinTarget(AbstractNodeWidget node, int pin) {}

    /// The topmost node (other than the wire source) whose input pin sits under the
    /// given graph point, or null when none.
    @Nullable
    private PinTarget findInputPinTarget(int gx, int gy) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            AbstractNodeWidget node = nodes.get(i);
            if (node == wireSourceNode) continue;

            int pin = node.inputPinIndexAt(gx, gy);
            if (pin >= 0) return new PinTarget(node, pin);
        }

        return null;
    }

    /// The topmost node (other than the wire source) whose **output** pin sits under the
    /// given graph point, or null — the drop target when wiring from an input pin.
    @Nullable
    private PinTarget findOutputPinTarget(int gx, int gy) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            AbstractNodeWidget node = nodes.get(i);
            if (node == wireSourceNode) continue;

            int pin = node.outputPinIndexAt(gx, gy);
            if (pin >= 0) return new PinTarget(node, pin);
        }

        return null;
    }

    @Nullable
    private AbstractNodeWidget nodeAt(int gx, int gy) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            if (nodes.get(i).containsGraphPoint(gx, gy)) return nodes.get(i);
        }

        return null;
    }

    /// Pushes each node's connected **input** pins (index → feeding output pin) and **output**
    /// pins (index → fed destination input pin) from the live connection set, ordered by pin
    /// A single validation problem: the offending node and a human-readable message. Returned in
    /// a flat list by [#validate], one entry per problem (a node may have several).
    public record ValidationIssue(AbstractNodeWidget node, Component message) {}

    /// Validates the live graph for the problems UE flags on compile and marks each offending node
    /// with a red "ERROR!" banner (see [AbstractNodeWidget#setErrors]):
    ///   - a **required data input** left unconnected (and not defaulting to `self`);
    ///   - an **incompatible connection** — a wire whose source value type cannot drive its
    ///     destination pin (catches a node re-typing its pins after the wire was made).
    ///
    /// Error state is recomputed wholesale: every node is re-flagged or cleared, so fixing a node
    /// and validating again clears it.
    ///
    /// Each node's own [AbstractNodeWidget#intrinsicErrors] — a pose player bound to a missing asset,
    /// an Animation Interface with no default animation — are **not** pushed through
    /// [AbstractNodeWidget#setErrors] (the node draws them live, with no compile needed) but are
    /// collected into the returned list, so they fail a compile like any wiring problem.
    ///
    /// Returns the flat issue list (empty = the graph is valid).
    public List<ValidationIssue> validate() {
        // Settle `self` badges / type-detected pins against the current wiring before inspecting
        // them, so validation never races the per-frame connection push.
        pushConnectionState();

        Map<AbstractNodeWidget, List<Component>> byNode = new HashMap<>();
        List<ValidationIssue> issues = new ArrayList<>();

        // Incompatible connections.
        for (Connection c : connections) {
            if (c.fromPin() >= c.fromNode().outputPinCount() || c.toPin() >= c.toNode().inputPinCount()) {
                continue; // stale index against a node whose pins shrank; ignore defensively
            }
            AbstractNodeWidget.Pin from = c.fromNode().outputPin(c.fromPin());
            AbstractNodeWidget.Pin to = c.toNode().inputPin(c.toPin());
            if (!AbstractNodeWidget.pinsCompatible(from, to)) {
                String slot = to.label().isEmpty() ? from.label() : to.label();
                addIssue(byNode, issues, c.toNode(),
                    Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VALIDATION_INCOMPATIBLE, slot));
            }
        }

        // Unconnected required inputs.
        for (AbstractNodeWidget node : nodes) {
            for (int i = 0; i < node.inputPinCount(); i++) {
                if (node.isRequiredInputUnconnected(i)) {
                    addIssue(byNode, issues, node,
                        Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VALIDATION_REQUIRED_INPUT,
                            node.inputPin(i).label()));
                }
            }
        }

        // Apply (and clear) error state across every node.
        for (AbstractNodeWidget node : nodes) {
            node.setErrors(byNode.getOrDefault(node, List.of()));
        }

        // Errors a node determines about itself — a pose player bound to an asset that resolves
        // nowhere, an Animation Interface missing its default animation. The node already draws these
        // live (see [AbstractNodeWidget#intrinsicErrors]), so they are not pushed through setErrors;
        // collecting them here is what makes them fail a compile too.
        for (AbstractNodeWidget node : nodes) {
            if (!node.reportsValidationErrors()) continue;

            for (Component message : node.intrinsicErrors()) {
                issues.add(new ValidationIssue(node, message));
            }
        }

        return issues;
    }

    /// Flags each node whose payload `flag` returns a message for, on top of whatever [#validate] left
    /// there. The host's hook for a problem no single canvas can see — a name this node shares with one
    /// on another canvas — so such a node reads red exactly like a wiring error. An unresolved
    /// placeholder describes no payload and is skipped.
    ///
    /// Additive, so run it *after* [#validate]: that pass rewrites every node's error state, and its
    /// rewrite is what clears a flag placed here once the duplicate is resolved.
    public void addPayloadErrors(Function<NodeData, @Nullable Component> flag) {
        for (AbstractNodeWidget node : nodes) {
            if (node instanceof UnresolvedNodeWidget || !node.reportsValidationErrors()) continue;

            Component message = flag.apply(node.toData());
            if (message != null) node.addError(message);
        }
    }

    private static void addIssue(
        Map<AbstractNodeWidget, List<Component>> byNode, List<ValidationIssue> issues,
        AbstractNodeWidget node, Component message
    ) {
        if (!node.reportsValidationErrors()) return; // cosmetic node (reroute knot)
        byNode.computeIfAbsent(node, _ -> new ArrayList<>()).add(message);
        issues.add(new ValidationIssue(node, message));
    }

    /// index. Drives the `self` badge and type-detecting nodes. Rebuilt every frame so it stays
    /// correct across undo/redo without hooking every mutation.
    private void pushConnectionState() {
        Map<AbstractNodeWidget, Map<Integer, AbstractNodeWidget.Pin>> inputsByNode = new HashMap<>();
        Map<AbstractNodeWidget, Map<Integer, AbstractNodeWidget.Pin>> outputsByNode = new HashMap<>();

        for (Connection c : connections) {
            AbstractNodeWidget.Pin source = c.fromNode().outputPin(c.fromPin());
            AbstractNodeWidget.Pin dest = c.toNode().inputPin(c.toPin());
            inputsByNode.computeIfAbsent(c.toNode(), _ -> new TreeMap<>()).put(c.toPin(), source);
            outputsByNode.computeIfAbsent(c.fromNode(), _ -> new TreeMap<>()).put(c.fromPin(), dest);
        }

        for (AbstractNodeWidget node : nodes) {
            node.setCanvas(this);
            node.updateConnections(
                inputsByNode.getOrDefault(node, Map.of()),
                outputsByNode.getOrDefault(node, Map.of()));
        }
    }

    /// Height of the editor screen this graph lives in (screen-space, editor scale), used to
    /// decide whether a suggestion modal should open downward or flip above the cursor.
    private int editorScreenHeight() {
        Screen screen = AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen();
        return screen != null ? screen.getRectangle().height() : getBottom();
    }

    /// Starts a wire drag from `node`'s pin `pin` (`fromOutput` selects the side),
    /// anchored to the cursor at `screenX/screenY`.
    private void beginWire(AbstractNodeWidget node, int pin, boolean fromOutput, double screenX, double screenY) {
        wiring = true;
        wirePending = false;
        wireSourceNode = node;
        wireSourcePin = pin;
        wireFromOutput = fromOutput;
        wireEndScreenX = screenX;
        wireEndScreenY = screenY;
    }

    private void clearWire() {
        wiring = false;
        wirePending = false;
        wireSourceNode = null;
    }

    /// Commits a connection and records it so making a wire can be undone / redone.
    ///
    /// A data input pin holds a single source, so a new wire into an already-wired input
    /// displaces the previous connection (UE-style). Execution input pins fan in (many flow
    /// predecessors into one node), so they keep every incoming wire.
    private void addConnection(Connection connection) {
        List<Connection> displaced = connection.toNode().inputPin(connection.toPin()).exec()
            ? List.of()
            : connections.stream()
                .filter(c -> c.toNode() == connection.toNode() && c.toPin() == connection.toPin())
                .toList();

        connections.removeAll(displaced);
        connections.add(connection);

        HistoryManager.getInstance().pushHistory(this, new WorkHistory() {
            @Override
            public void undo() {
                connections.remove(connection);
                connections.addAll(displaced);
            }

            @Override
            public void redo() {
                connections.removeAll(displaced);
                connections.add(connection);
            }
        });
    }

    /// Auto-inserts a [TypeConvertNodeWidget] between an output pin and an input pin of a different
    /// but explicitly-castable type (e.g. dropping a `double` output onto a `float` input): the node
    /// is centred on the midpoint of the two pins, wired `fromNode -> converter -> toNode`, and (since
    /// a data input holds one source) any wire already feeding the target input is displaced. Recorded
    /// as one undoable unit.
    private void insertConverter(AbstractNodeWidget fromNode, int fromPin, AbstractNodeWidget toNode, int toPin) {
        Class<?> fromType = fromNode.outputPin(fromPin).type();
        Class<?> toType = toNode.inputPin(toPin).type();

        int midX = (fromNode.outputPinCenterX(fromPin) + toNode.inputPinCenterX(toPin)) / 2;
        int midY = (fromNode.outputPinCenterY(fromPin) + toNode.inputPinCenterY(toPin)) / 2;

        TypeConvertNodeWidget converter = new TypeConvertNodeWidget(font, fromType, toType, midX, midY);
        if (scriptOwnerType != null) converter.setScriptOwnerType(scriptOwnerType);
        converter.setX(midX - converter.getWidth() / 2);
        converter.setY(midY - converter.getHeight() / 2);

        Connection in = new Connection(fromNode, fromPin, converter, 0);
        Connection out = new Connection(converter, 0, toNode, toPin);
        List<Connection> displaced = connections.stream()
            .filter(c -> c.toNode() == toNode && c.toPin() == toPin)
            .toList();

        applyConverter(converter, displaced, in, out);
        selectOnly(converter);

        HistoryManager.getInstance().pushHistory(this, new WorkHistory() {
            @Override
            public void undo() {
                connections.remove(in);
                connections.remove(out);
                nodes.remove(converter);
                connections.addAll(displaced);
                if (selectedNodes.remove(converter)) converter.setFocused(false);
            }

            @Override
            public void redo() {
                applyConverter(converter, displaced, in, out);
                selectOnly(converter);
            }
        });
    }

    private void applyConverter(TypeConvertNodeWidget converter, List<Connection> displaced, Connection in, Connection out) {
        connections.removeAll(displaced);
        if (!nodes.contains(converter)) nodes.add(converter);
        if (!connections.contains(in)) connections.add(in);
        if (!connections.contains(out)) connections.add(out);
    }

    /// Breaks every wire attached to the given pin (`output` selects which endpoint to
    /// match) and records it so the Alt+click disconnect can be undone / redone.
    private void disconnectPin(AbstractNodeWidget node, int pinIndex, boolean output) {
        List<Connection> removed = connections.stream()
            .filter(c -> output
                ? c.fromNode() == node && c.fromPin() == pinIndex
                : c.toNode() == node && c.toPin() == pinIndex)
            .toList();

        if (removed.isEmpty()) return;

        connections.removeAll(removed);

        HistoryManager.getInstance().pushHistory(this, new WorkHistory() {
            @Override
            public void undo() {
                connections.addAll(removed);
            }

            @Override
            public void redo() {
                connections.removeAll(removed);
            }
        });
    }

    /// Retargets every [VariableNodeWidget] (Get / Set) referencing `variableName` to
    /// `newType` — re-coloring / re-typing their data pins — and then drops every
    /// wire on those nodes the new type can no longer satisfy.
    ///
    /// Returns a [WorkHistory] that reverses both the pin retyping and the disconnections (or
    /// null when no node referenced the variable). The change is **not** pushed onto the undo
    /// stack here: the caller composes the returned handle with its own variable-type change so
    /// the whole retype is a single undo step.
    @Nullable
    public WorkHistory retypeVariableNodes(String variableName, ScriptType newType) {
        List<AbstractNodeWidget> targets = new ArrayList<>();
        for (AbstractNodeWidget node : nodes) {
            if (node instanceof VariableNodeWidget v && v.variableName().equals(variableName)) targets.add(node);
        }
        if (targets.isEmpty()) return null;

        // All nodes for one variable share its type, so a single old type reverses them all.
        ScriptType oldType = ((VariableNodeWidget) targets.getFirst()).variableType();

        retypeNodes(targets, newType);

        // Evaluated against the *new* pin types: a wire on a retyped node whose endpoints are
        // no longer compatible is dropped (exec wires are unaffected — exec pins stay typeless).
        List<Connection> removed = connections.stream()
            .filter(c -> targets.contains(c.fromNode()) || targets.contains(c.toNode()))
            .filter(c -> !AbstractNodeWidget.pinsCompatible(c.fromNode().outputPin(c.fromPin()), c.toNode().inputPin(c.toPin())))
            .toList();
        connections.removeAll(removed);

        return new WorkHistory() {
            @Override
            public void undo() {
                retypeNodes(targets, oldType);
                connections.addAll(removed);
            }

            @Override
            public void redo() {
                retypeNodes(targets, newType);
                connections.removeAll(removed);
            }
        };
    }

    private static void retypeNodes(List<AbstractNodeWidget> targets, @Nullable ScriptType type) {
        for (AbstractNodeWidget node : targets) ((VariableNodeWidget) node).applyVariableType(type);
    }

    /// Renames every [VariableNodeWidget] (Get / Set) referencing `oldName` to `newName`,
    /// updating each node's title / pin label in place. No history is recorded here: the
    /// caller's rename history re-invokes this on undo / redo with the names swapped, so the
    /// node names track the variable's name without a separate undo step.
    public void renameVariableNodes(String oldName, String newName) {
        for (AbstractNodeWidget node : nodes) {
            if (node instanceof VariableNodeWidget v && v.variableName().equals(oldName)) {
                v.applyVariableName(newName);
            }
        }
    }

    /// Retargets every [CachedPoseGetterNodeWidget] referencing `oldName` to `newName` (updating each
    /// node's title in place), so the getters track a renamed [PoseCachingNodeWidget]. No history is
    /// recorded here — the caller's rename handling re-applies it.
    public void renameCachedPoseGetters(String oldName, String newName) {
        for (AbstractNodeWidget node : nodes) {
            if (node instanceof CachedPoseGetterNodeWidget g && g.cacheName().equals(oldName)) {
                g.setCacheName(newName);
            }
        }
    }

    /// Re-points every [ComponentGetterNodeWidget] at a renamed component (updating each node's title in
    /// place), so a dropped component tracks the row it came from. No history is recorded here — the
    /// panel's rename handling re-applies it.
    public void renameComponentGetters(String fromPath, String toPath) {
        for (AbstractNodeWidget node : nodes) {
            if (node instanceof ComponentGetterNodeWidget getter) getter.renamePath(fromPath, toPath);
        }
    }

    /// Whether the screen point falls inside this canvas — used by the Variables panel to decide a
    /// dragged variable was dropped onto the graph.
    public boolean isPointInside(double screenX, double screenY) {
        return isMouseOver(screenX, screenY);
    }

    /// Spawns `factory`'s node where a screen point lands (converted to graph space), recording the
    /// add as one undoable unit and selecting the new node. Backs the Variables drag-and-drop: a
    /// variable released over the canvas drops its Get / Set node at the cursor.
    public void spawnNodeAt(NodeWidgetFactory factory, double screenX, double screenY) {
        int gx = screenToGraphX(screenX);
        int gy = screenToGraphY(screenY);

        AbstractNodeWidget created = factory.create(font, gx, gy);
        if (scriptOwnerType != null) created.setScriptOwnerType(scriptOwnerType);
        nodes.add(created);
        selectOnly(created);

        HistoryManager.getInstance().pushHistory(this, new WorkHistory() {
            @Override
            public void undo() {
                nodes.remove(created);
                if (selectedNodes.remove(created)) created.setFocused(false);
            }

            @Override
            public void redo() {
                nodes.add(created);
                selectOnly(created);
            }
        });
    }

    /// Pastes a clipboard [ScriptGraphData] onto this canvas: each node row is rebuilt as its widget
    /// (shifted by `dx` / `dy` in graph space so the paste doesn't sit exactly on the originals), and
    /// kept only when `canPlace` accepts it — so a node illegal on this canvas (e.g. a State Machine
    /// pasted into the Event Graph, or a duplicate signature) is silently dropped, mirroring UE. A
    /// pasted wire survives only when **both** of its endpoints survived the filter. The pasted nodes
    /// become the new selection and the whole paste is recorded as one undoable unit (one Ctrl+Z
    /// reverts the lot). Entry rows never reach the clipboard, so the rebuild's entry factory is a
    /// no-op. Returns the nodes actually added (empty = nothing could be placed here).
    public List<AbstractNodeWidget> pasteGraph(ScriptGraphData data, int dx, int dy, Predicate<AbstractNodeWidget> canPlace) {
        Map<Integer, AbstractNodeWidget> byId = new HashMap<>();
        List<AbstractNodeWidget> added = new ArrayList<>();

        for (GraphNode row : data.nodes()) {
            AbstractNodeWidget widget = row.data().createWidget(
                new NodeBuildContext(font, (_, _, _) -> null, members, enclosingFunction, row.x(), row.y()));
            if (widget == null) continue;

            widget.setPinLiterals(row.literals());
            widget.setX(widget.getX() + dx);
            widget.setY(widget.getY() + dy);
            if (!canPlace.test(widget)) continue;

            if (scriptOwnerType != null) widget.setScriptOwnerType(scriptOwnerType);
            byId.put(row.id(), widget);
            added.add(widget);
        }

        if (added.isEmpty()) return List.of();

        List<Connection> pastedWires = new ArrayList<>();

        for (ScriptGraphData.WireData wd : data.wires()) {
            AbstractNodeWidget from = byId.get(wd.from());
            AbstractNodeWidget to = byId.get(wd.to());
            if (from == null || to == null) continue; // an endpoint was filtered out
            if (wd.fromPin() < 0 || wd.fromPin() >= from.outputPinCount()) continue;
            if (wd.toPin() < 0 || wd.toPin() >= to.inputPinCount()) continue;

            pastedWires.add(new Connection(from, wd.fromPin(), to, wd.toPin()));
        }

        applyPaste(added, pastedWires);
        setSelection(added);

        HistoryManager.getInstance().pushHistory(this, new WorkHistory() {
            @Override
            public void undo() {
                nodes.removeAll(added);
                connections.removeAll(pastedWires);
                for (AbstractNodeWidget n : added) {
                    if (selectedNodes.remove(n)) n.setFocused(false);
                }
            }

            @Override
            public void redo() {
                applyPaste(added, pastedWires);
                setSelection(added);
            }
        });

        return added;
    }

    /// Re-adds the pasted nodes / wires (shared by the initial paste and a redo), skipping any that
    /// are somehow already present so a redo never double-inserts.
    private void applyPaste(List<AbstractNodeWidget> added, List<Connection> wires) {
        for (AbstractNodeWidget n : added) {
            if (!nodes.contains(n)) nodes.add(n);
        }
        for (Connection c : wires) {
            if (!connections.contains(c)) connections.add(c);
        }
    }

    /// Removes a dynamic output pin: drops the wires on it and reindexes the wires on
    /// the same node's higher outputs (their index shifts down by one), then removes
    /// the pin from the node.
    private void removeOutputPin(AbstractNodeWidget node, int pinIndex) {
        connections.removeIf(c -> c.fromNode() == node && c.fromPin() == pinIndex);

        for (int i = 0; i < connections.size(); i++) {
            Connection c = connections.get(i);
            if (c.fromNode() == node && c.fromPin() > pinIndex) {
                connections.set(i, new Connection(node, c.fromPin() - 1, c.toNode(), c.toPin()));
            }
        }

        node.removeOutputPin(pinIndex);
    }

    /// Right-click context menu for a dynamic output pin: offers "Remove Pin".
    private void openPinContextMenu(AbstractNodeWidget node, int pinIndex, int screenX, int screenY) {
        ContextMenuContainer menu = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(120)
            .entry()
                .message(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_REMOVE_PIN))
                .onPress(_ -> removeOutputPin(node, pinIndex))
                .done()
            .build();

        ModalManager.getInstance().openModalWidget(menu, screenX, screenY);
    }

    /// Right-click context menu for a data input pin: offers "Record this value" / "Stop recording",
    /// which toggles whether the pin writes the value fed into it after its name.
    private void openRecordContextMenu(AbstractNodeWidget node, int pinIndex, int screenX, int screenY) {
        boolean recorded = recordedPins.contains(new RecordedPin(node, pinIndex));

        ContextMenuContainer menu = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(140)
            .entry()
                .message(Component.translatable(recorded
                    ? LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_STOP_RECORDING
                    : LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_RECORD_VALUE))
                .onPress(_ -> toggleRecord(node, pinIndex))
                .done()
            .build();

        ModalManager.getInstance().openModalWidget(menu, screenX, screenY);
    }

    /// Right-click context menu for a node body: offers "Delete", which removes the whole
    /// current selection.
    private void openNodeContextMenu(int screenX, int screenY) {
        ContextMenuContainer menu = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(120)
            .entry()
                .message(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_DELETE))
                .onPress(_ -> deleteSelection())
                .done()
            .build();

        ModalManager.getInstance().openModalWidget(menu, screenX, screenY);
    }

    /// Deletes every deletable node in the current selection as one undoable unit.
    public void deleteSelection() {
        deleteNodes(selectedNodes);
    }

    /// Removes `targets` (skipping non-deletable nodes like the entry point) from the graph
    /// along with every wire attached to any of them, and records it so the whole removal
    /// can be undone / redone as a single unit.
    private void deleteNodes(List<AbstractNodeWidget> targets) {
        // Snapshot the victims in ascending z-order so re-insertion on undo restores it.
        List<AbstractNodeWidget> victims = targets.stream()
            .filter(n -> n.isDeletable() && nodes.contains(n))
            .sorted(Comparator.comparingInt(nodes::indexOf))
            .toList();
        if (victims.isEmpty()) return;

        List<Integer> indices = victims.stream().map(nodes::indexOf).toList();
        List<Connection> removed = connections.stream()
            .filter(c -> victims.contains(c.fromNode()) || victims.contains(c.toNode()))
            .toList();

        applyDelete(victims, removed);

        HistoryManager.getInstance().pushHistory(this, new WorkHistory() {
            @Override
            public void undo() {
                // Re-insert ascending so each node lands at its original z-order index
                // (clamped in case the list shrank), then re-select them so the user sees
                // what came back.
                for (int i = 0; i < victims.size(); i++) {
                    nodes.add(Math.min(indices.get(i), nodes.size()), victims.get(i));
                }
                connections.addAll(removed);
                setSelection(victims);
            }

            @Override
            public void redo() {
                applyDelete(victims, removed);
            }
        });
    }

    /// Drops `victims` and `conns` from the graph and the selection (the live removal shared
    /// by the initial delete and a redo).
    private void applyDelete(List<AbstractNodeWidget> victims, List<Connection> conns) {
        nodes.removeAll(victims);
        connections.removeAll(conns);
        for (AbstractNodeWidget n : victims) {
            if (selectedNodes.remove(n)) n.setFocused(false);
        }
    }

    /// Screen-space half-thickness within which a click counts as "on the wire".
    private static final double WIRE_HIT_TOLERANCE = 5.0;

    /// Double-click handler: if the point `(sx, sy)` (screen) lands on a wire, split it with a
    /// reroute knot centred at `(gx, gy)` (graph) and report it handled.
    private boolean tryInsertRerouteAt(double sx, double sy, int gx, int gy) {
        Connection hit = connectionAt(sx, sy);
        if (hit == null) return false;

        insertReroute(hit, gx, gy);
        return true;
    }

    /// The first connection whose drawn path passes within [#WIRE_HIT_TOLERANCE] of `(sx, sy)`, or null.
    private @Nullable Connection connectionAt(double sx, double sy) {
        for (Connection c : connections) {
            float[] path = AkytheraLabsConfig.getWireStyle().path(
                graphToScreenX(c.fromNode().outputPinCenterX(c.fromPin())),
                graphToScreenY(c.fromNode().outputPinCenterY(c.fromPin())),
                graphToScreenX(c.toNode().inputPinCenterX(c.toPin())),
                graphToScreenY(c.toNode().inputPinCenterY(c.toPin())),
                zoom
            );

            for (int i = 2; i + 1 < path.length; i += 2) {
                if (distanceToSegment(sx, sy, path[i - 2], path[i - 1], path[i], path[i + 1]) <= WIRE_HIT_TOLERANCE) return c;
            }
        }

        return null;
    }

    private static double distanceToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lenSq = dx * dx + dy * dy;
        double t = lenSq == 0.0 ? 0.0 : Math.clamp(((px - ax) * dx + (py - ay) * dy) / lenSq, 0.0, 1.0);
        double cx = ax + t * dx;
        double cy = ay + t * dy;
        return Math.hypot(px - cx, py - cy);
    }

    /// Splits `original` (A → B) with a reroute knot centred on `(gx, gy)`: the wire becomes
    /// A → knot → B, recorded as one undoable unit. The knot inherits the wire's value type.
    private void insertReroute(Connection original, int gx, int gy) {
        AbstractNodeWidget.Pin sample = original.fromNode().outputPin(original.fromPin());
        RerouteNodeWidget knot = new RerouteNodeWidget(font, sample, gx, gy);
        if (scriptOwnerType != null) knot.setScriptOwnerType(scriptOwnerType);
        knot.setX(gx - knot.getWidth() / 2);
        knot.setY(gy - knot.getHeight() / 2);

        Connection a = new Connection(original.fromNode(), original.fromPin(), knot, 0);
        Connection b = new Connection(knot, 0, original.toNode(), original.toPin());

        applyReroute(knot, original, a, b);
        selectOnly(knot);

        HistoryManager.getInstance().pushHistory(this, new WorkHistory() {
            @Override
            public void undo() {
                nodes.remove(knot);
                connections.remove(a);
                connections.remove(b);
                if (!connections.contains(original)) connections.add(original);
                if (selectedNodes.remove(knot)) knot.setFocused(false);
            }

            @Override
            public void redo() {
                applyReroute(knot, original, a, b);
                selectOnly(knot);
            }
        });
    }

    private void applyReroute(RerouteNodeWidget knot, Connection original, Connection a, Connection b) {
        if (!nodes.contains(knot)) nodes.add(knot);
        connections.remove(original);
        if (!connections.contains(a)) connections.add(a);
        if (!connections.contains(b)) connections.add(b);
    }

    /// The modal title for a suggestion `context`, matched to the gesture: the empty-canvas
    /// right-click (flow + pure) reads as the broadest set, a parameter-output drag as
    /// "actions taking this value", a data-input drag as "get value", and an execution drag
    /// as "executable actions".
    private static Component suggestTitle(NodeSuggestContext context) {
        if (context.flow() && context.pureFunctions()) {
            return Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SUGGEST_ENTRY_TITLE);
        }
        if (context.pureFunctions() && !context.valueProviders()) {
            return Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SUGGEST_PARAMETER_TITLE);
        }
        if (context.valueProviders()) {
            return Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SUGGEST_VALUE_TITLE);
        }
        return Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SUGGEST_TITLE);
    }

    /// Opens the suggestion modal while keeping the dropped wire pinned to `graphX/graphY`.
    /// The modal entries are tailored to `context` (flow nodes for an execution drag, value
    /// providers for a data drag). Picking an entry spawns its node there and links it to
    /// the source pin in the correct direction; closing without a pick clears the wire.
    private void openNodeSuggest(int screenX, int screenY, int graphX, int graphY, NodeSuggestContext context) {
        if (suggestionProvider == null) {
            clearWire();
            return;
        }

        AbstractNodeWidget source = wireSourceNode;
        int sourcePin = wireSourcePin;
        boolean fromOutput = wireFromOutput;

        wirePending = true;
        pendingDropGraphX = graphX;
        pendingDropGraphY = graphY;

        NodeSuggestModal<NodeWidgetFactory> modal = new NodeSuggestModal<>(
            font,
            suggestTitle(context),
            suggestionProvider.apply(this, context.withUsage(usage).withDataFlowOnly(dataFlowOnly)),
            factory -> {
                AbstractNodeWidget created = factory.create(font, graphX, graphY);
                if (scriptOwnerType != null) created.setScriptOwnerType(scriptOwnerType);
                nodes.add(created);

                Connection link = linkSpawnedNode(source, sourcePin, fromOutput, created);
                if (link != null) connections.add(link);

                // Spawn + wire as one undoable unit so undo doesn't strand the node.
                HistoryManager.getInstance().pushHistory(this, new WorkHistory() {
                    @Override
                    public void undo() {
                        if (link != null) connections.remove(link);
                        nodes.remove(created);
                        if (selectedNodes.remove(created)) created.setFocused(false);
                    }

                    @Override
                    public void redo() {
                        nodes.add(created);
                        if (link != null) connections.add(link);
                    }
                });

                clearWire();
            },
            this::clearWire
        );

        // Open downward from the cursor, but flip the modal above it when it would clip off the
        // screen bottom — calculated from the available space below the press point.
        int openY = NodeSuggestModal.openYFor(screenY, editorScreenHeight());
        ModalManager.getInstance().openModalWidget(modal, screenX, openY);
    }

    /// Builds the connection from a wire-drop to a freshly spawned node, in the direction
    /// the drag implies:
    /// - an **execution** output drag links the source's exec output into the new flow
    ///   node's exec input;
    /// - a **data-output** (parameter) drag links the source's value output into the new
    ///   pure node's first compatible parameter pin;
    /// - a **data-input** drag links the new pure node's value output back into the source
    ///   input pin.
    ///
    /// Returns null when there is no source (an empty-canvas spawn) or the new node has no
    /// matching pin to attach.
    @Nullable
    private Connection linkSpawnedNode(@Nullable AbstractNodeWidget source, int sourcePin, boolean fromOutput, AbstractNodeWidget created) {
        if (source == null) return null;

        if (fromOutput) {
            AbstractNodeWidget.Pin sourcePinDef = source.outputPin(sourcePin);
            if (sourcePinDef.exec()) {
                int execIn = created.firstExecInputPinIndex();
                return execIn >= 0 ? new Connection(source, sourcePin, created, execIn) : null;
            }

            int dataIn = created.firstDataInputPinIndex(sourcePinDef.type());
            return dataIn >= 0 ? new Connection(source, sourcePin, created, dataIn) : null;
        }

        int dataOut = created.firstDataOutputPinIndex();
        return dataOut >= 0 ? new Connection(created, dataOut, source, sourcePin) : null;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver(event.x(), event.y())) return false;

        int gx = screenToGraphX(event.x());
        int gy = screenToGraphY(event.y());

        // A click outside the node being inline-edited commits and closes its editor (a click
        // back on the same node lets its body handler keep / restart editing).
        AbstractNodeWidget editing = inlineEditingNode();

        if (editing != null && !editing.containsGraphPoint(gx, gy)) {
            editing.endInlineEdit();
        }

        // Right button defers to release: a plain click opens a context menu, a drag pans.
        if (event.button() == 1) {
            rightDown = true;
            rightDragged = false;
            rightPressX = event.x();
            rightPressY = event.y();
            return true;
        }

        if (event.button() != 0) return true;

        // Left button. Topmost node under the cursor wins (last drawn is on top).
        for (int i = nodes.size() - 1; i >= 0; i--) {
            AbstractNodeWidget node = nodes.get(i);
            if (!node.containsGraphPoint(gx, gy)) continue;

            // Double-clicking an activatable node (e.g. a State Machine) opens its own editor.
            if (doubleClick && node.isActivatable()) {
                selectOnly(node);
                if (nodeActivationHandler != null) nodeActivationHandler.accept(node);
                return true;
            }

            // Press the "Add pin" button to grow a dynamic-output node.
            if (node.isOverAddPinButton(gx, gy)) {
                selectOnly(node);
                node.onAddPin();
                return true;
            }

            // Ctrl+click toggles this node's membership in the selection without disturbing
            // the rest, so nodes can be picked one by one. It takes priority over wiring /
            // body controls so Ctrl is purely a "select" modifier (no drag is started).
            if ((event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
                toggleSelection(node);
                return true;
            }

            if ((event.modifiers() & GLFW.GLFW_MOD_ALT) != 0) {
                // Alt+click on a pin breaks every wire attached to it (undoable).
                int outPin = node.outputPinIndexAt(gx, gy);

                if (outPin >= 0) {
                    disconnectPin(node, outPin, true);
                    return true;
                }

                int inPin = node.inputPinIndexAt(gx, gy);

                if (inPin >= 0) {
                    disconnectPin(node, inPin, false);
                    return true;
                }
            } else {
                // Dragging out of an output pin draws a wire to the cursor.
                int outPin = node.outputPinIndexAt(gx, gy);

                if (outPin >= 0) {
                    beginWire(node, outPin, true, event.x(), event.y());
                    return true;
                }

                // Dragging out of an input pin draws a wire back toward a value provider.
                int inPin = node.inputPinIndexAt(gx, gy);

                if (inPin >= 0) {
                    beginWire(node, inPin, false, event.x(), event.y());
                    return true;
                }
            }

            // Body controls (e.g. an inline literal editor) claim the click first.
            boolean claimed = ModalManager.getInstance()
                .inWidgetSpace(this::graphToScreen, () -> node.onBodyClick(gx, gy, event.x(), event.y()));

            if (claimed) {
                selectOnly(node);
                return true;
            }

            // Clicking a node outside the current selection selects just it; clicking one
            // already in a multi-selection keeps the group so it can be dragged as a whole. Either
            // way the Details panel is rebuilt for the selection.
            if (selectedNodes.contains(node)) {
                fireSelectionChanged();
            } else {
                selectOnly(node);
            }

            beginNodeDrag(node, gx, gy);

            return true;
        }

        // Empty canvas. A double-click on a wire drops a reroute knot the wire bends through.
        if (doubleClick && tryInsertRerouteAt(event.x(), event.y(), gx, gy)) {
            return true;
        }

        // Empty canvas: start a marquee selection. Shift makes it additive (keeps the
        // current selection); otherwise it replaces the selection.
        marqueeBase.clear();

        if ((event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0) {
            marqueeBase.addAll(selectedNodes);
        } else {
            clearSelection();
            fireSelectionChanged();
        }

        marqueeing = true;
        marqueeStartGx = gx;
        marqueeStartGy = gy;
        marqueeEndGx = gx;
        marqueeEndGy = gy;

        return true;
    }

    private void clearSelection() {
        for (AbstractNodeWidget n : selectedNodes) n.setFocused(false);
        selectedNodes.clear();
    }

    private void addToSelection(AbstractNodeWidget node) {
        if (selectedNodes.contains(node)) return;
        selectedNodes.add(node);
        node.setFocused(true);
    }

    /// Flips `node`'s membership in the selection: drops it (and its focus outline) when
    /// already selected, adds it otherwise. Backs the Ctrl+click pick-one-by-one gesture.
    private void toggleSelection(AbstractNodeWidget node) {
        if (selectedNodes.remove(node)) {
            node.setFocused(false);
        } else {
            addToSelection(node);
        }
        fireSelectionChanged();
    }

    /// Selects the node holding `widget` and focuses the widget there, ending any other node's edit, see
    /// [AbstractNodeWidget#focusBodyWidget].
    @Override
    public boolean focusEmbedded(GuiEventListener widget) {
        for (AbstractNodeWidget node : nodes) {
            if (!node.focusBodyWidget(widget)) continue;

            for (AbstractNodeWidget other : nodes) {
                if (other != node) other.endInlineEdit();
            }

            selectOnly(node);

            return true;
        }

        return false;
    }

    private void selectOnly(AbstractNodeWidget node) {
        clearSelection();
        addToSelection(node);
        fireSelectionChanged();
    }

    private void setSelection(Collection<AbstractNodeWidget> selection) {
        clearSelection();
        for (AbstractNodeWidget n : selection) addToSelection(n);
        fireSelectionChanged();
    }

    /// Begins dragging `node`'s header: the whole selection moves with it, each node
    /// offset from its own captured start position so their relative layout is preserved.
    private void beginNodeDrag(AbstractNodeWidget node, int gx, int gy) {
        draggingNode = node;
        grabOffsetX = gx - node.getX();
        grabOffsetY = gy - node.getY();
        dragAnchorStartX = node.getX();
        dragAnchorStartY = node.getY();

        dragNodes.clear();
        dragStartPositions.clear();

        for (AbstractNodeWidget n : selectedNodes) {
            dragNodes.add(n);
            dragStartPositions.add(Pair.of(n.getX(), n.getY()));
        }
    }

    /// Recomputes the marquee selection: the base selection (for a Shift-additive drag)
    /// unioned with every node the box currently covers. Rebuilt each frame so shrinking
    /// the box deselects nodes it no longer encloses.
    private void updateMarqueeSelection() {
        int minX = Math.min(marqueeStartGx, marqueeEndGx);
        int maxX = Math.max(marqueeStartGx, marqueeEndGx);
        int minY = Math.min(marqueeStartGy, marqueeEndGy);
        int maxY = Math.max(marqueeStartGy, marqueeEndGy);

        clearSelection();

        for (AbstractNodeWidget n : marqueeBase) addToSelection(n);

        for (AbstractNodeWidget n : nodes) {
            if (nodeIntersects(n, minX, minY, maxX, maxY)) addToSelection(n);
        }

        fireSelectionChanged();
    }

    private static boolean nodeIntersects(AbstractNodeWidget node, int minX, int minY, int maxX, int maxY) {
        int nx0 = node.getX(), ny0 = node.getY();
        int nx1 = nx0 + node.getWidth(), ny1 = ny0 + node.getHeight();

        return nx0 < maxX && nx1 > minX && ny0 < maxY && ny1 > minY;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (wiring) {
            wireEndScreenX = event.x();
            wireEndScreenY = event.y();
            return true;
        }

        if (draggingNode != null) {
            // Re-derive the grabbed node's position from the cursor (so the grab point stays
            // fixed regardless of zoom) and snap it to the grid, then shift every selected
            // node by the same delta to keep their relative layout.
            int targetX = snap(screenToGraphX(event.x()) - grabOffsetX);
            int targetY = snap(screenToGraphY(event.y()) - grabOffsetY);
            int ddx = targetX - dragAnchorStartX;
            int ddy = targetY - dragAnchorStartY;

            for (int i = 0; i < dragNodes.size(); i++) {
                Pair<Integer, Integer> start = dragStartPositions.get(i);
                dragNodes.get(i).setX(start.getFirst() + ddx);
                dragNodes.get(i).setY(start.getSecond() + ddy);
            }

            return true;
        }

        if (marqueeing) {
            marqueeEndGx = screenToGraphX(event.x());
            marqueeEndGy = screenToGraphY(event.y());

            updateMarqueeSelection();

            return true;
        }

        if (rightDown) {
            // A right-press that moves past the slop becomes a pan (which also suppresses
            // the context menu on release).
            if (!rightDragged && Math.abs(event.x() - rightPressX) + Math.abs(event.y() - rightPressY) < PAN_THRESHOLD) {
                return true;
            }

            rightDragged = true;
            panX += (float) dx;
            panY += (float) dy;

            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (wiring) {
            wiring = false;

            int gx = screenToGraphX(event.x());
            int gy = screenToGraphY(event.y());

            AbstractNodeWidget source = wireSourceNode;
            int sourcePin = wireSourcePin;
            boolean fromOutput = wireFromOutput;

            // Dropped on a compatible opposite-side pin → commit the connection.
            PinTarget target = fromOutput ? findInputPinTarget(gx, gy) : findOutputPinTarget(gx, gy);

            if (source != null && target != null) {
                LinkKind kind = targetLinkKind(target);
                if (kind != LinkKind.NONE) {
                    AbstractNodeWidget fromNode = fromOutput ? source : target.node();
                    int fromPin = fromOutput ? sourcePin : target.pin();

                    AbstractNodeWidget toNode = fromOutput ? target.node() : source;
                    int toPin = fromOutput ? target.pin() : sourcePin;

                    if (kind == LinkKind.CONVERT) {
                        insertConverter(fromNode, fromPin, toNode, toPin);
                    } else {
                        addConnection(new Connection(fromNode, fromPin, toNode, toPin));
                    }
                    wireSourceNode = null;

                    return true;
                }
            }

            // Dropped on empty canvas → suggest a node, filtered to the drag context:
            // an execution output asks for a flow node, a data output asks for pure
            // functions taking that value, a data input asks for a value provider.
            if (source != null && nodeAt(gx, gy) == null) {
                AbstractNodeWidget.Pin sourcePinDef = fromOutput ? source.outputPin(sourcePin) : source.inputPin(sourcePin);

                if (fromOutput && sourcePinDef.exec()) {
                    openNodeSuggest((int) event.x(), (int) event.y(), gx, gy, NodeSuggestContext.execution());
                    return true;
                }

                if (fromOutput) {
                    openNodeSuggest((int) event.x(), (int) event.y(), gx, gy, NodeSuggestContext.parameter(sourcePinDef.type()));
                    return true;
                }

                if (!sourcePinDef.exec()) {
                    openNodeSuggest((int) event.x(), (int) event.y(), gx, gy, NodeSuggestContext.value(sourcePinDef.type()));
                    return true;
                }
            }

            // Otherwise (incompatible pin, node body, or unsupported drag): discard.
            clearWire();

            return true;
        }

        // Right button up: a click (no pan) opens the context menu under the cursor.
        if (event.button() == 1 && rightDown) {
            rightDown = false;
            if (!rightDragged) openContextMenu(event);
            rightDragged = false;

            return true;
        }

        if (draggingNode != null) {
            endNodeDrag();

            return true;
        }

        if (marqueeing) {
            marqueeing = false;
            marqueeBase.clear();

            return true;
        }

        return super.mouseReleased(event);
    }

    /// Commits a finished node drag: records a single history entry that moves every dragged
    /// node from its start position to where it landed (a no-op drag records nothing).
    private void endNodeDrag() {
        AbstractNodeWidget anchor = draggingNode;
        draggingNode = null;

        List<AbstractNodeWidget> moved = new ArrayList<>(dragNodes);
        List<Pair<Integer, Integer>> oldPos = new ArrayList<>(dragStartPositions);
        dragNodes.clear();
        dragStartPositions.clear();

        if (anchor.getX() == dragAnchorStartX && anchor.getY() == dragAnchorStartY) return;

        List<Pair<Integer, Integer>> newPos = new ArrayList<>();
        for (AbstractNodeWidget n : moved) newPos.add(Pair.of(n.getX(), n.getY()));

        HistoryManager.getInstance().pushHistory(this, new WorkHistory() {
            @Override
            public void undo() {
                applyPositions(moved, oldPos);
            }

            @Override
            public void redo() {
                applyPositions(moved, newPos);
            }
        });
    }

    private static void applyPositions(List<AbstractNodeWidget> ns, List<Pair<Integer, Integer>> pos) {
        for (int i = 0; i < ns.size(); i++) {
            ns.get(i).setX(pos.get(i).getFirst());
            ns.get(i).setY(pos.get(i).getSecond());
        }
    }

    /// Opens the right-click context menu under the cursor: a dynamic-output pin row offers
    /// "Remove Pin", a named data input pin offers "Record this value", otherwise the node body offers
    /// "Delete" for the selection. Right-clicking a node outside the selection first makes it the
    /// selection so the menu acts on it.
    private void openContextMenu(MouseButtonEvent event) {
        int gx = screenToGraphX(event.x());
        int gy = screenToGraphY(event.y());

        AbstractNodeWidget node = nodeAt(gx, gy);
        if (node == null) {
            // Empty canvas → offer the full action catalogue (flow + pure), as a free-standing
            // spawn (no wire to link, so source stays null).
            if (scriptOwnerType != null) {
                openNodeSuggest((int) event.x(), (int) event.y(), gx, gy, NodeSuggestContext.entryPoint());
            }
            return;
        }

        int outPin = node.outputPinRowAt(gx, gy);
        if (node.supportsDynamicOutputs() && outPin >= 0) {
            if (!selectedNodes.contains(node)) selectOnly(node);
            openPinContextMenu(node, outPin, (int) event.x(), (int) event.y());
            return;
        }

        // A pin with no name has nowhere to write the value, which is what leaves a reroute knot out.
        int recordPin = node.inputPinLabelAt(gx, gy);
        if (recordPin >= 0 && !node.inputPin(recordPin).exec() && !node.inputPin(recordPin).label().isEmpty()) {
            if (!selectedNodes.contains(node)) selectOnly(node);
            openRecordContextMenu(node, recordPin, (int) event.x(), (int) event.y());
            return;
        }

        if (!selectedNodes.contains(node)) selectOnly(node);
        if (selectedNodes.stream().anyMatch(AbstractNodeWidget::isDeletable)) {
            openNodeContextMenu((int) event.x(), (int) event.y());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0 || !isMouseOver(mouseX, mouseY)) return false;

        float oldZoom = zoom;
        float newZoom = Math.clamp(zoom * (scrollY > 0 ? ZOOM_STEP : 1.0F / ZOOM_STEP), MIN_ZOOM, MAX_ZOOM);

        // A step reaching a whole zoom stops on it, where text draws sharp.
        if (scrollY > 0) {
            float whole = (float) Math.floor(newZoom + WHOLE_ZOOM_EPSILON);
            if (whole >= 1.0F && whole > oldZoom) newZoom = whole;
        } else {
            float whole = (float) Math.ceil(newZoom - WHOLE_ZOOM_EPSILON);
            if (whole >= 1.0F && whole < oldZoom) newZoom = whole;
        }

        if (newZoom == oldZoom) return true;

        // Keep the graph point under the cursor anchored while zooming.
        float graphX = (float) (mouseX - getX() - panX) / oldZoom;
        float graphY = (float) (mouseY - getY() - panY) / oldZoom;

        panX = (float) (mouseX - getX()) - graphX * newZoom;
        panY = (float) (mouseY - getY()) - graphY * newZoom;
        zoom = newZoom;

        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        // An inline editor (e.g. a literal node being typed into) takes keys first, so typing never
        // triggers node shortcuts like DELETE. A key it leaves falls through to the canvas.
        AbstractNodeWidget editing = inlineEditingNode();

        if (editing != null && ModalManager.getInstance()
            .inWidgetSpace(this::graphToScreen, () -> editing.inlineKeyPressed(event))) {
            return true;
        }

        // DELETE removes the whole selection (clicking nodes makes this graph the focused
        // leaf, so the key lands here). Non-deletable nodes like the entry point are kept.
        if (EditorKeyMappings.DELETE.matches(event)
            && selectedNodes.stream().anyMatch(AbstractNodeWidget::isDeletable)) {
            deleteSelection();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        AbstractNodeWidget editing = inlineEditingNode();

        if (editing != null && ModalManager.getInstance()
            .inWidgetSpace(this::graphToScreen, () -> editing.inlineCharTyped(event))) {
            return true;
        }

        return super.charTyped(event);
    }

    /// The node currently hosting an inline text editor, or null. At most one node edits at a
    /// time; scanning keeps it correct across deletes / undo without a tracked reference.
    private @Nullable AbstractNodeWidget inlineEditingNode() {
        for (AbstractNodeWidget node : nodes) {
            if (node.isInlineEditing()) return node;
        }

        return null;
    }

    /// Whether a node here is hosting an inline text editor, which owns the keyboard while it is open.
    public boolean isInlineEditing() {
        return inlineEditingNode() != null;
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
