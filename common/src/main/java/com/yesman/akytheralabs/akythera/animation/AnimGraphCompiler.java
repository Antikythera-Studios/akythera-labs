package com.yesman.akytheralabs.akythera.animation;

import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.data.modularize.node.*;
import com.yesman.akythera.api.data.modularize.node.animgraph.AnimationStateMachineNode;
import com.yesman.akythera.api.data.modularize.node.animgraph.AnimationStateMachineNode.StateEntry;
import com.yesman.akythera.api.data.modularize.node.animgraph.AnimationStateMachineNode.TransitionEntry;
import com.yesman.akythera.api.data.modularize.node.animgraph.CachedPoseReferenceNode;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.StateMachineData;
import com.yesman.akytheralabs.gui.widget.script.node.*;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.*;

/// Lowers the **Anim Graph** — the pose data-flow into an `OUTPUT_POSE` root, plus its state
/// machines — into the engine's runtime nodes.
///
/// The graph contributes two flow nodes (see [Result]): a `construct` step that builds the root
/// [AnimationDriver] and stores it as a layer (`store_anim_graph_layer`), and a `poseGetter` that
/// reads that layer's pose each frame (`anim_graph_pose`). Everything that has to outlive a single
/// lifecycle call belongs in the construct step — `ScriptVariables` reset between them.
///
/// Each `CACHED_POSE` sink lowers to a construct-time `poseCaches.put(name, <driver>)` into the
/// animator's shared cache map (`BlendingAnimator#cachedPoseDrivers`), which a
/// [CachedPoseReferenceNode] wraps in a lazy `CachedPoseReference` at runtime. The puts are emitted
/// after the layer stores, so a wrapper created while a layer's driver tree was built is overwritten
/// by the real driver.
///
/// Pose pins carry an `AnimationDriver`, not a `Pose`: players lower to the `play_*` `ANIM_GRAPH`
/// functions (asset encoded as its identifier `String`), a blendspace's `X` / `Y` float inputs are
/// wrapped in [SupplierAdapterNode], and a State Machine node assembles an
/// [AnimationStateMachineNode] from its [StateMachineData]. Machine references are guarded against
/// cycles.
///
/// A machine's state groups are expanded here and nowhere else: every transition a
/// [StateMachineData.GroupData] holds is copied onto each state naming that group, so the engine only
/// ever sees per-state transitions.
///
/// Each canvas lowers under its [ScriptCanvasKeys] key, which the deferred sub-trees — a blendspace's
/// inputs, a transition's condition — carry into the pack so their samples can be recorded against
/// the canvas they came from.
public final class AnimGraphCompiler {
    private final Map<String, StateMachineData> stateMachines;
    private final Class<?> ownerType;

    /// Machine names currently being compiled — guards a machine that (transitively) references itself.
    private final Set<String> machineStack = new HashSet<>();

    private AnimGraphCompiler(Map<String, StateMachineData> stateMachines, Class<?> ownerType) {
        this.stateMachines = stateMachines;
        this.ownerType = ownerType;
    }

    /// The `construct` suffix that stores the compiled root driver as a layer and registers every
    /// cached pose — null when the graph has neither a wired Output Pose nor any cached pose — and the
    /// `poseGetter` that reads the layer back, which returns the empty pose when no layer was stored.
    public record Result(@Nullable FlowNode storeLayer, FlowNode poseGetter) {}

    public static Result compile(ScriptGraphData poseGraph, Map<String, StateMachineData> stateMachines, Class<?> ownerType) {
        return new AnimGraphCompiler(stateMachines, ownerType).run(poseGraph);
    }

