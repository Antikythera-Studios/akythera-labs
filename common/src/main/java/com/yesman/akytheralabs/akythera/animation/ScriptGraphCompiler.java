package com.yesman.akytheralabs.akythera.animation;

import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.*;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.node.EntryNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.FlowEmitter;
import com.yesman.akytheralabs.gui.widget.script.node.FlowNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.FlowSkeleton;
import com.yesman.akytheralabs.gui.widget.script.node.FlowStatement;
import com.yesman.akytheralabs.gui.widget.script.node.GraphNode;
import com.yesman.akytheralabs.gui.widget.script.node.LiteralNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeInputs;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

/// Lowers one flattened editor flow ([ScriptGraphData]) into the engine's tree form (a [FlowNode]).
///
/// A **shared output** — a shareable pin feeding more than one consumer — is computed once into a
/// `ScriptVariableSetterNode("cse_<id>_<pin>", …)` and read back at every use through a
/// [ContextVariableAccessNode]. Its setter sits just before the lowest common ancestor of all its
/// uses in the flow tree.
///
/// Statement shapes come from the nodes ([FlowNodeData]); this class owns the walk, the placement
/// and the emission order.
public final class ScriptGraphCompiler extends PureGraphCompiler implements FlowSkeleton, FlowEmitter {
    /// Which entry to follow when a graph holds more than one. UPDATE picks the entry exposing data
    /// outputs (`frameTime` / `partialTick`), CONSTRUCT the data-less one.
    public enum EntrySelector { UPDATE, CONSTRUCT }

    private final ScriptGraphData data;
    private final EntrySelector entrySelector;

    /// When non-null, roots the flow at the first row this predicate accepts, overriding
    /// [#entrySelector].
    private final @Nullable Predicate<GraphNode> entryMatch;

    /// (fromId,fromPin) → list of (toId,toPin) it feeds.
    private final Map<Long, List<long[]>> outgoing = new HashMap<>();

    /// All flow statements in build order (pass 1).
    private final List<FlowStatement> allStmts = new ArrayList<>();
    /// Shared output pin `(id,pin)` → the statement before which its cache setter is emitted.
    private final Map<Long, FlowStatement> placement = new HashMap<>();
    /// Statement → the shared output pins hoisted before it, ordered producer-first.
    private final Map<FlowStatement, List<Long>> hoistsAt = new HashMap<>();

    private ScriptGraphCompiler(
        ScriptGraphData data, Class<?> ownerType, EntrySelector entrySelector,
        @Nullable Predicate<GraphNode> entryMatch
    ) {
        super(data, ownerType);
        this.data = data;
        this.entrySelector = entrySelector;
        this.entryMatch = entryMatch;

        for (ScriptGraphData.WireData w : data.wires()) {
            outgoing.computeIfAbsent(key(w.from(), w.fromPin()), _ -> new ArrayList<>())
                .add(new long[]{w.to(), w.toPin()});
        }
    }

    /// Compiles the **Update** flow of `data`.
    public static FlowNode compile(ScriptGraphData data, Class<?> ownerType) {
        return compile(data, ownerType, EntrySelector.UPDATE);
    }

    /// Compiles the flow of `data` rooted at the `entrySelector` entry, or an empty [SequenceNode]
    /// when the canvas is empty or has no matching entry. An *incomplete* graph degrades to
    /// best-effort placeholder nodes; a graph carrying **unresolved (failed-decode) nodes** throws
    /// at the [PureGraphCompiler] gate.
    public static FlowNode compile(ScriptGraphData data, Class<?> ownerType, EntrySelector entrySelector) {
        if (data == null || data.isEmpty()) return new SequenceNode(List.of());

        return new ScriptGraphCompiler(data, ownerType, entrySelector, null).run();
    }

    /// Compiles the flow rooted at the first row `entryMatch` accepts.
    public static FlowNode compile(ScriptGraphData data, Class<?> ownerType, Predicate<GraphNode> entryMatch) {
        if (data == null || data.isEmpty()) return new SequenceNode(List.of());

        return new ScriptGraphCompiler(data, ownerType, EntrySelector.UPDATE, entryMatch).run();
    }

    private FlowNode run() {
        GraphNode entry = findEntry();
        if (entry == null) return new SequenceNode(List.of());

        FlowStatement root = new FlowStatement(null, -1, null);
        allStmts.add(root);
        chainInto(entry.id(), 0, root);

        computePlacements();

        return emit(root);
    }

    /// The entry row to root the flow at: [#entryMatch] when set, else the [#entrySelector] entry —
    /// UPDATE falling back to the first entry of any kind, CONSTRUCT to null.
    private @Nullable GraphNode findEntry() {
        if (entryMatch != null) {
            for (GraphNode n : data.nodes()) {
                if (entryMatch.test(n)) return n;
            }

            return null;
        }

        GraphNode firstEntry = null;

        for (GraphNode n : data.nodes()) {
            if (!(n.data() instanceof EntryNodeData entry)) continue;
            if (firstEntry == null) firstEntry = n;

            if (entrySelector == EntrySelector.CONSTRUCT && !entry.hasDataOutputs()) return n;
            if (entrySelector == EntrySelector.UPDATE && entry.hasDataOutputs()) return n;
        }

        return entrySelector == EntrySelector.UPDATE ? firstEntry : null;
    }

