package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.SelfNodeWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/// Yields the script owner. What that lowers to is the canvas' business: a script-backed canvas emits
/// `SelfNode`, a headless one a context read (see [PureCompileContext#selfValue]).
public record SelfNodeData() implements NodeData {
    public static final MapCodec<SelfNodeData> CODEC = MapCodec.unit(new SelfNodeData());

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new SelfNodeWidget(
            ctx.font(), Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_SELF),
            ctx.x(), ctx.y());
    }

    @Override
    public PureNode compilePure(NodeInputs in, int fromPin, @Nullable Class<?> expectedType) {
        return in.ctx().selfValue();
    }
}
