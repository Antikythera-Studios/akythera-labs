package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.node.PureFunctionNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.PureFunctionNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.ScriptNodeGraphWidget;
import com.yesman.akytheralabs.gui.widget.script.SelectNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.stream.IntStream;

/// Evaluates one pure [ScriptFunction]: no execution pins, one data input per parameter, one result.
/// The untyped `akythera:select` rebuilds as the type-detecting [SelectNodeWidget]; every other
/// function as a plain [PureFunctionNodeWidget].
///
/// This is the only shareable node type: a result read by more than one consumer is computed once
/// into a `cse_<id>_<pin>` local (see [com.yesman.akytheralabs.akythera.animation.ScriptGraphCompiler]).
public record PureFunctionNodeData(ScriptFunction function) implements NodeData {
    public static final MapCodec<PureFunctionNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec().fieldOf("function")
                    .forGetter(PureFunctionNodeData::function)
            ).apply(instance, PureFunctionNodeData::new)
    );

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return ScriptNodeGraphWidget.isSelect(function)
            ? new SelectNodeWidget(ctx.font(), function, ctx.x(), ctx.y())
            : new PureFunctionNodeWidget(ctx.font(), function, ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new PureFunctionNode(function, in.params(function, 0), in.id());
    }

    @Override
    public boolean shareableOutput() {
        return true;
    }

    @Override
    public IntStream valueInputs() {
        return IntStream.range(0, function.functionSignature().parameters().size());
    }
}
