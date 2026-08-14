package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.modularize.node.BranchNode;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.PureNode;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.BranchingNodeWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Runs one of two execution chains by the boolean on data input 1. Input 0 and outputs 0 / 1 are
/// execution pins ("True" / "False").
public record BranchNodeData() implements FlowNodeData {
    public static final MapCodec<BranchNodeData> CODEC = MapCodec.unit(new BranchNodeData());

    private static final int CONDITION_PIN = 1;

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new BranchingNodeWidget(
            ctx.font(), Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_NODE_BRANCH),
            ctx.x(), ctx.y());
    }

    @Override
    public void buildSkeleton(FlowSkeleton skeleton, FlowStatement self) {
        skeleton.readsInput(self, CONDITION_PIN);

        FlowStatement whenTrue = skeleton.newSequenceChild(self);
        FlowStatement whenFalse = skeleton.newSequenceChild(self);
        skeleton.chainInto(self.nodeId(), 0, whenTrue);
        skeleton.chainInto(self.nodeId(), 1, whenFalse);
    }

    @Override
    public FlowNode emit(FlowEmitter emitter, FlowStatement self) {
        PureNode condition = emitter.compileInputOr(self.nodeId(), CONDITION_PIN, boolean.class);

        return new BranchNode(
            condition, arm(emitter, self.child(0)), arm(emitter, self.child(1)), self.nodeId());
    }

    /// A branch arm as the list [BranchNode] wants — emitted as one node so any values hoisted at the
    /// arm are applied; an empty arm yields no nodes.
    private static List<FlowNode> arm(FlowEmitter emitter, @Nullable FlowStatement arm) {
        if (arm == null || emitter.isEmpty(arm)) return List.of();

        return List.of(emitter.emit(arm));
    }
}
