package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.node.ContextVariableAccessNode;
import com.yesman.akythera.api.data.modularize.node.PureFunctionNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.LayeredBlendPerBoneNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.IntStream;

/// Hands the skeleton branch below the `Bone` input over to the layered pose. The armature the branch
/// is walked on comes from the `animator` local, so the row names no armature of its own.
public record LayeredBlendPerBoneNodeData() implements NodeData {
    public static final MapCodec<LayeredBlendPerBoneNodeData> CODEC = MapCodec.unit(new LayeredBlendPerBoneNodeData());

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new LayeredBlendPerBoneNodeWidget(ctx.font(), ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new PureFunctionNode(
            AnimGraphFunctions.func("layered_blend_per_bone"),
            List.of(
                new ContextVariableAccessNode("animator"),
                in.valueOr(0, AnimationDriver.class),
                in.valueOr(1, AnimationDriver.class),
                in.valueOr(2, String.class),
                in.valueOr(3, int.class)),
            in.id());
    }

    @Override
    public IntStream valueInputs() {
        return IntStream.of(0, 1, 2, 3);
    }
}
