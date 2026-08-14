package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.NumericOperatorNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.OperatorNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.stream.IntStream;

/// Arithmetic (+, -, *, /) over the two values on data inputs 0 and 1. Mixed-width operands are
/// widened at runtime by the engine node, so no operand type is persisted.
public record OperatorNodeData(OperatorNodeWidget.Operator operator) implements NodeData {
    public static final MapCodec<OperatorNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.STRING.xmap(OperatorNodeData::parseOperator, Enum::name).fieldOf("op")
                    .forGetter(OperatorNodeData::operator)
            ).apply(instance, OperatorNodeData::new)
    );

    private static OperatorNodeWidget.Operator parseOperator(String name) {
        try {
            return OperatorNodeWidget.Operator.valueOf(name);
        } catch (IllegalArgumentException e) {
            return OperatorNodeWidget.Operator.ADD;
        }
    }

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new OperatorNodeWidget(ctx.font(), operator, ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new NumericOperatorNode(engineOperator(), in.valueOr(0, null), in.valueOr(1, null), in.id());
    }

    @Override
    public IntStream valueInputs() {
        return IntStream.of(0, 1);
    }

    /// The engine operator of the same name; `ADD` when the engine has no counterpart.
    private NumericOperatorNode.Operator engineOperator() {
        try {
            return NumericOperatorNode.Operator.valueOf(operator.name());
        } catch (IllegalArgumentException e) {
            return NumericOperatorNode.Operator.ADD;
        }
    }
}
