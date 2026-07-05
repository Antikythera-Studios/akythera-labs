package com.yesman.akytheralabs.dataholder.animation;

import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.NumericOperatorNode;
import com.yesman.akythera.api.data.modularize.node.TypeConvertNode;
import com.yesman.akythera.api.data.modularize.node.animgraph.CachedPoseReferenceNode;
import com.yesman.akythera.api.data.modularize.node.ContextVariableAccessNode;
import com.yesman.akythera.api.data.modularize.node.FlowFunctionNode;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.InvertBooleanNode;
import com.yesman.akythera.api.data.modularize.node.LiteralNode;
import com.yesman.akythera.api.data.modularize.node.LogicalOperationNode;
import com.yesman.akythera.api.data.modularize.node.PureFunctionNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ScriptVariableGetterNode;
import com.yesman.akythera.api.data.modularize.node.SelfNode;
import com.yesman.akythera.api.data.modularize.node.SequenceNode;
import com.yesman.akythera.api.data.modularize.node.SupplierAdapterNode;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.AnimationLayerSignature;
import com.yesman.akythera.api.data.modularize.node.animgraph.AnimationStateMachineNode;
import com.yesman.akythera.api.data.modularize.node.animgraph.AnimationStateMachineNode.StateEntry;
import com.yesman.akythera.api.data.modularize.node.animgraph.AnimationStateMachineNode.TransitionEntry;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData.Kind;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData.NodeData;
import com.yesman.akytheralabs.gui.widget.script.StateMachineData;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Lowers the **Anim Graph** (the pose data-flow into an `OUTPUT_POSE` root, plus its state machines)
/// into the engine's runtime nodes — the pose counterpart to the flow-graph [ScriptGraphCompiler].
///
/// Following `PlayerAnimatorScript`, an Anim Graph compiles into **two** of the script's flow nodes
/// (see [Result]): a `construct` step that builds the root [AnimationDriver] once and stores it as a
/// layer (`store_anim_graph_layer`), and the `poseGetter` that reads that layer's pose
/// (`anim_graph_pose`). The driver is built once because `ScriptVariables` reset between lifecycle
/// calls — only the layer map survives.
///
/// Cached poses are likewise a `construct`-time concern: each `CACHED_POSE` sink lowers to a
/// `poseCaches.put(name, <driver>)` step that registers its pose driver in the animator's shared
/// cache map (the engine local `poseCaches`, i.e. `BlendingAnimator#cachedPoseDrivers`), mirroring
/// `PlayerAnimatorScript#construct`. A [CachedPoseReferenceNode] elsewhere wraps that entry in a lazy
/// `CachedPoseReference` at runtime. These puts are emitted **after** the layer stores so the
/// reference wrappers (created while a layer's driver tree is built) are overwritten by the real
/// driver, never the other way round (which would self-reference and loop).
///
/// Pose pins carry an `AnimationDriver`, not a `Pose`: players lower to the `play_*` `ANIM_GRAPH`
/// functions (asset encoded as its identifier `String`), a blendspace's `X` / `Y` float inputs are
/// wrapped in [SupplierAdapterNode] so they stay live, and a State Machine node assembles an
/// [AnimationStateMachineNode] from its [StateMachineData] (each state's pose sub-graph → an
/// `AnimationDriver`, each transition's condition sub-graph → a boolean). Machine references are
/// guarded against cycles.
public final class AnimGraphCompiler {
    private final Map<String, StateMachineData> stateMachines;
    private final Class<?> ownerType;

    /// Machine names currently being compiled — guards a machine that (transitively) references itself.
    private final Set<String> machineStack = new HashSet<>();

    private AnimGraphCompiler(Map<String, StateMachineData> stateMachines, Class<?> ownerType) {
        this.stateMachines = stateMachines;
        this.ownerType = ownerType;
    }

    /// The two flow nodes an Anim Graph contributes — the `construct` suffix that stores the compiled
    /// root driver as a layer and registers every cached pose (null when the graph has neither a wired
    /// Output Pose nor any cached pose) and the `poseGetter` that reads the layer back (always present —
    /// returns the empty pose when no layer was stored).
    public record Result(@Nullable FlowNode storeLayer, FlowNode poseGetter) {}

