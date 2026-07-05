package com.yesman.akytheralabs.dataholder.animation;

import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.impl.NumericComparators;
import com.yesman.akythera.api.data.modularize.node.*;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akythera.core.util.helper.MathUtils;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData.Kind;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData.NodeData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/// Lowers one flattened editor flow ([ScriptGraphData]) into the engine's *tree* form (a
/// [FlowNode]), the "compile" half of the editor ↔ runtime split.
///
/// **Shared outputs** (a `PURE_FUNCTION` pin feeding more than one consumer) are de-duplicated by
/// compiler-side common-subexpression elimination: the value is computed once into a
/// `ScriptVariableSetterNode("cse_<id>_<pin>", …)` and every use reads it back through a
/// [ContextVariableAccessNode]. The setter is placed just before the **lowest common ancestor** of
/// all its uses in the structured flow tree — for a linear flow that is the first using statement
/// (captured exactly at first use); when uses straddle a [BranchNode] it is the statement before
/// the branch, so the value is available on every arm. This is correct regardless of branching,
/// unlike the engine's old runtime `return_%d` counter, which has been removed.
public final class ScriptGraphCompiler {
    /// Which event flow to follow when a graph holds more than one entry node. The Event Graph canvas
    /// hosts both the per-tick **Update** entry (the one exposing data outputs — `frameTime` /
    /// `partialTick`) and the data-less **Constructor** entry; they are compiled into separate flows.
    public enum EntrySelector { UPDATE, CONSTRUCT }

    private final ScriptGraphData data;
    private final Class<?> ownerType;
    private final EntrySelector entrySelector;

    /// When non-null, roots the flow at the first node this predicate accepts (used by graphs whose
    /// entries are told apart by an explicit marker rather than by the [EntrySelector] label
    /// heuristic — e.g. the entity-patch Event Graph's three param-less entries). Overrides
    /// [#entrySelector] when set.
    private final @Nullable Predicate<NodeData> entryMatch;

    private final Map<Integer, NodeData> byId = new HashMap<>();
    /// (fromId,fromPin) → list of (toId,toPin) it feeds.
    private final Map<Long, List<long[]>> outgoing = new HashMap<>();
    /// (toId,toPin) → the single (fromId,fromPin) feeding it (data inputs hold one source).
    private final Map<Long, long[]> incoming = new HashMap<>();

    /// All flow statements in build order (pass 1), used to attribute shared-output uses.
    private final List<Stmt> allStmts = new ArrayList<>();
    /// Shared output pin `(id,pin)` → the statement before which its cache setter is emitted.
    private final Map<Long, Stmt> placement = new HashMap<>();
    /// Statement → the shared output pins hoisted before it, ordered producer-first.
    private final Map<Stmt, List<Long>> hoistsAt = new HashMap<>();

    /// A node in the structured flow tree (mirrors the emitted [FlowNode] shape). `nodeId < 0`
    /// marks a synthetic `SEQUENCE` slot (a chain, a branch arm, or a `next` body).
    private static final class Stmt {
        final Kind kind;
        final int nodeId;
        final @Nullable Stmt parent;
        final int depth;
        final List<Stmt> children = new ArrayList<>();
        /// Packed (nodeId,inputPin) data roots this statement reads (params / condition / value).
        final List<Long> dataPins = new ArrayList<>();

        Stmt(Kind kind, int nodeId, @Nullable Stmt parent) {
            this.kind = kind;
            this.nodeId = nodeId;
            this.parent = parent;
            this.depth = parent == null ? 0 : parent.depth + 1;
        }
    }

    private ScriptGraphCompiler(ScriptGraphData data, Class<?> ownerType, EntrySelector entrySelector, @Nullable Predicate<NodeData> entryMatch) {
        this.data = data;
        this.ownerType = ownerType;
        this.entrySelector = entrySelector;
        this.entryMatch = entryMatch;

        for (NodeData n : data.nodes()) byId.put(n.id(), n);

        for (ScriptGraphData.WireData w : data.wires()) {
            outgoing.computeIfAbsent(key(w.from(), w.fromPin()), _ -> new ArrayList<>())
                .add(new long[]{w.to(), w.toPin()});
            incoming.putIfAbsent(key(w.to(), w.toPin()), new long[]{w.from(), w.fromPin()});
        }
    }

