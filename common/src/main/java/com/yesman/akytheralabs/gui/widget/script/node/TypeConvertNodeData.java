package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.TypeConvertNode;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.TypeConvertNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.stream.IntStream;

/// An explicit numeric cast of the value on data input 0.
///
/// The two JSON keys are historical: the source type persists as `variable_type` and the target as
/// `literal_type`.
public record TypeConvertNodeData(ScriptType fromType, ScriptType toType) implements NodeData {
    public static final MapCodec<TypeConvertNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().fieldOf("variable_type")
                    .forGetter(TypeConvertNodeData::fromType),
                AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().fieldOf("literal_type")
                    .forGetter(TypeConvertNodeData::toType)
            ).apply(instance, TypeConvertNodeData::new)
    );

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new TypeConvertNodeWidget(ctx.font(), fromType.type(), toType.type(), ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new TypeConvertNode(fromType, toType, in.valueOr(0, fromType.type()), in.id());
    }

    @Override
    public IntStream valueInputs() {
        return IntStream.of(0);
    }
}