    public static Result compile(ScriptGraphData poseGraph, Map<String, StateMachineData> stateMachines, Class<?> ownerType) {
        return new AnimGraphCompiler(stateMachines, ownerType).run(poseGraph);
    }

    private Result run(ScriptGraphData poseGraph) {
        GraphCtx ctx = new GraphCtx(poseGraph);

        // Every layer node and (optionally) the Output Pose contribute a `construct`-time step that puts
        // a layer in the animator's layer map. They are gathered into one construct sequence so each
        // layer exists for the whole lifecycle.
        List<FlowNode> stores = new ArrayList<>();

        // Each Anim Layer node puts an engine AnimationLayer into the layer map keyed by its
        // AnimationLayerSignature — its mere presence registers the layer in the constructor, whethern or
        // not its pose output is wired. The layer is built from the node's Source driver via the
        // `animation_layer_create` factory, then `map_put` under the signature literal (see
        // [com.yesman.akytheralabs.gui.widget.script.AnimLayerNodeWidget]).
        for (NodeData n : ctx.nodesOfKind(Kind.ANIM_LAYER)) {
            String signature = n.variable().orElse("");
            if (signature.isEmpty()) continue;

            PureNode source = ctx.compileInputOr(n.id(), 0, AnimationDriver.class);
            stores.add(new FlowFunctionNode(
                func("map_put"),
                List.of(
                    new ContextVariableAccessNode("animLayerMap"),
                    signatureLiteral(signature),
                    new PureFunctionNode(func("animation_layer_create"), List.of(source))),
                null));
        }

        // The poseGetter reads the graph's final pose each frame. When the Output Pose is fed (through
        // reroutes) by an Anim Layer node, read that layer's blended pose straight from the map via
        // `AnimationLayer#getPose`; otherwise store the Output Pose's driver tree as the graph layer and
        // read it back with `anim_graph_pose`.
        FlowNode poseGetter;
        NodeData root = ctx.findKind(Kind.OUTPUT_POSE);
        NodeData outputSource = root == null ? null : ctx.sourceNode(root.id(), 0);

        if (outputSource != null && outputSource.kind() == Kind.ANIM_LAYER
            && !outputSource.variable().orElse("").isEmpty()) {
            poseGetter = new FlowFunctionNode(
                func("animation_layer_get_pose"),
                List.of(new PureFunctionNode(func("map_get"), List.of(
                    new ContextVariableAccessNode("layers"),
                    signatureLiteral(outputSource.variable().orElse(""))))),
                null);
        } else {
            poseGetter = new FlowFunctionNode(
                func("anim_graph_pose"), List.of(new ContextVariableAccessNode("layers")), null);
            if (root != null) {
                PureNode driver = ctx.compileInput(root.id(), 0);
                if (driver != null) {
                    stores.add(new FlowFunctionNode(
                        func("store_anim_graph_layer"),
                        List.of(new ContextVariableAccessNode("animLayerMap"), driver),
                        null));
                }
            }
        }

        // Cached poses register their driver in the animator's shared pose cache (the engine local
        // `poseCaches`) at construct time, after the layer stores so a CachedPoseReference wrapper made
        // while building a layer's driver tree is overwritten by the real driver (see class doc).
        appendCachedPosePuts(ctx, stores);

        FlowNode store = stores.isEmpty() ? null
            : stores.size() == 1 ? stores.getFirst()
            : new SequenceNode(stores);
        return new Result(store, poseGetter);
    }

    /// Lowers every named `CACHED_POSE` sink on the main Anim Graph into a `poseCaches.put(name, driver)`
    /// construct step (`map_put` into the engine `poseCaches` local), where `driver` is the [PureNode]
    /// compiled from its pose input (an empty driver when unwired) — mirroring
    /// `PlayerAnimatorScript#construct`. A [CachedPoseReferenceNode] elsewhere wraps the entry in a lazy
    /// `CachedPoseReference`. Recursion (a cache whose input reaches its own getter) is not rejected here
    /// — it would loop at runtime, mirroring the existing machine-reference caveat.
    private void appendCachedPosePuts(GraphCtx ctx, List<FlowNode> stores) {
        for (NodeData n : ctx.nodesOfKind(Kind.CACHED_POSE)) {
            String name = n.variable().orElse("");
            if (name.isEmpty()) continue;
            stores.add(new FlowFunctionNode(
                func("map_put"),
                List.of(
                    new ContextVariableAccessNode("poseCaches"),
                    stringLiteral(name),
                    ctx.compileInputOr(n.id(), 0, AnimationDriver.class)),
                null));
        }
    }