    /// Compiles the **Update** flow of `data` (see [#compile(ScriptGraphData, Class, EntrySelector)]).
    public static FlowNode compile(ScriptGraphData data, Class<?> ownerType) {
        return compile(data, ownerType, EntrySelector.UPDATE);
    }

    /// Compiles the flow of `data` rooted at the `entrySelector` entry into a flow root, or an empty
    /// [SequenceNode] when the canvas is empty or has no matching entry. Never throws — an incomplete
    /// graph degrades to best-effort placeholder nodes.
    public static FlowNode compile(ScriptGraphData data, Class<?> ownerType, EntrySelector entrySelector) {
        if (data == null || data.isEmpty()) return new SequenceNode(List.of());
        return new ScriptGraphCompiler(data, ownerType, entrySelector, null).run();
    }

    /// Compiles the flow rooted at the **first** entry node `entryMatch` accepts into a flow root, or
    /// an empty [SequenceNode] when the canvas is empty or holds no matching entry. For graphs whose
    /// entries carry an explicit kind marker (e.g. the entity-patch Event Graph) rather than being told
    /// apart by their output labels. Never throws — an incomplete graph degrades to best-effort nodes.
    public static FlowNode compile(ScriptGraphData data, Class<?> ownerType, Predicate<NodeData> entryMatch) {
        if (data == null || data.isEmpty()) return new SequenceNode(List.of());
        return new ScriptGraphCompiler(data, ownerType, EntrySelector.UPDATE, entryMatch).run();
    }

    private FlowNode run() {
        NodeData entry = findEntry();
        if (entry == null) return new SequenceNode(List.of());

        Stmt root = new Stmt(Kind.SEQUENCE, -1, null);
        allStmts.add(root);
        skelChainInto(entry.id(), 0, root);

        computePlacements();
        return emit(root);
    }

    /// The entry node to root the flow at, chosen by [#entrySelector]: the Update entry is the one with
    /// a non-blank data-output label (`frameTime` / `partialTick`), the Constructor entry the data-less
    /// one. UPDATE falls back to the first entry (older single-entry saves); CONSTRUCT has no fallback
    /// (no dedicated Constructor entry ⇒ no construct flow).
    private @Nullable NodeData findEntry() {
        if (entryMatch != null) {
            for (NodeData n : data.nodes()) {
                if (entryMatch.test(n)) return n;
            }
            return null;
        }

        NodeData firstEntry = null;
        for (NodeData n : data.nodes()) {
            if (n.kind() != Kind.ENTRY) continue;
            if (firstEntry == null) firstEntry = n;

            boolean hasDataOutputs = n.outLabels().stream().anyMatch(label -> !label.isBlank());
            if (entrySelector == EntrySelector.CONSTRUCT && !hasDataOutputs) return n;
            if (entrySelector == EntrySelector.UPDATE && hasDataOutputs) return n;
        }
        return entrySelector == EntrySelector.UPDATE ? firstEntry : null;
    }

    /// ***************************************************************
    /// Pass 1 — flow skeleton
    /// ***************************************************************

    /// Appends the flow statements wired to execution output `(fromId,fromPin)` as children of
    /// `parent`, in target-id order (deterministic), splicing transparently through reroute knots.
    private void skelChainInto(int fromId, int fromPin, Stmt parent) {
        List<long[]> targets = new ArrayList<>(outgoing.getOrDefault(key(fromId, fromPin), List.of()));
        targets.sort((a, b) -> Long.compare(a[0], b[0]));

        for (long[] target : targets) {
            NodeData node = byId.get((int) target[0]);
            if (node == null) continue;
            if (node.kind() == Kind.REROUTE) {
                skelChainInto(node.id(), 0, parent);
                continue;
            }
            parent.children.add(skelNode(node, parent));
        }
    }

