package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.ScriptVariableGetterNode;
import com.yesman.akythera.api.data.modularize.node.ScriptVariableSetterNode;
import com.yesman.akythera.api.data.modularize.node.SequenceNode;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptVariableSetterNodeWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/// Writes a script member variable. Data input 1 is the value; the data output reads the variable
/// back, so a chain can consume what was just written.
///
/// [#variableType] is kept so the widget rebuilds with the right pin colour and type without
/// consulting the script's variable list.
public record VariableSetterNodeData(String variable, Optional<ScriptType> variableType) implements FlowNodeData {
    public static final MapCodec<VariableSetterNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.STRING.optionalFieldOf("variable", "").forGetter(VariableSetterNodeData::variable),
                AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().optionalFieldOf("variable_type")
                    .forGetter(VariableSetterNodeData::variableType)
            ).apply(instance, VariableSetterNodeData::new)
    );

    private static final int VALUE_PIN = 1;

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new ScriptVariableSetterNodeWidget(
            ctx.font(), Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SET),
            variable, variableType.<Class<?>>map(ScriptType::type).orElse(Object.class), ctx.x(), ctx.y());
    }

    @Override
    public void buildSkeleton(FlowSkeleton skeleton, FlowStatement self) {
        skeleton.readsInput(self, VALUE_PIN);
        skeleton.chainInto(self.nodeId(), 0, skeleton.newSequenceChild(self));
    }

    @Override
    public FlowNode emit(FlowEmitter emitter, FlowStatement self) {
        ScriptVariableSetterNode setter = new ScriptVariableSetterNode(
            variable, emitter.compileInputOr(self.nodeId(), VALUE_PIN, null), self.nodeId());

        FlowStatement nextSlot = self.child(0);
        if (emitter.isEmpty(nextSlot)) return setter;

        return new SequenceNode(List.of(setter, emitter.emit(nextSlot)));
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new ScriptVariableGetterNode(variable, in.id());
    }
}