    /// ***************************************************************
    /// State machines
    /// ***************************************************************

    /// Assembles the named machine into an [AnimationStateMachineNode]. A missing machine or a cyclic
    /// reference degrades to an empty driver so compilation always completes.
    private PureNode stateMachine(String name) {
        StateMachineData data = stateMachines.get(name);
        if (data == null || !machineStack.add(name)) return emptyDriver();

        try {
            List<TransitionEntry> entryTransitions = compileTransitions(data.entryTransitions());
            List<StateEntry> states = new ArrayList<>();
            for (StateMachineData.StateData s : data.states()) {
                PureNode driver = compilePoseGraph(s.poseGraph());
                states.add(new StateEntry(s.state().value(), driver, compileTransitions(s.transitions())));
            }
            return new AnimationStateMachineNode(entryTransitions, states);
        } finally {
            machineStack.remove(name);
        }
    }

    private List<TransitionEntry> compileTransitions(List<StateMachineData.TransitionData> transitions) {
        List<TransitionEntry> out = new ArrayList<>(transitions.size());
        for (StateMachineData.TransitionData t : transitions) {
            out.add(new TransitionEntry(t.to().value(), compileConditionGraph(t.conditionGraph())));
        }
        return out;
    }

    /// A state's `{state}` pose sub-graph → an [AnimationDriver] [PureNode] (empty driver if unwired).
    private PureNode compilePoseGraph(ScriptGraphData graph) {
        GraphCtx ctx = new GraphCtx(graph);
        NodeData root = ctx.findKind(Kind.OUTPUT_POSE);
        if (root == null) return emptyDriver();
        PureNode driver = ctx.compileInput(root.id(), 0);
        return driver != null ? driver : emptyDriver();
    }

    /// A transition's `{from} to {to}` condition sub-graph → a boolean [PureNode]. An unwired / absent
    /// result reads as an unconditional (always-true) transition.
    private PureNode compileConditionGraph(ScriptGraphData graph) {
        GraphCtx ctx = new GraphCtx(graph);
        NodeData root = ctx.findKind(Kind.TRANSITION_RESULT);
        if (root == null) return literalTrue();
        PureNode cond = ctx.compileInput(root.id(), 0);
        return cond != null ? cond : literalTrue();
    }

    /// ***************************************************************
    /// One pose / condition data-flow graph
    /// ***************************************************************
    private final class GraphCtx {
        private final Map<Integer, NodeData> byId = new HashMap<>();
        /// (toId,toPin) → the single (fromId,fromPin) feeding it.
        private final Map<Long, long[]> incoming = new HashMap<>();

        GraphCtx(ScriptGraphData data) {
            for (NodeData n : data.nodes()) byId.put(n.id(), n);
            for (ScriptGraphData.WireData w : data.wires()) {
                incoming.putIfAbsent(key(w.to(), w.toPin()), new long[]{w.from(), w.fromPin()});
            }
        }

        @Nullable NodeData findKind(Kind kind) {
            for (NodeData n : byId.values()) if (n.kind() == kind) return n;
            return null;
        }

        List<NodeData> nodesOfKind(Kind kind) {
            List<NodeData> out = new ArrayList<>();
            for (NodeData n : byId.values()) if (n.kind() == kind) out.add(n);
            return out;
        }

        /// The [PureNode] wired into data input `(toId,toPin)`, or null when nothing is wired.
        @Nullable PureNode compileInput(int toId, int toPin) {
            long[] source = resolveReroute(incoming.get(key(toId, toPin)));
            if (source == null) return null;
            return buildNode(byId.get((int) source[0]), (int) source[1]);
        }

        /// The node feeding data input `(toId,toPin)` after following reroutes, or null when unwired.
        @Nullable NodeData sourceNode(int toId, int toPin) {
            long[] source = resolveReroute(incoming.get(key(toId, toPin)));
            return source == null ? null : byId.get((int) source[0]);
        }

