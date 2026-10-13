package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.AkytheraScriptFunctions;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.FlowFunctionNode;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.PlayAbilityCueNodeWidget;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Plays one fixed Java ability cue, the [ScriptType] of its script class. Its pins are those of
/// [PlayAbilityCueNodeData].
public record PlayAbilityCueScriptNodeData(ScriptType script) implements FlowNodeData {
    public static final MapCodec<PlayAbilityCueScriptNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().fieldOf("script")
                    .forGetter(PlayAbilityCueScriptNodeData::script)
            ).apply(instance, PlayAbilityCueScriptNodeData::new)
    );

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new PlayAbilityCueNodeWidget(ctx.font(), script, ctx.x(), ctx.y());
    }

    @Override
    public void buildSkeleton(FlowSkeleton skeleton, FlowStatement self) {
        skeleton.readsInput(self, PlayAbilityCueNodeData.TARGET_PIN);
        skeleton.readsInput(self, PlayAbilityCueNodeData.SOURCE_PIN);
        skeleton.readsInput(self, PlayAbilityCueNodeData.WITH_STATE_PIN);
        skeleton.readsInput(self, PlayAbilityCueNodeData.ENDS_WITH_ABILITY_PIN);
        skeleton.chainInto(self.nodeId(), 0, skeleton.newSequenceChild(self));
    }

    @Override
    public FlowNode emit(FlowEmitter emitter, FlowStatement self) {
        FlowStatement nextSlot = self.child(0);
        @Nullable FlowNode next = emitter.isEmpty(nextSlot) ? null : emitter.emit(nextSlot);
        int id = self.nodeId();

        return new FlowFunctionNode(
            AkytheraScriptFunctions.PLAY_ABILITY_CUE_SCRIPT.value(),
            List.of(
                emitter.compileInputOr(id, PlayAbilityCueNodeData.TARGET_PIN, AbilitySystemComponent.class),
                AnimGraphFunctions.stringLiteral(AkytheraRegistries.Frozen.SCRIPT_TYPE.getKey(script).toString()),
                emitter.compileInputOr(id, PlayAbilityCueNodeData.SOURCE_PIN, AbilityInstance.class),
                emitter.compileInputOr(id, PlayAbilityCueNodeData.WITH_STATE_PIN, Boolean.class),
                emitter.compileInputOr(id, PlayAbilityCueNodeData.ENDS_WITH_ABILITY_PIN, Boolean.class)
            ),
            next,
            id
        );
    }
}
