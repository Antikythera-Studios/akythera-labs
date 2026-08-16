package com.yesman.akytheralabs.akythera.animation;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import com.yesman.akytheralabs.gui.widget.script.node.GraphNode;
import com.yesman.akytheralabs.gui.widget.script.node.NodeInputs;
import com.yesman.akytheralabs.gui.widget.script.node.PureCompileContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// The pure-data half of lowering one flattened editor graph ([ScriptGraphData]) into engine
/// [PureNode]s, shared by the flow compiler ([ScriptGraphCompiler]) and the pose compiler
/// ([AnimGraphCompiler]): node / wire indexing, reroute splicing, and the [PureCompileContext] the
/// nodes lower themselves through.
///
/// A node's own lowering lives on its
/// [com.yesman.akytheralabs.gui.widget.script.node.NodeData#compilePure]. A canvas customises the
/// shared node types by overriding the [PureCompileContext] defaults, and supplies its stand-in for
/// an unwired input through [#defaultValue].
abstract class PureGraphCompiler implements PureCompileContext {
    protected final Class<?> ownerType;
    protected final Map<Integer, GraphNode> byId = new HashMap<>();
    /// (toId,toPin) → the single (fromId,fromPin) feeding it (data inputs hold one source).
    protected final Map<Long, long[]> incoming = new HashMap<>();

    private final String canvasKey;

    /// A canvas that defers nothing past its entry point, so its rows need no canvas of their own.
    protected PureGraphCompiler(ScriptGraphData data, Class<?> ownerType) {
        this(data, ownerType, ScriptNode.NO_CANVAS);
    }

    protected PureGraphCompiler(ScriptGraphData data, Class<?> ownerType, String canvasKey) {
        data.requireNoFailedNodes();

        this.ownerType = ownerType;
        this.canvasKey = canvasKey;
        for (GraphNode n : data.nodes()) byId.put(n.id(), n);
        for (ScriptGraphData.WireData w : data.wires()) {
            incoming.putIfAbsent(key(w.to(), w.toPin()), new long[]{w.from(), w.fromPin()});
        }
    }

    @Override
    public final String canvasKey() {
        return canvasKey;
    }

    @Override
    public final @Nullable PureNode compileInput(int toId, int toPin) {
        long[] source = resolveReroute(incoming.get(key(toId, toPin)));
        if (source == null) return null;

        return buildOutput((int) source[0], (int) source[1], null);
    }

    @Override
    public final PureNode compileInputOr(int toId, int toPin, @Nullable Class<?> expectedType) {
        long[] source = resolveReroute(incoming.get(key(toId, toPin)));
        if (source == null) return defaultValue(expectedType);

        PureNode wired = buildOutput((int) source[0], (int) source[1], expectedType);

        return wired != null ? wired : defaultValue(expectedType);
    }

    @Override
    public final List<PureNode> compileParams(int id, ScriptFunction function, int inputOffset) {
        List<Class<?>> parameters = function.functionSignature().parameters();
        List<PureNode> params = new ArrayList<>(parameters.size());

        for (int slot = 0; slot < parameters.size(); slot++) {
            params.add(compileInputOr(id, inputOffset + slot, parameters.get(slot)));
        }

        return params;
    }

    @Override
    public final @Nullable GraphNode sourceNode(int toId, int toPin) {
        long[] source = resolveReroute(incoming.get(key(toId, toPin)));

        return source == null ? null : byId.get((int) source[0]);
    }

    /// Output pin `(fromId,fromPin)` as a [PureNode] — the node lowers itself. Override to intercept
    /// ahead of that; call super for everything not intercepted.
    protected @Nullable PureNode buildOutput(int fromId, int fromPin, @Nullable Class<?> expectedType) {
        GraphNode source = byId.get(fromId);
        if (source == null) return null;

        return source.data().compilePure(new NodeInputs(this, fromId), fromPin, expectedType);
    }

    /// Walks back past any chain of pass-through knots to the real source feeding a pin.
    protected final long @Nullable [] resolveReroute(long @Nullable [] source) {
        while (source != null) {
            GraphNode node = byId.get((int) source[0]);
            if (node == null || !node.data().passThrough()) return source;

            source = incoming.get(key((int) source[0], 0));
        }

        return null;
    }

    protected static long key(int id, int pin) {
        return ((long) id << 32) | (pin & 0xFFFFFFFFL);
    }
}
