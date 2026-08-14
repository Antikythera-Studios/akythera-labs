package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.RerouteNodeWidget;

import java.util.Optional;

/// A wire knot: layout only, transparent to evaluation. [#exec] selects an execution or a data pin
/// and [#valueType] the data value type, so the knot reloads with the right shape and colour.
public record RerouteNodeData(boolean exec, Optional<ScriptType> valueType) implements NodeData {
    public static final MapCodec<RerouteNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.BOOL.optionalFieldOf("exec", false).forGetter(RerouteNodeData::exec),
                AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().optionalFieldOf("variable_type")
                    .forGetter(RerouteNodeData::valueType)
            ).apply(instance, RerouteNodeData::new)
    );

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new RerouteNodeWidget(
            ctx.font(), exec, valueType.<Class<?>>map(ScriptType::type).orElse(null), ctx.x(), ctx.y());
    }

    @Override
    public boolean passThrough() {
        return true;
    }
}