    private Stmt skelNode(NodeData node, Stmt parent) {
        Stmt s = new Stmt(node.kind(), node.id(), parent);
        allStmts.add(s);

        switch (node.kind()) {
            case SEQUENCE -> {
                int outs = Math.max(1, node.outputs());
                for (int pin = 0; pin < outs; pin++) skelChainInto(node.id(), pin, s);
            }
            case BRANCH -> {
                s.dataPins.add(key(node.id(), 1));
                Stmt whenTrue = newSeqChild(s);
                Stmt whenFalse = newSeqChild(s);
                skelChainInto(node.id(), 0, whenTrue);
                skelChainInto(node.id(), 1, whenFalse);
            }
            case FLOW_FUNCTION -> {
                node.function().ifPresent(fn -> {
                    int count = fn.functionSignature().parameters().size();
                    for (int slot = 0; slot < count; slot++) s.dataPins.add(key(node.id(), 1 + slot));
                });
                skelChainInto(node.id(), 0, newSeqChild(s));
            }
            case VARIABLE_SETTER -> {
                s.dataPins.add(key(node.id(), 1));
                skelChainInto(node.id(), 0, newSeqChild(s));
            }
            default -> { }
        }
        return s;
    }

    /// A fresh synthetic `SEQUENCE` slot appended as a child of `parent` (a branch arm or a `next`
    /// body); registered in [#allStmts] so hoists can be attributed to it.
    private Stmt newSeqChild(Stmt parent) {
        Stmt s = new Stmt(Kind.SEQUENCE, -1, parent);
        allStmts.add(s);
        parent.children.add(s);
        return s;
    }

    /// ***************************************************************
    /// Placement — attribute uses, LCA, producer-first ordering
    /// ***************************************************************

    private void computePlacements() {
        // 1. Attribute every shared output to each statement that (transitively) reads it.
        Map<Long, Set<Stmt>> useStmts = new HashMap<>();
        for (Stmt s : allStmts) {
            for (long pin : s.dataPins) {
                Set<Long> reached = new HashSet<>();
                collectReachableShared((int) (pin >> 32), (int) pin, reached, new HashSet<>());
                for (long out : reached) useStmts.computeIfAbsent(out, _ -> new HashSet<>()).add(s);
            }
        }

        // 2. Place each shared output at the LCA of its uses.
        for (Map.Entry<Long, Set<Stmt>> e : useStmts.entrySet()) {
            placement.put(e.getKey(), lca(e.getValue()));
        }

        // 3. Producer-first ordering for co-located hoists: topo-sort on direct producer→consumer
        //    edges (a shared output → the shared outputs that directly read it).
        Map<Long, List<Long>> consumersOf = new HashMap<>();
        for (long out : useStmts.keySet()) {
            int id = (int) (out >> 32);
            NodeData node = byId.get(id);
            if (node == null) continue;
            ScriptFunction fn = node.function().orElse(null);
            if (fn == null) continue;
            Set<Long> direct = new HashSet<>();
            int count = fn.functionSignature().parameters().size();
            for (int slot = 0; slot < count; slot++) collectDirectShared(id, slot, direct);
            for (long producer : direct) consumersOf.computeIfAbsent(producer, _ -> new ArrayList<>()).add(out);
        }
        Map<Long, Integer> order = topoOrder(useStmts.keySet(), consumersOf);

        // 4. Group hoists by placement, producers (lower order index) first.
        for (Map.Entry<Long, Stmt> e : placement.entrySet()) {
            hoistsAt.computeIfAbsent(e.getValue(), _ -> new ArrayList<>()).add(e.getKey());
        }
        for (List<Long> keys : hoistsAt.values()) {
            keys.sort((a, b) -> Integer.compare(order.getOrDefault(a, 0), order.getOrDefault(b, 0)));
        }
    }

