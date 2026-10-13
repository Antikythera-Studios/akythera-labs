package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.client.api.particle.module.ParticleSystemParameterNode;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ParticleParameterGetterNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// Reads a parameter of the particle system a module runs in. [#parameterType] is kept so the widget rebuilds with
/// the right pin color and type without consulting the system's parameters.
public record ParticleParameterGetterNodeData(String parameter, Optional<ScriptType> parameterType) implements NodeData {
    public static final MapCodec<ParticleParameterGetterNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.STRING.fieldOf("parameter").forGetter(ParticleParameterGetterNodeData::parameter),
                ScriptType.CODEC.optionalFieldOf("parameter_type").forGetter(ParticleParameterGetterNodeData::parameterType)
            ).apply(instance, ParticleParameterGetterNodeData::new)
    );

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new ParticleParameterGetterNodeWidget(ctx.font(), parameter, parameterType.orElse(null), ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new ParticleSystemParameterNode(
            parameter, parameterType.orElseThrow(() -> new IllegalStateException("The type of the parameter " + parameter + " did not resolve")), in.id()
        );
    }
}
