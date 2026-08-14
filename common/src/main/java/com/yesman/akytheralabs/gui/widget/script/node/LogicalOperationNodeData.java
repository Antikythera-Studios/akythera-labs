package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.LogicalOperationNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.LogicalOperationNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.stream.IntStream;

/// Combines the two booleans on data inputs 0 and 1 (AND / OR / NAND / NOR / XOR).
public record LogicalOperationNodeData(LogicalOperationNodeWidget.Operator operator) implements NodeData {
    public static final MapCodec<LogicalOperationNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.STRING.xmap(LogicalOperationNodeData::parseOperator, Enum::name).fieldOf("op")
                    .forGetter(LogicalOperationNodeData::operator)
            ).apply(instance, LogicalOperationNodeData::new)
    );

    private static LogicalOperationNodeWidget.Operator parseOperator(String name) {
        try {
            return LogicalOperationNodeWidget.Operator.valueOf(name);
        } catch (IllegalArgumentException e) {
            return LogicalOperationNodeWidget.Operator.AND;
        }
    }

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new LogicalOperationNodeWidget(ctx.font(), operator, ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new LogicalOperationNode(
            in.valueOr(0, boolean.class), in.valueOr(1, boolean.class), engineOperator(), in.id());
    }

    @Override
    public IntStream valueInputs() {
        return IntStream.of(0, 1);
    }

    private LogicalOperationNode.Operator engineOperator() {
        try {
            return LogicalOperationNode.Operator.valueOf(operator.name());
        } catch (IllegalArgumentException e) {
            return LogicalOperationNode.Operator.AND;
        }
    }
}
