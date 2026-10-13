package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/// One persisted row: the canvas-level facts (identity, position, values typed into its inputs) plus the
/// type-specific [NodeData].
///
/// `id` is stable within the graph and is what a wire references; the entry node is always id 0.
/// `x` / `y` are graph-space, so a reopened canvas is laid out exactly as it was saved.
public record GraphNode(int id, int x, int y, NodeData data, List<PinLiteral> literals) {
    public static final Codec<GraphNode> CODEC = RecordCodecBuilder.create(
        instance ->
            instance.group(
                Codec.INT.fieldOf("id").forGetter(GraphNode::id),
                Codec.INT.fieldOf("x").forGetter(GraphNode::x),
                Codec.INT.fieldOf("y").forGetter(GraphNode::y),
                ScriptNodeTypes.MAP_CODEC.forGetter(GraphNode::data),
                PinLiteral.CODEC.listOf().optionalFieldOf("pin_literals", List.of()).forGetter(GraphNode::literals)
            ).apply(instance, GraphNode::new)
    );

    /// A row with nothing typed into its inputs.
    public GraphNode(int id, int x, int y, NodeData data) {
        this(id, x, y, data, List.of());
    }

    /// This row with `data` replaced, keeping its identity, position and typed values.
    public GraphNode withData(NodeData data) {
        return new GraphNode(id, x, y, data, literals);
    }
}