    private Result run(ScriptGraphData poseGraph) {
        GraphCtx ctx = new GraphCtx(poseGraph, ScriptCanvasKeys.ANIM_GRAPH);

        // The construct-time steps that fill the animator's layer map and pose cache.
        List<FlowNode> stores = new ArrayList<>();

        // An Anim Layer node builds an engine AnimationLayer from its Source driver and map_puts it
        // under its AnimationLayerSignature, whether or not its pose output is wired.
        for (GraphNode row : ctx.nodesOf(AnimLayerNodeData.class)) {
            PureNode signatureNode =
                AnimGraphFunctions.signatureLiteral(((AnimLayerNodeData) row.data()).signature());
            if (signatureNode == null) continue; // unresolved → layer skipped (logged in the literal)

            PureNode source = ctx.compileInputOr(row.id(), 0, AnimationDriver.class);
            stores.add(new FlowFunctionNode(
                AnimGraphFunctions.func("map_put"),
                List.of(
                    new ContextVariableAccessNode("animLayerMap"),
                    signatureNode,
                    new PureFunctionNode(AnimGraphFunctions.func("animation_layer_create"), List.of(source))),
                null));
        }

        // When the Output Pose is fed (through reroutes) by an Anim Layer node, the poseGetter reads
        // that layer's blended pose straight from the map; otherwise the Output Pose's driver tree is
        // stored as the graph layer and read back with `anim_graph_pose`.
        FlowNode poseGetter;
        GraphNode root = ctx.findOne(OutputPoseNodeData.class);
        GraphNode outputSource = root == null ? null : ctx.sourceNode(root.id(), 0);

        // Null when the feeding layer's signature is unresolved, which falls through to the generic
        // layer store below.
        PureNode outputLayerSignature = null;
        if (outputSource != null && outputSource.data() instanceof AnimLayerNodeData layer) {
            outputLayerSignature = AnimGraphFunctions.signatureLiteral(layer.signature());
        }

        if (outputLayerSignature != null) {
            poseGetter = new FlowFunctionNode(
                AnimGraphFunctions.func("animation_layer_get_pose"),
                List.of(new PureFunctionNode(AnimGraphFunctions.func("map_get"), List.of(
                    new ContextVariableAccessNode("layers"),
                    outputLayerSignature))),
                null);
        } else {
            poseGetter = new FlowFunctionNode(
                AnimGraphFunctions.func("anim_graph_pose"), List.of(new ContextVariableAccessNode("layers")), null);
            if (root != null) {
                PureNode driver = ctx.compileInput(root.id(), 0);
                if (driver != null) {
                    stores.add(new FlowFunctionNode(
                        AnimGraphFunctions.func("store_anim_graph_layer"),
                        List.of(new ContextVariableAccessNode("animLayerMap"), driver),
                        null));
                }
            }
        }

        appendCachedPosePuts(ctx, stores); // after the layer stores, see the class doc

        FlowNode store = stores.isEmpty() ? null
            : stores.size() == 1 ? stores.getFirst()
            : new SequenceNode(stores);
        return new Result(store, poseGetter);
    }

    /// Lowers every named `CACHED_POSE` sink into a `poseCaches.put(name, driver)` construct step,
    /// where `driver` is the [PureNode] compiled from its pose input (an empty driver when unwired).
    /// A cache whose input reaches its own getter is accepted here and loops at runtime.
    private void appendCachedPosePuts(GraphCtx ctx, List<FlowNode> stores) {
        for (GraphNode row : ctx.nodesOf(CachedPoseNodeData.class)) {
            String name = ((CachedPoseNodeData) row.data()).cacheName();
            if (name.isEmpty()) continue;

            stores.add(new FlowFunctionNode(
                AnimGraphFunctions.func("map_put"),
                List.of(
                    new ContextVariableAccessNode("poseCaches"),
                    AnimGraphFunctions.stringLiteral(name),
                    ctx.compileInputOr(row.id(), 0, AnimationDriver.class)),
                null));
        }
    }

    // ***************************************************************
    // State machines
    // ***************************************************************