    // ***************************************************************
    // [FlowSkeleton] — pass 1
    // ***************************************************************

    @Override
    public void chainInto(int fromId, int fromPin, FlowStatement parent) {
        List<long[]> targets = new ArrayList<>(outgoing.getOrDefault(key(fromId, fromPin), List.of()));
        targets.sort(Comparator.comparingLong(a -> a[0]));

        for (long[] target : targets) {
            GraphNode node = byId.get((int) target[0]);
            if (node == null) continue;

            if (node.data().passThrough()) {
                chainInto(node.id(), 0, parent);
                continue;
            }

            parent.children().add(skelNode(node, parent));
        }
    }

    @Override
    public FlowStatement newSequenceChild(FlowStatement parent) {
        FlowStatement s = new FlowStatement(null, -1, parent);
        allStmts.add(s);
        parent.children().add(s);

        return s;
    }

    @Override
    public void readsInput(FlowStatement self, int pin) {
        self.dataPins().add(key(self.nodeId(), pin));
    }

    /// A row's statement. A row that is not a [FlowNodeData] becomes an inert empty slot.
    private FlowStatement skelNode(GraphNode node, FlowStatement parent) {
        FlowNodeData flow = node.data() instanceof FlowNodeData f ? f : null;
        FlowStatement s = new FlowStatement(flow, node.id(), parent);
        allStmts.add(s);

        if (flow != null) flow.buildSkeleton(this, s);

        return s;
    }

    // ***************************************************************
    // Placement — attribute uses, LCA, producer-first ordering
    // ***************************************************************

    private void computePlacements() {
        // 1. Attribute every shared output to each statement that (transitively) reads it.
        Map<Long, Set<FlowStatement>> useStmts = new HashMap<>();
        for (FlowStatement s : allStmts) {
            for (long pin : s.dataPins()) {
                Set<Long> reached = new HashSet<>();
                collectReachableShared((int) (pin >> 32), (int) pin, reached, new HashSet<>());
                for (long out : reached) useStmts.computeIfAbsent(out, _ -> new HashSet<>()).add(s);
            }
        }

        // 2. Place each shared output at the LCA of its uses.
        for (Map.Entry<Long, Set<FlowStatement>> e : useStmts.entrySet()) {
            placement.put(e.getKey(), lca(e.getValue()));
        }

        // 3. Producer-first ordering for co-located hoists: topo-sort on direct producer→consumer
        //    edges (a shared output → the shared outputs that directly read it).
        Map<Long, List<Long>> consumersOf = new HashMap<>();
        for (long out : useStmts.keySet()) {
            int id = (int) (out >> 32);
            GraphNode node = byId.get(id);
            if (node == null) continue;

            Set<Long> direct = new HashSet<>();
            node.data().valueInputs().forEach(slot -> collectDirectShared(id, slot, direct));
            for (long producer : direct) consumersOf.computeIfAbsent(producer, _ -> new ArrayList<>()).add(out);
        }
        Map<Long, Integer> order = topoOrder(useStmts.keySet(), consumersOf);

        // 4. Group hoists by placement, producers (lower order index) first.
        for (Map.Entry<Long, FlowStatement> e : placement.entrySet()) {
            hoistsAt.computeIfAbsent(e.getValue(), _ -> new ArrayList<>()).add(e.getKey());
        }
        for (List<Long> keys : hoistsAt.values()) {
            keys.sort(Comparator.comparingInt(a -> order.getOrDefault(a, 0)));
        }
    }

    /// Shared outputs reachable from data input `(toId,toPin)`, descending **through** shared
    /// outputs.
    private void collectReachableShared(int toId, int toPin, Set<Long> out, Set<Integer> visited) {
        long[] source = resolveReroute(incoming.get(key(toId, toPin)));
        if (source == null) return;

        int fromId = (int) source[0];
        int fromPin = (int) source[1];
        if (isShared(fromId, fromPin)) out.add(key(fromId, fromPin));

        GraphNode node = byId.get(fromId);
        if (node == null || !visited.add(fromId)) return;

        node.data().valueInputs().forEach(slot -> collectReachableShared(fromId, slot, out, visited));
    }

    /// The shared outputs feeding `(toId,toPin)` directly — traversal stops at the first shared
    /// output on each path.
    private void collectDirectShared(int toId, int toPin, Set<Long> out) {
        long[] source = resolveReroute(incoming.get(key(toId, toPin)));
        if (source == null) return;

        int fromId = (int) source[0];
        int fromPin = (int) source[1];
        if (isShared(fromId, fromPin)) {
            out.add(key(fromId, fromPin));
            return;
        }

        GraphNode node = byId.get(fromId);
        if (node == null) return;

        node.data().valueInputs().forEach(slot -> collectDirectShared(fromId, slot, out));
    }

