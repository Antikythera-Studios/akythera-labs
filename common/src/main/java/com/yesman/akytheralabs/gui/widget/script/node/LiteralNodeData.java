package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.LiteralNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.LiteralNodeWidget;
import org.jspecify.annotations.Nullable;

/// A constant value of [#literalType], held in the raw text form the node widget round-trips
/// ([LiteralNodeWidget#serializedValue]).
public record LiteralNodeData(ScriptType literalType, String literalValue) implements NodeData {
    public static final MapCodec<LiteralNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().fieldOf("literal_type")
                    .forGetter(LiteralNodeData::literalType),
                Codec.STRING.optionalFieldOf("literal_value", "").forGetter(LiteralNodeData::literalValue)
            ).apply(instance, LiteralNodeData::new)
    );

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        LiteralNodeWidget widget = new LiteralNodeWidget(ctx.font(), literalType.type(), ctx.x(), ctx.y());
        widget.applySerializedValue(literalValue);

        return widget;
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new LiteralNode<>(literalType, parse(literalType.type(), literalValue), in.id());
    }

    /// Parses a literal's raw text into a value of `type`, falling back to a type-appropriate zero on
    /// malformed input so the emitted [LiteralNode] always carries a value of the right runtime class.
    /// Also the source of the typed empty literal the compilers use for an unwired input.
    public static @Nullable Object parse(Class<?> type, String raw) {
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
}