    /// Shared outputs reachable from data input `(toId,toPin)`, descending **through** shared
    /// outputs so a value nested under another shared value is still attributed to this use site.
    private void collectReachableShared(int toId, int toPin, Set<Long> out, Set<Integer> visited) {
        long[] source = resolveReroute(incoming.get(key(toId, toPin)));
        if (source == null) return;
        int fromId = (int) source[0];
        int fromPin = (int) source[1];
        if (isShared(fromId, fromPin)) out.add(key(fromId, fromPin));

        NodeData node = byId.get(fromId);
        if (node == null || !visited.add(fromId)) return;
        switch (node.kind()) {
            case PURE_FUNCTION -> node.function().ifPresent(fn -> {
                int count = fn.functionSignature().parameters().size();
                for (int slot = 0; slot < count; slot++) collectReachableShared(fromId, slot, out, visited);
            });
            case OPERATOR, LOGICAL_OPERATION, COMPARATOR -> {
                collectReachableShared(fromId, 0, out, visited);
                collectReachableShared(fromId, 1, out, visited);
            }
            case TYPE_CONVERT -> collectReachableShared(fromId, 0, out, visited);
            default -> { }
        }
    }

    /// The shared outputs feeding `(toId,toPin)` **directly** — traversal stops at the first shared
    /// output on each path (used to build producer→consumer dependency edges).
    private void collectDirectShared(int toId, int toPin, Set<Long> out) {
        long[] source = resolveReroute(incoming.get(key(toId, toPin)));
        if (source == null) return;
        int fromId = (int) source[0];
        int fromPin = (int) source[1];
        if (isShared(fromId, fromPin)) {
            out.add(key(fromId, fromPin));
            return;
        }
        NodeData node = byId.get(fromId);
        if (node == null) return;
        switch (node.kind()) {
            case PURE_FUNCTION -> node.function().ifPresent(fn -> {
                int count = fn.functionSignature().parameters().size();
                for (int slot = 0; slot < count; slot++) collectDirectShared(fromId, slot, out);
            });
            case OPERATOR, LOGICAL_OPERATION, COMPARATOR -> {
                collectDirectShared(fromId, 0, out);
                collectDirectShared(fromId, 1, out);
            }
            case TYPE_CONVERT -> collectDirectShared(fromId, 0, out);
            default -> { }
        }
    }

    /// A producer-first index (smaller = computed earlier) over the shared outputs, from a DFS
    /// topo-sort of producer→consumer edges. Acyclic by construction (pure data is a DAG).
    private Map<Long, Integer> topoOrder(Set<Long> nodes, Map<Long, List<Long>> consumersOf) {
        List<Long> postOrder = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        for (long n : nodes) topoVisit(n, consumersOf, visited, postOrder);
        // post-order lists a producer after its consumers; reverse so producers come first.
        Map<Long, Integer> index = new HashMap<>();
        for (int i = 0; i < postOrder.size(); i++) index.put(postOrder.get(postOrder.size() - 1 - i), i);
        return index;
    }

    private void topoVisit(long node, Map<Long, List<Long>> consumersOf, Set<Long> visited, List<Long> out) {
        if (!visited.add(node)) return;
        for (long consumer : consumersOf.getOrDefault(node, List.of())) {
            topoVisit(consumer, consumersOf, visited, out);
        }
        out.add(node);
    }

    private @Nullable Stmt lca(Collection<Stmt> stmts) {
        Stmt acc = null;
        for (Stmt s : stmts) acc = acc == null ? s : lca(acc, s);
        return acc;
    }

    private static Stmt lca(Stmt a, Stmt b) {
        while (a.depth > b.depth) a = a.parent;
        while (b.depth > a.depth) b = b.parent;
        while (a != b) {
            a = a.parent;
            b = b.parent;
        }
        return a;
    }

    private boolean isShared(int fromId, int fromPin) {
        NodeData n = byId.get(fromId);
        return n != null
            && n.kind() == Kind.PURE_FUNCTION
            && n.function().isPresent()
            && consumerCount(fromId, fromPin) > 1;
    }

    /// ***************************************************************
    /// Pass 2 — lower the statement tree
    /// ***************************************************************

    private FlowNode emit(Stmt s) {
        FlowNode core = core(s);
        List<Long> hoists = hoistsAt.get(s);
        if (hoists == null || hoists.isEmpty()) return core;

        List<FlowNode> seq = new ArrayList<>(hoists.size() + 1);
        for (long out : hoists) seq.add(new ScriptVariableSetterNode(cseName(out), buildShared(out)));
        seq.add(core);
        return new SequenceNode(seq);
    }

