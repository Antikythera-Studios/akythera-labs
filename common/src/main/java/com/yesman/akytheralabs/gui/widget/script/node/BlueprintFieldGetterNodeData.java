package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.node.BlueprintFieldGetterNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.core.reflection.BlueprintField;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.BlueprintFieldGetterNodeWidget;
import org.jspecify.annotations.Nullable;

/// Reads the [BlueprintField] [#field] of the script owner.
public record BlueprintFieldGetterNodeData(String field) implements NodeData {
    public static final MapCodec<BlueprintFieldGetterNodeData> CODEC =
        Codec.STRING.fieldOf("field").xmap(BlueprintFieldGetterNodeData::new, BlueprintFieldGetterNodeData::field);

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new BlueprintFieldGetterNodeWidget(ctx.font(), field, ctx.members().fieldType(field), ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new BlueprintFieldGetterNode(field, in.id());
    }
}