        /// As [#compileInput] but substitutes a typed default when unwired (empty driver for a pose,
        /// `false` for a boolean, `self` for an owner pin, else a zero literal / `self`).
        PureNode compileInputOr(int toId, int toPin, @Nullable Class<?> expectedType) {
            PureNode wired = compileInput(toId, toPin);
            return wired != null ? wired : defaultValue(expectedType);
        }

        private long @Nullable [] resolveReroute(long @Nullable [] source) {
            while (source != null) {
                NodeData node = byId.get((int) source[0]);
                if (node == null || node.kind() != Kind.REROUTE) return source;
                source = incoming.get(key((int) source[0], 0));
            }

            return null;
        }

        private @Nullable PureNode buildNode(@Nullable NodeData n, int fromPin) {
            if (n == null) return null;
            return switch (n.kind()) {
                case ANIM_SEQUENCE_PLAYER ->
                    new PureFunctionNode(func("play_anim_sequence"), List.of(stringLiteral(assetId(n))));
                case BLEND_SPACE_PLAYER -> new PureFunctionNode(func("play_blend_space_2d"), List.of(
                    stringLiteral(assetId(n)),
                    new SupplierAdapterNode(compileInputOr(n.id(), 0, float.class)),
                    new SupplierAdapterNode(compileInputOr(n.id(), 1, float.class))));
                case STATE_MACHINE -> stateMachine(n.variable().orElse(""));
                case CACHED_POSE_GETTER -> new CachedPoseReferenceNode(n.variable().orElse(""));
                case NOT -> new InvertBooleanNode(compileInputOr(n.id(), 0, boolean.class));
                case LOGICAL_OPERATION -> new LogicalOperationNode(
                    compileInputOr(n.id(), 0, boolean.class),
                    compileInputOr(n.id(), 1, boolean.class),
                    ScriptGraphCompiler.logicalOperatorOf(n));
                case PURE_FUNCTION -> pureFunction(n);
                case OPERATOR -> new NumericOperatorNode(
                    ScriptGraphCompiler.operatorOf(n),
                    compileInputOr(n.id(), 0, null),
                    compileInputOr(n.id(), 1, null));
                case COMPARATOR -> new PureFunctionNode(
                    ScriptGraphCompiler.comparatorFunction(n.op().orElse("GREATER")),
                    List.of(
                        ScriptGraphCompiler.comparatorLiteral(),
                        compileInputOr(n.id(), 0, null),
                        compileInputOr(n.id(), 1, null)));
                case TYPE_CONVERT -> {
                    ScriptType from = n.variableType().orElse(null);
                    ScriptType to = n.literalType().orElse(null);
                    yield (from == null || to == null) ? null
                        : new TypeConvertNode(from, to, compileInputOr(n.id(), 0, from.type()));
                }
                case LITERAL -> makeLiteral(n);
                case VARIABLE_GETTER -> new ScriptVariableGetterNode(n.variable().orElse(""));
                case SELF -> SelfNode.INSTANCE;
                default -> null;
            };
        }

        private PureNode pureFunction(NodeData n) {
            ScriptFunction fn = n.function().orElse(null);
            if (fn == null) return defaultValue(null);
            List<Class<?>> parameters = fn.functionSignature().parameters();
            List<PureNode> params = new ArrayList<>(parameters.size());
            for (int slot = 0; slot < parameters.size(); slot++) {
                params.add(compileInputOr(n.id(), slot, parameters.get(slot)));
            }
            return new PureFunctionNode(fn, params);
        }

        private PureNode defaultValue(@Nullable Class<?> expectedType) {
            if (expectedType == AnimationDriver.class) return emptyDriver();
            if (expectedType == boolean.class || expectedType == Boolean.class) {
                return new LiteralNode<>(AkytheraScriptTypes.BOOLEAN.value(), Boolean.FALSE);
            }
            if (expectedType != null && expectedType != Object.class && expectedType.isAssignableFrom(ownerType)) {
                return SelfNode.INSTANCE;
            }
            return SelfNode.INSTANCE;
        }
    }

    /// ***************************************************************
    /// Leaf builders / helpers
    /// ***************************************************************

    private static PureNode emptyDriver() {
        return new PureFunctionNode(func("empty_driver"), List.of());
    }

