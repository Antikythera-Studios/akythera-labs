package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.data.modularize.AkytheraScriptFunctions;
import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.data.modularize.node.LiteralNode;
import com.yesman.akythera.api.data.modularize.node.PureFunctionNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ComponentGetterNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Reads the component attached at [#path], a name path of [ComponentAttachable#PATH_SEPARATOR].
///
/// Lowers to `akythera:get_component` over the canvas' owner and the path as a literal, so the host is
/// whatever the canvas answers for `self` — the patch on a script canvas, its owner context param on a
/// headless one.
public record ComponentGetterNodeData(String path) implements NodeData {
    public static final MapCodec<ComponentGetterNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.STRING.optionalFieldOf("path", "").forGetter(ComponentGetterNodeData::path)
            ).apply(instance, ComponentGetterNodeData::new)
    );

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new ComponentGetterNodeWidget(ctx.font(), path, ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new PureFunctionNode(
            AkytheraScriptFunctions.GET_COMPONENT.value(),
            List.of(in.ctx().selfValue(), new LiteralNode<>(AkytheraScriptTypes.STRING.value(), path)),
            in.id()
        );
    }
}
