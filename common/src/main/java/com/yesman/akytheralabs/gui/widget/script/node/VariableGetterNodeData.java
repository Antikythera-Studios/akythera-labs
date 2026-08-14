package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ScriptVariableGetterNode;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptVariableGetterNodeWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// Reads a script member variable. [#variableType] is kept so the widget rebuilds with the right pin
/// colour and type without consulting the script's variable list.
public record VariableGetterNodeData(String variable, Optional<ScriptType> variableType) implements NodeData {
    public static final MapCodec<VariableGetterNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.STRING.optionalFieldOf("variable", "").forGetter(VariableGetterNodeData::variable),
                AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().optionalFieldOf("variable_type")
                    .forGetter(VariableGetterNodeData::variableType)
            ).apply(instance, VariableGetterNodeData::new)
    );

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new ScriptVariableGetterNodeWidget(
            ctx.font(),
            Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_GET_VARIABLE, variable),
            variable, variableType.<Class<?>>map(ScriptType::type).orElse(Object.class), ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new ScriptVariableGetterNode(variable, in.id());
    }
}
