package com.yesman.akytheralabs.gui.widget.script.node;

import com.yesman.akythera.api.data.modularize.node.FlowNode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// One node in the structured flow tree the flow compiler builds before it emits; it mirrors the emitted
/// [FlowNode] shape.
///
/// A statement with `data == null` is a **synthetic sequence slot** — a chain, a branch arm or a
/// `next` body — which exists so a hoisted shared value has somewhere to be placed.
public final class FlowStatement {
    private final @Nullable FlowNodeData data;
    private final int nodeId;
    private final @Nullable FlowStatement parent;
    private final int depth;
    private final List<FlowStatement> children = new ArrayList<>();
    /// Packed `(nodeId,inputPin)` data roots this statement reads (params / condition / value).
    private final List<Long> dataPins = new ArrayList<>();

    public FlowStatement(@Nullable FlowNodeData data, int nodeId, @Nullable FlowStatement parent) {
        this.data = data;
        this.nodeId = nodeId;
        this.parent = parent;
        this.depth = parent == null ? 0 : parent.depth + 1;
    }

    public @Nullable FlowNodeData data() { return data; }
    public int nodeId() { return nodeId; }
    public @Nullable FlowStatement parent() { return parent; }
    public int depth() { return depth; }
    public List<FlowStatement> children() { return children; }
    public List<Long> dataPins() { return dataPins; }

    /// The child at `index`, or null when this statement has fewer children (an unwired branch arm).
    public @Nullable FlowStatement child(int index) {
        return index < children.size() ? children.get(index) : null;
    }
}