    private FlowNode core(Stmt s) {
        switch (s.kind) {
            case SEQUENCE -> {
                List<FlowNode> blocks = new ArrayList<>(s.children.size());
                for (Stmt c : s.children) blocks.add(emit(c));
                if (s.nodeId >= 0) return new SequenceNode(blocks); // real SEQUENCE node
                FlowNode single = asSingleFlow(blocks);             // synthetic chain / arm / next
                return single != null ? single : new SequenceNode(List.of());
            }
            case BRANCH -> {
                PureNode condition = compileDataInput(s.nodeId, 1, boolean.class);
                List<FlowNode> whenTrue = !s.children.isEmpty() ? armFlows(s.children.get(0)) : List.of();
                List<FlowNode> whenFalse = s.children.size() > 1 ? armFlows(s.children.get(1)) : List.of();
                return new BranchNode(condition, whenTrue, whenFalse);
            }
            case FLOW_FUNCTION -> {
                NodeData node = byId.get(s.nodeId);
                ScriptFunction function = node == null ? null : node.function().orElse(null);
                if (function == null) return new SequenceNode(List.of());
                List<PureNode> params = compileParams(node, function, 1);
                Stmt nextSeq = s.children.isEmpty() ? null : s.children.get(0);
                FlowNode next = (nextSeq == null || isEmptyStmt(nextSeq)) ? null : emit(nextSeq);
                return new FlowFunctionNode(function, params, next);
            }
            case VARIABLE_SETTER -> {
                NodeData node = byId.get(s.nodeId);
                String name = node != null ? node.variable().orElse("") : "";
                PureNode value = compileDataInput(s.nodeId, 1, null);
                ScriptVariableSetterNode setter = new ScriptVariableSetterNode(name, value);

                Stmt nextSeq = s.children.isEmpty() ? null : s.children.get(0);
                if (nextSeq == null || isEmptyStmt(nextSeq)) return setter;
                return new SequenceNode(List.of(setter, emit(nextSeq)));
            }
            default -> {
                return new SequenceNode(List.of());
            }
        }
    }

    /// A branch arm (a synthetic `SEQUENCE` slot) as the list [BranchNode] wants. Emitted as one
    /// node so any hoists placed at the arm itself are applied; an empty arm yields no nodes.
    private List<FlowNode> armFlows(Stmt arm) {
        if (isEmptyStmt(arm)) return List.of();
        return List.of(emit(arm));
    }

    /// A synthetic `SEQUENCE` slot is "empty" only when it has neither children nor hoists — then it
    /// can be dropped (it cannot be an LCA without uses beneath it).
    private boolean isEmptyStmt(Stmt s) {
        List<Long> hoists = hoistsAt.get(s);
        return s.children.isEmpty() && (hoists == null || hoists.isEmpty());
    }

    private static @Nullable FlowNode asSingleFlow(List<FlowNode> flows) {
        if (flows.isEmpty()) return null;
        if (flows.size() == 1) return flows.getFirst();
        return new SequenceNode(flows);
    }

    /// ***************************************************************
    /// Pure data
    /// ***************************************************************

    private List<PureNode> compileParams(NodeData node, ScriptFunction function, int inputOffset) {
        List<Class<?>> parameters = function.functionSignature().parameters();
        List<PureNode> params = new ArrayList<>(parameters.size());
        for (int slot = 0; slot < parameters.size(); slot++) {
            params.add(compileDataInput(node.id(), inputOffset + slot, parameters.get(slot)));
        }
        return params;
    }

    private PureNode compileDataInput(int toId, int toPin, @Nullable Class<?> expectedType) {
        long[] source = resolveReroute(incoming.get(key(toId, toPin)));
        if (source == null) {
            if (ownerSatisfies(expectedType)) return SelfNode.INSTANCE;
            return defaultValue(expectedType);
        }
        return compileOutputAsPure((int) source[0], (int) source[1], expectedType);
    }