    /// A producer-first index (smaller = computed earlier) over the shared outputs, from a DFS
    /// topo-sort of producer→consumer edges.
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

    private @Nullable FlowStatement lca(Collection<FlowStatement> stmts) {
        FlowStatement acc = null;
        for (FlowStatement s : stmts) acc = acc == null ? s : lca(acc, s);

        return acc;
    }

    private static FlowStatement lca(FlowStatement a, FlowStatement b) {
        while (a.depth() > b.depth()) a = a.parent();
        while (b.depth() > a.depth()) b = b.parent();
        while (a != b) {
            a = a.parent();
            b = b.parent();
        }

        return a;
    }

    private boolean isShared(int fromId, int fromPin) {
        GraphNode n = byId.get(fromId);

        return n != null && n.data().shareableOutput() && consumerCount(fromId, fromPin) > 1;
    }

    // ***************************************************************
    // [FlowEmitter] — pass 2
    // ***************************************************************

    @Override
    public FlowNode emit(FlowStatement s) {
        FlowNode core = core(s);
        List<Long> hoists = hoistsAt.get(s);
        if (hoists == null || hoists.isEmpty()) return core;

        List<FlowNode> seq = new ArrayList<>(hoists.size() + 1);
        for (long out : hoists) seq.add(new ScriptVariableSetterNode(cseName(out), buildShared(out)));
        seq.add(core);

        return new SequenceNode(seq);
    }

    @Override
    public boolean isEmpty(@Nullable FlowStatement s) {
        if (s == null) return true;

        List<Long> hoists = hoistsAt.get(s);

        return s.children().isEmpty() && (hoists == null || hoists.isEmpty());
    }

    /// A statement's own lowering: the node's, or — for a synthetic slot — its children flattened.
    private FlowNode core(FlowStatement s) {
        FlowNodeData flow = s.data();
        if (flow != null) return flow.emit(this, s);

        List<FlowNode> blocks = new ArrayList<>(s.children().size());
        for (FlowStatement c : s.children()) blocks.add(emit(c));

        if (blocks.isEmpty()) return new SequenceNode(List.of());

        return blocks.size() == 1 ? blocks.getFirst() : new SequenceNode(blocks);
    }

    // ***************************************************************
    // Pure data
    // ***************************************************************

    /// Shared outputs read their hoisted `cse_<id>_<pin>` local; everything else lowers plainly.
    @Override
    protected @Nullable PureNode buildOutput(int fromId, int fromPin, @Nullable Class<?> expectedType) {
        if (isShared(fromId, fromPin)) return new ContextVariableAccessNode(cseName(key(fromId, fromPin)));

        return super.buildOutput(fromId, fromPin, expectedType);
    }

    /// The subtree of a hoisted shared output. The node lowers itself, bypassing its own shared
    /// status; nested shared outputs in its parameters still lower to context reads.
    private PureNode buildShared(long out) {
        int id = (int) (out >> 32);
        GraphNode source = byId.get(id);
        if (source == null) return SelfNode.INSTANCE;

        PureNode value = source.data().compilePure(new NodeInputs(this, id), (int) out, null);

        return value != null ? value : SelfNode.INSTANCE;
    }

    /// A stand-in for an unwired input: `self` when the owner fits the pin — through a
    /// [TypeConvertNode] when it only fits after a cast — else a default literal of the expected
    /// script type when one exists, else [SelfNode].
    @Override
    public PureNode defaultValue(@Nullable Class<?> expectedType) {
        if (expectedType == null || expectedType == Object.class) return SelfNode.INSTANCE;
        if (expectedType.isAssignableFrom(ownerType)) return SelfNode.INSTANCE;

        ScriptType owner = ScriptType.of(ownerType);
        ScriptType expected = ScriptType.of(expectedType);

        if (owner != null && expected != null && owner.implicitlyCastableTypes().containsKey(expected.type())) {
            return new TypeConvertNode(owner, expected, SelfNode.INSTANCE);
        }

        for (ScriptType st : AkytheraRegistries.Frozen.SCRIPT_TYPE) {
            if (sameType(st.type(), expectedType) && st.codec() != null) {
                return new LiteralNode<>(st, LiteralNodeData.parse(st.type(), ""));
            }
        }

        return SelfNode.INSTANCE;
    }

    // ***************************************************************
    // Helpers
    // ***************************************************************

    private int consumerCount(int fromId, int fromPin) {
        return outgoing.getOrDefault(key(fromId, fromPin), List.of()).size();
    }

    private static String cseName(long out) {
        return "cse_" + (int) (out >> 32) + "_" + (int) out;
    }

    private static boolean sameType(Class<?> a, Class<?> b) {
        return a == b || ParseUtils.box(a) == ParseUtils.box(b);
    }
}
