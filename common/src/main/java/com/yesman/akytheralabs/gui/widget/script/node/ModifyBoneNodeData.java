package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.node.ContextVariableAccessNode;
import com.yesman.akythera.api.data.modularize.node.PureFunctionNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.api.data.modularize.node.SupplierAdapterNode;
import com.yesman.akythera.core.animation.driver.AnimationDriver;
import com.yesman.akythera.core.animation.driver.ModifyBoneDriver;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ModifyBoneNodeWidget;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.IntStream;

public record ModifyBoneNodeData(ModifyBoneDriver.Space space) implements NodeData {
    public static final MapCodec<ModifyBoneNodeData> CODEC =
        ModifyBoneDriver.Space.CODEC.fieldOf("space").xmap(ModifyBoneNodeData::new, ModifyBoneNodeData::space);

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new ModifyBoneNodeWidget(ctx.font(), space, ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new PureFunctionNode(
            AnimGraphFunctions.func("modify_bone_" + space.getSerializedName()),
            List.of(
                new ContextVariableAccessNode("animator"),
                in.valueOr(0, AnimationDriver.class),
                in.valueOr(1, String.class),
                new SupplierAdapterNode(in.valueOr(2, Vec3.class), in.canvasKey())),
            in.id());
    }

    @Override
    public IntStream valueInputs() {
        return IntStream.of(0, 1, 2);
    }
}
