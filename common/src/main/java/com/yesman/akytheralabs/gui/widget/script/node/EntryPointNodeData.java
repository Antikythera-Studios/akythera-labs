package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.node.ContextVariableAccessNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// A flow origin (event) node. Its data outputs are the runtime context variables the event is
/// invoked with, in pin order — `frameTime` / `partialTick` for the animator script's Update event,
/// none for its Constructor — so an output lowers to a context read of the label at that pin.
///
/// The widget is built by the host screen ([NodeBuildContext#entryWidget]): an entry's title and pin
/// shape are event-specific, and a canvas that has no entry of its own supplies none.
public record EntryPointNodeData(List<String> outLabels) implements EntryNodeData {
    public static final MapCodec<EntryPointNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                Codec.STRING.listOf().optionalFieldOf("out_labels", List.of())
                    .forGetter(EntryPointNodeData::outLabels)
            ).apply(instance, EntryPointNodeData::new)
    );

    public EntryPointNodeData {
        outLabels = List.copyOf(outLabels);
    }

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public @Nullable AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return ctx.entryWidget(outLabels);
    }

    @Override
    public boolean hasDataOutputs() {
        return outLabels.stream().anyMatch(label -> !label.isBlank());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return new ContextVariableAccessNode(
            fromPin >= 0 && fromPin < outLabels.size() ? outLabels.get(fromPin) : "", in.id());
    }
}
