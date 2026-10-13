package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.node.ContextVariableAccessNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.core.blueprint.script.EntityPatchScript;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintSignature;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.MethodEntryNodeWidget;
import org.jspecify.annotations.Nullable;

/// The flow origin of the blueprint method or function [#method]. Output 0 is execution, and output `n`
/// reads the local the engine binds the `n - 1`th argument under.
public record MethodEntryNodeData(String method) implements NodeData {
    public static final MapCodec<MethodEntryNodeData> CODEC =
        Codec.STRING.fieldOf("method").xmap(MethodEntryNodeData::new, MethodEntryNodeData::method);

    /// Reads an `entity_patch_entry` row, whose `op` names one of [EntityPatchScript]'s events.
    public static final MapCodec<MethodEntryNodeData> ENTITY_PATCH_ENTRY_CODEC = Codec.STRING.fieldOf("op").xmap(
        op -> new MethodEntryNodeData(switch (op) {
            case "ON_CONSTRUCT" -> "onConstruct";
            case "ON_JOIN_WORLD" -> "onJoinWorld";
            default -> "update";
        }),
        MethodEntryNodeData::method
    );

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new MethodEntryNodeWidget(ctx.font(), method, ctx.members().method(method), ctx.x(), ctx.y());
    }

    @Override
    public @Nullable PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        BlueprintSignature signature = in.ctx().members().method(method);
        int parameter = fromPin - 1;

        if (signature == null || parameter < 0 || parameter >= signature.parameters().size()) return null;

        return new ContextVariableAccessNode(signature.parameters().get(parameter).name(), in.id());
    }
}
