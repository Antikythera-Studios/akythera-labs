package com.yesman.akytheralabs.gui.widget.script.node;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.AkytheraScriptFunctions;
import com.yesman.akythera.api.data.modularize.node.FlowFunctionNode;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.core.blueprint.AbilityCueBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import com.yesman.akytheralabs.gui.widget.script.PlayAbilityCueNodeWidget;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

/// Plays one fixed ability cue. Input pin 0 is execution, followed by the component, the source
/// ability, whether the graphs read its state and whether the play ends with it; output pin 0 is
/// execution.
///
/// The cue is the target's **shared live holder**, so a rename of that entry is reflected here with no
/// rewrite.
public record PlayAbilityCueNodeData(Holder<AbilityCueBlueprint> cue) implements FlowNodeData {
    public static final MapCodec<PlayAbilityCueNodeData> CODEC = RecordCodecBuilder.mapCodec(
        instance ->
            instance.group(
                EditorHolderCodecs.entry(AkytheraRegistries.DataPack.ABILITY_CUE_KEY).fieldOf("asset")
                    .forGetter(PlayAbilityCueNodeData::cue)
            ).apply(instance, PlayAbilityCueNodeData::new)
    );

    static final int TARGET_PIN = 1;
    static final int SOURCE_PIN = 2;
    static final int WITH_STATE_PIN = 3;
    static final int ENDS_WITH_ABILITY_PIN = 4;

    @Override
    public MapCodec<? extends NodeData> mapCodec() {
        return CODEC;
    }

    @Override
    public AbstractNodeWidget createWidget(NodeBuildContext ctx) {
        return new PlayAbilityCueNodeWidget(ctx.font(), cue, ctx.x(), ctx.y());
    }

    @Override
    public void buildSkeleton(FlowSkeleton skeleton, FlowStatement self) {
        skeleton.readsInput(self, TARGET_PIN);
        skeleton.readsInput(self, SOURCE_PIN);
        skeleton.readsInput(self, WITH_STATE_PIN);
        skeleton.readsInput(self, ENDS_WITH_ABILITY_PIN);
        skeleton.chainInto(self.nodeId(), 0, skeleton.newSequenceChild(self));
    }

    @Override
    public FlowNode emit(FlowEmitter emitter, FlowStatement self) {
        FlowStatement nextSlot = self.child(0);
        @Nullable FlowNode next = emitter.isEmpty(nextSlot) ? null : emitter.emit(nextSlot);
        int id = self.nodeId();

        return new FlowFunctionNode(
            AkytheraScriptFunctions.PLAY_ABILITY_CUE.value(),
            List.of(
                emitter.compileInputOr(id, TARGET_PIN, AbilitySystemComponent.class),
                AnimGraphFunctions.stringLiteral(EditorHolderCodecs.entryId(cue).toString()),
                emitter.compileInputOr(id, SOURCE_PIN, AbilityInstance.class),
                emitter.compileInputOr(id, WITH_STATE_PIN, Boolean.class),
                emitter.compileInputOr(id, ENDS_WITH_ABILITY_PIN, Boolean.class)
            ),
            next,
            id
        );
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return EditorHolderCodecs.referenceOf(cue);
    }

    @Override
    public Stream<Identifier> missingReferences() {
        return EditorHolderCodecs.missingOf(cue);
    }
}