    private long @Nullable [] resolveReroute(long @Nullable [] source) {
        while (source != null) {
            NodeData node = byId.get((int) source[0]);
            if (node == null || node.kind() != Kind.REROUTE) return source;
            source = incoming.get(key((int) source[0], 0));
        }
        return null;
    }

    /// Output pin `(fromId,fromPin)` as a [PureNode]: a shared output reads its hoisted
    /// `cse_<id>_<pin>` local; everything else is inlined.
    private PureNode compileOutputAsPure(int fromId, int fromPin, @Nullable Class<?> expectedType) {
        if (isShared(fromId, fromPin)) return new ContextVariableAccessNode(cseName(key(fromId, fromPin)));

        NodeData source = byId.get(fromId);
        if (source == null) return defaultValue(expectedType);
        return buildPure(source, fromPin, expectedType);
    }

    /// Builds the actual subtree of a hoisted shared output (its own shared status is bypassed; its
    /// parameters lower nested shared outputs to context reads).
    private PureNode buildShared(long out) {
        NodeData source = byId.get((int) (out >> 32));
        if (source == null) return SelfNode.INSTANCE;
        ScriptFunction function = source.function().orElse(null);
        if (function == null) return SelfNode.INSTANCE;
        return new PureFunctionNode(function, compileParams(source, function, 0));
    }

    private PureNode buildPure(NodeData source, int fromPin, @Nullable Class<?> expectedType) {
        return switch (source.kind()) {
            case SELF -> SelfNode.INSTANCE;
            case LITERAL -> makeLiteral(source);
            case VARIABLE_GETTER, VARIABLE_SETTER -> new ScriptVariableGetterNode(source.variable().orElse(""));
            case ENTRY -> {
                String name = fromPin >= 0 && fromPin < source.outLabels().size()
                    ? source.outLabels().get(fromPin) : "";
                yield new ContextVariableAccessNode(name);
            }
            case PURE_FUNCTION -> {
                ScriptFunction function = source.function().orElse(null);
                if (function == null) yield defaultValue(expectedType);
                yield new PureFunctionNode(function, compileParams(source, function, 0));
            }
            case OPERATOR -> new NumericOperatorNode(
                operatorOf(source),
                compileDataInput(source.id(), 0, null),
                compileDataInput(source.id(), 1, null));
            case LOGICAL_OPERATION -> new LogicalOperationNode(
                compileDataInput(source.id(), 0, boolean.class),
                compileDataInput(source.id(), 1, boolean.class),
                logicalOperatorOf(source));
            case COMPARATOR -> new PureFunctionNode(
                comparatorFunction(source.op().orElse("GREATER")),
                List.of(
                    comparatorLiteral(),
                    compileDataInput(source.id(), 0, null),
                    compileDataInput(source.id(), 1, null)));
            case TYPE_CONVERT -> {
                ScriptType from = source.variableType().orElse(null);
                ScriptType to = source.literalType().orElse(null);
                yield (from == null || to == null) ? defaultValue(expectedType)
                    : new TypeConvertNode(from, to, compileDataInput(source.id(), 0, from.type()));
            }
            default -> defaultValue(expectedType);
        };
    }

    static NumericOperatorNode.Operator operatorOf(NodeData source) {
        try {
            return NumericOperatorNode.Operator.valueOf(source.op().orElse("ADD"));
        } catch (IllegalArgumentException e) {
            return NumericOperatorNode.Operator.ADD;
        }
    }

    static LogicalOperationNode.Operator logicalOperatorOf(NodeData source) {
        try {
            return LogicalOperationNode.Operator.valueOf(source.op().orElse("AND"));
        } catch (IllegalArgumentException e) {
            return LogicalOperationNode.Operator.AND;
        }
    }

    /// The engine `MathUtils` comparison [ScriptFunction] for a [ComparatorNodeWidget] operator name
    /// (`LESS` / `LESS_EQUAL` / `GREATER` / `GREATER_EQUAL` / `EQUAL`). Shared with [AnimGraphCompiler].
    static ScriptFunction comparatorFunction(String op) {
        String method = switch (op) {
            case "LESS" -> "less";
            case "LESS_EQUAL" -> "lessEqual";
            case "GREATER_EQUAL" -> "greaterEqual";
            case "EQUAL" -> "equal";
            default -> "greater";
        };
        return ScriptModularizeHelper.findFunctionByMethod(MathUtils.class, method);
    }