    /// Assembles the named machine into an [AnimationStateMachineNode]. A missing machine or a cyclic
    /// reference yields an empty driver.
    private PureNode stateMachine(String name) {
        StateMachineData data = stateMachines.get(name);
        if (data == null || !machineStack.add(name)) return AnimGraphFunctions.emptyDriver();

        try {
            List<TransitionEntry> entryTransitions =
                compileTransitions(data.entryTransitions(), name, ScriptCanvasKeys.ENTRY);

            Map<String, StateMachineData.GroupData> groups = new HashMap<>();
            for (StateMachineData.GroupData group : data.groups()) groups.put(group.name(), group);

            List<StateEntry> states = new ArrayList<>();
            for (StateMachineData.StateData s : data.states()) {
                // A dangling state signature drops the state and keeps the rest of the machine.
                if (!s.state().isBound()) {
                    AkytheraLabs.LOGGER.warn("Skipping unresolved state {} in state machine {}", idOf(s.state()), name);
                    continue;
                }
                String stateName = ScriptCanvasKeys.stateName(s.state());
                PureNode driver = compilePoseGraph(s.poseGraph(), ScriptCanvasKeys.statePose(name, stateName));

                // The state's own transitions come first: a group edge is the fallback its members
                // share, so a state that answers a condition itself wins over the group's answer.
                List<TransitionEntry> outgoing =
                    new ArrayList<>(compileTransitions(s.transitions(), name, stateName));
                outgoing.addAll(compileGroupTransitions(groups.get(s.group()), name, stateName));

                states.add(new StateEntry(s.state().value(), driver, outgoing));
            }
            return new AnimationStateMachineNode(entryTransitions, states);
        } finally {
            machineStack.remove(name);
        }
    }

    /// The transitions a state inherits from the group it belongs to: one copy of every group edge,
    /// each keeping the group's single authored condition **and** its canvas key, so the values a run
    /// records land on the one sub-tab that draws it. An edge back onto the inheriting state is
    /// dropped — a machine never transitions a state to itself. Empty for a state in no group.
    private List<TransitionEntry> compileGroupTransitions(
        StateMachineData.@Nullable GroupData group, String machineName, String stateName
    ) {
        if (group == null) return List.of();

        List<TransitionEntry> out = new ArrayList<>(group.transitions().size());
        for (StateMachineData.TransitionData t : group.transitions()) {
            if (!t.to().isBound()) {
                AkytheraLabs.LOGGER.warn("Skipping transition to unresolved state {}", idOf(t.to()));
                continue;
            }
            String toName = ScriptCanvasKeys.stateName(t.to());
            if (toName.equals(stateName)) continue;

            String canvasKey = ScriptCanvasKeys.groupTransition(machineName, group.name(), toName);
            out.add(new TransitionEntry(
                t.to().value(), compileConditionGraph(t.conditionGraph(), canvasKey),
                t.blendTime(), t.transferElapsedTime(), t.transitionPriority(), canvasKey));
        }
        return out;
    }

    private List<TransitionEntry> compileTransitions(
        List<StateMachineData.TransitionData> transitions, String machineName, String fromName
    ) {
        List<TransitionEntry> out = new ArrayList<>(transitions.size());
        for (StateMachineData.TransitionData t : transitions) {
            if (!t.to().isBound()) {
                AkytheraLabs.LOGGER.warn("Skipping transition to unresolved state {}", idOf(t.to()));
                continue;
            }
            String canvasKey =
                ScriptCanvasKeys.transition(machineName, fromName, ScriptCanvasKeys.stateName(t.to()));
            out.add(new TransitionEntry(
                t.to().value(), compileConditionGraph(t.conditionGraph(), canvasKey),
                t.blendTime(), t.transferElapsedTime(), t.transitionPriority(), canvasKey));
        }
        return out;
    }

    /// A state's `{state}` pose sub-graph → an [AnimationDriver] [PureNode] (empty driver if unwired).
    private PureNode compilePoseGraph(ScriptGraphData graph, String canvasKey) {
        GraphCtx ctx = new GraphCtx(graph, canvasKey);
        GraphNode root = ctx.findOne(OutputPoseNodeData.class);
        if (root == null) return AnimGraphFunctions.emptyDriver();
        PureNode driver = ctx.compileInput(root.id(), 0);
        return driver != null ? driver : AnimGraphFunctions.emptyDriver();
    }

