package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.impl.ScriptComparators;
import com.yesman.akythera.api.data.modularize.node.LiteralNode;
import com.yesman.akythera.api.data.modularize.node.PureFunctionNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akythera.core.util.helper.MathUtils;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ComparatorNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.IntStream;

/// Compares the two values on data inputs 0 and 1, yielding a boolean. Lowers to the matching
/// `MathUtils` comparison [ScriptFunction] over one of the [ScriptComparators] singletons:
/// [ScriptComparators#EQUALITY_COMPARATOR] for `==`, which answers for any operand type, and
/// [ScriptComparators#NUMBER_COMPARATOR] for the ordering operators. Either way the operand type
/// itself is not needed, which is why the editor's type-detecting widget never persists it.
public record ComparatorNodeData(ComparatorNodeWidget.Operator operator) implements NodeData {
    public static final MapCodec<ComparatorNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.STRING.xmap(ComparatorNodeData::parseOperator, Enum::name).fieldOf("op")
                    .forGetter(ComparatorNodeData::operator)
            ).apply(instance, ComparatorNodeData::new)
    );

    private static ComparatorNodeWidget.Operator parseOperator(String name) {
        try {
            return ComparatorNodeWidget.Operator.valueOf(name);
        } catch (IllegalArgumentException e) {
            return ComparatorNodeWidget.Operator.EQUAL;
        }
    }

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new ComparatorNodeWidget(ctx.font(), operator, ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new PureFunctionNode(
            comparisonFunction(),
            List.of(
                new LiteralNode<>(
                    AkytheraScriptTypes.NUMERIC_COMPARATOR.value(),
                    operator == ComparatorNodeWidget.Operator.EQUAL
                        ? ScriptComparators.EQUALITY_COMPARATOR
                        : ScriptComparators.NUMBER_COMPARATOR),
                in.valueOr(0, null),
                in.valueOr(1, null)),
            in.id());
    }

    @Override
    public IntStream valueInputs() {
        return IntStream.of(0, 1);
    }

    private ScriptFunction comparisonFunction() {
        String method = switch (operator) {
            case LESS -> "less";
            case LESS_EQUAL -> "lessEqual";
            case GREATER_EQUAL -> "greaterEqual";
            case EQUAL -> "equal";
            case GREATER -> "greater";
        };

        return ScriptModularizeHelper.findFunctionByMethod(MathUtils.class, method);
    }
}