    /// The first param every `MathUtils` comparison takes: the type-agnostic [NumericComparators#NUMBER_COMPARATOR]
    /// (compares any boxed `Number` by `doubleValue`), so the comparator node lowers without needing its
    /// operands' concrete numeric type (which the editor does not persist).
    static PureNode comparatorLiteral() {
        return new LiteralNode<>(AkytheraScriptTypes.NUMERIC_COMPARATOR.value(), NumericComparators.NUMBER_COMPARATOR);
    }

    private LiteralNode<?> makeLiteral(NodeData source) {
        ScriptType type = source.literalType().orElse(null);
        if (type == null) return new LiteralNode<>(AkytheraScriptTypes.BOOLEAN.value(), Boolean.FALSE);
        return new LiteralNode<>(type, parseLiteral(type.type(), source.literalValue().orElse("")));
    }

    /// Parses a literal's raw text (see [com.yesman.akytheralabs.gui.widget.script.LiteralNodeWidget])
    /// into a value of `type`, falling back to a type-appropriate zero on malformed input so the
    /// emitted [LiteralNode] always carries a non-null value of the right runtime class.
    static @Nullable Object parseLiteral(Class<?> type, String raw) {
        try {
            if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(raw);
            if (type == byte.class || type == Byte.class)       return Byte.parseByte(raw);
            if (type == short.class || type == Short.class)     return Short.parseShort(raw);
            if (type == int.class || type == Integer.class)     return Integer.parseInt(raw);
            if (type == long.class || type == Long.class)       return Long.parseLong(raw);
            if (type == float.class || type == Float.class)     return Float.parseFloat(raw);
            if (type == double.class || type == Double.class)   return Double.parseDouble(raw);
            if (type == char.class || type == Character.class)  return raw.isEmpty() ? ' ' : raw.charAt(0);
            return raw; // String and anything else round-tripped verbatim
        } catch (NumberFormatException e) {
            return zeroOf(type);
        }
    }

    private static Object zeroOf(Class<?> type) {
        if (type == byte.class || type == Byte.class)     return (byte) 0;
        if (type == short.class || type == Short.class)   return (short) 0;
        if (type == long.class || type == Long.class)     return 0L;
        if (type == float.class || type == Float.class)   return 0.0F;
        if (type == double.class || type == Double.class) return 0.0D;
        return 0;
    }

    /// A stand-in [PureNode] for an unwired, non-owner-satisfying input: a default literal of the
    /// expected script type when one exists, else [SelfNode] (incomplete graphs stay buildable).
    private PureNode defaultValue(@Nullable Class<?> expectedType) {
        if (expectedType == null || expectedType == Object.class) return SelfNode.INSTANCE;
        for (ScriptType st : com.yesman.akythera.core.registry.AkytheraRegistries.Frozen.SCRIPT_TYPE) {
            if (sameType(st.type(), expectedType) && st.codec() != null) {
                return new LiteralNode<>(st, parseLiteral(st.type(), ""));
            }
        }
        return SelfNode.INSTANCE;
    }

    /// ***************************************************************
    /// Helpers
    /// ***************************************************************

    private boolean ownerSatisfies(@Nullable Class<?> expectedType) {
        return expectedType != null && expectedType != Object.class && expectedType.isAssignableFrom(ownerType);
    }

    private int consumerCount(int fromId, int fromPin) {
        return outgoing.getOrDefault(key(fromId, fromPin), List.of()).size();
    }

    private static String cseName(long out) {
        return "cse_" + (int) (out >> 32) + "_" + (int) out;
    }

    private static boolean sameType(Class<?> a, Class<?> b) {
        return a == b || box(a) == box(b);
    }

    private static Class<?> box(Class<?> t) {
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

    private static long key(int id, int pin) {
        return ((long) id << 32) | (pin & 0xFFFFFFFFL);
    }
}
