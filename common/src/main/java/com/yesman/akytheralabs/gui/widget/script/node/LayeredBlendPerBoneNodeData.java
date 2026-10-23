package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.data.modularize.node.ContextVariableAccessNode;
import com.yesman.akythera.api.data.modularize.node.LiteralNode;
import com.yesman.akythera.api.data.modularize.node.PureFunctionNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.SupplierAdapterNode;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.LayeredBlendPerBoneDriver;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.LayeredBlendPerBoneNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.IntStream;

/// Hands branches of the skeleton over to the layered pose by [#setup]. The armature the branches are walked on
/// comes from the `animator` local, so the row names no armature of its own.
public record LayeredBlendPerBoneNodeData(LayeredBlendPerBoneDriver.LayerSetup setup) implements NodeData {
    public static final MapCodec<LayeredBlendPerBoneNodeData> CODEC = LayeredBlendPerBoneDriver.LayerSetup.CODEC
        .optionalFieldOf("setup", LayeredBlendPerBoneDriver.LayerSetup.EMPTY)
        .xmap(LayeredBlendPerBoneNodeData::new, LayeredBlendPerBoneNodeData::setup);

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new LayeredBlendPerBoneNodeWidget(ctx.font(), setup, ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new PureFunctionNode(
            AnimGraphFunctions.func("layered_blend_per_bone"),
            List.of(
                new ContextVariableAccessNode("animator"),
                in.valueOr(0, AnimationDriver.class),
                in.valueOr(1, AnimationDriver.class),
                new LiteralNode<>(AkytheraScriptTypes.LAYER_SETUP.value(), setup),
                new SupplierAdapterNode(in.valueOr(2, float.class), in.canvasKey())),
            in.id());
    }

    @Override
    public IntStream valueInputs() {
        return IntStream.of(0, 1, 2);
    }
}