    private static PureNode literalTrue() {
        return new LiteralNode<>(AkytheraScriptTypes.BOOLEAN.value(), Boolean.TRUE);
    }

    private static PureNode stringLiteral(String value) {
        return new LiteralNode<>(AkytheraScriptTypes.STRING.value(), value);
    }

    /// A literal carrying the [AnimationLayerSignature] resolved from its identifier string — the map
    /// key shared by an Anim Layer node's `map_put` (construct) and `map_get` (pose read). Serializes
    /// through the [AkytheraScriptTypes#ANIM_LAYER_SIGNATURE] script type's name codec.
    private static PureNode signatureLiteral(String identifier) {
        // Resolve through project ∪ manager so an editor-created (labs) layer signature resolves too.
        Holder<AnimationLayerSignature> holder = AkytheraProject.getCurrentProject()
            .getDataBlockHolder(AnimationLayerSignature.ENUM_MANAGER, Identifier.parse(identifier));
        AnimationLayerSignature signature = Objects.requireNonNull(
            holder, () -> "Unknown anim layer signature " + identifier).value();
        return new LiteralNode<>(AkytheraScriptTypes.ANIM_LAYER_SIGNATURE.value(), signature);
    }

    /// Editor-only pre-compile gate: every data block reference (state signatures, transition
    /// targets, and root pose-graph anim layer signatures) whose holder resolves in **neither** the
    /// project nor the engine manager — i.e. a block **deleted mid-session**. Callers report the
    /// returned ids and abort the compile, so a later `value()` never hits an unbound holder.
    public static List<Identifier> findMissingDataBlocks(
        ScriptGraphData poseGraph, Map<String, StateMachineData> stateMachines
    ) {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        List<Identifier> missing = new ArrayList<>();

        // Anim layer signatures are the `variable` of each Kind.ANIM_LAYER node (see #appendLayerPuts).
        for (NodeData n : poseGraph.nodes()) {
            if (n.kind() != Kind.ANIM_LAYER) continue;
            String sig = n.variable().orElse("");
            if (sig.isEmpty()) continue;

            Identifier id = Identifier.tryParse(sig);
            if (id != null && unbound(project.getDataBlockHolder(AnimationLayerSignature.ENUM_MANAGER, id))) {
                missing.add(id);
            }
        }

        for (StateMachineData machine : stateMachines.values()) {
            for (StateMachineData.StateData s : machine.states()) {
                if (unbound(s.state())) missing.add(idOf(s.state()));

                for (StateMachineData.TransitionData t : s.transitions()) {
                    if (unbound(t.to())) missing.add(idOf(t.to()));
                }
            }

            for (StateMachineData.TransitionData t : machine.entryTransitions()) {
                if (unbound(t.to())) missing.add(idOf(t.to()));
            }
        }

        return missing;
    }

    /// A holder that's null (resolved to nothing) or an unbound `ProjectObjectHolder` (its entry was
    /// deleted) — i.e. its `value()` would throw.
    private static boolean unbound(@Nullable Holder<?> holder) {
        return holder == null || !holder.isBound();
    }

    private static Identifier idOf(Holder<?> holder) {
        return holder.unwrapKey().map(k -> k.identifier()).orElseThrow();
    }

    private static String assetId(NodeData n) {
        return n.asset().map(Identifier::toString).orElse("");
    }

    private static PureNode makeLiteral(NodeData n) {
        var type = n.literalType().orElse(null);
        if (type == null) return new LiteralNode<>(AkytheraScriptTypes.BOOLEAN.value(), Boolean.FALSE);
        return new LiteralNode<>(type, ScriptGraphCompiler.parseLiteral(type.type(), n.literalValue().orElse("")));
    }

    /// The registered [ScriptFunction] with the given registry path (e.g. `play_anim_sequence`).
    private static ScriptFunction func(String path) {
        for (var entry : AkytheraRegistries.Frozen.SCRIPT_FUNCTION.entrySet()) {
            if (entry.getKey().identifier().getPath().equals(path)) return entry.getValue();
        }
        throw new IllegalStateException("Missing anim-graph script function: " + path);
    }

    private static long key(int id, int pin) {
        return ((long) id << 32) | (pin & 0xFFFFFFFFL);
    }
}