    /// A transition's `{from} to {to}` condition sub-graph → a boolean [PureNode]. An unwired / absent
    /// result reads as an unconditional (always-true) transition.
    private PureNode compileConditionGraph(ScriptGraphData graph, String canvasKey) {
        GraphCtx ctx = new GraphCtx(graph, canvasKey);
        GraphNode root = ctx.findOne(TransitionResultNodeData.class);
        if (root == null) return AnimGraphFunctions.literalTrue();
        PureNode cond = ctx.compileInput(root.id(), 0);
        return cond != null ? cond : AnimGraphFunctions.literalTrue();
    }

    // ***************************************************************
    // One pose / condition data-flow graph
    // ***************************************************************

    /// The pure-data lowering of one pose / condition sub-graph. The nodes lower themselves; this
    /// canvas adds the state machines and the pose-typed defaults for unwired inputs.
    private final class GraphCtx extends PureGraphCompiler {
        GraphCtx(ScriptGraphData data, String canvasKey) {
            super(data, AnimGraphCompiler.this.ownerType, canvasKey);
        }

        /// The first row carrying `type`, or null.
        <D extends NodeData> @Nullable GraphNode findOne(Class<D> type) {
            for (GraphNode n : byId.values()) {
                if (type.isInstance(n.data())) return n;
            }

            return null;
        }

        /// Every row carrying `type`.
        <D extends NodeData> List<GraphNode> nodesOf(Class<D> type) {
            List<GraphNode> out = new ArrayList<>();
            for (GraphNode n : byId.values()) {
                if (type.isInstance(n.data())) out.add(n);
            }

            return out;
        }

        @Override
        public PureNode stateMachine(String name) {
            return AnimGraphCompiler.this.stateMachine(name);
        }

        /// A typed default for an unwired input: empty driver for a pose, `false` for a boolean,
        /// else `self` (owner pins and everything untyped).
        @Override
        public PureNode defaultValue(@Nullable Class<?> expectedType) {
            if (expectedType == AnimationDriver.class) return AnimGraphFunctions.emptyDriver();
            if (expectedType == boolean.class || expectedType == Boolean.class) {
                return new LiteralNode<>(AkytheraScriptTypes.BOOLEAN.value(), Boolean.FALSE);
            }

            return SelfNode.INSTANCE;
        }
    }

    // ***************************************************************
    // Reference checks
    // ***************************************************************

    /// Every reference in the graph that resolves to nothing: state signatures, transition targets,
    /// and whatever the pose nodes report through [NodeData#missingReferences].
    public static List<Identifier> findMissingReferences(
        ScriptGraphData poseGraph, Map<String, StateMachineData> stateMachines
    ) {
        List<Identifier> missing = new ArrayList<>(poseGraph.missingReferences().toList());

        for (StateMachineData machine : stateMachines.values()) {
            for (StateMachineData.StateData s : machine.states()) {
                if (unbound(s.state())) missing.add(idOf(s.state()));

                for (StateMachineData.TransitionData t : s.transitions()) {
                    if (unbound(t.to())) missing.add(idOf(t.to()));
                }

                s.poseGraph().missingReferences().forEach(missing::add);
            }

            for (StateMachineData.TransitionData t : machine.entryTransitions()) {
                if (unbound(t.to())) missing.add(idOf(t.to()));
            }

            for (StateMachineData.GroupData group : machine.groups()) {
                for (StateMachineData.TransitionData t : group.transitions()) {
                    if (unbound(t.to())) missing.add(idOf(t.to()));
                }
            }
        }

        return missing;
    }

    /// A holder whose `value()` would throw: null, or an unbound `ProjectObjectHolder`.
    private static boolean unbound(@Nullable Holder<?> holder) {
        return holder == null || !holder.isBound();
    }

    private static Identifier idOf(Holder<?> holder) {
        return holder.unwrapKey().map(k -> k.identifier()).orElseThrow();
    }
}
